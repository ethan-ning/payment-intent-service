package com.payments.intentservice.domain.model

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.payments.intentservice.domain.event.PaymentAttemptEvent
import com.payments.intentservice.domain.event.PaymentIntentEvent
import com.payments.intentservice.domain.exception.InvalidStateTransitionException
import com.payments.intentservice.domain.exception.PaymentAttemptViolationException
import java.time.Instant

/**
 * PaymentIntent — the core aggregate of this service.
 *
 * Owns the PaymentAttempt lifecycle and enforces invariants:
 *   - No new attempt may be created once one has SUCCEEDED
 *   - The succeeded attempt must be the last (latest) attempt
 *
 * No framework annotations. Pure Kotlin.
 */
data class PaymentIntent(
    val id: String,                              // pi_xxxxxxxxxxxxxxxxxxxxxxxx
    val amount: Money,
    val status: PaymentIntentStatus,
    val captureMethod: CaptureMethod,
    val confirmationMethod: ConfirmationMethod,
    val customerId: String?,
    val paymentMethodId: String?,
    val description: String?,
    val metadata: Map<String, String>,
    val idempotencyKey: String?,
    val clientSecret: String,                    // {id}_secret_{random}
    /**
     * Payment method types the merchant has enabled for this intent.
     * Drives the checkout UI — shopper picks one from this list.
     * Derived from merchant configuration at intent creation time.
     * Empty set = all methods enabled (no restriction).
     */
    val availablePaymentMethods: Set<PaymentMethodType>,
    val canceledAt: Instant?,
    val cancellationReason: CancellationReason?,
    val latestPaymentAttemptId: String?,         // denormalized for fast lookup
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /**
     * Create a new PaymentAttempt for this intent.
     * Enforces: no new attempt if a succeeded attempt already exists.
     */
    fun createAttempt(
        chosenPaymentMethod: PaymentMethod?,
        existingAttempts: List<PaymentAttempt>
    ): Triple<PaymentIntent, PaymentAttempt, PaymentAttemptEvent> {
        // Invariant: block new attempts if any prior attempt succeeded
        val succeededAttempt = existingAttempts.find { it.status == PaymentAttemptStatus.SUCCEEDED }
        if (succeededAttempt != null) {
            throw PaymentAttemptViolationException(
                "Cannot create a new attempt for PaymentIntent[$id]: " +
                "attempt [${succeededAttempt.id}] already succeeded"
            )
        }

        // Validate chosen method is available for this intent
        if (chosenPaymentMethod != null && availablePaymentMethods.isNotEmpty()) {
            if (chosenPaymentMethod.type !in availablePaymentMethods) {
                throw PaymentAttemptViolationException(
                    "Payment method [${chosenPaymentMethod.type}] is not available for intent [$id]. " +
                    "Available: $availablePaymentMethods"
                )
            }
        }

        val attemptId = generateAttemptId()
        val now = Instant.now()
        val attempt = PaymentAttempt(
            id = attemptId,
            paymentIntentId = id,
            amount = amount,
            status = PaymentAttemptStatus.PENDING,
            paymentMethod = chosenPaymentMethod,
            capturedAmount = null,
            processorReference = null,
            failureCode = null,
            failureMessage = null,
            nextAction = null,
            createdAt = now,
            updatedAt = now
        )

        val updatedIntent = copy(
            latestPaymentAttemptId = attemptId,
            updatedAt = now
        )

        val event = PaymentAttemptEvent.Created(
            attemptId = attemptId,
            paymentIntentId = id,
            amount = amount.amount,
            currency = amount.currency.code
        )

        return Triple(updatedIntent, attempt, event)
    }

    /**
     * Apply the result of a succeeded attempt back onto the intent.
     * Enforces: the succeeded attempt must be the latest attempt.
     */
    fun applyAttemptSucceeded(
        attempt: PaymentAttempt,
        existingAttempts: List<PaymentAttempt>
    ): Pair<PaymentIntent, PaymentIntentEvent> {
        // Invariant: succeeded attempt must be the latest one
        val latestAttempt = existingAttempts.maxByOrNull { it.createdAt }
        if (latestAttempt?.id != attempt.id) {
            throw PaymentAttemptViolationException(
                "Attempt [${attempt.id}] is not the latest attempt for PaymentIntent[$id]. " +
                "Latest is [${latestAttempt?.id}]."
            )
        }
        val updated = copy(status = PaymentIntentStatus.SUCCEEDED, updatedAt = Instant.now())
        return updated to PaymentIntentEvent.Succeeded(id, amount.amount, amount.currency.code)
    }

    /**
     * Apply the result of a failed attempt back onto the intent.
     * Intent stays in REQUIRES_PAYMENT_METHOD to allow retry.
     */
    fun applyAttemptFailed(): Pair<PaymentIntent, PaymentIntentEvent> {
        val updated = copy(
            status = PaymentIntentStatus.REQUIRES_PAYMENT_METHOD,
            updatedAt = Instant.now()
        )
        return updated to PaymentIntentEvent.AttemptFailed(id)
    }

    private fun generateAttemptId(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray()
        return "at_${NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet, 24)}"
    }
    /**
     * Attach a payment method. Transitions to REQUIRES_CONFIRMATION.
     */
    fun attachPaymentMethod(paymentMethodId: String): Pair<PaymentIntent, PaymentIntentEvent> {
        requireStatus(PaymentIntentStatus.REQUIRES_PAYMENT_METHOD)
        val updated = copy(
            paymentMethodId = paymentMethodId,
            status = PaymentIntentStatus.REQUIRES_CONFIRMATION,
            updatedAt = Instant.now()
        )
        return updated to PaymentIntentEvent.PaymentMethodAttached(id, paymentMethodId)
    }

    /**
     * Confirm the payment intent. Transitions depend on 3DS requirement and capture method.
     */
    fun confirm(requiresAction: Boolean = false): Pair<PaymentIntent, PaymentIntentEvent> {
        requireStatus(PaymentIntentStatus.REQUIRES_CONFIRMATION)
        requireNotNull(paymentMethodId) { "Cannot confirm without a payment method" }

        val newStatus = when {
            requiresAction -> PaymentIntentStatus.REQUIRES_ACTION
            captureMethod == CaptureMethod.MANUAL -> PaymentIntentStatus.REQUIRES_CAPTURE
            else -> PaymentIntentStatus.PROCESSING
        }

        val updated = copy(status = newStatus, updatedAt = Instant.now())
        val event = PaymentIntentEvent.Confirmed(id, newStatus.name)
        return updated to event
    }

    /**
     * Mark as requiring action (e.g. 3DS challenge).
     */
    fun requireAction(actionType: String): Pair<PaymentIntent, PaymentIntentEvent> {
        requireStatus(PaymentIntentStatus.REQUIRES_CONFIRMATION, PaymentIntentStatus.PROCESSING)
        val updated = copy(status = PaymentIntentStatus.REQUIRES_ACTION, updatedAt = Instant.now())
        return updated to PaymentIntentEvent.ActionRequired(id, actionType)
    }

    /**
     * Action completed (3DS passed). Resume processing.
     */
    fun completeAction(): Pair<PaymentIntent, PaymentIntentEvent> {
        requireStatus(PaymentIntentStatus.REQUIRES_ACTION)
        val newStatus = if (captureMethod == CaptureMethod.MANUAL) {
            PaymentIntentStatus.REQUIRES_CAPTURE
        } else {
            PaymentIntentStatus.PROCESSING
        }
        val updated = copy(status = newStatus, updatedAt = Instant.now())
        return updated to PaymentIntentEvent.Confirmed(id, newStatus.name)
    }

    /**
     * Mark payment as succeeded (automatic capture).
     */
    fun succeed(): Pair<PaymentIntent, PaymentIntentEvent> {
        requireStatus(PaymentIntentStatus.PROCESSING)
        val updated = copy(status = PaymentIntentStatus.SUCCEEDED, updatedAt = Instant.now())
        return updated to PaymentIntentEvent.Succeeded(id, amount.amount, amount.currency.code)
    }

    /**
     * Capture a manually-captured payment.
     */
    fun capture(captureAmount: Long? = null): Pair<PaymentIntent, PaymentIntentEvent> {
        requireStatus(PaymentIntentStatus.REQUIRES_CAPTURE)
        val amountToCapture = captureAmount ?: amount.amount
        require(amountToCapture <= amount.amount) { "Capture amount cannot exceed authorized amount" }
        require(amountToCapture > 0) { "Capture amount must be positive" }

        val updated = copy(status = PaymentIntentStatus.SUCCEEDED, updatedAt = Instant.now())
        return updated to PaymentIntentEvent.Captured(id, amountToCapture, amount.currency.code)
    }

    /**
     * Cancel the payment intent.
     */
    fun cancel(reason: CancellationReason? = null): Pair<PaymentIntent, PaymentIntentEvent> {
        val cancellableStatuses = setOf(
            PaymentIntentStatus.REQUIRES_PAYMENT_METHOD,
            PaymentIntentStatus.REQUIRES_CONFIRMATION,
            PaymentIntentStatus.REQUIRES_ACTION,
            PaymentIntentStatus.REQUIRES_CAPTURE,
            PaymentIntentStatus.PROCESSING
        )
        if (status !in cancellableStatuses) {
            throw InvalidStateTransitionException(
                aggregateId = id,
                fromStatus = status.name,
                toStatus = PaymentIntentStatus.CANCELED.name
            )
        }
        val now = Instant.now()
        val updated = copy(
            status = PaymentIntentStatus.CANCELED,
            canceledAt = now,
            cancellationReason = reason,
            updatedAt = now
        )
        return updated to PaymentIntentEvent.Canceled(id, reason?.name)
    }

    private fun requireStatus(vararg allowed: PaymentIntentStatus) {
        if (status !in allowed) {
            throw InvalidStateTransitionException(
                aggregateId = id,
                fromStatus = status.name,
                toStatus = "any of ${allowed.map { it.name }}"
            )
        }
    }

    val isTerminal: Boolean
        get() = status in setOf(PaymentIntentStatus.SUCCEEDED, PaymentIntentStatus.CANCELED)
}

enum class PaymentIntentStatus {
    REQUIRES_PAYMENT_METHOD,
    REQUIRES_CONFIRMATION,
    REQUIRES_ACTION,
    PROCESSING,
    REQUIRES_CAPTURE,
    SUCCEEDED,
    CANCELED
}

enum class CaptureMethod {
    AUTOMATIC,
    MANUAL
}

enum class ConfirmationMethod {
    AUTOMATIC,
    MANUAL
}

enum class CancellationReason {
    DUPLICATE,
    FRAUDULENT,
    REQUESTED_BY_CUSTOMER,
    ABANDONED
}
