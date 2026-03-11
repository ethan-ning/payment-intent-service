package com.payments.intentservice.domain.exception

/**
 * Base domain exception. No framework dependencies.
 */
sealed class DomainException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class InvalidStateTransitionException(
    val aggregateId: String,
    val fromStatus: String,
    val toStatus: String
) : DomainException(
    "PaymentIntent[$aggregateId]: invalid transition from $fromStatus to $toStatus"
)

class PaymentIntentNotFoundException(id: String) :
    DomainException("PaymentIntent not found: $id")

class DuplicateIdempotencyKeyException(key: String) :
    DomainException("Duplicate request with idempotency key: $key")

class InvalidPaymentAmountException(amount: Long) :
    DomainException("Invalid payment amount: $amount. Must be a positive integer in minor units.")

class InvalidCurrencyException(currency: String) :
    DomainException("Invalid or unsupported currency: $currency")

class PaymentIntentAlreadyTerminalException(id: String, status: String) :
    DomainException("PaymentIntent[$id] is already in terminal state: $status")

class InsufficientCaptureAmountException(requested: Long, authorized: Long) :
    DomainException("Capture amount $requested exceeds authorized amount $authorized")

class PaymentAttemptViolationException(message: String) :
    DomainException(message)

class PaymentAttemptNotFoundException(id: String) :
    DomainException("PaymentAttempt not found: $id")
