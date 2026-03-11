package com.payments.intentservice.infrastructure.persistence.jpa

import com.payments.intentservice.infrastructure.persistence.entity.OutboxEventEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Fetch pending events with SKIP LOCKED to safely support multiple poller instances.
     * SKIP LOCKED skips rows locked by other transactions — prevents double-processing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND retry_count < :maxRetries
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findPendingForUpdate(
        @Param("batchSize") batchSize: Int,
        @Param("maxRetries") maxRetries: Int
    ): List<OutboxEventEntity>
}
