package com.payments.intentservice.application.port.inbound

import com.payments.intentservice.domain.model.CancellationReason
import com.payments.intentservice.domain.model.CaptureMethod
import com.payments.intentservice.domain.model.ConfirmationMethod
import com.payments.intentservice.domain.model.PaymentIntent

// ─── Commands ────────────────────────────────────────────────────────────────

data class CreatePaymentIntentCommand(
    val amount: Long,
    val currency: String,
    val customerId: String? = null,
    val paymentMethodId: String? = null,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val captureMethod: CaptureMethod = CaptureMethod.AUTOMATIC,
    val confirmationMethod: ConfirmationMethod = ConfirmationMethod.AUTOMATIC,
    val idempotencyKey: String? = null,
    val confirm: Boolean = false
)

data class ConfirmPaymentIntentCommand(
    val paymentMethodId: String? = null,
    val returnUrl: String? = null
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
