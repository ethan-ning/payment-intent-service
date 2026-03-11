package com.payments.intentservice.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.payments.intentservice.application.port.outbound.OutboxRepository
import com.payments.intentservice.application.port.outbound.PaymentAttemptRepository
import com.payments.intentservice.application.port.outbound.PaymentIntentRepository
import com.payments.intentservice.application.port.outbound.PaymentProcessor
import com.payments.intentservice.application.usecase.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires use cases with their dependencies.
 * Use cases are plain Kotlin — Spring only touches them here via @Bean.
 * This is the Dependency Inversion Principle in action:
 * the application layer defines interfaces; infrastructure provides implementations.
 */
@Configuration
class UseCaseConfig {

    @Bean
    fun createPaymentIntentUseCase(
        repository: PaymentIntentRepository,
        outboxRepository: OutboxRepository,
        objectMapper: ObjectMapper
    ) = CreatePaymentIntentUseCaseImpl(repository, outboxRepository, objectMapper)

    @Bean
    fun confirmPaymentIntentUseCase(
        repository: PaymentIntentRepository,
        attemptRepository: PaymentAttemptRepository,
        outboxRepository: OutboxRepository,
        processor: PaymentProcessor,
        objectMapper: ObjectMapper
    ) = ConfirmPaymentIntentUseCaseImpl(repository, attemptRepository, outboxRepository, processor, objectMapper)

    @Bean
    fun capturePaymentIntentUseCase(
        repository: PaymentIntentRepository,
        attemptRepository: PaymentAttemptRepository,
        outboxRepository: OutboxRepository,
        processor: PaymentProcessor,
        objectMapper: ObjectMapper
    ) = CapturePaymentIntentUseCaseImpl(repository, attemptRepository, outboxRepository, processor, objectMapper)

    @Bean
    fun cancelPaymentIntentUseCase(
        repository: PaymentIntentRepository,
        attemptRepository: PaymentAttemptRepository,
        outboxRepository: OutboxRepository,
        processor: PaymentProcessor,
        objectMapper: ObjectMapper
    ) = CancelPaymentIntentUseCaseImpl(repository, attemptRepository, outboxRepository, processor, objectMapper)

    @Bean
    fun getPaymentIntentUseCase(repository: PaymentIntentRepository) =
        GetPaymentIntentUseCaseImpl(repository)

    @Bean
    fun listPaymentIntentsUseCase(repository: PaymentIntentRepository) =
        ListPaymentIntentsUseCaseImpl(repository)
}
