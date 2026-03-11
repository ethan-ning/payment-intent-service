package com.payments.intentservice.infrastructure.persistence.outbox

import com.payments.intentservice.infrastructure.messaging.KafkaEventPublisher
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Outbox Relay: polls the outbox_events table and publishes PENDING events to Kafka.
 *
 * Uses SELECT FOR UPDATE SKIP LOCKED to safely run multiple instances in parallel
 * without double-publishing. This is the only place we couple DB + Kafka.
 *
 * In production, consider replacing this with Debezium CDC for lower latency.
 */
@Component
class OutboxPoller(
    private val dsl: DSLContext,
    private val kafkaPublisher: KafkaEventPublisher
) {
    private val log = LoggerFactory.getLogger(OutboxPoller::class.java)

    companion object {
        private val TABLE = table("outbox_events")
        private const val BATCH_SIZE = 100
        private const val MAX_RETRIES = 5
    }

    @Scheduled(fixedDelayString = "\${outbox.poll-interval-ms:1000}")
    @Transactional
    fun poll() {
        val pending = dsl.select()
            .from(TABLE)
            .where(
                field("status").eq("PENDING")
                    .and(field("retry_count").lessThan(MAX_RETRIES))
            )
            .orderBy(field("created_at").asc())
            .limit(BATCH_SIZE)
            .forUpdate()
            .skipLocked()
            .fetch()

        if (pending.isEmpty()) return

        log.debug("Processing ${pending.size} outbox events")

        for (record in pending) {
            val eventId = record.getValue("id")
            val aggregateId = record.getValue("aggregate_id") as String
            val eventType = record.getValue("event_type") as String
            val payload = record.getValue("payload").toString()

            try {
                kafkaPublisher.publish(
                    topic = "payment-intents.events",
                    key = aggregateId,
                    eventType = eventType,
                    payload = payload
                )

                dsl.update(TABLE)
                    .set(field("status"), "PUBLISHED")
                    .set(field("published_at"), LocalDateTime.now(ZoneOffset.UTC))
                    .where(field("id").eq(eventId))
                    .execute()

            } catch (e: Exception) {
                log.error("Failed to publish outbox event $eventId: ${e.message}", e)

                dsl.update(TABLE)
                    .set(field("status"), "FAILED")
                    .set(field("retry_count"), field("retry_count").add(1))
                    .where(field("id").eq(eventId))
                    .execute()
            }
        }
    }
}
