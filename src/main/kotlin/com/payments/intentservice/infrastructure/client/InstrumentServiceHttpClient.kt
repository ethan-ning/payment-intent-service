package com.payments.intentservice.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.payments.intentservice.application.port.outbound.BillingDetailsData
import com.payments.intentservice.application.port.outbound.CreateInstrumentRequest
import com.payments.intentservice.application.port.outbound.InstrumentServiceClient
import com.payments.intentservice.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * HTTP adapter for payment-instrument-service.
 *
 * Only activated when `instrument-service.base-url` is configured.
 * When absent, InstrumentServiceClient bean is null and the use case skips instrument creation.
 */
@Component
@ConditionalOnProperty("instrument-service.base-url")
class InstrumentServiceHttpClient(
    @Value("\${instrument-service.base-url}") private val baseUrl: String,
    private val objectMapper: ObjectMapper,
) : InstrumentServiceClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = RestClient.builder().baseUrl(baseUrl).build()

    override fun createInstrument(request: CreateInstrumentRequest): String {
        val body = buildCreateBody(request)
        val response = restClient.post()
            .uri("/v1/payment_methods")
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve()
            .body(Map::class.java)
            ?: error("Empty response from instrument service on createInstrument")

        return response["id"] as? String
            ?: error("instrument service response missing 'id': $response")
    }

    override fun recordStoredCredential(
        instrumentId: String,
        networkTransactionId: String,
        paymentIntentId: String,
    ) {
        val body = mapOf(
            "networkTransactionId" to networkTransactionId,
            "paymentIntentId" to paymentIntentId,
        )
        restClient.post()
            .uri("/v1/payment_methods/$instrumentId/stored-credential")
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve()
            .toBodilessEntity()
        log.debug("Recorded stored credential on {} for intent {}", instrumentId, paymentIntentId)
    }

    private fun buildCreateBody(request: CreateInstrumentRequest): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            "type" to mapPaymentMethodType(request.paymentMethod),
            "customer" to request.customerId,
            "setup_future_usage" to request.setupFutureUsage.name.lowercase(),
            "metadata" to request.metadata,
        )

        request.billingDetails?.let {
            base["billing_details"] = mapOf(
                "name" to it.name,
                "email" to it.email,
                "phone" to it.phone,
            )
        }

        // Add type-specific fields
        when (val pm = request.paymentMethod) {
            is PaymentMethod.Card -> base["card"] = mapOf(
                "brand" to pm.scheme.name,
                "last4" to pm.last4,
                "expMonth" to pm.expiryMonth,
                "expYear" to pm.expiryYear,
                "funding" to pm.funding.name,
                "country" to pm.issuerCountry,
                "fingerprint" to (pm.fingerprint ?: "fp_unknown"),
            )
            is PaymentMethod.DeviceWallet -> base["card"] = mapOf(
                "last4" to pm.dynamicLast4,
                "brand" to (pm.underlyingCard?.scheme?.name ?: "UNKNOWN"),
                "expMonth" to (pm.underlyingCard?.expiryMonth ?: 0),
                "expYear" to (pm.underlyingCard?.expiryYear ?: 0),
                "fingerprint" to "fp_wallet_${pm.walletType.name.lowercase()}",
            )
            else -> { /* no extra fields for wallets/bank transfers */ }
        }

        return base
    }

    private fun mapPaymentMethodType(pm: PaymentMethod): String = when (pm) {
        is PaymentMethod.Card -> "card"
        is PaymentMethod.DeviceWallet -> "card"   // device wallets store underlying card
        is PaymentMethod.DigitalWallet -> pm.walletType.name.lowercase()
        is PaymentMethod.RealTimeBankTransfer -> pm.rail.name.lowercase()
        is PaymentMethod.BuyNowPayLater -> pm.provider.name.lowercase()
        is PaymentMethod.BankTransfer -> "us_bank_account"
    }
}
