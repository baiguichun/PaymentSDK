package com.xiaobai.paycore.channel

import android.content.Context
import com.xiaobai.paycore.PaymentResult
import java.math.BigDecimal

/**
 * 支付渠道接口定义
 */
interface IPaymentChannel {
    /** 渠道唯一ID（与后端保持一致） */
    val channelId: String
    /** 渠道展示名称 */
    val channelName: String
    /** 渠道图标资源（可选，0 表示未提供） */
    val channelIcon: Int get() = 0
    /** 渠道优先级，数字越大优先级越高 */
    val priority: Int get() = 0
    /** 是否需要依赖第三方APP */
    val requiresApp: Boolean get() = false
    
    /**
     * 检查支付APP是否已安装
     */
    fun isAppInstalled(context: Context): Boolean = true
    
    /**
     * 调起支付
     *
     * @param context 上下文
     * @param orderId 订单ID
     * @param amount 支付金额
     * @param extraParams 渠道所需额外参数
     * @return 支付结果
     */
    fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any> = emptyMap()
    ): PaymentResult
    
    /**
     * 渠道支持的能力
     */
    fun getSupportedFeatures(): List<PaymentFeature> {
        return listOf(PaymentFeature.BASIC_PAY)
    }
}

enum class PaymentFeature {
    BASIC_PAY,
    REFUND,
    QUERY_ORDER,
    QUICK_PAY,
    INSTALLMENT
}

/**
 * 支付渠道元数据
 * 用于从后端接口返回的渠道配置
 */
data class PaymentChannelMeta(
    val channelId: String,
    val channelName: String,
    val enabled: Boolean,
    val iconUrl: String? = null,
    val extraConfig: Map<String, Any> = emptyMap()
)
