package com.payments.intentservice.infrastructure.persistence.outbox

import com.payments.intentservice.application.port.outbound.OutboxEvent
import com.payments.intentservice.application.port.outbound.OutboxRepository
import com.payments.intentservice.infrastructure.persistence.entity.OutboxEventEntity
import com.payments.intentservice.infrastructure.persistence.jpa.OutboxEventJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Implements the outbound port [OutboxRepository] using Spring Data JPA.
 */
@Repository
@Transactional
class JpaOutboxRepository(
    private val jpaRepository: OutboxEventJpaRepository
) : OutboxRepository {

    override fun save(event: OutboxEvent) {
        jpaRepository.save(
            OutboxEventEntity(
                aggregateId = event.aggregateId,
                aggregateType = event.aggregateType,
                eventType = event.eventType,
                payload = event.payload
            )
        )
    }
}
