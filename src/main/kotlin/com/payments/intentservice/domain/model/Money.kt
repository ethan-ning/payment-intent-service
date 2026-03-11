package com.payments.intentservice.domain.model

import com.payments.intentservice.domain.exception.DomainException

/**
 * Value object representing a monetary amount.
 * Amount is always stored in minor currency units (e.g. cents for USD).
 * NEVER use Double or Float for monetary values.
 */
data class Money(
    val amount: Long,
    val currency: Currency
) {
    init {
        require(amount >= 0) { "Amount must be non-negative, got: $amount" }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add money with different currencies: $currency and ${other.currency}"
        }
        return copy(amount = amount + other.amount)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot subtract money with different currencies: $currency and ${other.currency}"
        }
        require(amount >= other.amount) { "Insufficient amount" }
        return copy(amount = amount - other.amount)
    }

    fun isGreaterThan(other: Money): Boolean {
        require(currency == other.currency)
        return amount > other.amount
    }

    override fun toString(): String = "${currency.code} ${amount}"
}
