package com.xiaobai.paycore.config

/**
 * SDK配置类
 */
data class PaymentConfig(
    /** 应用ID */
    val appId: String,
    
    /** 业务线标识，用于从后端获取对应的支付渠道配置 */
    val businessLine: String,
    
    /** API基础URL */
    val apiBaseUrl: String,
    
    /** 调试模式 */
    val debugMode: Boolean = false,
    
    /** 网络请求超时时间（秒） */
    val networkTimeout: Long = 30,
    
    /** 调起支付后延迟查询时间（毫秒），给用户完成支付的时间 */
    val initialQueryDelayMs: Long = 3000,
    
    /** 查询结果最大重试次数 */
    val maxQueryRetries: Int = 3,
    
    /** 查询间隔时间（毫秒） */
    val queryIntervalMs: Long = 2000,
    
    /** 查询超时时间（毫秒），超过此时间仍未得到明确结果则返回查询中状态 */
    val queryTimeoutMs: Long = 10000,
    
    /** 订单锁超时时间（毫秒），超过此时间自动释放锁，防止死锁 */
    val orderLockTimeoutMs: Long = 300000,  // 默认5分钟
    
    /** 安全配置：请求签名/验签、证书策略、防重放 */
    val securityConfig: SecurityConfig = SecurityConfig()
) {
    
    class Builder {
        private var appId: String = ""
        private var businessLine: String = ""
        private var apiBaseUrl: String = ""
        private var debugMode: Boolean = false
        private var networkTimeout: Long = 30
        private var initialQueryDelayMs: Long = 3000
        private var maxQueryRetries: Int = 3
        private var queryIntervalMs: Long = 2000
        private var queryTimeoutMs: Long = 10000
        private var orderLockTimeoutMs: Long = 300000  // 默认5分钟
        private var securityConfig: SecurityConfig = SecurityConfig()
        
        fun setAppId(appId: String) = apply { this.appId = appId }
        
        fun setBusinessLine(businessLine: String) = apply { this.businessLine = businessLine }
        
        fun setApiBaseUrl(url: String) = apply { this.apiBaseUrl = url }
        
        fun setDebugMode(debug: Boolean) = apply { this.debugMode = debug }
        
        fun setNetworkTimeout(timeout: Long) = apply { this.networkTimeout = timeout }
        
        fun setInitialQueryDelay(delayMs: Long) = apply {
            require(delayMs >= 0) { "initialQueryDelayMs must be >= 0" }
            this.initialQueryDelayMs = delayMs
        }
        
        fun setMaxQueryRetries(retries: Int) = apply {
            require(retries >= 0) { "maxQueryRetries must be >= 0" }
            this.maxQueryRetries = retries
        }
        
        fun setQueryIntervalMs(intervalMs: Long) = apply {
            require(intervalMs > 0) { "queryIntervalMs must be > 0" }
            this.queryIntervalMs = intervalMs
        }
        
        fun setQueryTimeoutMs(timeoutMs: Long) = apply {
            require(timeoutMs > 0) { "queryTimeoutMs must be > 0" }
            this.queryTimeoutMs = timeoutMs
        }
        
        fun setOrderLockTimeoutMs(timeoutMs: Long) = apply {
            require(timeoutMs > 0) { "orderLockTimeoutMs must be > 0" }
            this.orderLockTimeoutMs = timeoutMs
        }
        
        fun setSecurityConfig(config: SecurityConfig) = apply {
            this.securityConfig = config
        }
        
        fun build(): PaymentConfig {
            require(appId.isNotBlank()) { "appId cannot be blank" }
            require(businessLine.isNotBlank()) { "businessLine cannot be blank" }
            require(apiBaseUrl.isNotBlank()) { "apiBaseUrl cannot be blank" }
            
            return PaymentConfig(
                appId = appId,
                businessLine = businessLine,
                apiBaseUrl = apiBaseUrl,
                debugMode = debugMode,
                networkTimeout = networkTimeout,
                initialQueryDelayMs = initialQueryDelayMs,
                maxQueryRetries = maxQueryRetries,
                queryIntervalMs = queryIntervalMs,
                queryTimeoutMs = queryTimeoutMs,
                orderLockTimeoutMs = orderLockTimeoutMs,
                securityConfig = securityConfig
            )
        }
    }
}

/**
 * 安全相关配置
 */
data class SecurityConfig(
    /** 启用请求签名（HMAC SHA-256） */
    val enableSignature: Boolean = false,
    /** 签名密钥，必须与服务端约定一致 */
    val signingSecret: String? = null,
    /** 请求签名头 */
    val signatureHeader: String = "X-Signature",
    /** 请求时间戳头（毫秒） */
    val timestampHeader: String = "X-Timestamp",
    /** 请求随机数头 */
    val nonceHeader: String = "X-Nonce",
    /** 响应签名头（用于验签） */
    val serverSignatureHeader: String = "X-Server-Signature",
    /** 响应时间戳头（用于验签/防重放） */
    val serverTimestampHeader: String = "X-Server-Timestamp",
    /** 验证响应签名 */
    val enableResponseVerification: Boolean = false,
    /** 允许的服务端时间偏差（毫秒），用于验签防重放 */
    val maxServerClockSkewMs: Long = 5 * 60 * 1000,
    /** 启用证书绑定（certificate pinning） */
    val enableCertificatePinning: Boolean = false,
    /**
     * 证书指纹配置：host -> pins
     * pins 形如 "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
     */
    val certificatePins: Map<String, List<String>> = emptyMap()
) {
    init {
        if (enableSignature) {
            require(!signingSecret.isNullOrBlank()) { "signingSecret is required when enableSignature=true" }
        }
        if (enableCertificatePinning) {
            require(certificatePins.isNotEmpty()) { "certificatePins is required when enableCertificatePinning=true" }
        }
    }
}
