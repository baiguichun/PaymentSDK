package com.xiaobai.paycore.data

import com.xiaobai.paycore.PaymentErrorCode
import com.xiaobai.paycore.PaymentResult
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 错误映射集中管理：Throwable -> PaymentErrorCode -> PaymentResult.Failed
 */
class PaymentErrorMapper {

    fun buildFailure(
        code: PaymentErrorCode,
        detail: String? = null
    ): PaymentResult.Failed {
        val msg = detail?.takeIf { it.isNotBlank() }?.let { "${code.message}: $it" } ?: code.message
        return PaymentResult.Failed(msg, code.code)
    }

    fun mapExceptionToFailed(
        throwable: Throwable?,
        defaultCode: PaymentErrorCode
    ): PaymentResult.Failed {
        val code = mapExceptionToErrorCode(throwable, defaultCode)
        val messageDetail = throwable?.message
        return buildFailure(code, messageDetail)
    }

    fun mapExceptionToErrorCode(
        throwable: Throwable?,
        defaultCode: PaymentErrorCode
    ): PaymentErrorCode {
        if (throwable == null) return defaultCode
        return when (throwable) {
            is SocketTimeoutException -> PaymentErrorCode.NETWORK_TIMEOUT
            is UnknownHostException, is ConnectException -> PaymentErrorCode.NETWORK_ERROR
            is SSLException -> PaymentErrorCode.CERTIFICATE_VERIFY_FAILED
            else -> {
                val message = throwable.message.orEmpty().lowercase()
                when {
                    message.startsWith("http error") -> PaymentErrorCode.HTTP_ERROR
                    message.contains("signature") -> PaymentErrorCode.SIGNATURE_VERIFY_FAILED
                    message.contains("signingsecret") -> PaymentErrorCode.SIGNING_SECRET_MISSING
                    message.contains("timestamp skew") -> PaymentErrorCode.TIMESTAMP_INVALID
                    else -> defaultCode
                }
            }
        }
    }
}
