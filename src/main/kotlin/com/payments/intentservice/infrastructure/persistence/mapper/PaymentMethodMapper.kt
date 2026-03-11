package com.payments.intentservice.infrastructure.persistence.mapper

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.payments.intentservice.domain.model.*
import org.springframework.stereotype.Component

/**
 * Serializes/deserializes the PaymentMethod sealed class to/from JSON.
 *
 * Uses a "type" discriminator field in the JSON matching PaymentMethodType.
 * All values in the JSONB column are display-safe only — no PAN/CVV.
 *
 * Example JSON shapes:
 *   CARD:       {"type":"CARD","scheme":"VISA","last4":"4242","expiryMonth":12,"expiryYear":2026,"funding":"CREDIT"}
 *   WECHAT_PAY: {"type":"WECHAT_PAY","transactionReference":"wx_abc123"}
 *   APPLE_PAY:  {"type":"APPLE_PAY","walletType":"APPLE_PAY","dynamicLast4":"1234"}
 *   KLARNA:     {"type":"KLARNA","installments":3}
 */
@Component
class PaymentMethodMapper(private val objectMapper: ObjectMapper) {

    fun serialize(method: PaymentMethod): String {
        val node: ObjectNode = objectMapper.createObjectNode()
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
            is PaymentMethod.Wallet -> {
                node.put("walletType", method.walletType.name)
                method.email?.let { node.put("email", it) }
                method.dynamicLast4?.let { node.put("dynamicLast4", it) }
                method.underlyingCard?.let {
                    node.set<JsonNode>("underlyingCard", objectMapper.readTree(serialize(it)))
                }
            }
            is PaymentMethod.RealTimePayment -> {
                node.put("provider", method.provider.name)
                method.transactionReference?.let { node.put("transactionReference", it) }
            }
            is PaymentMethod.BuyNowPayLater -> {
                node.put("provider", method.provider.name)
                method.installments?.let { node.put("installments", it) }
            }
            is PaymentMethod.BankTransfer -> {
                method.bankName?.let { node.put("bankName", it) }
                method.last4?.let { node.put("last4", it) }
                node.put("scheme", method.scheme.name)
            }
        }

        return objectMapper.writeValueAsString(node)
    }

    fun deserialize(json: String): PaymentMethod {
        val node = objectMapper.readTree(json)
        val type = PaymentMethodType.valueOf(node.get("type").asText())

        return when {
            type == PaymentMethodType.CARD -> PaymentMethod.Card(
                scheme = CardScheme.valueOf(node.get("scheme").asText()),
                last4 = node.get("last4").asText(),
                expiryMonth = node.get("expiryMonth").asInt(),
                expiryYear = node.get("expiryYear").asInt(),
                funding = CardFunding.valueOf(node.get("funding")?.asText() ?: "UNKNOWN"),
                fingerprint = node.get("fingerprint")?.asText(),
                issuerCountry = node.get("issuerCountry")?.asText()
            )
            type.isWallet -> PaymentMethod.Wallet(
                walletType = WalletType.valueOf(node.get("walletType").asText()),
                email = node.get("email")?.asText(),
                dynamicLast4 = node.get("dynamicLast4")?.asText(),
                underlyingCard = node.get("underlyingCard")?.let {
                    deserialize(objectMapper.writeValueAsString(it)) as? PaymentMethod.Card
                }
            )
            type in setOf(PaymentMethodType.WECHAT_PAY, PaymentMethodType.ALIPAY,
                PaymentMethodType.GRABPAY, PaymentMethodType.PAYNOW, PaymentMethodType.PROMPTPAY) ->
                PaymentMethod.RealTimePayment(
                    provider = RtpProvider.valueOf(node.get("provider").asText()),
                    transactionReference = node.get("transactionReference")?.asText()
                )
            type in setOf(PaymentMethodType.KLARNA, PaymentMethodType.AFTERPAY, PaymentMethodType.ATOME) ->
                PaymentMethod.BuyNowPayLater(
                    provider = BnplProvider.valueOf(node.get("provider").asText()),
                    installments = node.get("installments")?.asInt()
                )
            type == PaymentMethodType.BANK_TRANSFER -> PaymentMethod.BankTransfer(
                bankName = node.get("bankName")?.asText(),
                last4 = node.get("last4")?.asText(),
                scheme = BankTransferScheme.valueOf(node.get("scheme").asText())
            )
            else -> throw IllegalArgumentException("Unknown payment method type: $type")
        }
    }
}
