package com.payments.intentservice.domain.model

/**
 * PaymentMethod — a VALUE OBJECT describing the payment scheme/type used in an attempt.
 *
 * This is NOT a saved payment instrument (no ID, no lifecycle).
 * It answers: "how did the shopper choose to pay?"
 *
 * It is also used to describe:
 *   - Merchant capability: which methods a merchant has enabled
 *   - PSP/Acquirer capability: which methods a processor supports
 *
 * Conceptually:
 *   PaymentAttempt.paymentMethod   → what was actually used in this attempt
 *   PaymentIntent.availablePaymentMethods → what's enabled for this intent (from merchant config)
 */
sealed class PaymentMethod {

    abstract val type: PaymentMethodType

    /**
     * Card payment — covers credit, debit, prepaid.
     * Includes sub-scheme (Visa, Mastercard, etc.) and display-safe fields only.
     * Raw PAN/CVV never appear here — those are tokenized upstream in the vault.
     */
    data class Card(
        val scheme: CardScheme,
        val last4: String,
        val expiryMonth: Int,
        val expiryYear: Int,
        val funding: CardFunding,
        val fingerprint: String?,       // processor-generated, used for dedup/fraud
        val issuerCountry: String?      // ISO 3166-1 alpha-2
    ) : PaymentMethod() {
        override val type: PaymentMethodType = PaymentMethodType.CARD

        init {
            require(last4.length == 4 && last4.all { it.isDigit() }) {
                "last4 must be exactly 4 digits"
            }
            require(expiryMonth in 1..12) { "expiryMonth must be 1-12" }
            require(expiryYear >= 2024) { "expiryYear must be >= 2024" }
        }
    }

    /**
     * Wallet-based payments — Apple Pay, Google Pay, PayPal, etc.
     * Apple Pay / Google Pay are card-backed wallets; the underlying card details
     * may be available after tokenization.
     */
    data class Wallet(
        val walletType: WalletType,
        val email: String? = null,          // PayPal, Klarna
        val dynamicLast4: String? = null,   // Apple Pay / Google Pay device PAN
        val underlyingCard: Card? = null    // populated if wallet is card-backed
    ) : PaymentMethod() {
        override val type: PaymentMethodType = when (walletType) {
            WalletType.APPLE_PAY -> PaymentMethodType.APPLE_PAY
            WalletType.GOOGLE_PAY -> PaymentMethodType.GOOGLE_PAY
            WalletType.PAYPAL -> PaymentMethodType.PAYPAL
        }
    }

    /**
     * Real-time payment rails — WeChat Pay, Alipay, GrabPay, etc.
     */
    data class RealTimePayment(
        val provider: RtpProvider,
        val transactionReference: String?   // provider-side reference
    ) : PaymentMethod() {
        override val type: PaymentMethodType = when (provider) {
            RtpProvider.WECHAT_PAY -> PaymentMethodType.WECHAT_PAY
            RtpProvider.ALIPAY -> PaymentMethodType.ALIPAY
            RtpProvider.GRABPAY -> PaymentMethodType.GRABPAY
            RtpProvider.PAYNOW -> PaymentMethodType.PAYNOW
            RtpProvider.PROMPTPAY -> PaymentMethodType.PROMPTPAY
        }
    }

    /**
     * Buy Now Pay Later schemes.
     */
    data class BuyNowPayLater(
        val provider: BnplProvider,
        val installments: Int? = null
    ) : PaymentMethod() {
        override val type: PaymentMethodType = when (provider) {
            BnplProvider.KLARNA -> PaymentMethodType.KLARNA
            BnplProvider.AFTERPAY -> PaymentMethodType.AFTERPAY
            BnplProvider.ATOME -> PaymentMethodType.ATOME
        }
    }

    /**
     * Bank-based transfers (ACH, SEPA, FPX, etc.)
     */
    data class BankTransfer(
        val bankName: String?,
        val last4: String?,             // last 4 of account number
        val scheme: BankTransferScheme  // ACH | SEPA | FPX | BACS
    ) : PaymentMethod() {
        override val type: PaymentMethodType = PaymentMethodType.BANK_TRANSFER
    }
}

// ─── Supporting Enums ─────────────────────────────────────────────────────────

/**
 * Top-level payment method type — the "scheme" a shopper chose.
 * Used for merchant capability lists and PSP support lists.
 */
enum class PaymentMethodType {
    // Card
    CARD,

    // Wallets
    APPLE_PAY,
    GOOGLE_PAY,
    PAYPAL,

    // Real-time payments
    WECHAT_PAY,
    ALIPAY,
    GRABPAY,
    PAYNOW,
    PROMPTPAY,

    // BNPL
    KLARNA,
    AFTERPAY,
    ATOME,

    // Bank
    BANK_TRANSFER;

    /** Whether this method is a card-backed scheme (affects routing/risk) */
    val isCardBacked: Boolean
        get() = this in setOf(CARD, APPLE_PAY, GOOGLE_PAY)

    /** Whether this method is a wallet */
    val isWallet: Boolean
        get() = this in setOf(APPLE_PAY, GOOGLE_PAY, PAYPAL)

    /** Whether this method requires redirect (off-session flow) */
    val requiresRedirect: Boolean
        get() = this in setOf(WECHAT_PAY, ALIPAY, GRABPAY, PAYNOW, PROMPTPAY, KLARNA, AFTERPAY, ATOME)
}

enum class CardScheme {
    VISA,
    MASTERCARD,
    AMEX,
    UNIONPAY,
    DISCOVER,
    JCB,
    DINERS,
    UNKNOWN
}

enum class CardFunding {
    CREDIT,
    DEBIT,
    PREPAID,
    UNKNOWN
}

enum class WalletType {
    APPLE_PAY,
    GOOGLE_PAY,
    PAYPAL
}

enum class RtpProvider {
    WECHAT_PAY,
    ALIPAY,
    GRABPAY,
    PAYNOW,
    PROMPTPAY
}

enum class BnplProvider {
    KLARNA,
    AFTERPAY,
    ATOME
}

enum class BankTransferScheme {
    ACH,
    SEPA,
    FPX,
    BACS,
    FASTER_PAYMENTS
}
