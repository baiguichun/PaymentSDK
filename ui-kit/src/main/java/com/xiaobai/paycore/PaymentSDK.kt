package com.xiaobai.paycore

import android.app.Activity
import android.app.Application
import android.content.Context
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.concurrent.PaymentLockManager
import com.xiaobai.paycore.config.PaymentConfig
import com.xiaobai.paycore.data.PaymentErrorMapper
import com.xiaobai.paycore.di.paymentModule
import com.xiaobai.paycore.domain.PaymentRepository
import com.xiaobai.paycore.domain.usecase.PaymentUseCases
import com.xiaobai.paycore.ui.PaymentSheetDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

/**
 * 聚合支付SDK入口类
 */
object PaymentSDK {
    
    private lateinit var application: Application
    private lateinit var config: PaymentConfig
    private lateinit var koinApp: KoinApplication
    private lateinit var koin: Koin
    private var isInternalKoin: Boolean = true
    private val repository: PaymentRepository by lazy { koin.get() }
    private val useCases: PaymentUseCases by lazy { koin.get() }
    private val errorMapper: PaymentErrorMapper by lazy { koin.get() }
    
    private val activeQueries = ConcurrentHashMap<String, CompletableDeferred<PaymentResult>>()
    
    @Volatile
    private var isInitialized = false
    
