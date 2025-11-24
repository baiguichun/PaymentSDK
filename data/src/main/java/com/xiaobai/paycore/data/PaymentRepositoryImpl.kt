package com.xiaobai.paycore.data

import android.content.Context
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.channel.PaymentChannelManager
import com.xiaobai.paycore.channel.PaymentChannelMeta
import com.xiaobai.paycore.domain.PaymentRepository
import com.xiaobai.paycore.domain.model.OrderStatusInfo
import com.xiaobai.paycore.network.PaymentApiService

class PaymentRepositoryImpl(
    private val apiService: PaymentApiService,
    private val channelManager: PaymentChannelManager
) : PaymentRepository {

    override suspend fun fetchPaymentChannels(
        businessLine: String,
        appId: String
    ): Result<List<PaymentChannelMeta>> {
        return apiService.getPaymentChannels(businessLine, appId)
    }

    override suspend fun createPaymentOrder(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any>
    ): Result<Map<String, Any>> {
        return apiService.createPaymentOrder(orderId, channelId, amount, extraParams)
    }

    override suspend fun queryOrderStatus(
        orderId: String,
        paymentId: String?
    ): Result<OrderStatusInfo> {
        return apiService.queryOrderStatus(orderId, paymentId)
    }

    override fun registerChannel(channel: IPaymentChannel) {
        channelManager.registerChannel(channel)
    }

    override fun getChannel(channelId: String): IPaymentChannel? {
        return channelManager.getChannel(channelId)
    }

    override fun getAllChannels(): List<IPaymentChannel> {
        return channelManager.getAllChannels()
    }

    override fun getAvailableChannels(context: Context): List<IPaymentChannel> {
        return channelManager.getAvailableChannels(context)
    }

    override fun filterAvailableChannels(
        context: Context,
        channelIds: List<String>
    ): List<IPaymentChannel> {
        return channelManager.filterAvailableChannels(context, channelIds)
    }
}
