package com.xiaobai.paycore.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.xiaobai.paycore.PaymentResult
import com.xiaobai.paycore.PaymentSDK
import com.xiaobai.paycore.PaymentErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * 透明Activity，用于监听支付生命周期
 * 
 * 功能：
 * 1. 调起第三方支付APP
 * 2. 监听用户从支付APP返回
 * 3. 自动查询支付结果
 * 4. 返回最终支付结果
 * 
 * 优势：
 * - 适用于有UI和无UI的场景
 * - 不依赖外部UI组件
 * - 可靠监听生命周期变化
 */
class PaymentLifecycleActivity : Activity() {
    
    private var orderId: String = ""
    private var channelId: String = ""
    private var amount: BigDecimal = BigDecimal.ZERO
    private var extraParams: Map<String, Any> = emptyMap()
    
    private var hasLaunchedPayment: Boolean = false
    private var hasReturnedFromPayment: Boolean = false
    private var queryJob: Job? = null
    private var isFinishing: Boolean = false
    
    // 手动管理的协程作用域
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val EXTRA_ORDER_ID = "order_id"
        private const val EXTRA_CHANNEL_ID = "channel_id"
        private const val EXTRA_AMOUNT = "amount"
        
        // 使用 Map 管理多个回调，key 为 orderId
        private val pendingCallbacks = java.util.concurrent.ConcurrentHashMap<String, CallbackData>()
        
        /**
         * 启动支付监听Activity
         */
        fun start(
            context: Context,
            orderId: String,
            channelId: String,
            amount: BigDecimal,
            extraParams: Map<String, Any> = emptyMap(),
            onResult: (PaymentResult) -> Unit
        ) {
            // 存储回调数据，使用 orderId 作为 key
            pendingCallbacks[orderId] = CallbackData(extraParams, onResult)
            
            val intent = Intent(context, PaymentLifecycleActivity::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_CHANNEL_ID, channelId)
                putExtra(EXTRA_AMOUNT, amount.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }
    }
    
    /**
     * 回调数据包装类
     */
    private data class CallbackData(
        val extraParams: Map<String, Any>,
        val callback: (PaymentResult) -> Unit
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 解析参数
        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
        amount = BigDecimal(intent.getStringExtra(EXTRA_AMOUNT) ?: "0")
        
        // 从 Map 中获取对应的回调数据
        val callbackData = pendingCallbacks[orderId]
        if (callbackData != null) {
            extraParams = callbackData.extraParams
        } else {
            extraParams = emptyMap()
        }
        
        if (orderId.isEmpty() || channelId.isEmpty()) {
            finishWithResult(PaymentResult.Failed(
                PaymentErrorCode.PARAMS_INVALID.message,
                PaymentErrorCode.PARAMS_INVALID.code
            ))
            return
        }
        
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentLifecycleActivity onCreate - orderId: $orderId")
        }
        
        // 首次创建时调起支付
        if (!hasLaunchedPayment) {
            launchPayment()
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentLifecycleActivity onResume - hasLaunchedPayment: $hasLaunchedPayment, hasReturnedFromPayment: $hasReturnedFromPayment")
        }
        
        // 如果已经调起过支付，且之前离开过（去了第三方APP），说明用户返回了
        if (hasLaunchedPayment && hasReturnedFromPayment) {
            onUserReturnedFromPayment()
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentLifecycleActivity onPause")
        }
        
