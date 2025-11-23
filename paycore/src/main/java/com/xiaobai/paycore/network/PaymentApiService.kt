package com.xiaobai.paycore.network

import com.xiaobai.paycore.channel.PaymentChannelMeta
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * 支付API服务（Retrofit + Moshi 自动解析）
 */
class PaymentApiService(
    baseUrl: String,
    timeoutMs: Long
) {

    private val api: PaymentApi

    init {
    val safeTimeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().toLong()
    val client = OkHttpClient.Builder()
        .connectTimeout(safeTimeout, TimeUnit.MILLISECONDS)
        .readTimeout(safeTimeout, TimeUnit.MILLISECONDS)
        .writeTimeout(safeTimeout, TimeUnit.MILLISECONDS)
        .build()

    val moshi = Moshi.Builder()
        .add(AnyJsonAdapterFactory)
        .add(KotlinJsonAdapterFactory())
        .build()

        api = Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
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
            handleResponse(response) { body ->
                body.data
                    ?.filter { it.enabled }
                    ?.map { dto ->
                        PaymentChannelMeta(
                            channelId = dto.channelId,
                            channelName = dto.channelName,
                            enabled = dto.enabled,
                            iconUrl = dto.iconUrl,
                            extraConfig = dto.extraConfig ?: emptyMap()
                        )
                    }
                    ?: emptyList()
            }
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
            val req = CreateOrderRequest(
                orderId = orderId,
                channelId = channelId,
                amount = amount,
                extraParams = extraParams
            )
            val response = api.createPaymentOrder(req)
            handleResponse(response) { it.data ?: emptyMap() }
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
            handleResponse(response) { body ->
                body.data ?: throw Exception("解析订单状态响应失败: data 为空")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun <T, R> handleResponse(
        response: Response<T>,
        mapper: (T) -> R
    ): Result<R> {
        if (!response.isSuccessful) {
            return Result.failure(Exception("HTTP Error: ${response.code()}"))
        }
        val body = response.body()
        return if (body != null) {
            try {
                Result.success(mapper(body))
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
    ): Response<ChannelsResponse>

    @POST("api/payment/create")
    suspend fun createPaymentOrder(
        @Body body: CreateOrderRequest
    ): Response<CreateOrderResponse>

    @GET("api/payment/order/status")
    suspend fun queryOrderStatus(
        @Query("orderId") orderId: String,
        @Query("paymentId") paymentId: String?
    ): Response<OrderStatusResponse>
}

@JsonClass(generateAdapter = true)
private data class ChannelsResponse(
    val data: List<ChannelDto>?
)

@JsonClass(generateAdapter = true)
private data class ChannelDto(
    val channelId: String,
    val channelName: String,
    val enabled: Boolean,
    val iconUrl: String? = null,
    val extraConfig: Map<String, Any?>? = emptyMap()
)

@JsonClass(generateAdapter = true)
private data class CreateOrderRequest(
    val orderId: String,
    val channelId: String,
    val amount: String,
    val extraParams: Map<String, Any?>? = emptyMap()
)

@JsonClass(generateAdapter = true)
private data class CreateOrderResponse(
    val data: Map<String, Any>?
)

@JsonClass(generateAdapter = true)
private data class OrderStatusResponse(
    val data: OrderStatusInfo?
)

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

/**
 * 为 Map<String, Any?> / List<Any?> / Any? 提供通用适配器，支持动态字段解析。
 */
private object AnyJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: java.lang.reflect.Type,
        annotations: MutableSet<out Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        return if (type == Any::class.java) {
            AnyJsonAdapter(moshi).nullSafe()
        } else {
            null
        }
    }
}

private class AnyJsonAdapter(private val moshi: Moshi) : JsonAdapter<Any>() {
    override fun fromJson(reader: JsonReader): Any? {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_OBJECT -> {
                val map = mutableMapOf<String, Any?>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    map[name] = fromJson(reader)
                }
                reader.endObject()
                map
            }
            JsonReader.Token.BEGIN_ARRAY -> {
                val list = mutableListOf<Any?>()
                reader.beginArray()
                while (reader.hasNext()) {
                    list.add(fromJson(reader))
                }
                reader.endArray()
                list
            }
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> reader.nextDouble()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean()
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            else -> throw IllegalStateException("Unexpected token: ${reader.peek()}")
        }
    }

    override fun toJson(writer: JsonWriter, value: Any?) {
        when (value) {
            null -> writer.nullValue()
            is Map<*, *> -> {
                writer.beginObject()
                value.entries.forEach { entry ->
                    val key = entry.key
                    if (key is String) {
                        writer.name(key)
                        toJson(writer, entry.value)
                    }
                }
                writer.endObject()
            }
            is Iterable<*> -> {
                writer.beginArray()
                value.forEach { item -> toJson(writer, item) }
                writer.endArray()
            }
            is Number -> writer.value(value)
            is Boolean -> writer.value(value)
            is String -> writer.value(value)
            else -> writer.value(value.toString())
        }
    }
}
