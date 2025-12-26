package com.xiaobai.paycore.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.xiaobai.paycore.PaymentResult
import com.xiaobai.paycore.PaymentSDK
import com.xiaobai.paycore.R
import com.xiaobai.paycore.channel.PaymentChannelMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.math.BigDecimal

/**
 * 支付渠道选择对话框（基于BottomSheetDialog）
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
    private var selectedChannel: PaymentChannelMeta? = null
    private val viewModel: PaymentSheetViewModel = createViewModel()
    
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val channelAdapter = PaymentChannelAdapter { channel ->
        selectedChannel = channel
        updatePayButtonState()
    }
    
    init {
        setupDialog()
    }

    private fun createViewModel(): PaymentSheetViewModel {
        val factory = PaymentSheetViewModel.Factory(
            PaymentSDK.getUseCases(),
            PaymentSDK.getRepository(),
            PaymentSDK.getErrorMapper()
        )
        val owner = activity as? ViewModelStoreOwner
        return if (owner != null) {
            ViewModelProvider(owner, factory)[PaymentSheetViewModel::class.java]
        } else {
            ViewModelProvider(ViewModelStore(), factory)[PaymentSheetViewModel::class.java]
        }
    }
    
    private fun setupDialog() {
        val view = LayoutInflater.from(activity).inflate(
            R.layout.payment_bottom_sheet, 
            null, 
            false
        )
        
        dialog.setContentView(view)
        
        initViews(view)
        
        dialog.setOnCancelListener {
            onCancelled()
        }
        
        dialog.setOnDismissListener {
            dialogScope.cancel()
        }
        
        observeState()
        loadPaymentChannels()
    }
    
    private fun initViews(view: View) {
        titleView = view.findViewById(R.id.tv_title)
        recyclerView = view.findViewById(R.id.rv_channels)
        loadingView = view.findViewById(R.id.loading_view)
        emptyView = view.findViewById(R.id.empty_view)
        payButton = view.findViewById(R.id.btn_pay)
        
        titleView.text = "选择支付方式"
        
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = channelAdapter
        
        view.findViewById<View>(R.id.btn_cancel)?.setOnClickListener {
            onCancelled()
            dialog.dismiss()
        }
        
        payButton.isEnabled = false
        payButton.setOnClickListener {
            val channel = selectedChannel
            if (channel != null && !isPaymentExecuting) {
                startPayment(channel)
            }
        }
    }
    
    private fun updatePayButtonState() {
        payButton.isEnabled = selectedChannel != null && !isPaymentExecuting
        
        if (payButton.isEnabled) {
            payButton.setBackgroundColor(0xFFFF6B00.toInt())
            payButton.alpha = 1.0f
        } else {
            payButton.setBackgroundColor(0xFFCCCCCC.toInt())
            payButton.alpha = 0.5f
        }
    }
    
    private fun loadPaymentChannels() {
        viewModel.loadChannels(activity, businessLine, PaymentSDK.getConfig().appId)
    }

    private fun observeState() {
        val scope = if (activity is LifecycleOwner) {
            (activity as LifecycleOwner).lifecycleScope
        } else {
            dialogScope
        }
        scope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is PaymentSheetUiState.Loading -> {
                        isPaymentExecuting = false
                        payButton.text = "加载中..."
                        showLoading()
                        updatePayButtonState()
                    }
                    is PaymentSheetUiState.ChannelsLoaded -> {
                        isPaymentExecuting = false
                        payButton.text = "立即支付"
                        showChannels(state.channels)
                        updatePayButtonState()
                    }
                    is PaymentSheetUiState.Empty -> {
                        isPaymentExecuting = false
                        payButton.text = "立即支付"
                        showEmpty(state.message)
                        updatePayButtonState()
                    }
                    is PaymentSheetUiState.CreatingOrder -> {
                        isPaymentExecuting = true
                        payButton.text = "创单中..."
                        updatePayButtonState()
                    }
                    is PaymentSheetUiState.Paying -> {
                        isPaymentExecuting = true
                        payButton.text = "支付中..."
                        updatePayButtonState()
                    }
                    is PaymentSheetUiState.Error -> handleFailure(state.failure)
                    is PaymentSheetUiState.Result -> handleResult(state.result)
                }
            }
        }
    }
    
    private fun showLoading() {
        loadingView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE
    }
    
    private fun showChannels(channels: List<PaymentChannelMeta>) {
        loadingView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        
        channelAdapter.submitList(channels)
    }
    
    private fun showEmpty(message: String) {
        loadingView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
        
        emptyView.findViewById<TextView>(R.id.tv_empty_message)?.text = message
    }
    
    private fun startPayment(channel: PaymentChannelMeta) {
        if (isPaymentExecuting) {
            Toast.makeText(activity, "支付处理中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        
        isPaymentExecuting = true
        updatePayButtonState()
        
        if (PaymentSDK.getConfig().debugMode) {
            println("开始创单和支付 - 渠道: ${channel.channelId}, 订单: $orderId, 金额: $amount")
        }
        
        viewModel.startPayment(
            activity = activity,
            orderId = orderId,
            amount = amount,
            channel = channel,
            extraParams = extraParams
        )
    }

    private fun handleFailure(failure: PaymentResult.Failed) {
        isPaymentExecuting = false
        payButton.text = "立即支付"
        updatePayButtonState()

        Toast.makeText(
            activity,
            "支付失败: ${failure.errorMessage}",
            Toast.LENGTH_LONG
        ).show()

        try {
            onPaymentResult(failure)
        } catch (_: Exception) {
        }

        safeDismiss()
    }

    private fun handleResult(result: PaymentResult) {
        isPaymentExecuting = false
        payButton.text = "立即支付"
        updatePayButtonState()

        try {
            onPaymentResult(result)
        } catch (_: Exception) {
        }

        safeDismiss()
    }

    private fun safeDismiss() {
        try {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        } catch (_: Exception) {
        }
    }
    
    fun show() {
        dialog.show()
    }
    
    fun dismiss() {
        dialog.dismiss()
    }
    
    fun cancel() {
        dialogScope.cancel()
        dialog.dismiss()
    }
}
