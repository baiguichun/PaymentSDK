package com.xiaobai.paycore.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.xiaobai.paycore.PaymentResult
import com.xiaobai.paycore.PaymentSDK
import com.xiaobai.paycore.R
import com.xiaobai.paycore.channel.IPaymentChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * 支付渠道选择对话框（基于BottomSheetDialog）
 * 
 * 功能：
 * 1. 从后端获取当前业务线可用的支付渠道
 * 2. 过滤出本地已注册且APP已安装的渠道
 * 3. 展示渠道列表供用户选择
 * 4. 自动执行支付并返回支付结果
 * 
 * 优势：
 * - 不依赖 Fragment，可以在任何 Activity 上使用
 * - 包括普通 Activity 和 FragmentActivity
 */
class PaymentSheetDialog(
    private val activity: Activity,
    private val orderId: String,
    private val amount: BigDecimal,
    private val extraParams: Map<String, Any> = emptyMap(),
    private val businessLine: String,
    private val onPaymentResult: (PaymentResult) -> Unit,
    private val onCancelled: () -> Unit
) {
    
    private val dialog: BottomSheetDialog = BottomSheetDialog(activity)
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: View
    private lateinit var emptyView: View
    private lateinit var titleView: TextView
    private lateinit var payButton: Button
    
    private var isPaymentExecuting: Boolean = false
    private var selectedChannel: IPaymentChannel? = null
    
    // Dialog 自己的协程作用域
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val channelAdapter = PaymentChannelAdapter { channel ->
        // 用户选择了支付渠道
        selectedChannel = channel
        updatePayButtonState()
    }
    
    init {
        setupDialog()
    }
    
    /**
     * 设置对话框
     */
    private fun setupDialog() {
        val view = LayoutInflater.from(activity).inflate(
            R.layout.payment_bottom_sheet, 
            null, 
            false
        )
        
        dialog.setContentView(view)
        
        initViews(view)
        
        // 设置取消监听
        dialog.setOnCancelListener {
            onCancelled()
        }
        
        // 设置关闭监听，取消所有协程
        dialog.setOnDismissListener {
            dialogScope.cancel()
        }
        
        // 加载支付渠道
        loadPaymentChannels()
    }
    
    /**
     * 初始化视图
     */
    private fun initViews(view: View) {
        titleView = view.findViewById(R.id.tv_title)
        recyclerView = view.findViewById(R.id.rv_channels)
        loadingView = view.findViewById(R.id.loading_view)
        emptyView = view.findViewById(R.id.empty_view)
        payButton = view.findViewById(R.id.btn_pay)
        
        titleView.text = "选择支付方式"
        
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = channelAdapter
        
        // 取消按钮
        view.findViewById<View>(R.id.btn_cancel)?.setOnClickListener {
            onCancelled()
            dialog.dismiss()
        }
        
        // 支付按钮
        payButton.isEnabled = false
        payButton.setOnClickListener {
            val channel = selectedChannel
            if (channel != null && !isPaymentExecuting) {
                startPayment(channel)
            }
        }
    }
    
    /**
     * 更新支付按钮状态
     */
    private fun updatePayButtonState() {
        payButton.isEnabled = selectedChannel != null && !isPaymentExecuting
        
        // 更新按钮背景色
        if (payButton.isEnabled) {
            payButton.setBackgroundColor(0xFFFF6B00.toInt())
            payButton.alpha = 1.0f
        } else {
            payButton.setBackgroundColor(0xFFCCCCCC.toInt())
            payButton.alpha = 0.5f
        }
    }
    
    /**
     * 加载支付渠道
     */
    private fun loadPaymentChannels() {
        showLoading()
        
        // 优先使用 Activity 的 lifecycleScope（如果可用）
        // 否则使用 Dialog 自己的 dialogScope
        val scope = if (activity is LifecycleOwner) {
            (activity as LifecycleOwner).lifecycleScope
        } else {
            dialogScope
        }
        
        scope.launch {
            loadChannelsAsync()
        }
    }
    
    /**
     * 异步加载渠道
     */
    private suspend fun loadChannelsAsync() {
        try {
            // 从后端获取可用支付渠道配置
            val config = PaymentSDK.getConfig()
            val apiService = PaymentSDK.getApiService()
            
            val result = apiService.getPaymentChannels(businessLine, config.appId)
            
            result.onSuccess { channelMetas ->
                // 提取渠道ID列表
                val channelIds = channelMetas.map { it.channelId }
                
                // 过滤出已注册且可用的渠道
                val channelManager = PaymentSDK.getChannelManager()
                val availableChannels = channelManager.filterAvailableChannels(
                    activity,
                    channelIds
                )
                
                if (availableChannels.isEmpty()) {
                    showEmpty("暂无可用支付方式")
                } else {
                    showChannels(availableChannels)
                }
            }.onFailure { error ->
                // 网络请求失败，使用本地所有可用渠道作为降级方案
                val channelManager = PaymentSDK.getChannelManager()
                val localChannels = channelManager.getAvailableChannels(activity)
                
                if (localChannels.isEmpty()) {
                    showEmpty("暂无可用支付方式")
                } else {
                    showChannels(localChannels)
                }
                
                if (config.debugMode) {
                    Toast.makeText(
                        activity,
                        "加载支付渠道失败: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            showEmpty("加载失败，请重试")
            e.printStackTrace()
        }
    }
    
    /**
     * 显示加载中
     */
    private fun showLoading() {
        loadingView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE
    }
    
    /**
     * 显示渠道列表
     */
    private fun showChannels(channels: List<com.xiaobai.paycore.channel.IPaymentChannel>) {
        loadingView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        
        channelAdapter.submitList(channels)
    }
    
    /**
     * 显示空视图
     */
    private fun showEmpty(message: String) {
        loadingView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        
        emptyView.findViewById<TextView>(R.id.tv_empty_message)?.text = message
    }
    
    /**
     * 开始支付（创单 + 支付）
     */
    private fun startPayment(channel: IPaymentChannel) {
        // 防止重复点击
        if (isPaymentExecuting) {
            Toast.makeText(activity, "支付处理中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        
        isPaymentExecuting = true
        updatePayButtonState()
        
        if (PaymentSDK.getConfig().debugMode) {
            println("开始创单和支付 - 渠道: ${channel.channelName}, 订单: $orderId, 金额: $amount")
        }
        
        // 显示loading
        payButton.text = "创单中..."
        
        // 使用协程创单
        val scope = if (activity is LifecycleOwner) {
            activity.lifecycleScope
        } else {
            dialogScope
        }
        
        scope.launch {
            try {
                // 1. 调用后端创单接口
                if (PaymentSDK.getConfig().debugMode) {
                    println("调用创单接口...")
                }
                
                val createOrderResult = PaymentSDK.getApiService().createPaymentOrder(
                    orderId = orderId,
                    channelId = channel.channelId,
                    amount = amount.toString(),
                    extraParams = extraParams
                )
                
                if (createOrderResult.isFailure) {
                    val error = createOrderResult.exceptionOrNull()
                    throw Exception(error?.message ?: "创单失败")
                }
                
                val paymentParams = createOrderResult.getOrNull() ?: emptyMap()
                
                if (PaymentSDK.getConfig().debugMode) {
                    println("创单成功，开始支付...")
                }
                
                // 2. 更新按钮状态
                payButton.text = "支付中..."
                
                // 3. 使用PaymentLifecycleActivity监听支付生命周期
                PaymentSDK.payWithChannel(
                    channelId = channel.channelId,
                    context = activity,
                    orderId = orderId,
                    amount = amount,
                    extraParams = paymentParams,
                    onResult = { result ->
                        // 先执行支付结果回调
                        try {
                            onPaymentResult(result)
                        } catch (e: Exception) {
                            if (PaymentSDK.getConfig().debugMode) {
                                println("支付结果回调异常: ${e.message}")
                                e.printStackTrace()
                            }
                        } finally {
                            // 回调完成后关闭对话框
                            try {
                                if (dialog.isShowing) {
                                    dialog.dismiss()
                                }
                            } catch (e: Exception) {
                                if (PaymentSDK.getConfig().debugMode) {
                                    println("关闭对话框异常: ${e.message}")
                                }
                            }
                            isPaymentExecuting = false
                        }
                    }
                )
                
            } catch (e: Exception) {
                // 创单或支付异常
                if (PaymentSDK.getConfig().debugMode) {
                    println("创单或支付异常: ${e.message}")
                    e.printStackTrace()
                }
                
                isPaymentExecuting = false
                payButton.text = "立即支付"
                updatePayButtonState()
                
                Toast.makeText(
                    activity,
                    "支付失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                
                // 通知失败
                try {
                    onPaymentResult(PaymentResult.Failed(e.message ?: "创单失败"))
                } catch (ex: Exception) {
                    if (PaymentSDK.getConfig().debugMode) {
                        println("失败回调异常: ${ex.message}")
                    }
                }
                
                // 关闭对话框
                try {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                } catch (ex: Exception) {
                    // 忽略 dismiss 异常
                }
            }
        }
    }
    
    /**
     * 显示对话框
     */
    fun show() {
        dialog.show()
    }
    
    /**
     * 关闭对话框
     */
    fun dismiss() {
        dialog.dismiss()
        // dismiss 会触发 OnDismissListener，自动取消协程
    }
    
    /**
     * 手动取消所有正在进行的操作
     */
    fun cancel() {
        dialogScope.cancel()
        dialog.dismiss()
    }
}

