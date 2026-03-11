package com.payments.intentservice.application.usecase

import com.payments.intentservice.application.port.inbound.CancelPaymentIntentCommand
import com.payments.intentservice.application.port.inbound.CancelPaymentIntentUseCase
import com.payments.intentservice.application.port.outbound.OutboxEvent
import com.payments.intentservice.application.port.outbound.OutboxRepository
import com.payments.intentservice.application.port.outbound.PaymentIntentRepository
import com.payments.intentservice.application.port.outbound.PaymentProcessor
import com.payments.intentservice.domain.exception.PaymentIntentNotFoundException
import com.fasterxml.jackson.databind.ObjectMapper

class CancelPaymentIntentUseCaseImpl(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val outboxRepository: OutboxRepository,
    private val paymentProcessor: PaymentProcessor,
    private val objectMapper: ObjectMapper
) : CancelPaymentIntentUseCase {

    override fun execute(id: String, command: CancelPaymentIntentCommand): com.payments.intentservice.domain.model.PaymentIntent {
        val existing = paymentIntentRepository.findById(id)
            ?: throw PaymentIntentNotFoundException(id)

        val (canceled, canceledEvent) = existing.cancel(command.cancellationReason)

        paymentProcessor.cancel(id)

        outboxRepository.save(
            OutboxEvent(
                aggregateId = id,
                eventType = canceledEvent.eventType,
                payload = objectMapper.writeValueAsString(canceledEvent)
            )
        )

        return paymentIntentRepository.update(canceled)
    }
}
