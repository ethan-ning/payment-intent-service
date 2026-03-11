package com.payments.intentservice.application.usecase

import com.payments.intentservice.application.port.inbound.CapturePaymentIntentCommand
import com.payments.intentservice.application.port.inbound.CapturePaymentIntentUseCase
import com.payments.intentservice.application.port.outbound.*
import com.payments.intentservice.domain.exception.PaymentAttemptNotFoundException
import com.payments.intentservice.domain.exception.PaymentIntentNotFoundException
import com.payments.intentservice.domain.model.PaymentIntent
import com.fasterxml.jackson.databind.ObjectMapper

class CapturePaymentIntentUseCaseImpl(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val outboxRepository: OutboxRepository,
    private val paymentProcessor: PaymentProcessor,
    private val objectMapper: ObjectMapper
) : CapturePaymentIntentUseCase {

    override fun execute(id: String, command: CapturePaymentIntentCommand): PaymentIntent {
        val intent = paymentIntentRepository.findById(id)
            ?: throw PaymentIntentNotFoundException(id)

        // Capture happens on the latest attempt
        val latestAttempt = paymentAttemptRepository.findLatestByPaymentIntentId(id)
            ?: throw PaymentAttemptNotFoundException("No attempt found for PaymentIntent[$id]")

        val captureAmount = command.amountToCapture ?: intent.amount.amount

        paymentProcessor.capture(id, captureAmount)

        val (capturedAttempt, attemptEvent) = latestAttempt.capture(
            captureAmount = captureAmount,
            processorRef = null
        )
        paymentAttemptRepository.update(capturedAttempt)
        outboxRepository.save(OutboxEvent(
            aggregateId = capturedAttempt.id,
            aggregateType = "PaymentAttempt",
            eventType = attemptEvent.eventType,
            payload = objectMapper.writeValueAsString(attemptEvent)
        ))

        // Apply to intent — domain enforces succeeded attempt must be latest
        val allAttempts = paymentAttemptRepository.findAllByPaymentIntentId(id)
        val (succeededIntent, intentEvent) = intent.applyAttemptSucceeded(capturedAttempt, allAttempts)
        val savedIntent = paymentIntentRepository.update(succeededIntent)
        outboxRepository.save(OutboxEvent(
            aggregateId = id,
            eventType = intentEvent.eventType,
            payload = objectMapper.writeValueAsString(intentEvent)
        ))

        return savedIntent
    }
}
