package com.payments.intentservice.adapter.advice

import com.payments.intentservice.domain.exception.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Stripe-compatible error response format.
 */
data class ApiError(val error: ErrorDetail)
data class ErrorDetail(
    val type: String,
    val code: String?,
    val message: String,
    val param: String? = null
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(PaymentIntentNotFoundException::class)
    fun handleNotFound(ex: PaymentIntentNotFoundException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiError(ErrorDetail(
                type = "invalid_request_error",
                code = "resource_missing",
                message = ex.message ?: "Resource not found"
            ))
        )

    @ExceptionHandler(InvalidStateTransitionException::class)
    fun handleInvalidTransition(ex: InvalidStateTransitionException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiError(ErrorDetail(
                type = "invalid_request_error",
                code = "payment_intent_unexpected_state",
                message = ex.message ?: "Invalid state transition"
            ))
        )

    @ExceptionHandler(InvalidPaymentAmountException::class)
    fun handleInvalidAmount(ex: InvalidPaymentAmountException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(ErrorDetail(
                type = "invalid_request_error",
                code = "payment_intent_invalid_parameter",
                message = ex.message ?: "Invalid amount",
                param = "amount"
            ))
        )

    @ExceptionHandler(InvalidCurrencyException::class)
    fun handleInvalidCurrency(ex: InvalidCurrencyException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(ErrorDetail(
                type = "invalid_request_error",
                code = "invalid_currency",
                message = ex.message ?: "Invalid currency",
                param = "currency"
            ))
        )

    @ExceptionHandler(DuplicateIdempotencyKeyException::class)
    fun handleDuplicateIdempotency(ex: DuplicateIdempotencyKeyException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiError(ErrorDetail(
                type = "idempotency_error",
                code = "idempotency_key_in_use",
                message = ex.message ?: "Idempotency key in use"
            ))
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val firstError = ex.bindingResult.fieldErrors.firstOrNull()
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(ErrorDetail(
                type = "invalid_request_error",
                code = "parameter_invalid",
                message = firstError?.defaultMessage ?: "Validation failed",
                param = firstError?.field
            ))
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ApiError> {
        log.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiError(ErrorDetail(
                type = "api_error",
                code = "internal_server_error",
                message = "An unexpected error occurred"
            ))
        )
    }
}
