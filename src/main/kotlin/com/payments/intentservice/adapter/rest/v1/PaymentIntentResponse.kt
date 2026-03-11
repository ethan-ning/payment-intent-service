package com.payments.intentservice.adapter.rest.v1

import com.payments.intentservice.domain.model.PaymentAttempt
import com.payments.intentservice.domain.model.PaymentIntent

/**
 * Stripe/Airwallex-compatible API response shape.
 * Maps from domain model → wire format.
 */
data class PaymentIntentResponse(
    val id: String,
    val `object`: String = "payment_intent",
    val amount: Long,
    val currency: String,
    val status: String,
    val clientSecret: String,
    val captureMethod: String,
    val confirmationMethod: String,
    val customerId: String?,
    val paymentMethodId: String?,
    val description: String?,
    val metadata: Map<String, String>,
    val latestPaymentAttempt: PaymentAttemptResponse?,
    val canceledAt: Long?,
    val cancellationReason: String?,
    val created: Long,
    val livemode: Boolean = false
) {
    companion object {
        fun from(pi: PaymentIntent, latestAttempt: PaymentAttempt? = null): PaymentIntentResponse =
            PaymentIntentResponse(
                id = pi.id,
                amount = pi.amount.amount,
                currency = pi.amount.currency.code.lowercase(),
                status = pi.status.name.lowercase(),
                clientSecret = pi.clientSecret,
                captureMethod = pi.captureMethod.name.lowercase(),
                confirmationMethod = pi.confirmationMethod.name.lowercase(),
                customerId = pi.customerId,
                paymentMethodId = pi.paymentMethodId,
                description = pi.description,
                metadata = pi.metadata,
                latestPaymentAttempt = latestAttempt?.let { PaymentAttemptResponse.from(it) },
                canceledAt = pi.canceledAt?.epochSecond,
                cancellationReason = pi.cancellationReason?.name?.lowercase(),
                created = pi.createdAt.epochSecond
            )
    }
}

data class PaymentAttemptResponse(
    val id: String,
    val `object`: String = "payment_attempt",
    val paymentIntentId: String,
    val amount: Long,
    val currency: String,
    val status: String,
    val paymentMethodId: String?,
    val capturedAmount: Long?,
    val processorReference: String?,
    val failureCode: String?,
    val failureMessage: String?,
    val nextAction: NextActionResponse?,
    val created: Long
) {
    companion object {
        fun from(a: PaymentAttempt): PaymentAttemptResponse = PaymentAttemptResponse(
            id = a.id,
            paymentIntentId = a.paymentIntentId,
            amount = a.amount.amount,
            currency = a.amount.currency.code.lowercase(),
            status = a.status.name.lowercase(),
            paymentMethodId = a.paymentMethodId,
            capturedAmount = a.capturedAmount,
            processorReference = a.processorReference,
            failureCode = a.failureCode,
            failureMessage = a.failureMessage,
            nextAction = a.nextAction?.let { NextActionResponse(it.type, it.redirectUrl) },
            created = a.createdAt.epochSecond
        )
    }
}

data class NextActionResponse(val type: String, val redirectUrl: String?)

data class PaymentIntentListResponse(
    val `object`: String = "list",
    val data: List<PaymentIntentResponse>,
    val hasMore: Boolean,
    val totalCount: Long,
    val url: String = "/v1/payment_intents"
)
