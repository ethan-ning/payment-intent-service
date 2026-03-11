package com.payments.intentservice.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.payments.intentservice.application.port.outbound.PaymentAttemptRepository
import com.payments.intentservice.domain.model.NextAction
import com.payments.intentservice.domain.model.PaymentAttempt
import com.payments.intentservice.domain.model.Currency
import com.payments.intentservice.domain.model.Money
import com.payments.intentservice.infrastructure.persistence.entity.PaymentAttemptEntity
import com.payments.intentservice.infrastructure.persistence.jpa.PaymentAttemptJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class JpaPaymentAttemptRepository(
    private val jpaRepository: PaymentAttemptJpaRepository,
    private val objectMapper: ObjectMapper
) : PaymentAttemptRepository {

    override fun save(attempt: PaymentAttempt): PaymentAttempt {
        jpaRepository.save(attempt.toEntity())
        return attempt
    }

    override fun update(attempt: PaymentAttempt): PaymentAttempt {
        val entity = jpaRepository.getReferenceById(attempt.id)
        entity.status = attempt.status
        entity.capturedAmount = attempt.capturedAmount
        entity.processorReference = attempt.processorReference
        entity.failureCode = attempt.failureCode
        entity.failureMessage = attempt.failureMessage
        entity.nextAction = attempt.nextAction?.let { objectMapper.writeValueAsString(it) }
        entity.updatedAt = attempt.updatedAt
        jpaRepository.save(entity)
        return attempt
    }

    @Transactional(readOnly = true)
    override fun findById(id: String): PaymentAttempt? =
        jpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findLatestByPaymentIntentId(paymentIntentId: String): PaymentAttempt? =
        jpaRepository.findLatestByPaymentIntentId(paymentIntentId, PageRequest.of(0, 1))
            .firstOrNull()?.toDomain()

    @Transactional(readOnly = true)
    override fun findAllByPaymentIntentId(paymentIntentId: String): List<PaymentAttempt> =
        jpaRepository.findAllByPaymentIntentId(paymentIntentId).map { it.toDomain() }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private fun PaymentAttemptEntity.toDomain() = PaymentAttempt(
        id = id,
        paymentIntentId = paymentIntentId,
        amount = Money(amount, Currency.of(currency)),
        status = status,
        paymentMethodId = paymentMethodId,
        capturedAmount = capturedAmount,
        processorReference = processorReference,
        failureCode = failureCode,
        failureMessage = failureMessage,
        nextAction = nextAction?.let { objectMapper.readValue(it, NextAction::class.java) },
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun PaymentAttempt.toEntity() = PaymentAttemptEntity(
        id = id,
        paymentIntentId = paymentIntentId,
        amount = amount.amount,
        currency = amount.currency.code,
        status = status,
        paymentMethodId = paymentMethodId,
        capturedAmount = capturedAmount,
        processorReference = processorReference,
        failureCode = failureCode,
        failureMessage = failureMessage,
        nextAction = nextAction?.let { objectMapper.writeValueAsString(it) },
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
