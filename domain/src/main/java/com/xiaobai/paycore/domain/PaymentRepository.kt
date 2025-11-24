package com.xiaobai.paycore.domain

import android.content.Context
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.channel.PaymentChannelMeta
import com.xiaobai.paycore.domain.model.OrderStatusInfo

interface PaymentRepository {
    suspend fun fetchPaymentChannels(businessLine: String, appId: String): Result<List<PaymentChannelMeta>>

    suspend fun createPaymentOrder(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any> = emptyMap()
    ): Result<Map<String, Any>>

    suspend fun queryOrderStatus(
        orderId: String,
        paymentId: String? = null
    ): Result<OrderStatusInfo>

    fun registerChannel(channel: IPaymentChannel)

    fun getChannel(channelId: String): IPaymentChannel?

    fun getAllChannels(): List<IPaymentChannel>

    fun getAvailableChannels(context: Context): List<IPaymentChannel>

    fun filterAvailableChannels(context: Context, channelIds: List<String>): List<IPaymentChannel>
}
