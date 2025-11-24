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
 */
class PaymentLifecycleActivity : Activity() {
    
    private var orderId: String = ""
    private var channelId: String = ""
    private var amount: BigDecimal = BigDecimal.ZERO
    private var extraParams: Map<String, Any> = emptyMap()
    
    private var hasLaunchedPayment: Boolean = false
    private var hasReturnedFromPayment: Boolean = false
    private var queryJob: Job? = null
    private var isFinishingActivity: Boolean = false
    
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val EXTRA_ORDER_ID = "order_id"
        private const val EXTRA_CHANNEL_ID = "channel_id"
        private const val EXTRA_AMOUNT = "amount"
        
        private val pendingCallbacks = java.util.concurrent.ConcurrentHashMap<String, CallbackData>()
        
        fun start(
            context: Context,
            orderId: String,
            channelId: String,
            amount: BigDecimal,
            extraParams: Map<String, Any> = emptyMap(),
            onResult: (PaymentResult) -> Unit
        ) {
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
    
    private data class CallbackData(
        val extraParams: Map<String, Any>,
        val callback: (PaymentResult) -> Unit
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
        amount = BigDecimal(intent.getStringExtra(EXTRA_AMOUNT) ?: "0")
        
        val callbackData = pendingCallbacks[orderId]
        extraParams = callbackData?.extraParams ?: emptyMap()
        
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
        
        if (!hasLaunchedPayment) {
            launchPayment()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentLifecycleActivity onResume - hasLaunchedPayment: $hasLaunchedPayment, hasReturnedFromPayment: $hasReturnedFromPayment")
        }
        if (hasLaunchedPayment && hasReturnedFromPayment) {
            onUserReturnedFromPayment()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentLifecycleActivity onPause")
        }
        if (hasLaunchedPayment) {
            hasReturnedFromPayment = true
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        queryJob?.cancel()
        activityScope.cancel()
        
        if (!isFinishingActivity) {
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
    
    private fun launchPayment() {
        activityScope.launch {
            try {
                if (PaymentSDK.getConfig().debugMode) {
                    println("开始调起支付 - channelId: $channelId")
                }
                
                val channel = PaymentSDK.getRepository().getChannel(channelId)
                if (channel == null) {
                    finishWithResult(PaymentResult.Failed(
                        "${PaymentErrorCode.CHANNEL_NOT_FOUND.message}: $channelId",
                        PaymentErrorCode.CHANNEL_NOT_FOUND.code
                    ))
                    return@launch
                }
                
                if (channel.requiresApp && !channel.isAppInstalled(this@PaymentLifecycleActivity)) {
                    finishWithResult(PaymentResult.Failed(
                        "${channel.channelName}${PaymentErrorCode.APP_NOT_INSTALLED.message}",
                        PaymentErrorCode.APP_NOT_INSTALLED.code
                    ))
                    return@launch
                }
                
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
                
                if (result !is PaymentResult.Success) {
                    finishWithResult(result)
                }
                
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
    
    private fun onUserReturnedFromPayment() {
        if (isFinishingActivity) return
        
        if (PaymentSDK.getConfig().debugMode) {
            println("检测到用户从支付APP返回，延迟后开始查询订单状态")
        }
        
        queryJob?.cancel()
        queryJob = activityScope.launch {
            try {
                delay(200)
                
                if (PaymentSDK.getConfig().debugMode) {
                    println("开始查询订单状态: $orderId")
                }
                
                val result = PaymentSDK.queryOrderStatus(orderId)
                
                if (PaymentSDK.getConfig().debugMode) {
                    println("查询结果: $result")
                }
                
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
    
    private fun finishWithResult(result: PaymentResult) {
        if (isFinishingActivity) {
            return
        }
        
        isFinishingActivity = true
        
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentLifecycleActivity 返回结果: $result")
        }
        
        val callbackData = pendingCallbacks.remove(orderId)
        callbackData?.callback?.invoke(result)
        
        finish()
        overridePendingTransition(0, 0)
    }
}
