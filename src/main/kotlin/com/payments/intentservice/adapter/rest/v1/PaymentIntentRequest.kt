package com.payments.intentservice.adapter.rest.v1

import com.payments.intentservice.domain.model.*
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * REST request/response DTOs — these live in the adapter layer.
 * They must NOT be used inside the application or domain layers.
 * Mapping between DTOs and domain objects happens in the controller.
 */

data class CreatePaymentIntentRequest(
    @field:NotNull(message = "amount is required")
    @field:Min(value = 1, message = "amount must be at least 1")
    val amount: Long?,

    @field:NotBlank(message = "currency is required")
    @field:Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
    val currency: String?,

    val customerId: String? = null,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val captureMethod: CaptureMethod = CaptureMethod.AUTOMATIC,
    val confirmationMethod: ConfirmationMethod = ConfirmationMethod.AUTOMATIC,
    /**
     * Payment method types the merchant enables for this intent.
     * Drives checkout UI — shopper picks one of these.
     * e.g. ["CARD", "WECHAT_PAY", "ALIPAY"]
     * Empty = all methods available.
     */
    val availablePaymentMethods: Set<PaymentMethodType> = emptySet(),
    val confirm: Boolean = false,
    val returnUrl: String? = null
)

data class ConfirmPaymentIntentRequest(
    /**
     * The payment method the shopper chose for this attempt.
     * Required fields depend on type — see PaymentMethodRequest variants.
     */
    val paymentMethod: PaymentMethodRequest? = null,
    val returnUrl: String? = null
)

/**
 * Inbound payment method selection from the shopper.
 * Discriminated by "type" field.
 */
sealed class PaymentMethodRequest {
    abstract val type: PaymentMethodType

    data class CardRequest(
        val scheme: CardScheme,
        val last4: String,
        val expiryMonth: Int,
        val expiryYear: Int,
        val funding: CardFunding = CardFunding.UNKNOWN,
        val fingerprint: String? = null,
        val issuerCountry: String? = null
    ) : PaymentMethodRequest() {
        override val type = PaymentMethodType.CARD
    }

    data class WalletRequest(
        val walletType: WalletType,
        val email: String? = null,
        val dynamicLast4: String? = null
    ) : PaymentMethodRequest() {
        override val type: PaymentMethodType = when (walletType) {
            WalletType.APPLE_PAY -> PaymentMethodType.APPLE_PAY
            WalletType.GOOGLE_PAY -> PaymentMethodType.GOOGLE_PAY
            WalletType.PAYPAL -> PaymentMethodType.PAYPAL
        }
    }

    data class RealTimePaymentRequest(val provider: RtpProvider) : PaymentMethodRequest() {
        override val type: PaymentMethodType = when (provider) {
            RtpProvider.WECHAT_PAY -> PaymentMethodType.WECHAT_PAY
            RtpProvider.ALIPAY -> PaymentMethodType.ALIPAY
            RtpProvider.GRABPAY -> PaymentMethodType.GRABPAY
            RtpProvider.PAYNOW -> PaymentMethodType.PAYNOW
            RtpProvider.PROMPTPAY -> PaymentMethodType.PROMPTPAY
        }
    }

    data class BnplRequest(val provider: BnplProvider, val installments: Int? = null) : PaymentMethodRequest() {
        override val type: PaymentMethodType = when (provider) {
            BnplProvider.KLARNA -> PaymentMethodType.KLARNA
            BnplProvider.AFTERPAY -> PaymentMethodType.AFTERPAY
            BnplProvider.ATOME -> PaymentMethodType.ATOME
        }
    }

    /** Maps this request to the domain value object */
    fun toDomain(): PaymentMethod = when (this) {
        is CardRequest -> PaymentMethod.Card(scheme, last4, expiryMonth, expiryYear, funding, fingerprint, issuerCountry)
        is WalletRequest -> PaymentMethod.Wallet(walletType, email, dynamicLast4)
        is RealTimePaymentRequest -> PaymentMethod.RealTimePayment(provider, null)
        is BnplRequest -> PaymentMethod.BuyNowPayLater(provider, installments)
    }
}

data class CapturePaymentIntentRequest(
    @field:Min(value = 1, message = "amount_to_capture must be at least 1")
    val amountToCapture: Long? = null
)

data class CancelPaymentIntentRequest(
    val cancellationReason: CancellationReason? = null
)
