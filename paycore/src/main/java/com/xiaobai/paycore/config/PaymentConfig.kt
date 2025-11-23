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
    val orderLockTimeoutMs: Long = 300000  // 默认5分钟
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
                orderLockTimeoutMs = orderLockTimeoutMs
            )
        }
    }
}

