package com.xiaobai.paycore.network

import com.xiaobai.paycore.channel.PaymentChannelMeta
import com.xiaobai.paycore.config.SecurityConfig
import com.xiaobai.paycore.domain.model.OrderStatusInfo
import com.xiaobai.paycore.security.SecuritySigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Query
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 支付API服务（Retrofit + 手动JSON解析）
 */
class PaymentApiService(
    baseUrl: String,
    timeoutMs: Long,
    securityConfig: SecurityConfig = SecurityConfig()
) {

    private val api: PaymentApi
    private val signer = SecuritySigner(securityConfig)
    private val securityCfg = securityConfig

    init {
        val safeTimeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().toLong()
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(safeTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(safeTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(safeTimeout, TimeUnit.MILLISECONDS)
        
        if (securityCfg.enableCertificatePinning && securityCfg.certificatePins.isNotEmpty()) {
            val pinnerBuilder = CertificatePinner.Builder()
            securityCfg.certificatePins.forEach { (host, pins) ->
                pins.forEach { pinnerBuilder.add(host, it) }
            }
            clientBuilder.certificatePinner(pinnerBuilder.build())
        }

        val client = clientBuilder.build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(PaymentApi::class.java)
    }

    suspend fun getPaymentChannels(
        businessLine: String,
        appId: String
    ): Result<List<PaymentChannelMeta>> = withContext(Dispatchers.IO) {
        try {
            val queryMap = mapOf(
                "businessLine" to businessLine.urlEncoded(),
                "appId" to appId.urlEncoded()
            )
            val headers = signer.buildRequestHeaders(PAYMENT_CHANNELS_PATH, queryMap)
            val response = api.getPaymentChannels(
                headers = headers,
                businessLine = businessLine.urlEncoded(),
                appId = appId.urlEncoded()
            )
            handleResponse(response, PAYMENT_CHANNELS_PATH, queryMap) { body ->
                parseChannelsResponse(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPaymentOrder(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any> = emptyMap()
    ): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val requestBodyString = JSONObject().apply {
                put("orderId", orderId)
                put("channelId", channelId)
                put("amount", amount)
                if (extraParams.isNotEmpty()) {
                    put("extraParams", JSONObject(extraParams))
                }
            }.toString()
            val headers = signer.buildRequestHeaders(
                path = PAYMENT_CREATE_PATH,
                body = requestBodyString
            )

            val response = api.createPaymentOrder(
                headers = headers,
                body = requestBodyString.toRequestBody(JSON_MEDIA_TYPE)
            )
            handleResponse(response, PAYMENT_CREATE_PATH, emptyMap()) { body ->
                parseCreateOrderResponse(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun queryOrderStatus(
        orderId: String,
        paymentId: String? = null
    ): Result<OrderStatusInfo> = withContext(Dispatchers.IO) {
        try {
            val queryMap = mapOf(
                "orderId" to orderId.urlEncoded(),
                "paymentId" to paymentId?.urlEncoded()
            )
            val headers = signer.buildRequestHeaders(PAYMENT_STATUS_PATH, queryMap)
            val response = api.queryOrderStatus(
                headers = headers,
                orderId = orderId.urlEncoded(),
                paymentId = paymentId?.urlEncoded()
            )
            handleResponse(response, PAYMENT_STATUS_PATH, queryMap) { body ->
                parseOrderStatusResponse(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    private fun <R> handleResponse(
        response: Response<String>,
        path: String,
        query: Map<String, String?>,
        parser: (String) -> R
    ): Result<R> {
        if (!response.isSuccessful) {
            return Result.failure(Exception("HTTP Error: ${response.code()}"))
        }
        val body = response.body()
        return if (body != null) {
            val verifyResult = signer.verifyResponseSignature(
                responseSignature = response.headers()[securityCfg.serverSignatureHeader],
                responseTimestamp = response.headers()[securityCfg.serverTimestampHeader],
                path = path,
                query = query,
                body = body
            )
            if (verifyResult.isFailure) {
                return Result.failure(verifyResult.exceptionOrNull()!!)
            }
            try {
                Result.success(parser(body))
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(Exception("Empty response body"))
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val PAYMENT_CHANNELS_PATH = "api/payment/channels"
        private const val PAYMENT_CREATE_PATH = "api/payment/create"
        private const val PAYMENT_STATUS_PATH = "api/payment/order/status"
    }
}

private interface PaymentApi {
    @GET("api/payment/channels")
    suspend fun getPaymentChannels(
        @HeaderMap headers: Map<String, String>,
        @Query("businessLine") businessLine: String,
        @Query("appId") appId: String
    ): Response<String>

    @POST("api/payment/create")
    suspend fun createPaymentOrder(
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Response<String>

    @GET("api/payment/order/status")
    suspend fun queryOrderStatus(
        @HeaderMap headers: Map<String, String>,
        @Query("orderId") orderId: String,
        @Query("paymentId") paymentId: String?
    ): Response<String>
}

private fun String.ensureTrailingSlash(): String =
    if (this.endsWith("/")) this else "$this/"

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
