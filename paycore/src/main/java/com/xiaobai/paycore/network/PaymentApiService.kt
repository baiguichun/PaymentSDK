package com.xiaobai.paycore.network

import com.xiaobai.paycore.channel.PaymentChannelMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * 支付API服务（Retrofit 版本）
 *
 * 负责与后端通信，获取支付渠道配置等信息
 */
class PaymentApiService(
    private val baseUrl: String,
    timeoutMs: Long
) {

    private val jsonMediaType = "application/json".toMediaType()

    private val api: PaymentApi

    init {
        val safeTimeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().toLong()
        val client = OkHttpClient.Builder()
            .connectTimeout(safeTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(safeTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(safeTimeout, TimeUnit.MILLISECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(PaymentApi::class.java)
    }

    /**
     * 获取指定业务线的支付渠道配置
     */
    suspend fun getPaymentChannels(
        businessLine: String,
        appId: String
    ): Result<List<PaymentChannelMeta>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPaymentChannels(businessLine, appId)
            handleResponse(response) { body -> parseChannelsResponse(body) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 创建支付订单
     */
    suspend fun createPaymentOrder(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any> = emptyMap()
    ): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("orderId", orderId)
                put("channelId", channelId)
                put("amount", amount)
                if (extraParams.isNotEmpty()) {
                    put("extraParams", JSONObject(extraParams))
                }
            }.toString().toRequestBody(jsonMediaType)

            val response = api.createPaymentOrder(requestBody)
            handleResponse(response) { body -> parseCreateOrderResponse(body) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 查询订单支付状态
     */
    suspend fun queryOrderStatus(
        orderId: String,
        paymentId: String? = null
    ): Result<OrderStatusInfo> = withContext(Dispatchers.IO) {
        try {
            val response = api.queryOrderStatus(orderId, paymentId)
            handleResponse(response) { body -> parseOrderStatusResponse(body) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // region Parsing helpers
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

    private fun parseCreateOrderResponse(jsonString: String): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        val jsonObject = JSONObject(jsonString)
        val dataObject = jsonObject.getJSONObject("data")

        dataObject.keys().forEach { key ->
            params[key] = dataObject.get(key)
        }

        return params
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
    // endregion

    private fun <T> handleResponse(
        response: Response<String>,
        parser: (String) -> T
    ): Result<T> {
        if (!response.isSuccessful) {
            return Result.failure(Exception("HTTP Error: ${response.code()}"))
        }
        val body = response.body()
        return if (body != null) {
            try {
                Result.success(parser(body))
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("Empty response body"))
        }
    }
}

private interface PaymentApi {
    @GET("api/payment/channels")
    suspend fun getPaymentChannels(
        @Query("businessLine") businessLine: String,
        @Query("appId") appId: String
    ): Response<String>

    @POST("api/payment/create")
    suspend fun createPaymentOrder(
        @Body body: RequestBody
    ): Response<String>

    @GET("api/payment/order/status")
    suspend fun queryOrderStatus(
        @Query("orderId") orderId: String,
        @Query("paymentId") paymentId: String?
    ): Response<String>
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

private fun String.ensureTrailingSlash(): String =
    if (this.endsWith("/")) this else "$this/"
