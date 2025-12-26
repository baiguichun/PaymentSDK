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
    // 渠道图标/优先级/是否依赖第三方APP 交由宿主或后端元数据处理，接口不再约束

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
    val requiresApp: Boolean = false,
    val priority: Int = 0,
    val iconUrl: String? = null,
    val extraConfig: Map<String, Any> = emptyMap()
)
