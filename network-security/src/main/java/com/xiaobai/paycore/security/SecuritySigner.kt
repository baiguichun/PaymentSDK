package com.xiaobai.paycore.security

import com.xiaobai.paycore.config.SecurityConfig
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * 请求签名/验签辅助类
 */
class SecuritySigner(
    private val config: SecurityConfig
) {
    
    private val secureRandom = SecureRandom()
    private val keySpec: SecretKeySpec? = if (config.enableSignature && !config.signingSecret.isNullOrBlank()) {
        SecretKeySpec(config.signingSecret!!.toByteArray(Charsets.UTF_8), HMAC_SHA256)
    } else {
        null
    }
    
    fun buildRequestHeaders(
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String? = null
    ): Map<String, String> {
        val spec = keySpec ?: return emptyMap()
        
        val timestamp = System.currentTimeMillis().toString()
        val nonce = generateNonce()
        val canonicalQuery = canonicalizeQuery(query)
        val canonicalString = listOf(path, canonicalQuery, body.orEmpty(), timestamp, nonce)
            .joinToString("\n")
        
        val signature = sign(spec, canonicalString)
        
        return mapOf(
            config.signatureHeader to signature,
            config.timestampHeader to timestamp,
            config.nonceHeader to nonce
        )
    }
    
    fun verifyResponseSignature(
        responseSignature: String?,
        responseTimestamp: String?,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String? = null
    ): Result<Unit> {
        if (!config.enableResponseVerification) {
            return Result.success(Unit)
        }
        val spec = keySpec ?: return Result.failure(
            IllegalStateException("signingSecret is required for response verification")
        )
        if (responseSignature.isNullOrBlank()) {
            return Result.failure(
                IllegalStateException("Missing server signature header: ${config.serverSignatureHeader}")
            )
        }
        
        val canonicalQuery = canonicalizeQuery(query)
        val ts = responseTimestamp.orEmpty()
        
        if (ts.isNotEmpty()) {
            val serverTs = ts.toLongOrNull()
            if (serverTs != null && config.maxServerClockSkewMs > 0) {
                val skew = abs(System.currentTimeMillis() - serverTs)
                if (skew > config.maxServerClockSkewMs) {
                    return Result.failure(
                        IllegalStateException("Server timestamp skew too large: $skew ms")
                    )
                }
            }
        }
        
        val canonicalString = listOf(path, canonicalQuery, body.orEmpty(), ts)
            .joinToString("\n")
        val expectedSignature = sign(spec, canonicalString)
        
        return if (expectedSignature == responseSignature) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Server signature mismatch"))
        }
    }
    
    private fun generateNonce(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.toBase64()
    }
    
    private fun canonicalizeQuery(query: Map<String, String?>): String {
        if (query.isEmpty()) return ""
        return query
            .filterValues { it != null }
            .toSortedMap()
            .entries
            .joinToString("&") { (k, v) -> "$k=$v" }
    }
    
    private fun sign(spec: SecretKeySpec, data: String): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(spec)
        val raw = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return raw.toBase64()
    }
    
    private fun ByteArray.toBase64(): String =
        Base64.getEncoder().withoutPadding().encodeToString(this)
    
    companion object {
        private const val HMAC_SHA256 = "HmacSHA256"
    }
}
