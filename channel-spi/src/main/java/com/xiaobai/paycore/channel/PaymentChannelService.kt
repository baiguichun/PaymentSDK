package com.xiaobai.paycore.channel

/**
 * 标记支付渠道实现，用于编译期生成 ServiceLoader 元数据。
 *
 * 任何实现了 [IPaymentChannel] 且添加了该注解的类，都会在编译期被处理器收集，
 * 并写入 `META-INF/services/com.xiaobai.paycore.channel.IPaymentChannel` 以及
 * 渠道映射文件，供运行时自动发现和注册。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PaymentChannelService(val channelId: String)