    fun init(app: Application, config: PaymentConfig, externalKoinApp: KoinApplication? = null) {
        if (isInitialized) {
            if (config.debugMode) {
                println("PaymentSDK 已经初始化，跳过重复初始化")
            }
            return
        }
        
        this.application = app
        this.config = config
        // 使用外部 Koin 容器或创建独立容器，避免干扰宿主
        isInternalKoin = externalKoinApp == null
        koinApp = externalKoinApp ?: koinApplication {
            modules(paymentModule(config))
        }
        koin = koinApp.koin
        
        PaymentLockManager.setOnTimeoutCallback { orderId ->
            if (config.debugMode) {
                println("订单锁超时自动释放: $orderId (超时时间: ${config.orderLockTimeoutMs}ms)")
            }
        }
        
        isInitialized = true
        
        if (config.debugMode) {
            println("PaymentSDK initialized with config: $config")
        }
    }
    
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(
                PaymentErrorCode.SDK_NOT_INITIALIZED.message
            )
        }
    }
    
    fun registerChannel(channel: IPaymentChannel) {
        checkInitialized()
        repository.registerChannel(channel)
        if (config.debugMode) {
            println("Payment channel registered: ${channel.channelId}")
        }
    }
    
    fun registerChannels(channels: List<IPaymentChannel>) {
        channels.forEach { registerChannel(it) }
    }
    
    fun showPaymentSheet(
        activity: Activity,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any> = emptyMap(),
        businessLine: String? = null,
        onPaymentResult: (PaymentResult) -> Unit,
        onCancelled: () -> Unit
    ) {
        checkInitialized()

        validateOrderInput(orderId, amount)?.let {
            onPaymentResult(it)
            return
        }
        
        val dialog = PaymentSheetDialog(
            activity = activity,
            orderId = orderId,
            amount = amount,
            extraParams = extraParams,
            businessLine = businessLine ?: config.businessLine,
            onPaymentResult = onPaymentResult,
            onCancelled = onCancelled
        )
        
        dialog.show()
    }
    
    fun payWithChannel(
        channelId: String,
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any> = emptyMap(),
        onResult: (PaymentResult) -> Unit
    ) {
        checkInitialized()

        validateOrderInput(orderId, amount)?.let {
            onResult(it)
            return
        }

        if (channelId.isBlank()) {
            onResult(buildFailure(PaymentErrorCode.PARAMS_INVALID, "channelId 不能为空"))
            return
        }
        
        if (PaymentLockManager.isOrderPaying(orderId)) {
            if (config.debugMode) {
                println("订单 $orderId 正在支付中，拒绝重复支付")
            }
            onResult(buildFailure(PaymentErrorCode.ORDER_LOCKED))
            return
        }
        
        val channel = repository.getChannel(channelId)
        if (channel == null) {
            onResult(buildFailure(PaymentErrorCode.CHANNEL_NOT_FOUND, channelId))
            return
        }
        
        if (!PaymentLockManager.tryLockOrder(orderId, config.orderLockTimeoutMs)) {
            onResult(buildFailure(PaymentErrorCode.ORDER_LOCKED))
            return
        }
        
        com.xiaobai.paycore.ui.PaymentLifecycleActivity.start(
            context = context,
            orderId = orderId,
            channelId = channelId,
            amount = amount,
            extraParams = extraParams,
            onResult = { result ->
                PaymentLockManager.unlockOrder(orderId)
                onResult(result)
            }
        )
    }
    
    private suspend fun queryPaymentResultWithRetry(orderId: String): PaymentResult {
        val existingQuery = activeQueries[orderId]
        if (existingQuery != null) {
            if (config.debugMode) {
                println("订单 $orderId 正在查询中，等待现有查询完成...")
            }
            return try {
                existingQuery.await()
            } catch (e: Exception) {
                if (config.debugMode) {
                    println("等待现有查询失败: ${e.message}")
                }
                PaymentResult.Failed(
                    "${PaymentErrorCode.QUERY_FAILED.message}: ${e.message}",
                    PaymentErrorCode.QUERY_FAILED.code
                )
            }
        }
        
        val queryDeferred = CompletableDeferred<PaymentResult>()
        activeQueries[orderId] = queryDeferred
        
        if (config.debugMode) {
            println("开始新的查询任务，订单: $orderId")
        }
        
        val startTime = System.currentTimeMillis()
        var retryCount = 0
        
        try {
            var finalResult: PaymentResult? = null
            
            while (retryCount <= config.maxQueryRetries && finalResult == null) {
                if (System.currentTimeMillis() - startTime > config.queryTimeoutMs) {
                    if (config.debugMode) {
                        println("查询超时，返回处理中状态")
                    }
                    finalResult = PaymentResult.Processing(
                        PaymentErrorCode.QUERY_TIMEOUT.message,
                        PaymentErrorCode.QUERY_TIMEOUT.code
                    )
                    break
                }
                
                try {
                    if (config.debugMode) {
                        println("查询支付结果，第${retryCount + 1}次尝试...")
                    }
                    
                    val result = useCases.queryStatus(orderId)
                    
                    if (result.isSuccess) {
                        val orderStatus = result.getOrNull()
                        if (orderStatus != null) {
                            when (orderStatus.status) {
                                "paid" -> {
                                    if (config.debugMode) {
                                        println("支付成功: ${orderStatus.transactionId}")
                                    }
                                    finalResult = PaymentResult.Success(
                                        orderStatus.transactionId ?: orderId
                                    )
                                }
                                "failed" -> {
                                    if (config.debugMode) {
                                        println("支付失败")
                                    }
                                    finalResult = buildFailure(PaymentErrorCode.CHANNEL_ERROR)
                                }
                                "cancelled" -> {
                                    if (config.debugMode) {
                                        println("支付已取消")
                                    }
                                    finalResult = PaymentResult.Cancelled
                                }
                                "pending" -> {
                                    if (config.debugMode) {
                                        println("订单待支付，等待${config.queryIntervalMs}ms后重试...")
                                    }
                                }
                                else -> {
                                    if (config.debugMode) {
                                        println("未知状态: ${orderStatus.status}")
                                    }
                                }
                            }
                        }
                    } else {
                        val failure = mapExceptionToFailed(
                            result.exceptionOrNull(),
                            PaymentErrorCode.QUERY_FAILED
                        )
                        finalResult = failure
                        if (config.debugMode) {
                            println("查询失败: ${result.exceptionOrNull()?.message}")
                        }
                        break
                    }
                } catch (e: Exception) {
                    finalResult = mapExceptionToFailed(e, PaymentErrorCode.QUERY_EXCEPTION)
                    if (config.debugMode) {
                        println("查询异常: ${e.message}")
                    }
                    break
                }
                
                if (finalResult == null && retryCount < config.maxQueryRetries) {
                    delay(config.queryIntervalMs)
                }
                
                retryCount++
            }
            
            val result = finalResult ?: run {
                if (config.debugMode) {
                    println("达到最大重试次数，返回处理中状态")
                }
                PaymentResult.Processing(
                    PaymentErrorCode.QUERY_TIMEOUT.message,
                    PaymentErrorCode.QUERY_TIMEOUT.code
                )
            }
            
            queryDeferred.complete(result)
            
            return result
        } catch (e: Exception) {
            val errorResult = mapExceptionToFailed(e, PaymentErrorCode.QUERY_EXCEPTION)
            queryDeferred.complete(errorResult)
            return errorResult
        } finally {
            activeQueries.remove(orderId)
            if (config.debugMode) {
                println("查询任务完成，清理订单记录: $orderId")
            }
        }
    }
    
    suspend fun queryOrderStatus(orderId: String): PaymentResult {
        return try {
            if (config.debugMode) {
                println("手动查询订单状态: $orderId")
            }
            queryPaymentResultWithRetry(orderId)
        } catch (e: Exception) {
            if (config.debugMode) {
                println("查询订单状态异常: ${e.message}")
            }
            PaymentResult.Failed(
                "${PaymentErrorCode.QUERY_FAILED.message}: ${e.message}",
                PaymentErrorCode.QUERY_FAILED.code
            )
        }
    }
    
    fun getRegisteredChannels(): List<IPaymentChannel> {
        return repository.getAllChannels()
    }
    
    fun getAvailableChannels(context: Context): List<IPaymentChannel> {
        return repository.getAvailableChannels(context)
    }
    
    fun getConfig(): PaymentConfig = config
    
    internal fun getRepository(): PaymentRepository = repository
    internal fun getUseCases(): PaymentUseCases = useCases
    internal fun getErrorMapper(): PaymentErrorMapper = errorMapper
    
    fun isOrderPaying(orderId: String): Boolean {
        return PaymentLockManager.isOrderPaying(orderId)
    }
    
    fun cancelPayment(orderId: String): Boolean {
        return if (PaymentLockManager.isOrderPaying(orderId)) {
            PaymentLockManager.unlockOrder(orderId)
            true
        } else {
            false
        }
    }
    
    private fun validateOrderInput(
        orderId: String,
        amount: BigDecimal
    ): PaymentResult.Failed? {
        if (orderId.isBlank()) {
            return buildFailure(PaymentErrorCode.ORDER_ID_EMPTY)
        }
        if (amount <= BigDecimal.ZERO) {
            return buildFailure(PaymentErrorCode.ORDER_AMOUNT_INVALID)
        }
        return null
    }

    internal fun buildFailure(
        code: PaymentErrorCode,
        detail: String? = null
    ): PaymentResult.Failed {
        return errorMapper.buildFailure(code, detail)
    }

    internal fun mapExceptionToFailed(
        throwable: Throwable?,
        defaultCode: PaymentErrorCode
    ): PaymentResult.Failed {
        return errorMapper.mapExceptionToFailed(throwable, defaultCode)
    }

    private fun mapExceptionToErrorCode(
        throwable: Throwable?,
        defaultCode: PaymentErrorCode
    ): PaymentErrorCode {
        return errorMapper.mapExceptionToErrorCode(throwable, defaultCode)
    }
    
    fun getPaymentStatus(): String {
        val payingOrders = PaymentLockManager.getPayingOrders()
        val queryingOrders = activeQueries.keys.toList()
        
        return buildString {
            appendLine("=== 支付状态 ===")
            appendLine("正在支付订单数: ${payingOrders.size}")
            appendLine("正在支付订单: ${payingOrders.joinToString()}")
            appendLine()
            appendLine("=== 查询状态 ===")
            appendLine("正在查询订单数: ${queryingOrders.size}")
            appendLine("正在查询订单: ${queryingOrders.joinToString()}")
        }
    }
    
    fun shutdown() {
        PaymentLockManager.clearAll()
        activeQueries.values.forEach { it.cancel() }
        activeQueries.clear()

        if (::koinApp.isInitialized && isInternalKoin) {
            koinApp.close()
        }
        
        if (config.debugMode) {
            println("PaymentSDK 已关闭")
        }
    }
}
