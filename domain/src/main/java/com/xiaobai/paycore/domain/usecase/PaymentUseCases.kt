package com.xiaobai.paycore.domain.usecase

import android.content.Context
import com.xiaobai.paycore.channel.PaymentChannelMeta
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
    ): Result<List<PaymentChannelMeta>> {
        val remoteResult = repository.fetchPaymentChannels(businessLine, appId)
        return remoteResult.mapCatching { metas ->
            // 仅返回本地已注册映射的渠道，避免缺失实现
            metas.filter { repository.getChannel(it.channelId) != null }
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
