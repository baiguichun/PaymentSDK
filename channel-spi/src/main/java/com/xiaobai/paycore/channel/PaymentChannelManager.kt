package com.xiaobai.paycore.channel

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * 支付渠道管理器
 */
class PaymentChannelManager {
    
    private val channels = ConcurrentHashMap<String, IPaymentChannel>()
    
    fun registerChannel(channel: IPaymentChannel) {
        if (channels.containsKey(channel.channelId)) {
            throw IllegalArgumentException(
                "Payment channel '${channel.channelId}' already registered"
            )
        }
        channels[channel.channelId] = channel
    }
    
    fun registerChannels(channelList: List<IPaymentChannel>) {
        channelList.forEach { registerChannel(it) }
    }
    
    fun unregisterChannel(channelId: String): IPaymentChannel? {
        return channels.remove(channelId)
    }
    
    fun getChannel(channelId: String): IPaymentChannel? = channels[channelId]
    
    fun getAllChannels(): List<IPaymentChannel> =
        channels.values.sortedByDescending { it.priority }
    
    fun getAvailableChannels(context: Context): List<IPaymentChannel> =
        channels.values
            .filter { !it.requiresApp || it.isAppInstalled(context) }
            .sortedByDescending { it.priority }
    
    fun filterRegisteredChannels(channelIds: List<String>): List<IPaymentChannel> =
        channelIds.mapNotNull { channels[it] }.sortedByDescending { it.priority }
    
    fun filterAvailableChannels(
        context: Context,
        channelIds: List<String>
    ): List<IPaymentChannel> =
        channelIds
            .mapNotNull { channels[it] }
            .filter { !it.requiresApp || it.isAppInstalled(context) }
            .sortedByDescending { it.priority }
    
    fun isChannelRegistered(channelId: String): Boolean = channels.containsKey(channelId)
    
    fun getChannelCount(): Int = channels.size
    
    fun clear() {
        channels.clear()
    }
}
