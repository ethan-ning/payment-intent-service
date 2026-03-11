package com.payments.intentservice.infrastructure.persistence.outbox

import com.payments.intentservice.application.port.outbound.OutboxEvent
import com.payments.intentservice.application.port.outbound.OutboxRepository
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL.*
import org.springframework.stereotype.Repository

@Repository
class JooqOutboxRepository(
    private val dsl: DSLContext
) : OutboxRepository {

    companion object {
        private val TABLE = table("outbox_events")
    }

    override fun save(event: OutboxEvent) {
        dsl.insertInto(TABLE)
            .set(field("aggregate_id"), event.aggregateId)
            .set(field("aggregate_type"), event.aggregateType)
            .set(field("event_type"), event.eventType)
            .set(field("payload"), JSONB.valueOf(event.payload))
            .set(field("status"), "PENDING")
            .execute()
    }
}
