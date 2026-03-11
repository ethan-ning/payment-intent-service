package com.payments.intentservice.infrastructure.persistence.outbox

import com.payments.intentservice.infrastructure.messaging.KafkaEventPublisher
import com.payments.intentservice.infrastructure.persistence.jpa.OutboxEventJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Outbox Relay: polls PENDING outbox_events and publishes them to Kafka.
 *
 * Uses SELECT FOR UPDATE SKIP LOCKED (via native query in OutboxEventJpaRepository)
 * to safely handle concurrent poller instances without double-publishing.
 *
 * In production, consider replacing with Debezium CDC for sub-second latency.
 */
@Component
class OutboxPoller(
    private val outboxJpaRepository: OutboxEventJpaRepository,
    private val kafkaPublisher: KafkaEventPublisher
) {
    private val log = LoggerFactory.getLogger(OutboxPoller::class.java)

    companion object {
        private const val BATCH_SIZE = 100
        private const val MAX_RETRIES = 5
    }

    @Scheduled(fixedDelayString = "\${outbox.poll-interval-ms:1000}")
    @Transactional
    fun poll() {
        val pending = outboxJpaRepository.findPendingForUpdate(BATCH_SIZE, MAX_RETRIES)
        if (pending.isEmpty()) return

        log.debug("Processing ${pending.size} outbox events")

        for (event in pending) {
            try {
                kafkaPublisher.publish(
                    topic = KafkaEventPublisher.PAYMENT_INTENTS_TOPIC,
                    key = event.aggregateId,
                    eventType = event.eventType,
                    payload = event.payload
                )
                event.status = "PUBLISHED"
                event.publishedAt = Instant.now()

            } catch (e: Exception) {
                log.error("Failed to publish outbox event ${event.id}: ${e.message}", e)
                event.status = "FAILED"
                event.retryCount++
            }

            outboxJpaRepository.save(event)
        }
    }
}
