package com.payments.intentservice.application.usecase

import com.payments.intentservice.application.port.inbound.ConfirmPaymentIntentCommand
import com.payments.intentservice.application.port.inbound.ConfirmPaymentIntentUseCase
import com.payments.intentservice.application.port.outbound.*
import com.payments.intentservice.domain.exception.PaymentIntentNotFoundException
import com.payments.intentservice.domain.model.PaymentIntent
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Confirm use case — creates a new PaymentAttempt and processes it.
 *
 * Flow:
 *   1. Attach payment method if provided
 *   2. Create a new PaymentAttempt (domain enforces no-new-attempt-after-success)
 *   3. Delegate to PaymentProcessor
 *   4. Update attempt + intent status based on processor result
 *   5. Publish all events via outbox
 */
class ConfirmPaymentIntentUseCaseImpl(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val outboxRepository: OutboxRepository,
    private val paymentProcessor: PaymentProcessor,
    private val objectMapper: ObjectMapper
) : ConfirmPaymentIntentUseCase {

    override fun execute(id: String, command: ConfirmPaymentIntentCommand): PaymentIntent {
        val existing = paymentIntentRepository.findById(id)
            ?: throw PaymentIntentNotFoundException(id)

        // Step 1: Move intent to REQUIRES_CONFIRMATION if not already
        val withPaymentMethod = if (existing.status == com.payments.intentservice.domain.model.PaymentIntentStatus.REQUIRES_PAYMENT_METHOD
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

        // Step 3: Delegate to processor
        val processorResult = paymentProcessor.process(savedIntent)

        // Step 4: Update attempt and intent based on result
        return when {
            processorResult.requiresAction -> {
                val action = com.payments.intentservice.domain.model.NextAction(
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
                // Attempt succeeded
                val (succeededAttempt, attemptEvent) = savedAttempt.succeed(processorResult.processorReference)
                paymentAttemptRepository.update(succeededAttempt)
                outboxRepository.save(OutboxEvent(aggregateId = succeededAttempt.id, aggregateType = "PaymentAttempt", eventType = attemptEvent.eventType, payload = objectMapper.writeValueAsString(attemptEvent)))

                // Apply to intent — domain enforces succeeded attempt must be latest
                val allAttempts = paymentAttemptRepository.findAllByPaymentIntentId(id)
                val (succeededIntent, intentEvent) = savedIntent.applyAttemptSucceeded(succeededAttempt, allAttempts)
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
}
