package com.payments.intentservice.application.usecase

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.payments.intentservice.application.port.inbound.CreatePaymentIntentCommand
import com.payments.intentservice.application.port.inbound.CreatePaymentIntentUseCase
import com.payments.intentservice.application.port.outbound.OutboxEvent
import com.payments.intentservice.application.port.outbound.OutboxRepository
import com.payments.intentservice.application.port.outbound.PaymentIntentRepository
import com.payments.intentservice.domain.event.PaymentIntentEvent
import com.payments.intentservice.domain.exception.InvalidCurrencyException
import com.payments.intentservice.domain.exception.InvalidPaymentAmountException
import com.payments.intentservice.domain.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Use case: Create a new PaymentIntent.
 * Validates input, creates the aggregate, persists via outbox pattern.
 *
 * Note: @Transactional lives in the infrastructure adapter that calls this,
 * or the repository implementation — NOT here. This layer is framework-free.
 */
class CreatePaymentIntentUseCaseImpl(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) : CreatePaymentIntentUseCase {

    override fun execute(command: CreatePaymentIntentCommand): PaymentIntent {
        validateCommand(command)

        // Check idempotency
        if (command.idempotencyKey != null) {
            val existing = paymentIntentRepository.findByIdempotencyKey(command.idempotencyKey)
            if (existing != null) return existing
        }

        val id = generatePaymentIntentId()
        val currency = Currency.of(command.currency)
        val money = Money(command.amount, currency)
        val now = Instant.now()

        val paymentIntent = PaymentIntent(
            id = id,
            amount = money,
            status = if (command.paymentMethodId != null) {
                PaymentIntentStatus.REQUIRES_CONFIRMATION
            } else {
                PaymentIntentStatus.REQUIRES_PAYMENT_METHOD
            },
            captureMethod = command.captureMethod,
            confirmationMethod = command.confirmationMethod,
            customerId = command.customerId,
            paymentMethodId = command.paymentMethodId,
            description = command.description,
            metadata = command.metadata,
            idempotencyKey = command.idempotencyKey,
            clientSecret = generateClientSecret(id),
            canceledAt = null,
            cancellationReason = null,
            createdAt = now,
            updatedAt = now
        )

        val saved = paymentIntentRepository.save(paymentIntent)

        val createdEvent = PaymentIntentEvent.Created(
            paymentIntentId = saved.id,
            amount = saved.amount.amount,
            currency = saved.amount.currency.code,
            captureMethod = saved.captureMethod.name,
            confirmationMethod = saved.confirmationMethod.name,
            customerId = saved.customerId
        )

        outboxRepository.save(
            OutboxEvent(
                aggregateId = saved.id,
                eventType = createdEvent.eventType,
                payload = objectMapper.writeValueAsString(createdEvent)
            )
        )

        return saved
    }

    private fun validateCommand(command: CreatePaymentIntentCommand) {
        if (command.amount <= 0) throw InvalidPaymentAmountException(command.amount)
        try {
            Currency.of(command.currency)
        } catch (e: IllegalArgumentException) {
            throw InvalidCurrencyException(command.currency)
        }
    }

    private fun generatePaymentIntentId(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray()
        return "pi_${NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet, 24)}"
    }

    private fun generateClientSecret(id: String): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray()
        val secret = NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet, 24)
        return "${id}_secret_${secret}"
    }
}
