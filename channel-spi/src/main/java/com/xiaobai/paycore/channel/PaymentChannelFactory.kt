package com.xiaobai.paycore.channel

/**
 * 渠道工厂，避免在发现阶段就实例化渠道，实现真正的懒加载。
 */
data class PaymentChannelFactory(
    val channelId: String,
    val create: () -> IPaymentChannel
)
