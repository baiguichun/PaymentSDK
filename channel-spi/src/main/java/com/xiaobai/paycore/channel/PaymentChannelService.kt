package com.xiaobai.paycore.channel

/**
 * 标记支付渠道实现，用于编译期生成静态注册表（channelId + 工厂闭包）。
 *
 * 任何实现了 [IPaymentChannel] 且添加了该注解的类，都会在编译期被处理器收集，
 * 写入 `GeneratedPaymentChannelRegistry` 的工厂列表，供运行时加载懒代理时使用。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PaymentChannelService(val channelId: String)
