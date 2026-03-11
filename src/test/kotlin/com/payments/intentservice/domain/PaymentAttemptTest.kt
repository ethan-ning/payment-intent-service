package com.payments.intentservice.domain

import com.payments.intentservice.domain.exception.InvalidStateTransitionException
import com.payments.intentservice.domain.exception.PaymentAttemptViolationException
import com.payments.intentservice.domain.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class PaymentAttemptTest {

    private val visaCard = PaymentMethod.Card(
        scheme = CardScheme.VISA, last4 = "4242",
        expiryMonth = 12, expiryYear = 2027,
        funding = CardFunding.CREDIT, fingerprint = null, issuerCountry = "SG"
    )

    private val wechatPay = PaymentMethod.DigitalWallet(walletType = DigitalWalletType.WECHAT_PAY)
    private val applePay = PaymentMethod.DeviceWallet(walletType = DeviceWalletType.APPLE_PAY, dynamicLast4 = "1234", underlyingCard = visaCard)
    private val payNow = PaymentMethod.RealTimeBankTransfer(rail = RealTimeBankRail.PAYNOW)

    private fun buildIntent(
        status: PaymentIntentStatus = PaymentIntentStatus.REQUIRES_CONFIRMATION,
        availableMethods: Set<PaymentMethodType> = emptySet()
    ) = PaymentIntent(
            id = "pi_test",
            amount = Money(2000L, Currency.USD),
            status = status,
            captureMethod = CaptureMethod.AUTOMATIC,
            confirmationMethod = ConfirmationMethod.AUTOMATIC,
            customerId = null,
            paymentMethodId = null,
            description = null,
            metadata = emptyMap(),
            idempotencyKey = null,
            clientSecret = "pi_test_secret_abc",
            availablePaymentMethods = availableMethods,
            canceledAt = null,
            cancellationReason = null,
            latestPaymentAttemptId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

    private fun buildAttempt(
        id: String = "at_001",
        status: PaymentAttemptStatus = PaymentAttemptStatus.PENDING,
        createdAt: Instant = Instant.now()
    ) = PaymentAttempt(
        id = id,
        paymentIntentId = "pi_test",
        amount = Money(2000L, Currency.USD),
        status = status,
        paymentMethod = PaymentMethod.Card(
            scheme = CardScheme.VISA,
            last4 = "4242",
            expiryMonth = 12,
            expiryYear = 2027,
            funding = CardFunding.CREDIT,
            fingerprint = null,
            issuerCountry = "SG"
        ),
        capturedAmount = null,
        processorReference = null,
        failureCode = null,
        failureMessage = null,
        nextAction = null,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    // ─── createAttempt ────────────────────────────────────────────────────────

    @Test
    fun `createAttempt succeeds when no prior attempts`() {
        val intent = buildIntent()
        val (updatedIntent, attempt, _) = intent.createAttempt(
            chosenPaymentMethod = visaCard,
            existingAttempts = emptyList()
        )
        assertTrue(attempt.id.startsWith("at_"))
        assertEquals(PaymentAttemptStatus.PENDING, attempt.status)
        assertEquals(attempt.id, updatedIntent.latestPaymentAttemptId)
        assertEquals(CardScheme.VISA, (attempt.paymentMethod as PaymentMethod.Card).scheme)
    }

    @Test
    fun `createAttempt succeeds after a failed attempt (retry)`() {
        val intent = buildIntent()
        val failedAttempt = buildAttempt(id = "at_001", status = PaymentAttemptStatus.FAILED)

        val (_, newAttempt, _) = intent.createAttempt(
            chosenPaymentMethod = visaCard,
            existingAttempts = listOf(failedAttempt)
        )

        assertNotEquals("at_001", newAttempt.id)
        assertEquals(PaymentAttemptStatus.PENDING, newAttempt.status)
    }

    @Test
    fun `createAttempt blocked after a succeeded attempt`() {
        val intent = buildIntent()
        val succeededAttempt = buildAttempt(id = "at_001", status = PaymentAttemptStatus.SUCCEEDED)

        assertThrows<PaymentAttemptViolationException> {
            intent.createAttempt(
                chosenPaymentMethod = visaCard,
                existingAttempts = listOf(succeededAttempt)
            )
        }
    }

    @Test
    fun `PayPal and WeChat Pay are digital wallets — same category`() {
        val paypal = PaymentMethod.DigitalWallet(walletType = DigitalWalletType.PAYPAL, email = "user@example.com")
        val wechat = PaymentMethod.DigitalWallet(walletType = DigitalWalletType.WECHAT_PAY)
        assertTrue(paypal.type.isDigitalWallet)
        assertTrue(wechat.type.isDigitalWallet)
        assertFalse(paypal.type.isDeviceWallet)
        assertFalse(wechat.type.isDeviceWallet)
    }

    @Test
    fun `Apple Pay is a device wallet (card-backed), not a digital wallet`() {
        val applePay = PaymentMethod.DeviceWallet(DeviceWalletType.APPLE_PAY, dynamicLast4 = "1234", underlyingCard = visaCard)
        assertTrue(applePay.type.isDeviceWallet)
        assertTrue(applePay.type.isCardBacked)
        assertFalse(applePay.type.isDigitalWallet)
    }

    @Test
    fun `PayNow is a real-time bank transfer, not a digital wallet`() {
        val payNow = PaymentMethod.RealTimeBankTransfer(rail = RealTimeBankRail.PAYNOW)
        assertTrue(payNow.type.isRealTimeBankTransfer)
        assertFalse(payNow.type.isDigitalWallet)
        assertFalse(payNow.type.isCardBacked)
    }

    @Test
    fun `createAttempt blocked if method not in availablePaymentMethods`() {
        val intent = buildIntent(availableMethods = setOf(PaymentMethodType.WECHAT_PAY, PaymentMethodType.ALIPAY))

        assertThrows<PaymentAttemptViolationException> {
            intent.createAttempt(
                chosenPaymentMethod = visaCard,  // CARD not in available list
                existingAttempts = emptyList()
            )
        }
    }

    @Test
    fun `createAttempt allowed when availablePaymentMethods is empty (no restriction)`() {
        val intent = buildIntent(availableMethods = emptySet())  // empty = all allowed

        val (_, attempt, _) = intent.createAttempt(
            chosenPaymentMethod = visaCard,
            existingAttempts = emptyList()
        )
        assertEquals(PaymentAttemptStatus.PENDING, attempt.status)
    }

    // ─── applyAttemptSucceeded ────────────────────────────────────────────────

    @Test
    fun `applyAttemptSucceeded works when attempt is the latest`() {
        val intent = buildIntent()
        val attempt = buildAttempt(id = "at_002", status = PaymentAttemptStatus.SUCCEEDED)

        val (updatedIntent, event) = intent.applyAttemptSucceeded(
            attempt = attempt,
            existingAttempts = listOf(attempt)  // it IS the latest
        )

        assertEquals(PaymentIntentStatus.SUCCEEDED, updatedIntent.status)
    }

    @Test
    fun `applyAttemptSucceeded fails when attempt is not the latest`() {
        val intent = buildIntent()
        val olderAttempt = buildAttempt(id = "at_001", status = PaymentAttemptStatus.SUCCEEDED,
            createdAt = Instant.now().minusSeconds(60))
        val newerAttempt = buildAttempt(id = "at_002", status = PaymentAttemptStatus.FAILED,
            createdAt = Instant.now())

        assertThrows<PaymentAttemptViolationException> {
            intent.applyAttemptSucceeded(
                attempt = olderAttempt,
                existingAttempts = listOf(olderAttempt, newerAttempt)
            )
        }
    }

    // ─── PaymentAttempt state machine ─────────────────────────────────────────

    @Test
    fun `attempt can succeed from PENDING`() {
        val attempt = buildAttempt(status = PaymentAttemptStatus.PENDING)
        val (updated, _) = attempt.succeed(processorRef = "txn_123")
        assertEquals(PaymentAttemptStatus.SUCCEEDED, updated.status)
        assertEquals("txn_123", updated.processorReference)
    }

    @Test
    fun `attempt can fail from PENDING`() {
        val attempt = buildAttempt(status = PaymentAttemptStatus.PENDING)
        val (updated, _) = attempt.fail("card_declined", "Your card was declined.")
        assertEquals(PaymentAttemptStatus.FAILED, updated.status)
        assertEquals("card_declined", updated.failureCode)
    }

    @Test
    fun `attempt can require action from PENDING`() {
        val attempt = buildAttempt(status = PaymentAttemptStatus.PENDING)
        val action = NextAction("redirect_to_url", "https://3ds.bank.com/auth")
        val (updated, _) = attempt.requireAction(action)
        assertEquals(PaymentAttemptStatus.REQUIRES_ACTION, updated.status)
        assertEquals("redirect_to_url", updated.nextAction?.type)
    }

    @Test
    fun `attempt cannot succeed from CANCELLED`() {
        val attempt = buildAttempt(status = PaymentAttemptStatus.CANCELLED)
        assertThrows<InvalidStateTransitionException> {
            attempt.succeed(null)
        }
    }

    @Test
    fun `attempt cannot fail from SUCCEEDED`() {
        val attempt = buildAttempt(status = PaymentAttemptStatus.SUCCEEDED)
        assertThrows<InvalidStateTransitionException> {
            attempt.fail("code", "msg")
        }
    }

    @Test
    fun `capture reduces to provided amount`() {
        val attempt = buildAttempt(status = PaymentAttemptStatus.PENDING)
        val (updated, _) = attempt.capture(captureAmount = 1500L, processorRef = null)
        assertEquals(PaymentAttemptStatus.SUCCEEDED, updated.status)
        assertEquals(1500L, updated.capturedAmount)
    }

    @Test
    fun `capture exceeding authorized amount throws`() {
        val attempt = buildAttempt(status = PaymentAttemptStatus.PENDING)
        assertThrows<IllegalArgumentException> {
            attempt.capture(captureAmount = 9999L, processorRef = null)
        }
    }

    @Test
    fun `terminal attempts are correctly identified`() {
        assertTrue(buildAttempt(status = PaymentAttemptStatus.SUCCEEDED).isTerminal)
        assertTrue(buildAttempt(status = PaymentAttemptStatus.FAILED).isTerminal)
        assertTrue(buildAttempt(status = PaymentAttemptStatus.CANCELLED).isTerminal)
        assertFalse(buildAttempt(status = PaymentAttemptStatus.PENDING).isTerminal)
        assertFalse(buildAttempt(status = PaymentAttemptStatus.PROCESSING).isTerminal)
        assertFalse(buildAttempt(status = PaymentAttemptStatus.REQUIRES_ACTION).isTerminal)
    }
}
