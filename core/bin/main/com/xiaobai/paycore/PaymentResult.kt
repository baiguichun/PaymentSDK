package com.xiaobai.paycore

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
     * @param errorCode 错误码
     */
    data class Failed(
        val errorMessage: String,
        val errorCode: String = PaymentErrorCode.UNKNOWN_ERROR.code
    ) : PaymentResult() {
        
        /**
         * 是否可重试
         */
        val isRetryable: Boolean
            get() = PaymentErrorCode.isRetryable(errorCode)
        
        /**
         * 获取错误码枚举
         */
        val errorCodeEnum: PaymentErrorCode?
            get() = PaymentErrorCode.fromCode(errorCode)
    }
    
    /**
     * 用户取消支付
     */
    object Cancelled : PaymentResult()
    
    /**
     * 支付处理中
     * @param message 提示信息
     * @param errorCode 错误码
     * 
     * 当SDK自动查询后端结果超时或达到最大重试次数时返回此状态
     * 调用方可以稍后手动查询订单状态
     */
    data class Processing(
        val message: String,
        val errorCode: String = PaymentErrorCode.QUERY_TIMEOUT.code
    ) : PaymentResult()
}
