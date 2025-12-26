package com.xiaobai.paycore.channel

import java.util.Properties

/**
 * 渠道发现工具。
 *
 * 编译期处理器会生成渠道映射表，运行时通过此类加载映射并创建懒加载代理。
 */
object PaymentChannelServiceLoader {

    private const val CHANNEL_INDEX_PATH = "META-INF/paycore/payment-channels.properties"

    /**
     * 读取编译期生成的渠道映射 (channelId -> 实现类全名)。
     * 当 ServiceLoader 不可用时，可用作回退。
     */
    fun loadChannelMappings(classLoader: ClassLoader? = null): Map<String, String> {
        val loader = classLoader ?: defaultClassLoader()
        val resources = runCatching { loader.getResources(CHANNEL_INDEX_PATH) }
            .getOrElse { return emptyMap() }

        val mappings = linkedMapOf<String, String>()
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            url.openStream().use { input ->
                val properties = Properties()
                properties.load(input)
                properties.entries.forEach { entry ->
                    mappings.putIfAbsent(entry.key.toString(), entry.value.toString())
                }
            }
        }

        return mappings
    }

    /**
     * 基于映射文件创建懒加载代理，避免初始化阶段就实例化所有渠道。
     */
    fun createLazyChannels(
        classLoader: ClassLoader? = null
    ): List<IPaymentChannel> {
        val loader = classLoader ?: defaultClassLoader()
        val mappings = loadChannelMappings(loader)
        if (mappings.isEmpty()) return emptyList()
        return mappings.map { (channelId, className) ->
            LazyPaymentChannel(channelId, className, loader)
        }
    }

    /**
     * 当 ServiceLoader 不可用时，通过映射文件反射实例化渠道实现。
     * 渠道类需提供无参构造函数。
     */
    fun instantiateFromMappings(classLoader: ClassLoader? = null): List<IPaymentChannel> {
        val loader = classLoader ?: defaultClassLoader()
        val mappings = loadChannelMappings(loader)
        if (mappings.isEmpty()) return emptyList()

        return mappings.values.mapNotNull { className ->
            runCatching {
                val clazz = Class.forName(className, true, loader)
                val ctor = clazz.getDeclaredConstructor()
                ctor.isAccessible = true
                ctor.newInstance() as? IPaymentChannel
            }.getOrNull()
        }
    }

    private fun defaultClassLoader(): ClassLoader {
        return Thread.currentThread().contextClassLoader
            ?: PaymentChannelServiceLoader::class.java.classLoader
            ?: IPaymentChannel::class.java.classLoader
    }
}
