package com.payments.intentservice.application.usecase

import com.payments.intentservice.application.port.inbound.CancelPaymentIntentCommand
import com.payments.intentservice.application.port.inbound.CancelPaymentIntentUseCase
import com.payments.intentservice.application.port.outbound.*
import com.payments.intentservice.domain.exception.PaymentIntentNotFoundException
import com.payments.intentservice.domain.model.PaymentAttemptStatus
import com.fasterxml.jackson.databind.ObjectMapper

class CancelPaymentIntentUseCaseImpl(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val outboxRepository: OutboxRepository,
    private val paymentProcessor: PaymentProcessor,
    private val objectMapper: ObjectMapper
) : CancelPaymentIntentUseCase {

    override fun execute(id: String, command: CancelPaymentIntentCommand) =
        paymentIntentRepository.findById(id)?.let { intent ->

            // Cancel any active (non-terminal) attempt
            val latestAttempt = paymentAttemptRepository.findLatestByPaymentIntentId(id)
            if (latestAttempt != null && !latestAttempt.isTerminal) {
                val (cancelledAttempt, attemptEvent) = latestAttempt.cancel()
                paymentAttemptRepository.update(cancelledAttempt)
                outboxRepository.save(OutboxEvent(
                    aggregateId = cancelledAttempt.id,
                    aggregateType = "PaymentAttempt",
                    eventType = attemptEvent.eventType,
                    payload = objectMapper.writeValueAsString(attemptEvent)
                ))
            }

            paymentProcessor.cancel(id)

            val (canceledIntent, intentEvent) = intent.cancel(command.cancellationReason)
            outboxRepository.save(OutboxEvent(
                aggregateId = id,
                eventType = intentEvent.eventType,
                payload = objectMapper.writeValueAsString(intentEvent)
            ))

            paymentIntentRepository.update(canceledIntent)

        } ?: throw PaymentIntentNotFoundException(id)
}
