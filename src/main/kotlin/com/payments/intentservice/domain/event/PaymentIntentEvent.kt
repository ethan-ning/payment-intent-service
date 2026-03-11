package com.payments.intentservice.domain.event

import java.time.Instant
import java.util.UUID

/**
 * Domain events for the PaymentIntent aggregate.
 * These are pure Kotlin — no framework deps.
 */
sealed class PaymentIntentEvent {
    abstract val eventId: String
    abstract val paymentIntentId: String
    abstract val occurredAt: Instant

    data class Created(
        override val paymentIntentId: String,
        val amount: Long,
        val currency: String,
        val captureMethod: String,
        val confirmationMethod: String,
        val customerId: String?,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentIntentEvent()

    data class PaymentMethodAttached(
        override val paymentIntentId: String,
        val paymentMethodId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentIntentEvent()

    data class Confirmed(
        override val paymentIntentId: String,
        val newStatus: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentIntentEvent()

    data class ActionRequired(
        override val paymentIntentId: String,
        val actionType: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentIntentEvent()

    data class Captured(
        override val paymentIntentId: String,
        val amountCaptured: Long,
        val currency: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentIntentEvent()

    data class Succeeded(
        override val paymentIntentId: String,
        val amount: Long,
        val currency: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentIntentEvent()

    data class Canceled(
        override val paymentIntentId: String,
        val reason: String?,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentIntentEvent()

    /** Emitted when an attempt fails — intent reverts to REQUIRES_PAYMENT_METHOD for retry */
    data class AttemptFailed(
        override val paymentIntentId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentIntentEvent()

    /** Canonical event type string for Kafka/outbox routing */
    val eventType: String
        get() = when (this) {
            is Created -> "payment_intent.created"
            is PaymentMethodAttached -> "payment_intent.payment_method_attached"
            is Confirmed -> "payment_intent.confirmed"
            is ActionRequired -> "payment_intent.action_required"
            is Captured -> "payment_intent.captured"
            is Succeeded -> "payment_intent.succeeded"
            is Canceled -> "payment_intent.canceled"
            is AttemptFailed -> "payment_intent.attempt_failed"
        }
}
