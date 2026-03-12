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
    /**
     * Whether to save the payment method after a successful payment for future reuse.
     * "on_session" — save for convenience (customer present for future payments).
     * "off_session" — save for recurring/MIT (merchant charges without customer present).
     */
    val setupFutureUsage: String? = null,
    val confirm: Boolean = false,
    val returnUrl: String? = null,
)

data class ConfirmPaymentIntentRequest(
    /**
     * The transient payment method the shopper chose — a value object for this payment only.
     * Mutually exclusive with paymentInstrumentId.
     */
    val paymentMethod: PaymentMethodRequest? = null,
    /**
     * ID of a saved PaymentInstrument (pm_xxx) from payment-instrument-service.
     * Use when the customer picks a previously saved card/wallet.
     */
    val paymentInstrumentId: String? = null,
    /**
     * Override setup_future_usage at confirm time.
     * "on_session" or "off_session" — takes precedence over the value set at create.
     * Set to "" (empty string) to explicitly clear a previously set value.
     */
    val setupFutureUsage: String? = null,
    val returnUrl: String? = null,
)

/**
 * Inbound payment method selection from the shopper at confirm time.
 * Discriminated by "type" field matching PaymentMethodType.
 *
 * Variants:
 *   CardRequest          → type = CARD
 *   DeviceWalletRequest  → type = APPLE_PAY | GOOGLE_PAY
 *   DigitalWalletRequest → type = PAYPAL | ALIPAY | WECHAT_PAY | GRABPAY
 *   RealTimeBankRequest  → type = PAYNOW | PROMPTPAY | FPS | UPI | SEPA_INSTANT | ...
 *   BnplRequest          → type = KLARNA | AFTERPAY | ATOME
 *   BankTransferRequest  → type = BANK_TRANSFER
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

    /** Apple Pay / Google Pay — card passthrough, no independent balance */
    data class DeviceWalletRequest(
        val walletType: DeviceWalletType,
        val dynamicLast4: String? = null
    ) : PaymentMethodRequest() {
        override val type = when (walletType) {
            DeviceWalletType.APPLE_PAY  -> PaymentMethodType.APPLE_PAY
            DeviceWalletType.GOOGLE_PAY -> PaymentMethodType.GOOGLE_PAY
        }
    }

    /** PayPal / Alipay / WeChat Pay / GrabPay — closed-loop e-money wallets */
    data class DigitalWalletRequest(
        val walletType: DigitalWalletType,
        val email: String? = null
    ) : PaymentMethodRequest() {
        override val type = when (walletType) {
            DigitalWalletType.PAYPAL     -> PaymentMethodType.PAYPAL
            DigitalWalletType.ALIPAY     -> PaymentMethodType.ALIPAY
            DigitalWalletType.WECHAT_PAY -> PaymentMethodType.WECHAT_PAY
            DigitalWalletType.GRABPAY    -> PaymentMethodType.GRABPAY
        }
    }

    /** PayNow / PromptPay / FPS / UPI / SEPA Instant — account-to-account bank rails */
    data class RealTimeBankRequest(val rail: RealTimeBankRail) : PaymentMethodRequest() {
        override val type = when (rail) {
            RealTimeBankRail.PAYNOW          -> PaymentMethodType.PAYNOW
            RealTimeBankRail.PROMPTPAY       -> PaymentMethodType.PROMPTPAY
            RealTimeBankRail.FPS             -> PaymentMethodType.FPS
            RealTimeBankRail.UPI             -> PaymentMethodType.UPI
            RealTimeBankRail.SEPA_INSTANT    -> PaymentMethodType.SEPA_INSTANT
            RealTimeBankRail.FASTER_PAYMENTS -> PaymentMethodType.FASTER_PAYMENTS
        }
    }

    data class BnplRequest(
        val provider: BnplProvider,
        val installments: Int? = null
    ) : PaymentMethodRequest() {
        override val type = when (provider) {
            BnplProvider.KLARNA   -> PaymentMethodType.KLARNA
            BnplProvider.AFTERPAY -> PaymentMethodType.AFTERPAY
            BnplProvider.ATOME    -> PaymentMethodType.ATOME
        }
    }

    data class BankTransferRequest(
        val scheme: BankTransferScheme,
        val bankName: String? = null,
        val last4: String? = null
    ) : PaymentMethodRequest() {
        override val type = PaymentMethodType.BANK_TRANSFER
    }

    fun toDomain(): PaymentMethod = when (this) {
        is CardRequest         -> PaymentMethod.Card(scheme, last4, expiryMonth, expiryYear, funding, fingerprint, issuerCountry)
        is DeviceWalletRequest -> PaymentMethod.DeviceWallet(walletType, dynamicLast4, underlyingCard = null)
        is DigitalWalletRequest -> PaymentMethod.DigitalWallet(walletType, email)
        is RealTimeBankRequest -> PaymentMethod.RealTimeBankTransfer(rail)
        is BnplRequest         -> PaymentMethod.BuyNowPayLater(provider, installments)
        is BankTransferRequest -> PaymentMethod.BankTransfer(scheme, bankName, last4)
    }
}

data class CapturePaymentIntentRequest(
    @field:Min(value = 1, message = "amount_to_capture must be at least 1")
    val amountToCapture: Long? = null
)

data class CancelPaymentIntentRequest(
    val cancellationReason: CancellationReason? = null
)
