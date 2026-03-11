package com.payments.intentservice.application.port.outbound

import com.payments.intentservice.application.port.inbound.ListPaymentIntentsQuery
import com.payments.intentservice.application.port.inbound.Page
import com.payments.intentservice.domain.event.PaymentIntentEvent
import com.payments.intentservice.domain.model.PaymentIntent
import java.time.Duration

// ─── Repository Port ──────────────────────────────────────────────────────────

interface PaymentIntentRepository {
    fun save(paymentIntent: PaymentIntent): PaymentIntent
    fun update(paymentIntent: PaymentIntent): PaymentIntent
    fun findById(id: String): PaymentIntent?
    fun findByIdempotencyKey(key: String): PaymentIntent?
    fun findAll(query: ListPaymentIntentsQuery): Page<PaymentIntent>
}

// ─── Outbox Port ──────────────────────────────────────────────────────────────

data class OutboxEvent(
    val aggregateId: String,
    val aggregateType: String = "PaymentIntent",
    val eventType: String,
    val payload: String          // JSON-serialized event
)

interface OutboxRepository {
    fun save(event: OutboxEvent)
}

// ─── Idempotency Port ────────────────────────────────────────────────────────

data class IdempotencyRecord(
    val key: String,
    val requestHash: String,
    val responseBody: String,
    val statusCode: Int
)

interface IdempotencyStore {
    fun get(key: String): IdempotencyRecord?
    fun set(key: String, record: IdempotencyRecord, ttl: Duration = Duration.ofHours(24))
    fun tryAcquireLock(key: String, ttl: Duration = Duration.ofSeconds(30)): Boolean
    fun releaseLock(key: String)
}

// ─── Event Publisher Port ────────────────────────────────────────────────────

interface DomainEventPublisher {
    fun publish(event: PaymentIntentEvent)
}

// ─── Payment Processor Port ──────────────────────────────────────────────────

data class ProcessorResult(
    val success: Boolean,
    val requiresAction: Boolean = false,
    val actionType: String? = null,
    val processorReference: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

interface PaymentProcessor {
    fun process(paymentIntent: PaymentIntent): ProcessorResult
    fun capture(paymentIntentId: String, amount: Long): ProcessorResult
    fun cancel(paymentIntentId: String): ProcessorResult
}
