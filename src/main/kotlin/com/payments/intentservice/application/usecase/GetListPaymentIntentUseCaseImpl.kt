package com.payments.intentservice.application.usecase

import com.payments.intentservice.application.port.inbound.*
import com.payments.intentservice.application.port.outbound.PaymentIntentRepository
import com.payments.intentservice.domain.exception.PaymentIntentNotFoundException
import com.payments.intentservice.domain.model.PaymentIntent

class GetPaymentIntentUseCaseImpl(
    private val repository: PaymentIntentRepository
) : GetPaymentIntentUseCase {
    override fun execute(id: String): PaymentIntent =
        repository.findById(id) ?: throw PaymentIntentNotFoundException(id)
}

class ListPaymentIntentsUseCaseImpl(
    private val repository: PaymentIntentRepository
) : ListPaymentIntentsUseCase {
    override fun execute(query: ListPaymentIntentsQuery): Page<PaymentIntent> =
        repository.findAll(query)
}
