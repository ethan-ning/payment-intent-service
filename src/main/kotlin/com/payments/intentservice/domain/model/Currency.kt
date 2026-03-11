package com.payments.intentservice.domain.model

/**
 * ISO 4217 Currency value object.
 */
data class Currency(val code: String) {
    init {
        require(code.length == 3 && code == code.uppercase()) {
            "Currency code must be a 3-letter uppercase ISO 4217 code, got: $code"
        }
    }

    companion object {
        val USD = Currency("USD")
        val EUR = Currency("EUR")
        val GBP = Currency("GBP")
        val SGD = Currency("SGD")
        val AUD = Currency("AUD")
        val JPY = Currency("JPY")

        fun of(code: String) = Currency(code.uppercase())

        /** Currencies where the minor unit IS the major unit (no cents). */
        val ZERO_DECIMAL_CURRENCIES = setOf("JPY", "KRW", "VND", "IDR", "BIF", "GNF", "PYG", "RWF", "UGX", "XAF", "XOF", "XPF")

        fun isZeroDecimal(code: String) = code.uppercase() in ZERO_DECIMAL_CURRENCIES
    }

    override fun toString(): String = code
}
