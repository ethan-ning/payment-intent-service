package com.payments.intentservice.domain.event

import java.time.Instant
import java.util.UUID

/**
 * Domain events for the PaymentAttempt lifecycle.
 */
sealed class PaymentAttemptEvent {
    abstract val eventId: String
    abstract val attemptId: String
    abstract val paymentIntentId: String
    abstract val occurredAt: Instant

    data class Created(
        override val attemptId: String,
        override val paymentIntentId: String,
        val amount: Long,
        val currency: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentAttemptEvent()

    data class Processing(
        override val attemptId: String,
        override val paymentIntentId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentAttemptEvent()

    data class ActionRequired(
        override val attemptId: String,
        override val paymentIntentId: String,
        val actionType: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentAttemptEvent()

    data class Succeeded(
        override val attemptId: String,
        override val paymentIntentId: String,
        val amountCaptured: Long,
        val currency: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentAttemptEvent()

    data class Failed(
        override val attemptId: String,
        override val paymentIntentId: String,
        val failureCode: String?,
        val failureMessage: String?,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentAttemptEvent()

    data class Cancelled(
        override val attemptId: String,
        override val paymentIntentId: String,
        override val eventId: String = UUID.randomUUID().toString(),
        override val occurredAt: Instant = Instant.now()
    ) : PaymentAttemptEvent()

    val eventType: String
        get() = when (this) {
            is Created -> "payment_attempt.created"
            is Processing -> "payment_attempt.processing"
            is ActionRequired -> "payment_attempt.action_required"
            is Succeeded -> "payment_attempt.succeeded"
            is Failed -> "payment_attempt.failed"
            is Cancelled -> "payment_attempt.cancelled"
        }
}
