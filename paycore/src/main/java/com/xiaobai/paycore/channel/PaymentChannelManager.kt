package com.xiaobai.paycore.channel

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * 支付渠道管理器
 * 
 * 负责：
 * 1. 支付渠道的注册和管理
 * 2. 渠道查询和验证
 * 3. 渠道过滤（根据APP安装状态等）
 */
class PaymentChannelManager {
    
    // 使用ConcurrentHashMap保证线程安全
    private val channels = ConcurrentHashMap<String, IPaymentChannel>()
    
    /**
     * 注册支付渠道
     * 
     * @param channel 支付渠道实例
     * @throws IllegalArgumentException 如果channelId已存在
     */
    fun registerChannel(channel: IPaymentChannel) {
        if (channels.containsKey(channel.channelId)) {
            throw IllegalArgumentException(
                "Payment channel '${channel.channelId}' already registered"
            )
        }
        channels[channel.channelId] = channel
    }
    
    /**
     * 批量注册支付渠道
     */
    fun registerChannels(channelList: List<IPaymentChannel>) {
        channelList.forEach { registerChannel(it) }
    }
    
    /**
     * 注销支付渠道
     * 
     * @param channelId 渠道ID
     * @return 被注销的渠道实例，如果不存在则返回null
     */
    fun unregisterChannel(channelId: String): IPaymentChannel? {
        return channels.remove(channelId)
    }
    
    /**
     * 获取指定的支付渠道
     * 
     * @param channelId 渠道ID
     * @return 渠道实例，如果不存在则返回null
     */
    fun getChannel(channelId: String): IPaymentChannel? {
        return channels[channelId]
    }
    
    /**
     * 获取所有已注册的支付渠道
     * 
     * @return 按优先级排序的渠道列表
     */
    fun getAllChannels(): List<IPaymentChannel> {
        return channels.values
            .sortedByDescending { it.priority }
    }
    
    /**
     * 获取可用的支付渠道
     * （已注册且APP已安装的渠道）
     * 
     * @param context 上下文
     * @return 按优先级排序的可用渠道列表
     */
    fun getAvailableChannels(context: Context): List<IPaymentChannel> {
        return channels.values
            .filter { channel ->
                // 如果不需要APP，或者需要APP但已安装，则认为可用
                !channel.requiresApp || channel.isAppInstalled(context)
            }
            .sortedByDescending { it.priority }
    }
    
    /**
     * 根据渠道ID列表过滤出已注册的渠道
     * 用于后端返回可用渠道列表后，过滤出本地已集成的渠道
     * 
     * @param channelIds 渠道ID列表
     * @return 已注册的渠道列表
     */
    fun filterRegisteredChannels(channelIds: List<String>): List<IPaymentChannel> {
        return channelIds
            .mapNotNull { channels[it] }
            .sortedByDescending { it.priority }
    }
    
    /**
     * 根据渠道ID列表和APP安装状态过滤可用渠道
     * 
     * @param context 上下文
     * @param channelIds 渠道ID列表（通常来自后端配置）
     * @return 已注册且可用的渠道列表
     */
    fun filterAvailableChannels(
        context: Context, 
        channelIds: List<String>
    ): List<IPaymentChannel> {
        return channelIds
            .mapNotNull { channels[it] }
            .filter { !it.requiresApp || it.isAppInstalled(context) }
            .sortedByDescending { it.priority }
    }
    
    /**
     * 检查渠道是否已注册
     */
    fun isChannelRegistered(channelId: String): Boolean {
        return channels.containsKey(channelId)
    }
    
    /**
     * 获取已注册渠道数量
     */
    fun getChannelCount(): Int {
        return channels.size
    }
    
    /**
     * 清空所有已注册的渠道
     * 通常仅用于测试
     */
    fun clear() {
        channels.clear()
    }
}

