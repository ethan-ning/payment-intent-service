package com.payments.intentservice.domain.model

/**
 * PaymentMethod — VALUE OBJECT describing the payment scheme/type used in an attempt.
 *
 * Taxonomy (corrected):
 *
 *   CARD
 *     Visa, Mastercard, AMEX, UnionPay, JCB...
 *     The card scheme IS the payment method.
 *
 *   DEVICE_WALLET (card passthrough — no balance of its own)
 *     Apple Pay, Google Pay
 *     These tokenize an underlying card. The wallet is just a UX/tokenization layer.
 *     The actual instrument is still a card; the DeviceWallet wraps it.
 *
 *   DIGITAL_WALLET (closed-loop e-money account, redirect-based)
 *     PayPal, Alipay, WeChat Pay, GrabPay
 *     Shopper's balance lives inside the wallet provider's system.
 *     PayPal is in the same category as Alipay — both are e-money wallets.
 *
 *   REAL_TIME_BANK_TRANSFER (account-to-account, bank rails)
 *     PayNow (SG), PromptPay (TH), FPS (HK), UPI (IN), SEPA Instant
 *     These move money directly between bank accounts via national/regional rails.
 *     No wallet balance involved — the source is always a bank account.
 *
 *   BNPL (Buy Now Pay Later)
 *     Klarna, Afterpay, Atome
 *
 *   BANK_TRANSFER (non-instant, batch settlement)
 *     ACH, SEPA, FPX, BACS, Faster Payments
 */
sealed class PaymentMethod {

    abstract val type: PaymentMethodType

    /**
     * Traditional card payment (credit, debit, prepaid).
     * Display-safe fields only — no raw PAN/CVV.
     */
    data class Card(
        val scheme: CardScheme,
        val last4: String,
        val expiryMonth: Int,
        val expiryYear: Int,
        val funding: CardFunding,
        val fingerprint: String?,       // processor-generated token for dedup/fraud
        val issuerCountry: String?
    ) : PaymentMethod() {
        override val type = PaymentMethodType.CARD

        init {
            require(last4.length == 4 && last4.all { it.isDigit() }) {
                "last4 must be exactly 4 digits, got: '$last4'"
            }
            require(expiryMonth in 1..12) { "expiryMonth must be 1-12" }
            require(expiryYear >= 2024) { "expiryYear must be >= 2024" }
        }
    }

    /**
     * Device wallet — tokenizes an underlying card (no independent balance).
     * Apple Pay and Google Pay present a card via device-bound token (DPAN).
     * The real payment instrument is the [underlyingCard]; the device wallet is the delivery mechanism.
     */
    data class DeviceWallet(
        val walletType: DeviceWalletType,
        val dynamicLast4: String?,          // device PAN last 4 (differs from card last 4)
        val underlyingCard: Card?           // resolved after tokenization
    ) : PaymentMethod() {
        override val type = when (walletType) {
            DeviceWalletType.APPLE_PAY  -> PaymentMethodType.APPLE_PAY
            DeviceWalletType.GOOGLE_PAY -> PaymentMethodType.GOOGLE_PAY
        }
    }

    /**
     * Digital wallet — closed-loop e-money account with its own balance.
     * PayPal, Alipay, WeChat Pay, GrabPay all fall here.
     * Checkout is redirect-based; settlement happens wallet-to-merchant.
     */
    data class DigitalWallet(
        val walletType: DigitalWalletType,
        val email: String? = null,          // PayPal account email (display only)
        val accountReference: String? = null // wallet-side account reference (masked)
    ) : PaymentMethod() {
        override val type = when (walletType) {
            DigitalWalletType.PAYPAL    -> PaymentMethodType.PAYPAL
            DigitalWalletType.ALIPAY    -> PaymentMethodType.ALIPAY
            DigitalWalletType.WECHAT_PAY -> PaymentMethodType.WECHAT_PAY
            DigitalWalletType.GRABPAY   -> PaymentMethodType.GRABPAY
        }
    }

    /**
     * Real-time bank transfer — account-to-account via national/regional bank rails.
     * Source of funds is always a bank account (not a wallet balance).
     * Examples: PayNow (SG), PromptPay (TH), FPS (HK), UPI (IN), SEPA Instant (EU).
     */
    data class RealTimeBankTransfer(
        val rail: RealTimeBankRail,
        val bankReference: String? = null   // bank-side transaction reference
    ) : PaymentMethod() {
        override val type = when (rail) {
            RealTimeBankRail.PAYNOW        -> PaymentMethodType.PAYNOW
            RealTimeBankRail.PROMPTPAY     -> PaymentMethodType.PROMPTPAY
            RealTimeBankRail.FPS           -> PaymentMethodType.FPS
            RealTimeBankRail.UPI           -> PaymentMethodType.UPI
            RealTimeBankRail.SEPA_INSTANT  -> PaymentMethodType.SEPA_INSTANT
            RealTimeBankRail.FASTER_PAYMENTS -> PaymentMethodType.FASTER_PAYMENTS
        }
    }

