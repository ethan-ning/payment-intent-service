package com.payments.intentservice.infrastructure.persistence.mapper

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.payments.intentservice.domain.model.*
import org.springframework.stereotype.Component

/**
 * Serializes/deserializes PaymentMethod sealed class to/from JSONB.
 *
 * Uses "type" as the discriminator (matches PaymentMethodType enum name).
 * All fields are display-safe — no PAN, no CVV.
 *
 * Examples:
 *   CARD:            {"type":"CARD","scheme":"VISA","last4":"4242","expiryMonth":12,"expiryYear":2027,"funding":"CREDIT"}
 *   APPLE_PAY:       {"type":"APPLE_PAY","walletType":"APPLE_PAY","dynamicLast4":"1234","underlyingCard":{...card...}}
 *   PAYPAL:          {"type":"PAYPAL","walletType":"PAYPAL","email":"shopper@example.com"}
 *   WECHAT_PAY:      {"type":"WECHAT_PAY","walletType":"WECHAT_PAY"}
 *   PAYNOW:          {"type":"PAYNOW","rail":"PAYNOW","bankReference":"TXN123"}
 *   KLARNA:          {"type":"KLARNA","provider":"KLARNA","installments":3}
 *   BANK_TRANSFER:   {"type":"BANK_TRANSFER","scheme":"ACH","last4":"6789"}
 */
@Component
class PaymentMethodMapper(private val objectMapper: ObjectMapper) {

    fun serialize(method: PaymentMethod): String {
        val node = objectMapper.createObjectNode()
        node.put("type", method.type.name)

        when (method) {
            is PaymentMethod.Card -> {
                node.put("scheme", method.scheme.name)
                node.put("last4", method.last4)
                node.put("expiryMonth", method.expiryMonth)
                node.put("expiryYear", method.expiryYear)
                node.put("funding", method.funding.name)
                method.fingerprint?.let { node.put("fingerprint", it) }
                method.issuerCountry?.let { node.put("issuerCountry", it) }
            }

            is PaymentMethod.DeviceWallet -> {
                node.put("walletType", method.walletType.name)
                method.dynamicLast4?.let { node.put("dynamicLast4", it) }
                method.underlyingCard?.let {
                    node.set<JsonNode>("underlyingCard", objectMapper.readTree(serialize(it)))
                }
            }

            is PaymentMethod.DigitalWallet -> {
                node.put("walletType", method.walletType.name)
                method.email?.let { node.put("email", it) }
                method.accountReference?.let { node.put("accountReference", it) }
            }

            is PaymentMethod.RealTimeBankTransfer -> {
                node.put("rail", method.rail.name)
                method.bankReference?.let { node.put("bankReference", it) }
            }

            is PaymentMethod.BuyNowPayLater -> {
                node.put("provider", method.provider.name)
                method.installments?.let { node.put("installments", it) }
            }

            is PaymentMethod.BankTransfer -> {
                node.put("scheme", method.scheme.name)
                method.bankName?.let { node.put("bankName", it) }
                method.last4?.let { node.put("last4", it) }
            }
        }

        return objectMapper.writeValueAsString(node)
    }

    fun deserialize(json: String): PaymentMethod {
        val node = objectMapper.readTree(json)
        val type = PaymentMethodType.valueOf(node.get("type").asText())

        return when {
            type == PaymentMethodType.CARD ->
                PaymentMethod.Card(
                    scheme = CardScheme.valueOf(node.get("scheme").asText()),
                    last4 = node.get("last4").asText(),
                    expiryMonth = node.get("expiryMonth").asInt(),
                    expiryYear = node.get("expiryYear").asInt(),
                    funding = CardFunding.valueOf(node.get("funding")?.asText() ?: "UNKNOWN"),
                    fingerprint = node.get("fingerprint")?.asText(),
                    issuerCountry = node.get("issuerCountry")?.asText()
                )

            type.isDeviceWallet ->
                PaymentMethod.DeviceWallet(
                    walletType = DeviceWalletType.valueOf(node.get("walletType").asText()),
                    dynamicLast4 = node.get("dynamicLast4")?.asText(),
                    underlyingCard = node.get("underlyingCard")?.let {
                        deserialize(objectMapper.writeValueAsString(it)) as? PaymentMethod.Card
                    }
                )

            type.isDigitalWallet ->
                PaymentMethod.DigitalWallet(
                    walletType = DigitalWalletType.valueOf(node.get("walletType").asText()),
                    email = node.get("email")?.asText(),
                    accountReference = node.get("accountReference")?.asText()
                )

            type.isRealTimeBankTransfer ->
                PaymentMethod.RealTimeBankTransfer(
                    rail = RealTimeBankRail.valueOf(node.get("rail").asText()),
                    bankReference = node.get("bankReference")?.asText()
                )

            type.isBnpl ->
                PaymentMethod.BuyNowPayLater(
                    provider = BnplProvider.valueOf(node.get("provider").asText()),
                    installments = node.get("installments")?.asInt()
                )

            type == PaymentMethodType.BANK_TRANSFER ->
                PaymentMethod.BankTransfer(
                    scheme = BankTransferScheme.valueOf(node.get("scheme").asText()),
                    bankName = node.get("bankName")?.asText(),
                    last4 = node.get("last4")?.asText()
                )

            else -> throw IllegalArgumentException("Cannot deserialize unknown payment method type: $type")
        }
    }
}
