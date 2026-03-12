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

// ─── Payment Attempt Repository Port ─────────────────────────────────────────

interface PaymentAttemptRepository {
    fun save(attempt: com.payments.intentservice.domain.model.PaymentAttempt): com.payments.intentservice.domain.model.PaymentAttempt
    fun update(attempt: com.payments.intentservice.domain.model.PaymentAttempt): com.payments.intentservice.domain.model.PaymentAttempt
    fun findById(id: String): com.payments.intentservice.domain.model.PaymentAttempt?
    fun findLatestByPaymentIntentId(paymentIntentId: String): com.payments.intentservice.domain.model.PaymentAttempt?
    fun findAllByPaymentIntentId(paymentIntentId: String): List<com.payments.intentservice.domain.model.PaymentAttempt>
}

// ─── Payment Processor Port ──────────────────────────────────────────────────

data class ProcessorResult(
    val success: Boolean,
    val requiresAction: Boolean = false,
    val actionType: String? = null,
    val processorReference: String? = null,
    /**
     * Network transaction ID returned by the acquiring network on a successful CIT authorization.
     * Must be stored on the PaymentInstrument to enable future MIT charges (stored credential framework).
     */
    val networkTransactionId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

interface PaymentProcessor {
    fun process(paymentIntent: PaymentIntent): ProcessorResult
    fun capture(paymentIntentId: String, amount: Long): ProcessorResult
    fun cancel(paymentIntentId: String): ProcessorResult
}

// ─── Instrument Service Client Port ──────────────────────────────────────────

/**
 * Outbound port for calling payment-instrument-service.
 * Implemented in infrastructure by an HTTP client adapter.
 */
interface InstrumentServiceClient {
    /**
     * Create (or retrieve by fingerprint) a saved PaymentInstrument from the
     * transient PaymentMethod used in this CIT.
     *
     * Called after a successful payment when setup_future_usage is set.
     * Returns the instrument ID (pm_xxx).
     */
    fun createInstrument(request: CreateInstrumentRequest): String

    /**
     * Record the network transaction ID on an existing instrument after a successful CIT.
     * This establishes the stored credential and makes the instrument MIT-eligible.
     */
    fun recordStoredCredential(
        instrumentId: String,
        networkTransactionId: String,
        paymentIntentId: String,
    )
}

data class CreateInstrumentRequest(
    val paymentMethod: com.payments.intentservice.domain.model.PaymentMethod,
    val customerId: String?,
    val setupFutureUsage: com.payments.intentservice.domain.model.SetupFutureUsage,
    val billingDetails: BillingDetailsData? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class BillingDetailsData(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
)
