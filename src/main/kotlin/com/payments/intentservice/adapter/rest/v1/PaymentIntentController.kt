package com.payments.intentservice.adapter.rest.v1

import com.payments.intentservice.application.port.inbound.*
import com.payments.intentservice.application.port.outbound.IdempotencyRecord
import com.payments.intentservice.application.port.outbound.IdempotencyStore
import com.payments.intentservice.application.port.outbound.PaymentAttemptRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

/**
 * REST adapter — translates HTTP ↔ use case commands.
 * No business logic here. Just:
 *   1. Parse & validate request
 *   2. Map to command
 *   3. Delegate to use case
 *   4. Map result to response DTO
 */
@RestController
@RequestMapping("/v1/payment_intents")
class PaymentIntentController(
    private val createUseCase: CreatePaymentIntentUseCase,
    private val confirmUseCase: ConfirmPaymentIntentUseCase,
    private val captureUseCase: CapturePaymentIntentUseCase,
    private val cancelUseCase: CancelPaymentIntentUseCase,
    private val getUseCase: GetPaymentIntentUseCase,
    private val listUseCase: ListPaymentIntentsUseCase,
    private val attemptRepository: PaymentAttemptRepository,
    private val idempotencyStore: IdempotencyStore,
    private val objectMapper: ObjectMapper
) {

    @PostMapping
    fun create(
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreatePaymentIntentRequest
    ): ResponseEntity<PaymentIntentResponse> {
        // Check idempotency cache first
        if (idempotencyKey != null) {
            val cached = idempotencyStore.get(idempotencyKey)
            if (cached != null) {
                val response = objectMapper.readValue(cached.responseBody, PaymentIntentResponse::class.java)
                return ResponseEntity.status(cached.statusCode).body(response)
            }
        }

        val command = CreatePaymentIntentCommand(
            amount = request.amount!!,
            currency = request.currency!!,
            customerId = request.customerId,
            description = request.description,
            metadata = request.metadata,
            captureMethod = request.captureMethod,
            confirmationMethod = request.confirmationMethod,
            availablePaymentMethods = request.availablePaymentMethods,
            setupFutureUsage = request.setupFutureUsage
                ?.takeIf { it.isNotBlank() }
                ?.let { com.payments.intentservice.domain.model.SetupFutureUsage.valueOf(it.uppercase()) },
            idempotencyKey = idempotencyKey,
            confirm = request.confirm,
        )

        val paymentIntent = createUseCase.execute(command)
        val response = PaymentIntentResponse.from(paymentIntent, null)

        // Cache the response
        if (idempotencyKey != null) {
            idempotencyStore.set(
                key = idempotencyKey,
                record = IdempotencyRecord(
                    key = idempotencyKey,
                    requestHash = idempotencyKey,
                    responseBody = objectMapper.writeValueAsString(response),
                    statusCode = HttpStatus.CREATED.value()
                ),
                ttl = Duration.ofHours(24)
            )
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<PaymentIntentResponse> {
        val paymentIntent = getUseCase.execute(id)
        val latestAttempt = attemptRepository.findLatestByPaymentIntentId(id)
        return ResponseEntity.ok(PaymentIntentResponse.from(paymentIntent, latestAttempt))
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) customerId: String?,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(required = false) startingAfter: String?,
        @RequestParam(required = false) endingBefore: String?
    ): ResponseEntity<PaymentIntentListResponse> {
        val query = ListPaymentIntentsQuery(
            customerId = customerId,
            limit = limit.coerceIn(1, 100),
            startingAfter = startingAfter,
            endingBefore = endingBefore
        )
        val page = listUseCase.execute(query)
        return ResponseEntity.ok(
            PaymentIntentListResponse(
                data = page.data.map { pi ->
                    PaymentIntentResponse.from(pi, attemptRepository.findLatestByPaymentIntentId(pi.id))
                },
                hasMore = page.hasMore,
                totalCount = page.totalCount
            )
        )
    }

    @PostMapping("/{id}/confirm")
    fun confirm(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody(required = false) request: ConfirmPaymentIntentRequest?
    ): ResponseEntity<PaymentIntentResponse> {
        val command = ConfirmPaymentIntentCommand(
            paymentMethod = request?.paymentMethod?.toDomain(),
            paymentInstrumentId = request?.paymentInstrumentId,
            setupFutureUsage = request?.setupFutureUsage
                ?.takeIf { it.isNotBlank() }
                ?.let { com.payments.intentservice.domain.model.SetupFutureUsage.valueOf(it.uppercase()) },
            returnUrl = request?.returnUrl,
        )
        val paymentIntent = confirmUseCase.execute(id, command)
        val latestAttempt = attemptRepository.findLatestByPaymentIntentId(id)
        return ResponseEntity.ok(PaymentIntentResponse.from(paymentIntent, latestAttempt))
    }

    @PostMapping("/{id}/capture")
    fun capture(
        @PathVariable id: String,
        @Valid @RequestBody(required = false) request: CapturePaymentIntentRequest?
    ): ResponseEntity<PaymentIntentResponse> {
        val command = CapturePaymentIntentCommand(amountToCapture = request?.amountToCapture)
        val paymentIntent = captureUseCase.execute(id, command)
        val latestAttempt = attemptRepository.findLatestByPaymentIntentId(id)
        return ResponseEntity.ok(PaymentIntentResponse.from(paymentIntent, latestAttempt))
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        @RequestBody(required = false) request: CancelPaymentIntentRequest?
    ): ResponseEntity<PaymentIntentResponse> {
        val command = CancelPaymentIntentCommand(cancellationReason = request?.cancellationReason)
        val paymentIntent = cancelUseCase.execute(id, command)
        val latestAttempt = attemptRepository.findLatestByPaymentIntentId(id)
        return ResponseEntity.ok(PaymentIntentResponse.from(paymentIntent, latestAttempt))
    }
}
