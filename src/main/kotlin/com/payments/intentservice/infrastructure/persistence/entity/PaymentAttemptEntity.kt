package com.payments.intentservice.infrastructure.persistence.entity

import com.payments.intentservice.domain.model.PaymentAttemptStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "payment_attempts",
    indexes = [
        Index(name = "idx_pa_payment_intent_id", columnList = "payment_intent_id"),
        Index(name = "idx_pa_status", columnList = "status"),
        Index(name = "idx_pa_created_at", columnList = "created_at")
    ]
)
class PaymentAttemptEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 255)
    val id: String,

    @Column(name = "payment_intent_id", nullable = false, updatable = false, length = 255)
    val paymentIntentId: String,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: PaymentAttemptStatus,

    /**
     * The payment method (scheme/type) used in this attempt.
     * Stored as JSONB — a discriminated union keyed by "type".
     * e.g. {"type":"CARD","scheme":"VISA","last4":"4242","expiryMonth":12,"expiryYear":2026,...}
     * or   {"type":"WECHAT_PAY","transactionReference":"..."}
     */
    @Column(name = "payment_method", columnDefinition = "jsonb")
    var paymentMethod: String? = null,

    @Column(name = "payment_method_type", length = 50)
    var paymentMethodType: String? = null,   // denormalized for indexed queries

    @Column(name = "captured_amount")
    var capturedAmount: Long? = null,

    @Column(name = "processor_reference", length = 255)
    var processorReference: String? = null,

    @Column(name = "failure_code", length = 100)
    var failureCode: String? = null,

    @Column(name = "failure_message", columnDefinition = "TEXT")
    var failureMessage: String? = null,

    /** JSON blob: { "type": "redirect_to_url", "redirectUrl": "https://..." } */
    @Column(name = "next_action", columnDefinition = "jsonb")
    var nextAction: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    protected constructor() : this(
        id = "",
        paymentIntentId = "",
        amount = 0,
        currency = "",
        status = PaymentAttemptStatus.PENDING
    )
}
