package com.xiaobai.paycore.channel

/**
 * 渠道工厂，保存 channelId 与实例化闭包，避免在发现阶段就 new 渠道，实现真正的懒加载。
 */
data class PaymentChannelFactory(
    val channelId: String,
    val create: () -> IPaymentChannel
)
