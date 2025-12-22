package com.xiaobai.paycore

/**
 * 支付错误码枚举
 * 
 * 错误码规则：
 * - 1xxx: 客户端参数/状态错误
 * - 2xxx: 网络通信错误
 * - 3xxx: 查询相关错误
 * - 4xxx: 安全验证错误
 * - 5xxx: 渠道相关错误
 * - 6xxx: 系统/未知错误
 * 
 * @property code 错误码
 * @property message 错误描述
 * @property isRetryable 是否可重试
 */
enum class PaymentErrorCode(
    val code: String,
    val message: String,
    val isRetryable: Boolean = false
) {
    
    // ========== 1xxx: 客户端参数/状态错误 ==========
    
    /**
     * 订单正在支付中
     * 场景：同一订单重复支付被订单锁拦截
     */
    ORDER_LOCKED("1001", "订单正在支付中，请勿重复操作", isRetryable = false),
    
    /**
     * 订单ID为空
     * 场景：调用支付接口时未提供订单ID
     */
    ORDER_ID_EMPTY("1002", "订单ID不能为空", isRetryable = false),
    
    /**
     * 订单金额无效
     * 场景：金额为0或负数
     */
    ORDER_AMOUNT_INVALID("1003", "订单金额无效", isRetryable = false),
    
    /**
     * 支付参数缺失
     * 场景：extraParams中缺少必需参数
     */
    PARAMS_MISSING("1004", "支付参数缺失", isRetryable = false),
    
    /**
     * 支付参数无效
     * 场景：参数格式错误或值不合法
     */
    PARAMS_INVALID("1005", "支付参数无效", isRetryable = false),
    
    /**
     * SDK未初始化
     * 场景：调用SDK接口前未调用init()
     */
    SDK_NOT_INITIALIZED("1006", "SDK未初始化，请先调用PaymentSDK.init()", isRetryable = false),
    
    /**
     * Activity无效
     * 场景：传入的Activity为null或已销毁
     */
    ACTIVITY_INVALID("1007", "Activity无效或已销毁", isRetryable = false),
    
    
    // ========== 2xxx: 网络通信错误 ==========
    
    /**
     * 网络请求失败
     * 场景：网络不可用或请求失败
     */
    NETWORK_ERROR("2001", "网络请求失败，请检查网络连接", isRetryable = true),
    
    /**
     * 网络请求超时
     * 场景：请求超过设定的超时时间
     */
    NETWORK_TIMEOUT("2002", "网络请求超时，请稍后重试", isRetryable = true),
    
    /**
     * HTTP错误
     * 场景：服务器返回4xx或5xx错误
     */
    HTTP_ERROR("2003", "服务器请求失败", isRetryable = true),
    
    /**
     * 响应解析失败
     * 场景：服务器返回的数据格式错误
     */
    RESPONSE_PARSE_ERROR("2004", "响应数据解析失败", isRetryable = false),
    
    /**
     * 服务器返回错误
     * 场景：服务器业务逻辑返回错误
     */
    SERVER_ERROR("2005", "服务器处理失败", isRetryable = true),
    
    
    // ========== 3xxx: 查询相关错误 ==========
    
    /**
     * 查询超时
     * 场景：达到最大重试次数或总超时时间后仍未得到明确结果
     */
    QUERY_TIMEOUT("3001", "支付结果查询超时，请稍后在订单列表中查看", isRetryable = true),
    
    /**
     * 查询失败
     * 场景：查询接口调用失败
     */
    QUERY_FAILED("3002", "支付结果查询失败", isRetryable = true),
    
    /**
     * 查询结果为空
     * 场景：服务器未返回订单信息
     */
    QUERY_RESULT_EMPTY("3003", "未查询到订单信息", isRetryable = true),
    
    /**
     * 查询异常
     * 场景：查询过程中发生未预期的异常
     */
    QUERY_EXCEPTION("3004", "查询过程发生异常", isRetryable = true),
    
    
    // ========== 4xxx: 安全验证错误 ==========
    
    /**
     * 签名生成失败
     * 场景：请求签名计算失败
     */
    SIGNATURE_GENERATE_FAILED("4001", "签名生成失败", isRetryable = false),
    
    /**
     * 签名验证失败
     * 场景：响应签名验证不通过
     */
    SIGNATURE_VERIFY_FAILED("4002", "签名验证失败", isRetryable = false),
    
    /**
     * 时间戳无效
     * 场景：客户端与服务器时间偏差过大
     */
    TIMESTAMP_INVALID("4003", "时间戳无效，请检查系统时间", isRetryable = false),
    
    /**
     * 证书验证失败
     * 场景：证书绑定验证失败
     */
    CERTIFICATE_VERIFY_FAILED("4004", "证书验证失败", isRetryable = false),
    
    /**
     * 签名密钥缺失
     * 场景：启用签名但未配置密钥
     */
    SIGNING_SECRET_MISSING("4005", "签名密钥未配置", isRetryable = false),
    
    
    // ========== 5xxx: 渠道相关错误 ==========
    
    /**
     * 支付渠道不存在
     * 场景：指定的channelId未注册
     */
    CHANNEL_NOT_FOUND("5001", "支付渠道不存在", isRetryable = false),
    
    /**
     * 支付APP未安装
     * 场景：需要第三方APP但用户未安装
     */
    APP_NOT_INSTALLED("5002", "未安装支付APP", isRetryable = false),
    
    /**
     * 调起支付失败
     * 场景：调用第三方支付SDK失败
     */
    LAUNCH_PAY_FAILED("5003", "调起支付失败", isRetryable = true),
    
    /**
     * 支付渠道不可用
     * 场景：渠道被禁用或维护中
     */
    CHANNEL_UNAVAILABLE("5004", "支付渠道暂时不可用", isRetryable = true),
    
    /**
     * 渠道返回错误
     * 场景：第三方SDK返回错误
     */
    CHANNEL_ERROR("5005", "支付渠道返回错误", isRetryable = false),
    
    /**
     * 渠道列表为空
     * 场景：没有可用的支付渠道
     */
    CHANNEL_LIST_EMPTY("5006", "暂无可用支付渠道", isRetryable = false),
    
    
    // ========== 6xxx: 系统/未知错误 ==========
    
    /**
     * 支付流程中断
     * 场景：应用在后台被系统回收或支付生命周期被打断
     */
    PAYMENT_INTERRUPTED("6001", "支付流程已中断，请重试", isRetryable = true),
    
    /**
     * 未知异常
     * 场景：捕获到未预期的异常
     */
    UNKNOWN_ERROR("6002", "发生未知错误", isRetryable = false),
    
    /**
     * 系统繁忙
     * 场景：系统资源不足或过载
     */
    SYSTEM_BUSY("6003", "系统繁忙，请稍后重试", isRetryable = true),
    
    /**
     * 操作取消
     * 场景：用户主动取消操作
     */
    USER_CANCELLED("6004", "操作已取消", isRetryable = false),
    
    /**
     * 权限不足
     * 场景：缺少必要的系统权限
     */
    PERMISSION_DENIED("6005", "权限不足", isRetryable = false);
    
    
    companion object {
        /**
         * 根据错误码获取枚举
         * 
         * @param code 错误码
         * @return 对应的枚举，如果不存在则返回null
         */
        fun fromCode(code: String): PaymentErrorCode? {
            return values().find { it.code == code }
        }
        
        /**
         * 判断错误码是否可重试
         * 
         * @param code 错误码
         * @return true表示可重试，false表示不可重试
         */
        fun isRetryable(code: String): Boolean {
            return fromCode(code)?.isRetryable ?: false
        }
        
        /**
         * 获取错误描述
         * 
         * @param code 错误码
         * @return 错误描述，如果错误码不存在则返回通用描述
         */
        fun getMessage(code: String): String {
            return fromCode(code)?.message ?: "未知错误(错误码: $code)"
        }
    }
}
