package com.payments.intentservice.adapter.rest.v1

import com.payments.intentservice.domain.model.*

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
    val description: String?,
    val metadata: Map<String, String>,
    val availablePaymentMethods: List<String>,
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
                description = pi.description,
                metadata = pi.metadata,
                availablePaymentMethods = pi.availablePaymentMethods.map { it.name.lowercase() },
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
    /** The payment method (scheme/type) used in this attempt */
    val paymentMethodType: String?,
    val paymentMethodDetails: PaymentMethodDetailsResponse?,
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
            paymentMethodType = a.paymentMethod?.type?.name?.lowercase(),
            paymentMethodDetails = a.paymentMethod?.let { PaymentMethodDetailsResponse.from(it) },
            capturedAmount = a.capturedAmount,
            processorReference = a.processorReference,
            failureCode = a.failureCode,
            failureMessage = a.failureMessage,
            nextAction = a.nextAction?.let { NextActionResponse(it.type, it.redirectUrl) },
            created = a.createdAt.epochSecond
        )
    }
}

/**
 * Display-safe payment method details in the API response.
 * Type-specific fields exposed only for their relevant type.
 */
data class PaymentMethodDetailsResponse(
    val type: String,
    // Card / Device wallet card details
    val scheme: String? = null,
    val last4: String? = null,
    val expiryMonth: Int? = null,
    val expiryYear: Int? = null,
    val funding: String? = null,
    val issuerCountry: String? = null,
    // Wallet (device or digital)
    val walletType: String? = null,
    val email: String? = null,
    // Real-time bank transfer
    val rail: String? = null,
    val transactionReference: String? = null,
    // BNPL
    val provider: String? = null,
    val installments: Int? = null
) {
    companion object {
        fun from(pm: PaymentMethod): PaymentMethodDetailsResponse = when (pm) {
            is PaymentMethod.Card ->
                PaymentMethodDetailsResponse(
                    type = "card",
                    scheme = pm.scheme.name.lowercase(),
                    last4 = pm.last4,
                    expiryMonth = pm.expiryMonth,
                    expiryYear = pm.expiryYear,
                    funding = pm.funding.name.lowercase(),
                    issuerCountry = pm.issuerCountry
                )

            is PaymentMethod.DeviceWallet ->
                PaymentMethodDetailsResponse(
                    type = pm.type.name.lowercase(),  // "apple_pay" | "google_pay"
                    walletType = pm.walletType.name.lowercase(),
                    last4 = pm.dynamicLast4,
                    // expose underlying card details if resolved
                    scheme = pm.underlyingCard?.scheme?.name?.lowercase(),
                    funding = pm.underlyingCard?.funding?.name?.lowercase()
                )

            is PaymentMethod.DigitalWallet ->
                PaymentMethodDetailsResponse(
                    type = pm.type.name.lowercase(),  // "paypal" | "alipay" | "wechat_pay" | "grabpay"
                    walletType = pm.walletType.name.lowercase(),
                    email = pm.email
                )

            is PaymentMethod.RealTimeBankTransfer ->
                PaymentMethodDetailsResponse(
                    type = pm.type.name.lowercase(),  // "paynow" | "promptpay" | "fps" | ...
                    rail = pm.rail.name.lowercase(),
                    transactionReference = pm.bankReference
                )

            is PaymentMethod.BuyNowPayLater ->
                PaymentMethodDetailsResponse(
                    type = pm.type.name.lowercase(),  // "klarna" | "afterpay" | "atome"
                    provider = pm.provider.name.lowercase(),
                    installments = pm.installments
                )

            is PaymentMethod.BankTransfer ->
                PaymentMethodDetailsResponse(
                    type = "bank_transfer",
                    scheme = pm.scheme.name.lowercase(),
                    last4 = pm.last4
                )
        }
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
