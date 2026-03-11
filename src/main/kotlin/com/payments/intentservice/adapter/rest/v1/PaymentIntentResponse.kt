package com.payments.intentservice.adapter.rest.v1

import com.payments.intentservice.domain.model.PaymentIntent

/**
 * Stripe-compatible API response shape.
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
    val canceledAt: Long?,
    val cancellationReason: String?,
    val created: Long,
    val livemode: Boolean = false
) {
    companion object {
        fun from(pi: PaymentIntent): PaymentIntentResponse = PaymentIntentResponse(
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
            canceledAt = pi.canceledAt?.epochSecond,
            cancellationReason = pi.cancellationReason?.name?.lowercase(),
            created = pi.createdAt.epochSecond
        )
    }
}

data class PaymentIntentListResponse(
    val `object`: String = "list",
    val data: List<PaymentIntentResponse>,
    val hasMore: Boolean,
    val totalCount: Long,
    val url: String = "/v1/payment_intents"
)
