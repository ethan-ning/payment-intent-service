package com.payments.intentservice.infrastructure.persistence.entity

import com.payments.intentservice.domain.model.*
import jakarta.persistence.*
import java.time.Instant

/**
 * JPA entity for payment_intents table.
 * Lives in infrastructure — the domain model is never annotated with JPA.
 * Mapping between this entity and the domain PaymentIntent happens in the repository.
 */
@Entity
@Table(
    name = "payment_intents",
    indexes = [
        Index(name = "idx_pi_customer_id", columnList = "customer_id"),
        Index(name = "idx_pi_status", columnList = "status"),
        Index(name = "idx_pi_created_at", columnList = "created_at"),
        Index(name = "idx_pi_idempotency_key", columnList = "idempotency_key")
    ]
)
class PaymentIntentEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 255)
    val id: String,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: PaymentIntentStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_method", nullable = false, length = 20)
    val captureMethod: CaptureMethod,

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_method", nullable = false, length = 20)
    val confirmationMethod: ConfirmationMethod,

    @Column(name = "customer_id", length = 255)
    val customerId: String? = null,

    @Column(name = "payment_method_id", length = 255)
    var paymentMethodId: String? = null,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: String = "{}",

    @Column(name = "idempotency_key", unique = true, length = 255)
    val idempotencyKey: String? = null,

    @Column(name = "client_secret", nullable = false, length = 500)
    val clientSecret: String,

    @Column(name = "latest_payment_attempt_id", length = 255)
    var latestPaymentAttemptId: String? = null,

    @Column(name = "canceled_at")
    var canceledAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 50)
    var cancellationReason: CancellationReason? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    // Required by JPA/Hibernate
    protected constructor() : this(
        id = "",
        amount = 0,
        currency = "",
        status = PaymentIntentStatus.REQUIRES_PAYMENT_METHOD,
        captureMethod = CaptureMethod.AUTOMATIC,
        confirmationMethod = ConfirmationMethod.AUTOMATIC,
        clientSecret = ""
    )
}
