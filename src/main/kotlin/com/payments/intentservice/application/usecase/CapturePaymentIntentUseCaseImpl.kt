package com.payments.intentservice.application.usecase

import com.payments.intentservice.application.port.inbound.CapturePaymentIntentCommand
import com.payments.intentservice.application.port.inbound.CapturePaymentIntentUseCase
import com.payments.intentservice.application.port.outbound.OutboxEvent
import com.payments.intentservice.application.port.outbound.OutboxRepository
import com.payments.intentservice.application.port.outbound.PaymentIntentRepository
import com.payments.intentservice.application.port.outbound.PaymentProcessor
import com.payments.intentservice.domain.exception.PaymentIntentNotFoundException
import com.payments.intentservice.domain.model.PaymentIntent
import com.fasterxml.jackson.databind.ObjectMapper

class CapturePaymentIntentUseCaseImpl(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val outboxRepository: OutboxRepository,
    private val paymentProcessor: PaymentProcessor,
    private val objectMapper: ObjectMapper
) : CapturePaymentIntentUseCase {

    override fun execute(id: String, command: CapturePaymentIntentCommand): PaymentIntent {
        val existing = paymentIntentRepository.findById(id)
            ?: throw PaymentIntentNotFoundException(id)

        val (captured, capturedEvent) = existing.capture(command.amountToCapture)

        paymentProcessor.capture(id, command.amountToCapture ?: existing.amount.amount)

        outboxRepository.save(
            OutboxEvent(
                aggregateId = id,
                eventType = capturedEvent.eventType,
                payload = objectMapper.writeValueAsString(capturedEvent)
            )
        )

        return paymentIntentRepository.update(captured)
    }
}
