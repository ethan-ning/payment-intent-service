package com.payments.intentservice.application.port.inbound

import com.payments.intentservice.domain.model.*

// ─── Commands ────────────────────────────────────────────────────────────────

data class CreatePaymentIntentCommand(
    val amount: Long,
    val currency: String,
    val customerId: String? = null,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val captureMethod: CaptureMethod = CaptureMethod.AUTOMATIC,
    val confirmationMethod: ConfirmationMethod = ConfirmationMethod.AUTOMATIC,
    /**
     * Payment method types the merchant allows for this intent.
     * Empty = all methods enabled (merchant has no restriction).
     * Populated from merchant configuration at order time.
     */
    val availablePaymentMethods: Set<PaymentMethodType> = emptySet(),
    /**
     * Whether to save the payment method used in this intent for future use.
     * ON_SESSION: save for convenience (customer present next time).
     * OFF_SESSION: save for recurring/MIT (customer not present next time).
     */
    val setupFutureUsage: SetupFutureUsage? = null,
    val idempotencyKey: String? = null,
    val confirm: Boolean = false,
)

data class ConfirmPaymentIntentCommand(
    /**
     * The transient payment method the shopper chose — a VALUE OBJECT, not a saved instrument.
     * Mutually exclusive with paymentInstrumentId (use one or the other).
     */
    val paymentMethod: PaymentMethod? = null,
    /**
     * ID of a saved PaymentInstrument from payment-instrument-service (pm_xxx).
     * When provided, the instrument's details are used for this payment attempt.
     * The customer must own this instrument.
     */
    val paymentInstrumentId: String? = null,
    /**
     * Override setup_future_usage at confirm time (takes precedence over the value set at create).
     * Allows the shopper to opt in/out of saving their method at the last step of checkout.
     */
    val setupFutureUsage: SetupFutureUsage? = null,
    val returnUrl: String? = null,
)

data class CapturePaymentIntentCommand(
    val amountToCapture: Long? = null
)

data class CancelPaymentIntentCommand(
    val cancellationReason: CancellationReason? = null
)

data class ListPaymentIntentsQuery(
    val customerId: String? = null,
    val limit: Int = 10,
    val startingAfter: String? = null,
    val endingBefore: String? = null
)

data class Page<T>(
    val data: List<T>,
    val hasMore: Boolean,
    val totalCount: Long
)

// ─── Use Case Interfaces (Input Ports) ───────────────────────────────────────

/**
 * All use case interfaces live here — pure Kotlin, no Spring.
 * Application services implement these; controllers depend on these, not the implementations.
 */

interface CreatePaymentIntentUseCase {
    fun execute(command: CreatePaymentIntentCommand): PaymentIntent
}

interface ConfirmPaymentIntentUseCase {
    fun execute(id: String, command: ConfirmPaymentIntentCommand): PaymentIntent
}

interface CapturePaymentIntentUseCase {
    fun execute(id: String, command: CapturePaymentIntentCommand): PaymentIntent
}

interface CancelPaymentIntentUseCase {
    fun execute(id: String, command: CancelPaymentIntentCommand): PaymentIntent
}

interface GetPaymentIntentUseCase {
    fun execute(id: String): PaymentIntent
}

interface ListPaymentIntentsUseCase {
    fun execute(query: ListPaymentIntentsQuery): Page<PaymentIntent>
}
