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
    
    fun getChannel(channelId: String): IPaymentChannel? = channels[channelId]
    
    fun getAllChannels(): List<IPaymentChannel> = channels.values.toList()
    
    fun getAvailableChannels(context: Context): List<IPaymentChannel> =
        channels.values.toList()
    
    fun filterAvailableChannels(
        context: Context,
        channelIds: List<String>
    ): List<IPaymentChannel> =
        channelIds
            .mapNotNull { channels[it] }
            .toList()
    
    fun isChannelRegistered(channelId: String): Boolean = channels.containsKey(channelId)
    
    fun clear() {
        channels.clear()
    }
}
