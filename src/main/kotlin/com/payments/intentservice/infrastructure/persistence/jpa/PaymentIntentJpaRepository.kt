package com.payments.intentservice.infrastructure.persistence.jpa

import com.payments.intentservice.infrastructure.persistence.entity.PaymentIntentEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

/**
 * Spring Data JPA repository — infrastructure detail.
 * Never referenced directly by the application or domain layer.
 * The JpaPaymentIntentRepository adapter implements the outbound port.
 */
interface PaymentIntentJpaRepository : JpaRepository<PaymentIntentEntity, String> {

    fun findByIdempotencyKey(idempotencyKey: String): Optional<PaymentIntentEntity>

    fun findByCustomerId(customerId: String, pageable: Pageable): Page<PaymentIntentEntity>

    @Query("""
        SELECT p FROM PaymentIntentEntity p
        WHERE (:customerId IS NULL OR p.customerId = :customerId)
        AND (:startingAfter IS NULL OR p.id > :startingAfter)
        AND (:endingBefore IS NULL OR p.id < :endingBefore)
        ORDER BY p.createdAt DESC
    """)
    fun findAllFiltered(
        @Param("customerId") customerId: String?,
        @Param("startingAfter") startingAfter: String?,
        @Param("endingBefore") endingBefore: String?,
        pageable: Pageable
    ): Page<PaymentIntentEntity>
}
