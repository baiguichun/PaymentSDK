package com.xiaobai.paycore.channel

/**
 * 懒加载的渠道代理：通过工厂闭包 + `by lazy`，仅在第一次访问除 channelId 以外的信息时才实例化真实渠道。
 */
class LazyPaymentChannel(
    override val channelId: String,
    private val factory: () -> IPaymentChannel
) : IPaymentChannel {

    private val delegate: IPaymentChannel by lazy { instantiateDelegate() }

    // UI 文案/图标依赖后端渠道元数据，这里使用占位避免提前实例化真实渠道
    override val channelName: String
        get() = channelId
    
    override fun isAppInstalled(context: android.content.Context): Boolean =
        delegate.isAppInstalled(context)

    override fun pay(
        context: android.content.Context,
        orderId: String,
        amount: java.math.BigDecimal,
        extraParams: Map<String, Any>
    ): com.xiaobai.paycore.PaymentResult = delegate.pay(context, orderId, amount, extraParams)

    override fun getSupportedFeatures(): List<PaymentFeature> =
        listOf(PaymentFeature.BASIC_PAY)

    private fun instantiateDelegate(): IPaymentChannel {
        // 工厂闭包由 KSP 生成的注册表提供，首次访问时才真正 new 出渠道实例
        return factory.invoke()
    }
}
