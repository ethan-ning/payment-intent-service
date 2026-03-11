package com.payments.intentservice.application.usecase

import com.payments.intentservice.application.port.inbound.ConfirmPaymentIntentCommand
import com.payments.intentservice.application.port.inbound.ConfirmPaymentIntentUseCase
import com.payments.intentservice.application.port.outbound.OutboxEvent
import com.payments.intentservice.application.port.outbound.OutboxRepository
import com.payments.intentservice.application.port.outbound.PaymentIntentRepository
import com.payments.intentservice.application.port.outbound.PaymentProcessor
import com.payments.intentservice.domain.exception.PaymentIntentNotFoundException
import com.payments.intentservice.domain.model.PaymentIntent
import com.payments.intentservice.domain.model.PaymentIntentStatus
import com.fasterxml.jackson.databind.ObjectMapper

class ConfirmPaymentIntentUseCaseImpl(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val outboxRepository: OutboxRepository,
    private val paymentProcessor: PaymentProcessor,
    private val objectMapper: ObjectMapper
) : ConfirmPaymentIntentUseCase {

    override fun execute(id: String, command: ConfirmPaymentIntentCommand): PaymentIntent {
        val existing = paymentIntentRepository.findById(id)
            ?: throw PaymentIntentNotFoundException(id)

        // Attach payment method if provided in confirm call
        val withPaymentMethod = if (command.paymentMethodId != null && existing.paymentMethodId == null) {
            val (updated, attachEvent) = existing.attachPaymentMethod(command.paymentMethodId)
            outboxRepository.save(
                OutboxEvent(
                    aggregateId = id,
                    eventType = attachEvent.eventType,
                    payload = objectMapper.writeValueAsString(attachEvent)
                )
            )
            paymentIntentRepository.update(updated)
            updated
        } else {
            existing
        }

        // Delegate to processor to determine if 3DS/action is required
        val processorResult = paymentProcessor.process(withPaymentMethod)

        val (confirmed, confirmEvent) = withPaymentMethod.confirm(
            requiresAction = processorResult.requiresAction
        )

        outboxRepository.save(
            OutboxEvent(
                aggregateId = id,
                eventType = confirmEvent.eventType,
                payload = objectMapper.writeValueAsString(confirmEvent)
            )
        )

        val saved = paymentIntentRepository.update(confirmed)

        // If auto-capture and processing → attempt to succeed immediately
        if (saved.status == PaymentIntentStatus.PROCESSING && !processorResult.requiresAction) {
            if (processorResult.success) {
                val (succeeded, succeededEvent) = saved.succeed()
                outboxRepository.save(
                    OutboxEvent(
                        aggregateId = id,
                        eventType = succeededEvent.eventType,
                        payload = objectMapper.writeValueAsString(succeededEvent)
                    )
                )
                return paymentIntentRepository.update(succeeded)
            }
        }

        return saved
    }
}
