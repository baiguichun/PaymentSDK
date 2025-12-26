package com.xiaobai.paycore.ui

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaobai.paycore.PaymentErrorCode
import com.xiaobai.paycore.PaymentResult
import com.xiaobai.paycore.PaymentSDK
import com.xiaobai.paycore.channel.PaymentChannelMeta
import com.xiaobai.paycore.data.PaymentErrorMapper
import com.xiaobai.paycore.domain.PaymentRepository
import com.xiaobai.paycore.domain.usecase.PaymentUseCases
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PaymentSheetUiState {
    object Loading : PaymentSheetUiState()
    data class ChannelsLoaded(val channels: List<PaymentChannelMeta>) : PaymentSheetUiState()
    data class Empty(val message: String) : PaymentSheetUiState()
    data class CreatingOrder(val channel: PaymentChannelMeta) : PaymentSheetUiState()
    data class Paying(val channel: PaymentChannelMeta) : PaymentSheetUiState()
    data class Error(val failure: PaymentResult.Failed) : PaymentSheetUiState()
    data class Result(val result: PaymentResult) : PaymentSheetUiState()
}

class PaymentSheetViewModel(
    private val useCases: PaymentUseCases,
    private val repository: PaymentRepository,
    private val errorMapper: PaymentErrorMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentSheetUiState>(PaymentSheetUiState.Loading)
    val uiState: StateFlow<PaymentSheetUiState> = _uiState

    fun loadChannels(context: Context, businessLine: String, appId: String) {
        viewModelScope.launch {
            _uiState.value = PaymentSheetUiState.Loading
            val result = useCases.fetchChannels(businessLine, appId, context)
            result.onSuccess { channels ->
                if (channels.isEmpty()) {
                    _uiState.value = PaymentSheetUiState.Empty("暂无可用支付方式")
                } else {
                    _uiState.value = PaymentSheetUiState.ChannelsLoaded(
                        channels.sortedByDescending { it.priority }
                    )
                }
            }.onFailure { _ ->
                _uiState.value = PaymentSheetUiState.Empty("加载支付渠道失败")
            }
        }
    }

    fun startPayment(
        activity: Activity,
        orderId: String,
        amount: BigDecimal,
        channel: PaymentChannelMeta,
        extraParams: Map<String, Any>
    ) {
        viewModelScope.launch {
            _uiState.value = PaymentSheetUiState.CreatingOrder(channel)
            val createOrderResult = useCases.createOrder(
                orderId = orderId,
                channelId = channel.channelId,
                amount = amount.toString(),
                extraParams = extraParams
            )

            if (createOrderResult.isFailure) {
                val failure = errorMapper.mapExceptionToFailed(
                    createOrderResult.exceptionOrNull(),
                    PaymentErrorCode.SERVER_ERROR
                )
                _uiState.value = PaymentSheetUiState.Error(failure)
                return@launch
            }

            val paymentParams = createOrderResult.getOrNull() ?: emptyMap()
            _uiState.value = PaymentSheetUiState.Paying(channel)

            // 发起支付并将结果转为状态输出
            PaymentSDK.payWithChannel(
                channelId = channel.channelId,
                context = activity,
                orderId = orderId,
                amount = amount,
                extraParams = paymentParams,
                channelMeta = channel,
                onResult = { result ->
                    _uiState.value = PaymentSheetUiState.Result(result)
                }
            )
        }
    }

    class Factory(
        private val useCases: PaymentUseCases,
        private val repository: PaymentRepository,
        private val errorMapper: PaymentErrorMapper
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PaymentSheetViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PaymentSheetViewModel(useCases, repository, errorMapper) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
