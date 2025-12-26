package com.xiaobai.paycore

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * 基于 ProcessLifecycleOwner 的支付流程监听器，避免透明 Activity 被系统回收带来的流程中断。
 */
internal object PaymentProcessLifecycleObserver : DefaultLifecycleObserver {
    
    private const val QUERY_DELAY_MS = 200L
    private const val FALLBACK_QUERY_DELAY_MS = 1200L
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isRegistered = false
    private var session: PaymentSession? = null
    
    fun start(
        context: Context,
        orderId: String,
        channelId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>,
        channelMeta: com.xiaobai.paycore.channel.PaymentChannelMeta? = null,
        onResult: (PaymentResult) -> Unit
    ) {
        registerIfNeeded()
        
        session?.let { existing ->
            if (!existing.isFinished) {
                finishSession(
                    PaymentSDK.buildFailure(PaymentErrorCode.PAYMENT_INTERRUPTED),
                    existing
                )
            }
        }
        
        val newSession = PaymentSession(
            orderId = orderId,
            channelId = channelId,
            amount = amount,
            extraParams = extraParams,
            channelMeta = channelMeta,
            callback = onResult
        )
        session = newSession
        
        scope.launch {
            launchPayment(context, newSession)
        }
    }
    
    override fun onStop(owner: LifecycleOwner) {
        val current = session ?: return
        if (current.hasLaunchedPayment && !current.isFinished) {
            current.hasMovedToBackground = true
        }
    }
    
    override fun onStart(owner: LifecycleOwner) {
        val current = session ?: return
        if (current.hasLaunchedPayment && current.hasMovedToBackground && !current.isFinished) {
            startQuery(current)
        }
    }
    
    private suspend fun launchPayment(context: Context, session: PaymentSession) {
        try {
            if (PaymentSDK.getConfig().debugMode) {
                println("开始调起支付 - channelId: ${session.channelId}")
            }
            
            val channel = PaymentSDK.getRepository().getChannel(session.channelId)
            if (channel == null) {
                finishSession(
                    PaymentSDK.buildFailure(
                        PaymentErrorCode.CHANNEL_NOT_FOUND,
                        session.channelId
                    ),
                    session
                )
                return
            }

            // requiresApp 为 true 时才校验安装状态
            val requiresApp = session.channelMeta?.requiresApp ?: false
            if (requiresApp && !channel.isAppInstalled(context)) {
                finishSession(
                    PaymentSDK.buildFailure(
                        PaymentErrorCode.APP_NOT_INSTALLED,
                        channel.channelName
                    ),
                    session
                )
                return
            }

        session.hasLaunchedPayment = true
        val result = channel.pay(
            context = context,
            orderId = session.orderId,
            amount = session.amount,
            extraParams = session.extraParams
            )
            
            if (PaymentSDK.getConfig().debugMode) {
                println("支付调起结果: $result")
            }
            
            if (result is PaymentResult.Success) {
                // 视为调起成功，等待前后台切换或兜底查询后回调最终结果
                ensureFallbackQuery(session)
            } else {
                finishSession(result, session)
            }
            
        } catch (e: Exception) {
            if (PaymentSDK.getConfig().debugMode) {
                println("调起支付异常: ${e.message}")
                e.printStackTrace()
            }
            finishSession(
                PaymentSDK.buildFailure(
                    PaymentErrorCode.LAUNCH_PAY_FAILED,
                    e.message
                ),
                session
            )
        }
    }
    
    private fun startQuery(session: PaymentSession) {
        session.queryJob?.cancel()
        session.fallbackJob?.cancel()
        session.queryJob = scope.launch {
            try {
                delay(QUERY_DELAY_MS)
                
                if (PaymentSDK.getConfig().debugMode) {
                    println("开始查询订单状态: ${session.orderId}")
                }
                
                val result = PaymentSDK.queryOrderStatus(session.orderId)
                
                if (PaymentSDK.getConfig().debugMode) {
                    println("查询结果: $result")
                }
                
                finishSession(result, session)
            } catch (e: Exception) {
                if (PaymentSDK.getConfig().debugMode) {
                    println("查询订单状态异常: ${e.message}")
                }
                finishSession(
                    PaymentSDK.buildFailure(
                        PaymentErrorCode.QUERY_FAILED,
                        e.message
                    ),
                    session
                )
            }
        }
    }
    
    private fun finishSession(result: PaymentResult, session: PaymentSession) {
        if (session.isFinished) return
        
        session.isFinished = true
        session.queryJob?.cancel()
        session.fallbackJob?.cancel()
        
        if (PaymentSDK.getConfig().debugMode) {
            println("PaymentProcessLifecycleObserver 返回结果: $result")
        }
        
        session.callback.invoke(result)
        cleanup()
    }
    
    private fun cleanup() {
        session = null
        scope.coroutineContext.cancelChildren()
        
        if (isRegistered) {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
            isRegistered = false
        }
    }
    
    private fun ensureFallbackQuery(session: PaymentSession) {
        session.fallbackJob?.cancel()
        session.fallbackJob = scope.launch {
            delay(FALLBACK_QUERY_DELAY_MS)
            if (!session.isFinished) {
                if (PaymentSDK.getConfig().debugMode) {
                    println("未检测到前后台切换，兜底开始查询订单状态")
                }
                startQuery(session)
            }
        }
    }
    
    private fun registerIfNeeded() {
        if (!isRegistered) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            isRegistered = true
        }
    }
    
    private data class PaymentSession(
        val orderId: String,
        val channelId: String,
        val amount: BigDecimal,
        val extraParams: Map<String, Any>,
        val channelMeta: com.xiaobai.paycore.channel.PaymentChannelMeta? = null,
        val callback: (PaymentResult) -> Unit,
        var hasLaunchedPayment: Boolean = false,
        var hasMovedToBackground: Boolean = false,
        var queryJob: Job? = null,
        var fallbackJob: Job? = null,
        var isFinished: Boolean = false
    )
}
