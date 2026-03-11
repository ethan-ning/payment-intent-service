package com.payments.intentservice.adapter.rest.v1

import com.payments.intentservice.domain.model.CancellationReason
import com.payments.intentservice.domain.model.CaptureMethod
import com.payments.intentservice.domain.model.ConfirmationMethod
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
    val paymentMethodId: String? = null,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val captureMethod: CaptureMethod = CaptureMethod.AUTOMATIC,
    val confirmationMethod: ConfirmationMethod = ConfirmationMethod.AUTOMATIC,
    val confirm: Boolean = false,
    val returnUrl: String? = null
)

data class ConfirmPaymentIntentRequest(
    val paymentMethodId: String? = null,
    val returnUrl: String? = null
)

data class CapturePaymentIntentRequest(
    @field:Min(value = 1, message = "amount_to_capture must be at least 1")
    val amountToCapture: Long? = null
)

data class CancelPaymentIntentRequest(
    val cancellationReason: CancellationReason? = null
)
