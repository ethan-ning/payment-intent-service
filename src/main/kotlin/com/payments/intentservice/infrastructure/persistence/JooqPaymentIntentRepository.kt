package com.payments.intentservice.infrastructure.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.payments.intentservice.application.port.inbound.ListPaymentIntentsQuery
import com.payments.intentservice.application.port.inbound.Page
import com.payments.intentservice.application.port.outbound.PaymentIntentRepository
import com.payments.intentservice.domain.model.*
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * jOOQ implementation of PaymentIntentRepository.
 * @Transactional annotations belong here, NOT in the use case layer.
 */
@Repository
class JooqPaymentIntentRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) : PaymentIntentRepository {

    companion object {
        private val TABLE = table("payment_intents")
        private val OUTBOX_TABLE = table("outbox_events")
    }

    override fun save(paymentIntent: PaymentIntent): PaymentIntent {
        dsl.insertInto(TABLE)
            .set(field("id"), paymentIntent.id)
            .set(field("amount"), paymentIntent.amount.amount)
            .set(field("currency"), paymentIntent.amount.currency.code)
            .set(field("status"), paymentIntent.status.name.lowercase())
            .set(field("capture_method"), paymentIntent.captureMethod.name.lowercase())
            .set(field("confirmation_method"), paymentIntent.confirmationMethod.name.lowercase())
            .set(field("customer_id"), paymentIntent.customerId)
            .set(field("payment_method_id"), paymentIntent.paymentMethodId)
            .set(field("description"), paymentIntent.description)
            .set(field("metadata"), JSONB.valueOf(objectMapper.writeValueAsString(paymentIntent.metadata)))
            .set(field("idempotency_key"), paymentIntent.idempotencyKey)
            .set(field("client_secret"), paymentIntent.clientSecret)
            .set(field("canceled_at"), paymentIntent.canceledAt?.toLocalDateTimeUtc())
            .set(field("cancellation_reason"), paymentIntent.cancellationReason?.name?.lowercase())
            .set(field("created_at"), paymentIntent.createdAt.toLocalDateTimeUtc())
            .set(field("updated_at"), paymentIntent.updatedAt.toLocalDateTimeUtc())
            .execute()

        return paymentIntent
    }

    override fun update(paymentIntent: PaymentIntent): PaymentIntent {
        dsl.update(TABLE)
            .set(field("status"), paymentIntent.status.name.lowercase())
            .set(field("payment_method_id"), paymentIntent.paymentMethodId)
            .set(field("description"), paymentIntent.description)
            .set(field("metadata"), JSONB.valueOf(objectMapper.writeValueAsString(paymentIntent.metadata)))
            .set(field("canceled_at"), paymentIntent.canceledAt?.toLocalDateTimeUtc())
            .set(field("cancellation_reason"), paymentIntent.cancellationReason?.name?.lowercase())
            .set(field("updated_at"), paymentIntent.updatedAt.toLocalDateTimeUtc())
            .where(field("id").eq(paymentIntent.id))
            .execute()

        return paymentIntent
    }

    override fun findById(id: String): PaymentIntent? {
        return dsl.select()
            .from(TABLE)
            .where(field("id").eq(id))
            .fetchOne()
            ?.let { record -> mapToPaymentIntent(record.intoMap()) }
    }

    override fun findByIdempotencyKey(key: String): PaymentIntent? {
        return dsl.select()
            .from(TABLE)
            .where(field("idempotency_key").eq(key))
            .fetchOne()
            ?.let { record -> mapToPaymentIntent(record.intoMap()) }
    }

    override fun findAll(query: ListPaymentIntentsQuery): Page<PaymentIntent> {
        val limit = minOf(query.limit, 100)

        var condition = trueCondition()
        query.customerId?.let { condition = condition.and(field("customer_id").eq(it)) }
        query.startingAfter?.let { condition = condition.and(field("id").greaterThan(it)) }
        query.endingBefore?.let { condition = condition.and(field("id").lessThan(it)) }

        val total = dsl.fetchCount(TABLE, condition).toLong()

        val records = dsl.select()
            .from(TABLE)
            .where(condition)
            .orderBy(field("created_at").desc())
            .limit(limit + 1)
            .fetch()
            .map { record -> mapToPaymentIntent(record.intoMap()) }

        val hasMore = records.size > limit
        return Page(
            data = records.take(limit),
            hasMore = hasMore,
            totalCount = total
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToPaymentIntent(row: Map<String, Any?>): PaymentIntent {
        val metadataJson = row["metadata"]?.toString() ?: "{}"
        val metadata: Map<String, String> = objectMapper.readValue(metadataJson, Map::class.java) as Map<String, String>

        return PaymentIntent(
            id = row["id"] as String,
            amount = Money(
                amount = (row["amount"] as Number).toLong(),
                currency = Currency.of(row["currency"] as String)
            ),
            status = PaymentIntentStatus.valueOf((row["status"] as String).uppercase()),
            captureMethod = CaptureMethod.valueOf((row["capture_method"] as String).uppercase()),
            confirmationMethod = ConfirmationMethod.valueOf((row["confirmation_method"] as String).uppercase()),
            customerId = row["customer_id"] as? String,
            paymentMethodId = row["payment_method_id"] as? String,
            description = row["description"] as? String,
            metadata = metadata,
            idempotencyKey = row["idempotency_key"] as? String,
            clientSecret = row["client_secret"] as String,
            canceledAt = (row["canceled_at"] as? LocalDateTime)?.toInstantUtc(),
            cancellationReason = (row["cancellation_reason"] as? String)?.let {
                CancellationReason.valueOf(it.uppercase())
            },
            createdAt = (row["created_at"] as LocalDateTime).toInstantUtc(),
            updatedAt = (row["updated_at"] as LocalDateTime).toInstantUtc()
        )
    }

    private fun Instant.toLocalDateTimeUtc() = LocalDateTime.ofInstant(this, ZoneOffset.UTC)
    private fun LocalDateTime.toInstantUtc() = this.toInstant(ZoneOffset.UTC)
}
