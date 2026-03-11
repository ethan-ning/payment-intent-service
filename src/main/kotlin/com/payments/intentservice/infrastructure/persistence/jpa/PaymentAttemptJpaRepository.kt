package com.payments.intentservice.infrastructure.persistence.jpa

import com.payments.intentservice.infrastructure.persistence.entity.PaymentAttemptEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface PaymentAttemptJpaRepository : JpaRepository<PaymentAttemptEntity, String> {

    fun findAllByPaymentIntentId(paymentIntentId: String): List<PaymentAttemptEntity>

    @Query("""
        SELECT a FROM PaymentAttemptEntity a
        WHERE a.paymentIntentId = :paymentIntentId
        ORDER BY a.createdAt DESC
    """)
    fun findLatestByPaymentIntentId(
        @Param("paymentIntentId") paymentIntentId: String,
        pageable: org.springframework.data.domain.Pageable
    ): List<PaymentAttemptEntity>
}
