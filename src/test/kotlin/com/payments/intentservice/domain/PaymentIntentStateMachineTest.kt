package com.payments.intentservice.domain

import com.payments.intentservice.domain.event.PaymentIntentEvent
import com.payments.intentservice.domain.exception.InvalidStateTransitionException
import com.payments.intentservice.domain.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class PaymentIntentStateMachineTest {

    private fun buildIntent(status: PaymentIntentStatus = PaymentIntentStatus.REQUIRES_PAYMENT_METHOD) =
        PaymentIntent(
            id = "pi_test_123",
            amount = Money(2000L, Currency.USD),
            status = status,
            captureMethod = CaptureMethod.AUTOMATIC,
            confirmationMethod = ConfirmationMethod.AUTOMATIC,
            customerId = "cus_123",
            paymentMethodId = null,
            description = "Test payment",
            metadata = emptyMap(),
            idempotencyKey = null,
            clientSecret = "pi_test_123_secret_abc",
            canceledAt = null,
            cancellationReason = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

    // ─── Attach Payment Method ────────────────────────────────────────────────

    @Test
    fun `attach payment method transitions to REQUIRES_CONFIRMATION`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_PAYMENT_METHOD)
        val (updated, event) = intent.attachPaymentMethod("pm_card_visa")

        assertEquals(PaymentIntentStatus.REQUIRES_CONFIRMATION, updated.status)
        assertEquals("pm_card_visa", updated.paymentMethodId)
        assertTrue(event is PaymentIntentEvent.PaymentMethodAttached)
    }

    @Test
    fun `attach payment method fails on wrong status`() {
        val intent = buildIntent(PaymentIntentStatus.PROCESSING)
        assertThrows<InvalidStateTransitionException> {
            intent.attachPaymentMethod("pm_card_visa")
        }
    }

    // ─── Confirm ─────────────────────────────────────────────────────────────

    @Test
    fun `confirm without 3DS transitions to PROCESSING for automatic capture`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_CONFIRMATION)
            .copy(paymentMethodId = "pm_card_visa")

        val (updated, event) = intent.confirm(requiresAction = false)

        assertEquals(PaymentIntentStatus.PROCESSING, updated.status)
        assertTrue(event is PaymentIntentEvent.Confirmed)
    }

    @Test
    fun `confirm with 3DS transitions to REQUIRES_ACTION`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_CONFIRMATION)
            .copy(paymentMethodId = "pm_card_3ds")

        val (updated, event) = intent.confirm(requiresAction = true)

        assertEquals(PaymentIntentStatus.REQUIRES_ACTION, updated.status)
        assertTrue(event is PaymentIntentEvent.Confirmed)
    }

    @Test
    fun `confirm with manual capture transitions to REQUIRES_CAPTURE`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_CONFIRMATION)
            .copy(
                paymentMethodId = "pm_card_visa",
                captureMethod = CaptureMethod.MANUAL
            )

        val (updated, _) = intent.confirm(requiresAction = false)

        assertEquals(PaymentIntentStatus.REQUIRES_CAPTURE, updated.status)
    }

    @Test
    fun `confirm fails without payment method`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_CONFIRMATION)
        // paymentMethodId is null
        assertThrows<IllegalArgumentException> {
            intent.confirm()
        }
    }

    // ─── Capture ─────────────────────────────────────────────────────────────

    @Test
    fun `capture transitions to SUCCEEDED`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_CAPTURE)
        val (updated, event) = intent.capture()

        assertEquals(PaymentIntentStatus.SUCCEEDED, updated.status)
        assertTrue(event is PaymentIntentEvent.Captured)
        assertEquals(2000L, (event as PaymentIntentEvent.Captured).amountCaptured)
    }

    @Test
    fun `partial capture uses provided amount`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_CAPTURE)
        val (updated, event) = intent.capture(captureAmount = 1000L)

        assertEquals(PaymentIntentStatus.SUCCEEDED, updated.status)
        assertEquals(1000L, (event as PaymentIntentEvent.Captured).amountCaptured)
    }

    @Test
    fun `capture exceeding authorized amount throws`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_CAPTURE)
        assertThrows<IllegalArgumentException> {
            intent.capture(captureAmount = 9999L)
        }
    }

    @Test
    fun `capture on wrong status throws`() {
        val intent = buildIntent(PaymentIntentStatus.PROCESSING)
        assertThrows<InvalidStateTransitionException> {
            intent.capture()
        }
    }

    // ─── Succeed ─────────────────────────────────────────────────────────────

    @Test
    fun `succeed transitions PROCESSING to SUCCEEDED`() {
        val intent = buildIntent(PaymentIntentStatus.PROCESSING)
        val (updated, event) = intent.succeed()

        assertEquals(PaymentIntentStatus.SUCCEEDED, updated.status)
        assertTrue(event is PaymentIntentEvent.Succeeded)
    }

    @Test
    fun `succeed on non-PROCESSING status throws`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_CAPTURE)
        assertThrows<InvalidStateTransitionException> {
            intent.succeed()
        }
    }

    // ─── Cancel ──────────────────────────────────────────────────────────────

    @Test
    fun `cancel from REQUIRES_PAYMENT_METHOD succeeds`() {
        val intent = buildIntent(PaymentIntentStatus.REQUIRES_PAYMENT_METHOD)
        val (updated, event) = intent.cancel(CancellationReason.REQUESTED_BY_CUSTOMER)

        assertEquals(PaymentIntentStatus.CANCELED, updated.status)
        assertEquals(CancellationReason.REQUESTED_BY_CUSTOMER, updated.cancellationReason)
        assertNotNull(updated.canceledAt)
        assertTrue(event is PaymentIntentEvent.Canceled)
    }

    @Test
    fun `cancel from SUCCEEDED throws`() {
        val intent = buildIntent(PaymentIntentStatus.SUCCEEDED)
        assertThrows<InvalidStateTransitionException> {
            intent.cancel()
        }
    }

    @Test
    fun `cancel from CANCELED throws`() {
        val intent = buildIntent(PaymentIntentStatus.CANCELED)
        assertThrows<InvalidStateTransitionException> {
            intent.cancel()
        }
    }

    // ─── Terminal State ───────────────────────────────────────────────────────

    @Test
    fun `SUCCEEDED is terminal`() {
        assertTrue(buildIntent(PaymentIntentStatus.SUCCEEDED).isTerminal)
    }

    @Test
    fun `CANCELED is terminal`() {
        assertTrue(buildIntent(PaymentIntentStatus.CANCELED).isTerminal)
    }

    @Test
    fun `PROCESSING is not terminal`() {
        assertFalse(buildIntent(PaymentIntentStatus.PROCESSING).isTerminal)
    }
}
