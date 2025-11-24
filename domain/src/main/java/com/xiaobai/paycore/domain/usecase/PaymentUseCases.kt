package com.xiaobai.paycore.domain.usecase

import android.content.Context
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.domain.PaymentRepository
import com.xiaobai.paycore.domain.model.OrderStatusInfo

data class PaymentUseCases(
    val fetchChannels: FetchChannelsUseCase,
    val createOrder: CreateOrderUseCase,
    val queryStatus: QueryStatusUseCase
)

class FetchChannelsUseCase(
    private val repository: PaymentRepository
) {
    suspend operator fun invoke(
        businessLine: String,
        appId: String,
        context: Context
    ): Result<List<IPaymentChannel>> {
        val remoteResult = repository.fetchPaymentChannels(businessLine, appId)
        return remoteResult.mapCatching { metas ->
            val channelIds = metas.map { it.channelId }
            repository.filterAvailableChannels(context, channelIds)
        }
    }
}

class CreateOrderUseCase(private val repository: PaymentRepository) {
    suspend operator fun invoke(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any>
    ): Result<Map<String, Any>> {
        return repository.createPaymentOrder(orderId, channelId, amount, extraParams)
    }
}

class QueryStatusUseCase(private val repository: PaymentRepository) {
    suspend operator fun invoke(orderId: String, paymentId: String? = null): Result<OrderStatusInfo> {
        return repository.queryOrderStatus(orderId, paymentId)
    }
}
