package com.xiaobai.paycore.network

import com.xiaobai.paycore.channel.PaymentChannelMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 支付API服务
 * 
 * 负责与后端通信，获取支付渠道配置等信息
 */
class PaymentApiService(private val baseUrl: String) {
    
    /**
     * 获取指定业务线的支付渠道配置
     * 
     * @param businessLine 业务线标识
     * @param appId 应用ID
     * @return 支付渠道元数据列表
     */
    suspend fun getPaymentChannels(
        businessLine: String,
        appId: String
    ): Result<List<PaymentChannelMeta>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/payment/channels?businessLine=$businessLine&appId=$appId")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                
                val channels = parseChannelsResponse(response)
                Result.success(channels)
            } else {
                Result.failure(Exception("HTTP Error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 解析渠道配置响应
     */
    private fun parseChannelsResponse(jsonString: String): List<PaymentChannelMeta> {
        val channels = mutableListOf<PaymentChannelMeta>()
        val jsonObject = JSONObject(jsonString)
        val dataArray = jsonObject.getJSONArray("data")
        
        for (i in 0 until dataArray.length()) {
            val channelJson = dataArray.getJSONObject(i)
            
            val extraConfig = mutableMapOf<String, Any>()
            if (channelJson.has("extraConfig")) {
                val extraConfigJson = channelJson.getJSONObject("extraConfig")
                extraConfigJson.keys().forEach { key ->
                    extraConfig[key] = extraConfigJson.get(key)
                }
            }
            
            val channel = PaymentChannelMeta(
                channelId = channelJson.getString("channelId"),
                channelName = channelJson.getString("channelName"),
                enabled = channelJson.getBoolean("enabled"),
                iconUrl = channelJson.optString("iconUrl", null),
                extraConfig = extraConfig
            )
            
            if (channel.enabled) {
                channels.add(channel)
            }
        }
        
        return channels
    }
    
    /**
     * 创建支付订单
     * 
     * @param orderId 订单ID
     * @param channelId 支付渠道ID
     * @param amount 金额
     * @param extraParams 额外参数
     * @return 支付参数（用于调起第三方支付）
     */
    suspend fun createPaymentOrder(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any> = emptyMap()
    ): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/api/payment/create")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // 构建请求体
            val requestBody = JSONObject().apply {
                put("orderId", orderId)
                put("channelId", channelId)
                put("amount", amount)
                
                if (extraParams.isNotEmpty()) {
                    put("extraParams", JSONObject(extraParams))
                }
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                
                val paymentParams = parseCreateOrderResponse(response)
                Result.success(paymentParams)
            } else {
                Result.failure(Exception("HTTP Error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseCreateOrderResponse(jsonString: String): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        val jsonObject = JSONObject(jsonString)
        val dataObject = jsonObject.getJSONObject("data")
        
        dataObject.keys().forEach { key ->
            params[key] = dataObject.get(key)
        }
        
        return params
    }
    
    /**
     * 查询订单支付状态
     * 
     * @param orderId 订单ID
     * @param paymentId 支付订单ID（可选）
     * @return 订单状态信息
     */
    suspend fun queryOrderStatus(
        orderId: String,
        paymentId: String? = null
    ): Result<OrderStatusInfo> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$baseUrl/api/payment/order/status?orderId=$orderId")
            if (paymentId != null) {
                urlBuilder.append("&paymentId=$paymentId")
            }
            
            val url = URL(urlBuilder.toString())
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                
                val orderStatus = parseOrderStatusResponse(response)
                Result.success(orderStatus)
            } else {
                Result.failure(Exception("HTTP Error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseOrderStatusResponse(jsonString: String): OrderStatusInfo {
        try {
            val jsonObject = JSONObject(jsonString)
            val dataObject = jsonObject.getJSONObject("data")
            
            return OrderStatusInfo(
                orderId = dataObject.getString("orderId"),
                paymentId = dataObject.optString("paymentId", null),
                channelId = dataObject.optString("channelId", null),
                channelName = dataObject.optString("channelName", null),
                amount = dataObject.optString("amount", null),
                status = dataObject.getString("status"),
                transactionId = dataObject.optString("transactionId", null),
                paidTime = dataObject.optLong("paidTime", 0),
                createTime = dataObject.optLong("createTime", 0)
            )
        } catch (e: Exception) {
            throw Exception("解析订单状态响应失败: ${e.message}")
        }
    }
}

/**
 * 订单状态信息
 */
data class OrderStatusInfo(
    val orderId: String,
    val paymentId: String?,
    val channelId: String?,
    val channelName: String?,
    val amount: String?,
    val status: String, // pending-待支付, paid-已支付, cancelled-已取消, failed-支付失败
    val transactionId: String?,
    val paidTime: Long,
    val createTime: Long
)
