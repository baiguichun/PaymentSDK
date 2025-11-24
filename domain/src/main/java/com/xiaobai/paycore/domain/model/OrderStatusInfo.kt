package com.xiaobai.paycore.domain.model

/**
 * 订单状态信息
 */
data class OrderStatusInfo(
    val orderId: String,
    val paymentId: String?,
    val channelId: String?,
    val channelName: String?,
    val amount: String?,
    val status: String, // pending-待支付, paid-已支付, cancelled-已取消, failed-支付失败
    val transactionId: String?,
    val paidTime: Long,
    val createTime: Long
)
