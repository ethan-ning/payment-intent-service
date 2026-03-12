package com.payments.intentservice.application.usecase

import com.payments.intentservice.application.port.inbound.ConfirmPaymentIntentCommand
import com.payments.intentservice.application.port.inbound.ConfirmPaymentIntentUseCase
import com.payments.intentservice.application.port.outbound.*
import com.payments.intentservice.domain.exception.PaymentIntentNotFoundException
import com.payments.intentservice.domain.model.NextAction
import com.payments.intentservice.domain.model.PaymentIntent
import com.payments.intentservice.domain.model.PaymentIntentStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 * Confirm use case — creates a new PaymentAttempt and processes it.
 *
 * Flow:
 *   1. Attach payment method if provided
 *   2. Create a new PaymentAttempt (domain enforces no-new-attempt-after-success)
 *   3. Delegate to PaymentProcessor
 *   4. Update attempt + intent status based on processor result
 *   5. If CIT succeeded and setup_future_usage is set → create/update PaymentInstrument
 *      in payment-instrument-service and record the network transaction ID
 *   6. Publish all events via outbox
 */
class ConfirmPaymentIntentUseCaseImpl(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val outboxRepository: OutboxRepository,
    private val paymentProcessor: PaymentProcessor,
    private val objectMapper: ObjectMapper,
    /**
     * Optional — null in environments where instrument service is not configured.
     * When null, setup_future_usage is accepted but silently no-ops.
     */
    private val instrumentServiceClient: InstrumentServiceClient? = null,
) : ConfirmPaymentIntentUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(id: String, command: ConfirmPaymentIntentCommand): PaymentIntent {
        val existing = paymentIntentRepository.findById(id)
            ?: throw PaymentIntentNotFoundException(id)

        // Resolve effective setup_future_usage: confirm-time override takes precedence
        val effectiveSetupFutureUsage = command.setupFutureUsage ?: existing.setupFutureUsage

        // Step 1: Move intent to REQUIRES_CONFIRMATION if not already
        val withPaymentMethod = if (existing.status == PaymentIntentStatus.REQUIRES_PAYMENT_METHOD
            && command.paymentMethod != null) {
            val (updated, attachEvent) = existing.attachPaymentMethod(command.paymentMethod.type.name)
            outboxRepository.save(OutboxEvent(
                aggregateId = id,
                eventType = attachEvent.eventType,
                payload = objectMapper.writeValueAsString(attachEvent)
            ))
            paymentIntentRepository.update(updated)
            updated
        } else {
            existing
        }

        // Step 2: Create a new PaymentAttempt with the chosen payment method
        val existingAttempts = paymentAttemptRepository.findAllByPaymentIntentId(id)
        val (intentWithAttempt, newAttempt, attemptCreatedEvent) = withPaymentMethod.createAttempt(
            chosenPaymentMethod = command.paymentMethod,
            existingAttempts = existingAttempts
        )
        val savedAttempt = paymentAttemptRepository.save(newAttempt)
        val savedIntent = paymentIntentRepository.update(intentWithAttempt)

        outboxRepository.save(OutboxEvent(
            aggregateId = savedAttempt.id,
            aggregateType = "PaymentAttempt",
            eventType = attemptCreatedEvent.eventType,
            payload = objectMapper.writeValueAsString(attemptCreatedEvent)
        ))

        // Step 3: Delegate to payment processor
        val processorResult = paymentProcessor.process(savedIntent)

        // Step 4: Update attempt + intent based on result
        return when {
            processorResult.requiresAction -> {
                val action = NextAction(
                    type = processorResult.actionType ?: "redirect_to_url",
                    redirectUrl = null
                )
                val (updatedAttempt, attemptEvent) = savedAttempt.requireAction(action)
                paymentAttemptRepository.update(updatedAttempt)

                val (updatedIntent, intentEvent) = savedIntent.confirm(requiresAction = true)
                paymentIntentRepository.update(updatedIntent)

                outboxRepository.save(OutboxEvent(aggregateId = updatedAttempt.id, aggregateType = "PaymentAttempt", eventType = attemptEvent.eventType, payload = objectMapper.writeValueAsString(attemptEvent)))
                outboxRepository.save(OutboxEvent(aggregateId = id, eventType = intentEvent.eventType, payload = objectMapper.writeValueAsString(intentEvent)))

                updatedIntent
            }

            processorResult.success -> {
                // Attempt succeeded — record the attempt
                val (succeededAttempt, attemptEvent) = savedAttempt.succeed(processorResult.processorReference)
                paymentAttemptRepository.update(succeededAttempt)
                outboxRepository.save(OutboxEvent(aggregateId = succeededAttempt.id, aggregateType = "PaymentAttempt", eventType = attemptEvent.eventType, payload = objectMapper.writeValueAsString(attemptEvent)))

                // Step 5: Handle setup_future_usage — create/update instrument in instrument service
                val instrumentId: String? = if (effectiveSetupFutureUsage != null) {
                    createOrUpdateInstrument(
                        intent = savedIntent,
                        effectiveSetupFutureUsage = effectiveSetupFutureUsage,
                        networkTransactionId = processorResult.networkTransactionId,
                        command = command,
                    )
                } else null

                // Apply success to intent, recording instrument ID if created
                val allAttempts = paymentAttemptRepository.findAllByPaymentIntentId(id)
                val (succeededIntent, intentEvent) = savedIntent.applyAttemptSucceeded(
                    attempt = succeededAttempt,
                    existingAttempts = allAttempts,
                    paymentInstrumentId = instrumentId,
                )
                val finalIntent = paymentIntentRepository.update(succeededIntent)
                outboxRepository.save(OutboxEvent(aggregateId = id, eventType = intentEvent.eventType, payload = objectMapper.writeValueAsString(intentEvent)))

                finalIntent
            }

            else -> {
                // Attempt failed — intent reverts to REQUIRES_PAYMENT_METHOD for retry
                val (failedAttempt, attemptEvent) = savedAttempt.fail(
                    processorResult.errorCode,
                    processorResult.errorMessage
                )
                paymentAttemptRepository.update(failedAttempt)
                outboxRepository.save(OutboxEvent(aggregateId = failedAttempt.id, aggregateType = "PaymentAttempt", eventType = attemptEvent.eventType, payload = objectMapper.writeValueAsString(attemptEvent)))

                val (updatedIntent, intentEvent) = savedIntent.applyAttemptFailed()
                val finalIntent = paymentIntentRepository.update(updatedIntent)
                outboxRepository.save(OutboxEvent(aggregateId = id, eventType = intentEvent.eventType, payload = objectMapper.writeValueAsString(intentEvent)))

                finalIntent
            }
        }
    }

    /**
     * After a successful CIT with setup_future_usage set:
     * 1. Create a PaymentInstrument in payment-instrument-service (or retrieve by fingerprint)
     * 2. Record the network transaction ID from the acquirer (enables future MIT)
     *
     * This is best-effort — a failure here should NOT roll back the payment.
     * Log and continue; the instrument can be created retroactively via an event.
     */
    private fun createOrUpdateInstrument(
        intent: PaymentIntent,
        effectiveSetupFutureUsage: com.payments.intentservice.domain.model.SetupFutureUsage,
        networkTransactionId: String?,
        command: ConfirmPaymentIntentCommand,
    ): String? {
        val client = instrumentServiceClient ?: run {
            log.warn("setup_future_usage={} set on intent {} but InstrumentServiceClient is not configured — skipping instrument creation",
                effectiveSetupFutureUsage, intent.id)
            return null
        }
        val paymentMethod = command.paymentMethod ?: run {
            log.warn("setup_future_usage set but no paymentMethod on confirm command for intent {} — skipping", intent.id)
            return null
        }

        return try {
            val instrumentId = client.createInstrument(
                CreateInstrumentRequest(
                    paymentMethod = paymentMethod,
                    customerId = intent.customerId,
                    setupFutureUsage = effectiveSetupFutureUsage,
                )
            )
            log.info("Created instrument {} for intent {} (setup_future_usage={})",
                instrumentId, intent.id, effectiveSetupFutureUsage)

            // Record network transaction ID — establishes stored credential for future MIT
            if (networkTransactionId != null) {
                client.recordStoredCredential(
                    instrumentId = instrumentId,
                    networkTransactionId = networkTransactionId,
                    paymentIntentId = intent.id,
                )
                log.info("Recorded stored credential on instrument {} (networkTxnId={})", instrumentId, networkTransactionId)
            } else {
                log.warn("No networkTransactionId from processor for intent {} — instrument {} created but not MIT-eligible yet",
                    intent.id, instrumentId)
            }

            instrumentId
        } catch (ex: Exception) {
            // Instrument creation failure must NOT fail the payment — it's post-auth
            log.error("Failed to create/update instrument after successful payment for intent {}: {}",
                intent.id, ex.message, ex)
            null
        }
    }
}
