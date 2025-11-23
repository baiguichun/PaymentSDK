package com.xiaobai.paycore.channel

import android.content.Context
import com.xiaobai.paycore.PaymentResult
import java.math.BigDecimal

/**
 * 支付渠道接口
 * 
 * 所有具体的支付渠道SDK都需要实现此接口
 * 每个支付渠道可以作为独立的SDK模块，按需集成
 */
interface IPaymentChannel {
    
    /**
     * 渠道唯一标识
     * 例如：wechat_pay, alipay, union_pay
     */
    val channelId: String
    
    /**
     * 渠道显示名称
     * 例如：微信支付、支付宝、银联支付
     */
    val channelName: String
    
    /**
     * 渠道图标资源ID
     */
    val channelIcon: Int
    
    /**
     * 是否需要第三方APP
     * 如果为true，在选择此渠道前会验证对应APP是否已安装
     */
    val requiresApp: Boolean
    
    /**
     * 第三方APP的包名
     * 仅当requiresApp为true时需要提供
     */
    val packageName: String?
        get() = null
    
    /**
     * 渠道优先级（数字越大优先级越高，用于排序）
     */
    val priority: Int
        get() = 0
    
    /**
     * 执行支付
     * 
     * 对于第三方APP支付（微信/支付宝等）：
     * - 调起第三方APP后立即返回 Success
     * - SDK会自动通过后端查询获取实际支付结果
     * 
     * 对于需要网络请求的支付渠道：
     * - 可以在实现中使用 runBlocking 或在调用前切换到IO线程
     * 
     * @param context 上下文
     * @param orderId 订单ID
     * @param amount 支付金额
     * @param extraParams 额外参数（如商品信息、回调URL等）
     * @return 支付结果（通常返回 Success 表示成功调起支付）
     */
    fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any> = emptyMap()
    ): PaymentResult
    
    /**
     * 检查对应的APP是否已安装
     * 
     * @param context 上下文
     * @return true表示已安装，false表示未安装
     */
    fun isAppInstalled(context: Context): Boolean {
        if (!requiresApp || packageName == null) {
            return true
        }
        return AppInstallChecker.isPackageInstalled(context, packageName!!)
    }
    
    /**
     * 获取渠道支持的功能
     * 用于扩展性，可选实现
     */
    fun getSupportedFeatures(): List<PaymentFeature> {
        return listOf(PaymentFeature.BASIC_PAY)
    }
}

/**
 * 支付功能枚举
 */
enum class PaymentFeature {
    /** 基础支付 */
    BASIC_PAY,
    
    /** 退款 */
    REFUND,
    
    /** 订单查询 */
    QUERY_ORDER,
    
    /** 免密支付 */
    QUICK_PAY,
    
    /** 分期支付 */
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

