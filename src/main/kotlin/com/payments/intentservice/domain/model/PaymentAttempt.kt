package com.payments.intentservice.domain.model

import com.payments.intentservice.domain.event.PaymentAttemptEvent
import com.payments.intentservice.domain.exception.InvalidStateTransitionException
import java.time.Instant

/**
 * PaymentAttempt — models a single shopper payment try.
 *
 * A PaymentIntent can accumulate multiple failed attempts before one succeeds.
 * Business invariants:
 *   - At most ONE attempt can be in SUCCEEDED state per intent
 *   - The succeeded attempt, if any, must be the latest attempt
 *   - No new attempt can be created once one has SUCCEEDED
 *
 * No framework annotations — pure Kotlin domain model.
 */
data class PaymentAttempt(
    val id: String,                          // at_xxxxxxxxxxxxxxxxxxxxxxxx
    val paymentIntentId: String,
    val amount: Money,
    val status: PaymentAttemptStatus,
    val paymentMethodId: String?,
    val capturedAmount: Long?,               // set after capture (manual capture flow)
    val processorReference: String?,         // external processor transaction ID
    val failureCode: String?,
    val failureMessage: String?,
    val nextAction: NextAction?,             // populated when status = REQUIRES_ACTION
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun markProcessing(): Pair<PaymentAttempt, PaymentAttemptEvent> {
        requireStatus(PaymentAttemptStatus.PENDING)
        val updated = copy(status = PaymentAttemptStatus.PROCESSING, updatedAt = Instant.now())
        return updated to PaymentAttemptEvent.Processing(id, paymentIntentId)
    }

    fun requireAction(action: NextAction): Pair<PaymentAttempt, PaymentAttemptEvent> {
        requireStatus(PaymentAttemptStatus.PENDING, PaymentAttemptStatus.PROCESSING)
        val updated = copy(
            status = PaymentAttemptStatus.REQUIRES_ACTION,
            nextAction = action,
            updatedAt = Instant.now()
        )
        return updated to PaymentAttemptEvent.ActionRequired(id, paymentIntentId, action.type)
    }

    fun succeed(processorRef: String?): Pair<PaymentAttempt, PaymentAttemptEvent> {
        requireStatus(PaymentAttemptStatus.PENDING, PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.REQUIRES_ACTION)
        val updated = copy(
            status = PaymentAttemptStatus.SUCCEEDED,
            processorReference = processorRef,
            updatedAt = Instant.now()
        )
        return updated to PaymentAttemptEvent.Succeeded(id, paymentIntentId, amount.amount, amount.currency.code)
    }

    fun capture(captureAmount: Long, processorRef: String?): Pair<PaymentAttempt, PaymentAttemptEvent> {
        requireStatus(PaymentAttemptStatus.PENDING, PaymentAttemptStatus.PROCESSING)
        require(captureAmount <= amount.amount) {
            "Capture amount $captureAmount exceeds authorized amount ${amount.amount}"
        }
        val updated = copy(
            status = PaymentAttemptStatus.SUCCEEDED,
            capturedAmount = captureAmount,
            processorReference = processorRef,
            updatedAt = Instant.now()
        )
        return updated to PaymentAttemptEvent.Succeeded(id, paymentIntentId, captureAmount, amount.currency.code)
    }

    fun fail(code: String?, message: String?): Pair<PaymentAttempt, PaymentAttemptEvent> {
        requireStatus(PaymentAttemptStatus.PENDING, PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.REQUIRES_ACTION)
        val updated = copy(
            status = PaymentAttemptStatus.FAILED,
            failureCode = code,
            failureMessage = message,
            updatedAt = Instant.now()
        )
        return updated to PaymentAttemptEvent.Failed(id, paymentIntentId, code, message)
    }

    fun cancel(): Pair<PaymentAttempt, PaymentAttemptEvent> {
        requireStatus(PaymentAttemptStatus.PENDING, PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.REQUIRES_ACTION)
        val updated = copy(status = PaymentAttemptStatus.CANCELLED, updatedAt = Instant.now())
        return updated to PaymentAttemptEvent.Cancelled(id, paymentIntentId)
    }

    val isTerminal: Boolean
        get() = status in setOf(
            PaymentAttemptStatus.SUCCEEDED,
            PaymentAttemptStatus.FAILED,
            PaymentAttemptStatus.CANCELLED
        )

    private fun requireStatus(vararg allowed: PaymentAttemptStatus) {
        if (status !in allowed) {
            throw InvalidStateTransitionException(
                aggregateId = id,
                fromStatus = status.name,
                toStatus = "any of ${allowed.map { it.name }}"
            )
        }
    }
}

enum class PaymentAttemptStatus {
    PENDING,
    PROCESSING,
    REQUIRES_ACTION,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

/**
 * Represents the next action the shopper must take (e.g. 3DS redirect).
 */
data class NextAction(
    val type: String,            // "redirect_to_url" | "use_stripe_sdk"
    val redirectUrl: String? = null
)
