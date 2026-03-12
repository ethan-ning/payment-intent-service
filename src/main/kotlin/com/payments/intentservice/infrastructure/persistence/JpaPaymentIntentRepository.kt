package com.payments.intentservice.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.payments.intentservice.application.port.inbound.ListPaymentIntentsQuery
import com.payments.intentservice.application.port.inbound.Page
import com.payments.intentservice.application.port.outbound.PaymentIntentRepository
import com.payments.intentservice.domain.model.*
import com.payments.intentservice.domain.model.PaymentMethodType
import com.payments.intentservice.infrastructure.persistence.entity.PaymentIntentEntity
import com.payments.intentservice.infrastructure.persistence.jpa.PaymentIntentJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Implements the outbound port [PaymentIntentRepository] using Spring Data JPA.
 * All @Transactional annotations live here — never in the application/use case layer.
 *
 * Mapping: domain PaymentIntent ↔ JPA PaymentIntentEntity happens exclusively here.
 */
@Repository
@Transactional
class JpaPaymentIntentRepository(
    private val jpaRepository: PaymentIntentJpaRepository,
    private val objectMapper: ObjectMapper
) : PaymentIntentRepository {

    override fun save(paymentIntent: PaymentIntent): PaymentIntent {
        val entity = paymentIntent.toEntity()
        jpaRepository.save(entity)
        return paymentIntent
    }

    override fun update(paymentIntent: PaymentIntent): PaymentIntent {
        val entity = jpaRepository.getReferenceById(paymentIntent.id)
        entity.status = paymentIntent.status
        entity.paymentMethodId = paymentIntent.paymentMethodId
        entity.metadata = objectMapper.writeValueAsString(paymentIntent.metadata)
        entity.setupFutureUsage = paymentIntent.setupFutureUsage?.name
        entity.paymentInstrumentId = paymentIntent.paymentInstrumentId
        entity.latestPaymentAttemptId = paymentIntent.latestPaymentAttemptId
        entity.canceledAt = paymentIntent.canceledAt
        entity.cancellationReason = paymentIntent.cancellationReason
        entity.updatedAt = paymentIntent.updatedAt
        jpaRepository.save(entity)
        return paymentIntent
    }

    @Transactional(readOnly = true)
    override fun findById(id: String): PaymentIntent? =
        jpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findByIdempotencyKey(key: String): PaymentIntent? =
        jpaRepository.findByIdempotencyKey(key).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findAll(query: ListPaymentIntentsQuery): Page<PaymentIntent> {
        val limit = minOf(query.limit, 100)
        val pageable = PageRequest.of(0, limit + 1)

        val result = jpaRepository.findAllFiltered(
            customerId = query.customerId,
            startingAfter = query.startingAfter,
            endingBefore = query.endingBefore,
            pageable = pageable
        )

        val items = result.content.map { it.toDomain() }
        val hasMore = items.size > limit

        return Page(
            data = items.take(limit),
            hasMore = hasMore,
            totalCount = result.totalElements
        )
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun PaymentIntentEntity.toDomain(): PaymentIntent {
        val metadataMap: Map<String, String> = objectMapper.readValue(metadata, Map::class.java) as Map<String, String>
        return PaymentIntent(
            id = id,
            amount = Money(amount, Currency.of(currency)),
            status = status,
            captureMethod = captureMethod,
            confirmationMethod = confirmationMethod,
            customerId = customerId,
            paymentMethodId = paymentMethodId,
            description = description,
            metadata = metadataMap,
            idempotencyKey = idempotencyKey,
            clientSecret = clientSecret,
            availablePaymentMethods = availablePaymentMethods
                .takeIf { it.isNotEmpty() }
                ?.let { parseAvailablePaymentMethods(it) }
                ?: emptySet(),
            setupFutureUsage = setupFutureUsage?.let {
                com.payments.intentservice.domain.model.SetupFutureUsage.valueOf(it)
            },
            paymentInstrumentId = paymentInstrumentId,
            latestPaymentAttemptId = latestPaymentAttemptId,
            canceledAt = canceledAt,
            cancellationReason = cancellationReason,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseAvailablePaymentMethods(csv: String): Set<PaymentMethodType> =
        csv.split(",").filter { it.isNotBlank() }.map { PaymentMethodType.valueOf(it.trim()) }.toSet()

    private fun PaymentIntent.toEntity() = PaymentIntentEntity(
        id = id,
        amount = amount.amount,
        currency = amount.currency.code,
        status = status,
        captureMethod = captureMethod,
        confirmationMethod = confirmationMethod,
        customerId = customerId,
        paymentMethodId = paymentMethodId,
        description = description,
        metadata = objectMapper.writeValueAsString(metadata),
        idempotencyKey = idempotencyKey,
        clientSecret = clientSecret,
        availablePaymentMethods = availablePaymentMethods.joinToString(",") { it.name },
        setupFutureUsage = setupFutureUsage?.name,
        paymentInstrumentId = paymentInstrumentId,
        latestPaymentAttemptId = latestPaymentAttemptId,
        canceledAt = canceledAt,
        cancellationReason = cancellationReason,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