        // 标记用户离开了（可能去了第三方APP）
        if (hasLaunchedPayment) {
            hasReturnedFromPayment = true
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        queryJob?.cancel()
        activityScope.cancel() // 取消所有协程
        
        // 如果在未返回结果就被系统销毁，兜底清理回调并告知调用方
        if (!isFinishing) {
            val callbackData = pendingCallbacks.remove(orderId)
            callbackData?.callback?.invoke(
                PaymentResult.Failed(
                    PaymentErrorCode.PAYMENT_INTERRUPTED.message,
                    PaymentErrorCode.PAYMENT_INTERRUPTED.code
                )
            )
        }
        
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentLifecycleActivity onDestroy")
        }
    }
    
    /**
     * 调起支付
     */
    private fun launchPayment() {
        activityScope.launch {
            try {
                if (PaymentSDK.getConfig().debugMode) {
                    println("开始调起支付 - channelId: $channelId")
                }
                
                // 获取支付渠道
                val channel = PaymentSDK.getChannelManager().getChannel(channelId)
                if (channel == null) {
                    finishWithResult(PaymentResult.Failed(
                        "${PaymentErrorCode.CHANNEL_NOT_FOUND.message}: $channelId",
                        PaymentErrorCode.CHANNEL_NOT_FOUND.code
                    ))
                    return@launch
                }
                
                // 检查APP是否安装
                if (channel.requiresApp && !channel.isAppInstalled(this@PaymentLifecycleActivity)) {
                    finishWithResult(PaymentResult.Failed(
                        "${channel.channelName}${PaymentErrorCode.APP_NOT_INSTALLED.message}",
                        PaymentErrorCode.APP_NOT_INSTALLED.code
                    ))
                    return@launch
                }
                
                // 调起支付
                val result = channel.pay(
                    context = this@PaymentLifecycleActivity,
                    orderId = orderId,
                    amount = amount,
                    extraParams = extraParams
                )
                
                hasLaunchedPayment = true
                
                if (PaymentSDK.getConfig().debugMode) {
                    println("支付调起结果: $result")
                }
                
                // 如果调起失败，直接返回结果
                if (result !is PaymentResult.Success) {
                    finishWithResult(result)
                }
                
                // 调起成功，等待用户返回
                // 用户会跳转到第三方APP，onPause会被调用
                // 用户返回时onResume会被调用
                
            } catch (e: Exception) {
                if (PaymentSDK.getConfig().debugMode) {
                    println("调起支付异常: ${e.message}")
                    e.printStackTrace()
                }
                finishWithResult(PaymentResult.Failed(
                    "${PaymentErrorCode.LAUNCH_PAY_FAILED.message}: ${e.message}",
                    PaymentErrorCode.LAUNCH_PAY_FAILED.code
                ))
            }
        }
    }
    
    /**
     * 用户从第三方APP返回
     */
    private fun onUserReturnedFromPayment() {
        if (isFinishing) {
            return
        }
        
        if (PaymentSDK.getConfig().debugMode) {
            println("检测到用户从支付APP返回，延迟后开始查询订单状态")
        }
        
        queryJob?.cancel()
        queryJob = activityScope.launch {
            try {
                // 延迟一小段时间，确保支付结果已经同步到后端
                delay(200)
                
                if (PaymentSDK.getConfig().debugMode) {
                    println("开始查询订单状态: $orderId")
                }
                
                // 查询订单状态
                val result = PaymentSDK.queryOrderStatus(orderId)
                
                if (PaymentSDK.getConfig().debugMode) {
                    println("查询结果: $result")
                }
                
                // 返回结果
                finishWithResult(result)
                
            } catch (e: Exception) {
                if (PaymentSDK.getConfig().debugMode) {
                    println("查询订单状态异常: ${e.message}")
                }
                finishWithResult(PaymentResult.Failed(
                    "${PaymentErrorCode.QUERY_FAILED.message}: ${e.message}",
                    PaymentErrorCode.QUERY_FAILED.code
                ))
            }
        }
    }
    
    /**
     * 完成Activity并返回结果
     */
    private fun finishWithResult(result: PaymentResult) {
        if (isFinishing) {
            return
        }
        
        isFinishing = true
        
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentLifecycleActivity 返回结果: $result")
        }
        
        // 从 Map 中获取并移除回调
        val callbackData = pendingCallbacks.remove(orderId)
        callbackData?.callback?.invoke(result)
        
        // 关闭Activity
        finish()
        overridePendingTransition(0, 0) // 无动画
    }
}
