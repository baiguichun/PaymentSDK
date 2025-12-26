package com.xiaobai.paycore.channel

/**
 * 渠道发现工具。
 *
 * 编译期处理器会生成静态注册表对象，运行时直接加载工厂并创建懒加载代理。
 */
object PaymentChannelServiceLoader {

    private const val REGISTRY_CLASS =
        "com.xiaobai.paycore.channel.generated.GeneratedPaymentChannelRegistry"

    /**
     * 基于静态注册表的工厂生成懒加载渠道，避免一次性实例化。
     */
    fun createLazyChannels(
        classLoader: ClassLoader? = null
    ): List<IPaymentChannel> {
        val loader = classLoader ?: defaultClassLoader()

        val factories = runCatching {
            val clazz = Class.forName(REGISTRY_CLASS, true, loader)
            val field = clazz.getDeclaredField("factories")
            @Suppress("UNCHECKED_CAST")
            field.get(null) as? List<PaymentChannelFactory>
        }.getOrNull() ?: emptyList()

        return factories.map { factory -> LazyPaymentChannel(factory.channelId, factory.create) }
    }

    private fun defaultClassLoader(): ClassLoader {
        return Thread.currentThread().contextClassLoader
            ?: PaymentChannelServiceLoader::class.java.classLoader
            ?: IPaymentChannel::class.java.classLoader
    }
}