    /**
     * Buy Now Pay Later.
     */
    data class BuyNowPayLater(
        val provider: BnplProvider,
        val installments: Int? = null
    ) : PaymentMethod() {
        override val type = when (provider) {
            BnplProvider.KLARNA   -> PaymentMethodType.KLARNA
            BnplProvider.AFTERPAY -> PaymentMethodType.AFTERPAY
            BnplProvider.ATOME    -> PaymentMethodType.ATOME
        }
    }

    /**
     * Non-instant batch bank transfer (ACH, SEPA credit, FPX, BACS).
     */
    data class BankTransfer(
        val scheme: BankTransferScheme,
        val bankName: String? = null,
        val last4: String? = null
    ) : PaymentMethod() {
        override val type = PaymentMethodType.BANK_TRANSFER
    }
}

// ─── PaymentMethodType ────────────────────────────────────────────────────────

/**
 * Top-level payment method type.
 * Used for:
 *   - PaymentAttempt: which type was used in this attempt
 *   - PaymentIntent.availablePaymentMethods: what the merchant has enabled
 *   - PSP/Acquirer config: what the processor supports
 */
enum class PaymentMethodType {
    // ── Card ──────────────────────────────────────────────────────────────────
    CARD,

    // ── Device Wallets (card passthrough, no independent balance) ─────────────
    APPLE_PAY,
    GOOGLE_PAY,

    // ── Digital Wallets (closed-loop e-money, redirect-based) ─────────────────
    PAYPAL,
    ALIPAY,
    WECHAT_PAY,
    GRABPAY,

    // ── Real-Time Bank Transfers (A2A via bank rails) ──────────────────────────
    PAYNOW,
    PROMPTPAY,
    FPS,
    UPI,
    SEPA_INSTANT,
    FASTER_PAYMENTS,

    // ── BNPL ──────────────────────────────────────────────────────────────────
    KLARNA,
    AFTERPAY,
    ATOME,

    // ── Batch Bank Transfer ───────────────────────────────────────────────────
    BANK_TRANSFER;

    val isCard: Boolean get() = this == CARD

    /** Device wallets — card-backed, no independent balance */
    val isDeviceWallet: Boolean get() = this in setOf(APPLE_PAY, GOOGLE_PAY)

    /** Digital wallets — own e-money balance, redirect-based */
    val isDigitalWallet: Boolean get() = this in setOf(PAYPAL, ALIPAY, WECHAT_PAY, GRABPAY)

    val isWallet: Boolean get() = isDeviceWallet || isDigitalWallet

    /** Real-time bank-to-bank transfers */
    val isRealTimeBankTransfer: Boolean
        get() = this in setOf(PAYNOW, PROMPTPAY, FPS, UPI, SEPA_INSTANT, FASTER_PAYMENTS)

    val isBnpl: Boolean get() = this in setOf(KLARNA, AFTERPAY, ATOME)

    /** Methods that are card-backed (underlying instrument is always a card) */
    val isCardBacked: Boolean get() = this in setOf(CARD, APPLE_PAY, GOOGLE_PAY)

    /** Methods that require a redirect to complete (off-session browser flow) */
    val requiresRedirect: Boolean
        get() = isDigitalWallet || isRealTimeBankTransfer || isBnpl
}

// ─── Supporting Enums ─────────────────────────────────────────────────────────

enum class CardScheme {
    VISA, MASTERCARD, AMEX, UNIONPAY, DISCOVER, JCB, DINERS, UNKNOWN
}

enum class CardFunding {
    CREDIT, DEBIT, PREPAID, UNKNOWN
}

/** Apple Pay and Google Pay — device-bound tokenization of an underlying card */
enum class DeviceWalletType {
    APPLE_PAY, GOOGLE_PAY
}

/** Closed-loop e-money wallets with their own balance */
enum class DigitalWalletType {
    PAYPAL, ALIPAY, WECHAT_PAY, GRABPAY
}

/** Real-time account-to-account bank rails */
enum class RealTimeBankRail {
    PAYNOW,           // Singapore
    PROMPTPAY,        // Thailand
    FPS,              // Hong Kong
    UPI,              // India
    SEPA_INSTANT,     // Europe
    FASTER_PAYMENTS   // UK
}

enum class BnplProvider {
    KLARNA, AFTERPAY, ATOME
}

enum class BankTransferScheme {
    ACH,              // USA
    SEPA,             // Europe (non-instant)
    FPX,              // Malaysia
    BACS,             // UK (non-instant)
    FASTER_PAYMENTS   // UK (near-instant, distinct from real-time rail above)
}
