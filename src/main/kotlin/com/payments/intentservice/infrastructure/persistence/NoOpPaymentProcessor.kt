package com.payments.intentservice.infrastructure.persistence

import com.payments.intentservice.application.port.outbound.PaymentProcessor
import com.payments.intentservice.application.port.outbound.ProcessorResult
import com.payments.intentservice.domain.model.PaymentIntent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Stub/No-op payment processor.
 * Replace with real processor adapter (Stripe, Adyen, etc.) in production.
 * This lives in infrastructure — adapters to external services belong here.
 */
@Component
class NoOpPaymentProcessor : PaymentProcessor {

    private val log = LoggerFactory.getLogger(NoOpPaymentProcessor::class.java)

    override fun process(paymentIntent: PaymentIntent): ProcessorResult {
        log.info("Processing payment intent ${paymentIntent.id} (no-op)")
        return ProcessorResult(
            success = true,
            requiresAction = false,
            processorReference = "proc_${paymentIntent.id}"
        )
    }

    override fun capture(paymentIntentId: String, amount: Long): ProcessorResult {
        log.info("Capturing $amount for payment intent $paymentIntentId (no-op)")
        return ProcessorResult(success = true)
    }

    override fun cancel(paymentIntentId: String): ProcessorResult {
        log.info("Canceling payment intent $paymentIntentId (no-op)")
        return ProcessorResult(success = true)
    }
}
