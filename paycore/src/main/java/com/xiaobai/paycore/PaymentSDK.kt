package com.xiaobai.paycore

import android.app.Activity
import android.app.Application
import android.content.Context
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.channel.PaymentChannelManager
import com.xiaobai.paycore.concurrent.PaymentLockManager
import com.xiaobai.paycore.config.PaymentConfig
import com.xiaobai.paycore.network.PaymentApiService
import com.xiaobai.paycore.ui.PaymentSheetDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * 聚合支付SDK入口类
 * 
 * 功能：
 * 1. SDK初始化和配置管理
 * 2. 支付渠道注册和管理
 * 3. 发起支付流程
 * 4. 全局生命周期管理
 */
object PaymentSDK {
    
    private lateinit var application: Application
    private lateinit var config: PaymentConfig
    private lateinit var apiService: PaymentApiService
    private val channelManager = PaymentChannelManager()
    
    // 查询去重：存储正在查询的订单，避免重复查询
    private val activeQueries = ConcurrentHashMap<String, CompletableDeferred<PaymentResult>>()
    
    // 初始化状态标记
    @Volatile
    private var isInitialized = false
    
    /**
     * 初始化SDK
     * 
     * @param app 应用Application实例
     * @param config SDK配置
     */
    fun init(app: Application, config: PaymentConfig) {
        if (isInitialized) {
            if (config.debugMode) {
                println("PaymentSDK 已经初始化，跳过重复初始化")
            }
            return
        }
        
        this.application = app
        this.config = config
        this.apiService = PaymentApiService(config.apiBaseUrl)
        
        // 设置订单锁超时回调（用于日志记录）
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
    
    /**
     * 检查SDK是否已初始化
     */
    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException(
                "PaymentSDK 未初始化！请先在 Application.onCreate() 中调用 PaymentSDK.init()"
            )
        }
    }
    
    /**
     * 注册支付渠道
     * 
     * 在Application的onCreate中调用，注册所有已集成的支付渠道SDK
     * 
     * @param channel 支付渠道实例
     */
    fun registerChannel(channel: IPaymentChannel) {
        checkInitialized()
        
        channelManager.registerChannel(channel)
        if (config.debugMode) {
            println("Payment channel registered: ${channel.channelId}")
        }
    }
    
    /**
     * 批量注册支付渠道
     */
    fun registerChannels(channels: List<IPaymentChannel>) {
        channels.forEach { registerChannel(it) }
    }
    
    /**
     * 显示支付渠道选择半屏弹窗
     * 
     * 会自动从后端获取当前业务线可用的支付渠道，过滤出本地已注册的渠道展示
     * 用户选择渠道后，SDK会自动调用支付接口完成支付流程
     * 
     * @param activity Activity实例（支持任何 Activity，包括普通 Activity）
     * @param orderId 订单ID
     * @param amount 支付金额
     * @param extraParams 额外参数（可选）
     * @param businessLine 业务线标识（可选，默认使用配置中的业务线）
     * @param onPaymentResult 支付结果回调
     * @param onCancelled 取消选择回调
     */
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
    
    /**
     * 直接使用指定渠道发起支付（不显示选择弹窗） - 回调版本
     * 
     * 使用透明Activity监听生命周期，适用于所有场景（有UI/无UI）
     * 
     * 集成了以下保护机制：
     * 1. 防止同一订单重复支付
     * 2. 生命周期感知（监听用户从第三方APP返回）
     * 3. 自动查询支付结果
     * 
     * @param channelId 支付渠道ID
     * @param context Context实例
     * @param orderId 订单ID
     * @param amount 支付金额
     * @param extraParams 额外参数
     * @param onResult 支付结果回调
     */
    fun payWithChannel(
        channelId: String,
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any> = emptyMap(),
        onResult: (PaymentResult) -> Unit
    ) {
        checkInitialized()
        
        // 检查订单是否正在支付中
        if (PaymentLockManager.isOrderPaying(orderId)) {
            if (config.debugMode) {
                println("订单 $orderId 正在支付中，拒绝重复支付")
            }
            onResult(PaymentResult.Failed("订单正在支付中，请勿重复操作"))
            return
        }
        
        // 验证渠道是否存在
        val channel = channelManager.getChannel(channelId)
        if (channel == null) {
            onResult(PaymentResult.Failed("支付渠道不存在: $channelId"))
            return
        }
        
        // 锁定订单（使用配置的超时时间）
        if (!PaymentLockManager.tryLockOrder(orderId, config.orderLockTimeoutMs)) {
            onResult(PaymentResult.Failed("订单正在支付中，请勿重复操作"))
            return
        }
        
        // 启动透明Activity监听支付生命周期
        com.xiaobai.paycore.ui.PaymentLifecycleActivity.start(
            context = context,
            orderId = orderId,
            channelId = channelId,
            amount = amount,
            extraParams = extraParams,
            onResult = { result ->
                // 解锁订单
                PaymentLockManager.unlockOrder(orderId)
                // 返回结果
                onResult(result)
            }
        )
    }
    
    /**
     * 带重试的查询支付结果
     * 
     * 去重机制：如果同一订单正在查询中，则等待现有查询完成，避免重复请求
     * 
     * @param orderId 订单ID
     * @return 最终支付结果
     */
    private suspend fun queryPaymentResultWithRetry(orderId: String): PaymentResult {
        // 检查是否已有正在进行的查询
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
                PaymentResult.Failed("查询失败: ${e.message}")
            }
        }
        
        // 创建新的查询任务
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
                // 检查是否超时
                if (System.currentTimeMillis() - startTime > config.queryTimeoutMs) {
                    if (config.debugMode) {
                        println("查询超时，返回处理中状态")
                    }
                    finalResult = PaymentResult.Processing("支付处理中，请稍后查询订单状态")
                    break
                }
                
                try {
                    if (config.debugMode) {
                        println("查询支付结果，第${retryCount + 1}次尝试...")
                    }
                    
                    val result = apiService.queryOrderStatus(orderId)
                    
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
                                    finalResult = PaymentResult.Failed("支付失败")
                                }
                                "cancelled" -> {
                                    if (config.debugMode) {
                                        println("支付已取消")
                                    }
                                    finalResult = PaymentResult.Cancelled
                                }
                                "pending" -> {
                                    // 待支付状态，继续重试
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
                        if (config.debugMode) {
                            println("查询失败: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    if (config.debugMode) {
                        println("查询异常: ${e.message}")
                    }
                }
                
                // 如果不是最后一次重试，等待后继续
                if (finalResult == null && retryCount < config.maxQueryRetries) {
                    delay(config.queryIntervalMs)
                }
                
                retryCount++
            }
            
            // 达到最大重试次数仍未得到明确结果
            val result = finalResult ?: run {
                if (config.debugMode) {
                    println("达到最大重试次数，返回处理中状态")
                }
                PaymentResult.Processing("支付处理中，请稍后查询订单状态")
            }
            
            // 通知所有等待的协程
            queryDeferred.complete(result)
            
            return result
        } catch (e: Exception) {
            // 查询过程中发生异常
            val errorResult = PaymentResult.Failed("查询异常: ${e.message}")
            queryDeferred.completeExceptionally(e)
            return errorResult
        } finally {
            // 清理查询记录
            activeQueries.remove(orderId)
            if (config.debugMode) {
                println("查询任务完成，清理订单记录: $orderId")
            }
        }
    }
    
    /**
     * 手动查询订单支付状态
     * 
     * 适用场景：
     * 1. 用户从第三方APP返回后，手动刷新订单状态
     * 2. 支付返回Processing状态后，用户主动查询
     * 3. 订单列表中查询订单状态
     * 
     * @param orderId 订单ID
     * @return 支付结果
     */
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
            PaymentResult.Failed("查询订单状态失败: ${e.message}")
        }
    }
    
    /**
     * 获取所有已注册的支付渠道
     */
    fun getRegisteredChannels(): List<IPaymentChannel> {
        return channelManager.getAllChannels()
    }
    
    /**
     * 获取可用的支付渠道（已注册且APP已安装）
     */
    fun getAvailableChannels(context: Context): List<IPaymentChannel> {
        return channelManager.getAvailableChannels(context)
    }
    
    /**
     * 获取配置信息
     */
    fun getConfig(): PaymentConfig = config
    
    /**
     * 获取API服务实例
     */
    internal fun getApiService(): PaymentApiService = apiService
    
    /**
     * 获取渠道管理器实例
     */
    internal fun getChannelManager(): PaymentChannelManager = channelManager
    
    /**
     * 检查订单是否正在支付中
     * 
     * @param orderId 订单ID
     * @return true表示正在支付，false表示未在支付
     */
    fun isOrderPaying(orderId: String): Boolean {
        return PaymentLockManager.isOrderPaying(orderId)
    }
    
    /**
     * 取消指定订单的支付
     * 
     * @param orderId 订单ID
     * @return true表示取消成功，false表示订单未在支付中
     */
    fun cancelPayment(orderId: String): Boolean {
        return if (PaymentLockManager.isOrderPaying(orderId)) {
            PaymentLockManager.unlockOrder(orderId)
            true
        } else {
            false
        }
    }
    
    /**
     * 获取支付状态（调试用）
     */
    fun getPaymentStatus(): String {
        val payingOrders = PaymentLockManager.getPayingOrders()
        val queryingOrders = activeQueries.keys()
        
        return buildString {
            appendLine("=== 支付状态 ===")
            appendLine("正在支付订单数: ${payingOrders.size}")
            appendLine("正在支付订单: ${payingOrders.joinToString()}")
            appendLine()
            appendLine("=== 查询状态 ===")
            appendLine("正在查询订单数: ${queryingOrders.size}")
            appendLine("正在查询订单: ${queryingOrders.toList().joinToString()}")
        }
    }
    
    /**
     * 关闭SDK，释放资源
     * 建议在Application的onTerminate中调用
     */
    fun shutdown() {
        // 清理所有锁
        PaymentLockManager.clearAll()
        
        // 取消所有正在进行的查询
        activeQueries.values.forEach { it.cancel() }
        activeQueries.clear()
        
        if (config.debugMode) {
            println("PaymentSDK 已关闭")
        }
    }
}

/**
 * 支付结果封装类
 */
sealed class PaymentResult {
    /**
     * 支付成功
     * @param transactionId 交易流水号
     */
    data class Success(val transactionId: String) : PaymentResult()
    
    /**
     * 支付失败
     * @param errorMessage 错误信息
     * @param errorCode 错误码（可选）
     */
    data class Failed(
        val errorMessage: String,
        val errorCode: String? = null
    ) : PaymentResult()
    
    /**
     * 用户取消支付
     */
    object Cancelled : PaymentResult()
    
    /**
     * 支付处理中
     * @param message 提示信息
     * 
     * 当SDK自动查询后端结果超时或达到最大重试次数时返回此状态
     * 调用方可以稍后手动查询订单状态
     */
    data class Processing(val message: String) : PaymentResult()
}

