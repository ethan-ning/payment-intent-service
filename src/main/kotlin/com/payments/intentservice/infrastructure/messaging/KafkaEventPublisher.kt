package com.payments.intentservice.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.payments.intentservice.domain.event.PaymentIntentEvent
import com.payments.intentservice.application.port.outbound.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

data class KafkaEventEnvelope(
    val eventId: String,
    val eventType: String,
    val aggregateId: String,
    val aggregateType: String,
    val timestamp: String,
    val data: Any
)

@Component
class KafkaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : DomainEventPublisher {

    private val log = LoggerFactory.getLogger(KafkaEventPublisher::class.java)

    companion object {
        const val PAYMENT_INTENTS_TOPIC = "payment-intents.events"
    }

    override fun publish(event: PaymentIntentEvent) {
        val envelope = KafkaEventEnvelope(
            eventId = event.eventId,
            eventType = event.eventType,
            aggregateId = event.paymentIntentId,
            aggregateType = "PaymentIntent",
            timestamp = event.occurredAt.toString(),
            data = event
        )

        val payload = objectMapper.writeValueAsString(envelope)
        publish(PAYMENT_INTENTS_TOPIC, event.paymentIntentId, event.eventType, payload)
    }

    fun publish(topic: String, key: String, eventType: String, payload: String) {
        val future = kafkaTemplate.send(topic, key, payload)
        future.whenComplete { result, ex ->
            if (ex != null) {
                log.error("Failed to publish $eventType for key=$key to $topic", ex)
                throw ex
            } else {
                log.debug("Published $eventType for key=$key to $topic offset=${result.recordMetadata.offset()}")
            }
        }
    }
}
