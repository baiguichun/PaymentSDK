# 错误码使用指南

本文档介绍PaymentSDK的标准化错误码体系及使用方法。

> **v2.0.3+新增**: SDK现在具备智能错误处理能力，包括自动参数校验、智能异常映射和统一错误构建。

---

## 🎯 v2.0.3 智能错误处理

### 核心特性

**1. 自动参数校验** ✅
- orderId为空 → 自动返回 `ORDER_ID_EMPTY (1002)`
- amount <= 0 → 自动返回 `ORDER_AMOUNT_INVALID (1003)`
- channelId为空 → 自动返回 `PARAMS_INVALID (1005)`

**2. 智能异常映射** ✅
- `SocketTimeoutException` → `NETWORK_TIMEOUT (2002)`
- `UnknownHostException` → `NETWORK_ERROR (2001)`
- `SSLException` → `CERTIFICATE_VERIFY_FAILED (4004)`
- 消息关键字匹配: "signature"、"timestamp"等

**3. 详细错误信息** ✅
- 格式: "标准信息" 或 "标准信息: 详情"
- 包含底层异常详情，便于调试

---

## 📚 错误码规则

### 分类体系

| 分类 | 错误码范围 | 说明 |
|------|-----------|------|
| 客户端参数/状态错误 | 1xxx | 调用参数错误、状态不正确等 |
| 网络通信错误 | 2xxx | 网络请求失败、超时、响应解析错误等 |
| 查询相关错误 | 3xxx | 支付结果查询失败、超时等 |
| 安全验证错误 | 4xxx | 签名验证、证书验证失败等 |
| 渠道相关错误 | 5xxx | 支付渠道不存在、APP未安装等 |
| 系统/未知错误 | 6xxx | 系统异常、未知错误等 |

---

## 📋 错误码列表

### 1xxx - 客户端参数/状态错误

| 错误码 | 枚举 | 描述 | 可重试 |
|-------|------|------|--------|
| 1001 | ORDER_LOCKED | 订单正在支付中，请勿重复操作 | ❌ |
| 1002 | ORDER_ID_EMPTY | 订单ID不能为空 | ❌ |
| 1003 | ORDER_AMOUNT_INVALID | 订单金额无效 | ❌ |
| 1004 | PARAMS_MISSING | 支付参数缺失 | ❌ |
| 1005 | PARAMS_INVALID | 支付参数无效 | ❌ |
| 1006 | SDK_NOT_INITIALIZED | SDK未初始化 | ❌ |
| 1007 | ACTIVITY_INVALID | Activity无效或已销毁 | ❌ |

### 2xxx - 网络通信错误

| 错误码 | 枚举 | 描述 | 可重试 |
|-------|------|------|--------|
| 2001 | NETWORK_ERROR | 网络请求失败，请检查网络连接 | ✅ |
| 2002 | NETWORK_TIMEOUT | 网络请求超时，请稍后重试 | ✅ |
| 2003 | HTTP_ERROR | 服务器请求失败 | ✅ |
| 2004 | RESPONSE_PARSE_ERROR | 响应数据解析失败 | ❌ |
| 2005 | SERVER_ERROR | 服务器处理失败 | ✅ |

### 3xxx - 查询相关错误

| 错误码 | 枚举 | 描述 | 可重试 |
|-------|------|------|--------|
| 3001 | QUERY_TIMEOUT | 支付结果查询超时，请稍后在订单列表中查看 | ✅ |
| 3002 | QUERY_FAILED | 支付结果查询失败 | ✅ |
| 3003 | QUERY_RESULT_EMPTY | 未查询到订单信息 | ✅ |
| 3004 | QUERY_EXCEPTION | 查询过程发生异常 | ✅ |

### 4xxx - 安全验证错误

| 错误码 | 枚举 | 描述 | 可重试 |
|-------|------|------|--------|
| 4001 | SIGNATURE_GENERATE_FAILED | 签名生成失败 | ❌ |
| 4002 | SIGNATURE_VERIFY_FAILED | 签名验证失败 | ❌ |
| 4003 | TIMESTAMP_INVALID | 时间戳无效，请检查系统时间 | ❌ |
| 4004 | CERTIFICATE_VERIFY_FAILED | 证书验证失败 | ❌ |
| 4005 | SIGNING_SECRET_MISSING | 签名密钥未配置 | ❌ |

### 5xxx - 渠道相关错误

| 错误码 | 枚举 | 描述 | 可重试 |
|-------|------|------|--------|
| 5001 | CHANNEL_NOT_FOUND | 支付渠道不存在 | ❌ |
| 5002 | APP_NOT_INSTALLED | 未安装支付APP | ❌ |
| 5003 | LAUNCH_PAY_FAILED | 调起支付失败 | ✅ |
| 5004 | CHANNEL_UNAVAILABLE | 支付渠道暂时不可用 | ✅ |
| 5005 | CHANNEL_ERROR | 支付渠道返回错误 | ❌ |
| 5006 | CHANNEL_LIST_EMPTY | 暂无可用支付渠道 | ❌ |

### 6xxx - 系统/未知错误

| 错误码 | 枚举 | 描述 | 可重试 |
|-------|------|------|--------|
| 6001 | PAYMENT_INTERRUPTED | 支付流程已中断，请重试 | ✅ |
| 6002 | UNKNOWN_ERROR | 发生未知错误 | ❌ |
| 6003 | SYSTEM_BUSY | 系统繁忙，请稍后重试 | ✅ |
| 6004 | USER_CANCELLED | 操作已取消 | ❌ |
| 6005 | PERMISSION_DENIED | 权限不足 | ❌ |

---

## 💻 使用方式

### 1. 基础用法

```kotlin
PaymentSDK.payWithChannel(
    channelId = "wechat_pay",
    context = this,
    orderId = orderId,
    amount = amount,
    onResult = { result ->
        when (result) {
            is PaymentResult.Success -> {
                // 支付成功
                Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
            }
            
            is PaymentResult.Failed -> {
                // 获取错误码和错误信息
                val errorCode = result.errorCode
                val errorMessage = result.errorMessage
                
                // 显示错误信息
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                
                // 上报错误（用于统计分析）
                reportError(errorCode, errorMessage)
            }
            
            is PaymentResult.Cancelled -> {
                Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
            }
            
            is PaymentResult.Processing -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }
)
```

### SDK自动参数校验

SDK会自动校验关键参数，提前返回标准错误：

```kotlin
// SDK内部自动校验
validateOrderInput(orderId, amount)?.let {
    onResult(it)  // 返回 ORDER_ID_EMPTY 或 ORDER_AMOUNT_INVALID
    return
}

// 校验逻辑
- orderId为空 → ORDER_ID_EMPTY (1002)
- amount <= 0 → ORDER_AMOUNT_INVALID (1003)
- channelId为空 → PARAMS_INVALID (1005)
```

### SDK智能异常映射

SDK会自动将底层异常映射到标准错误码：

```kotlin
// 网络异常自动映射
SocketTimeoutException → NETWORK_TIMEOUT (2002)
UnknownHostException → NETWORK_ERROR (2001)
ConnectException → NETWORK_ERROR (2001)
SSLException → CERTIFICATE_VERIFY_FAILED (4004)

// 消息关键字映射
"http error" → HTTP_ERROR (2003)
"signature" → SIGNATURE_VERIFY_FAILED (4002)
"signingsecret" → SIGNING_SECRET_MISSING (4005)
"timestamp skew" → TIMESTAMP_INVALID (4003)
```

### 2. 判断是否可重试

```kotlin
when (result) {
    is PaymentResult.Failed -> {
        // 判断错误是否可重试
        if (result.isRetryable) {
            // 显示重试按钮
            showRetryButton()
        } else {
            // 不可重试的错误，引导用户采取其他操作
            showErrorDialog(result.errorMessage)
        }
    }
}
```

### 3. 根据错误码执行不同逻辑

```kotlin
when (result) {
    is PaymentResult.Failed -> {
        when (result.errorCode) {
            PaymentErrorCode.ORDER_LOCKED.code -> {
                // 订单锁定，提示用户
                Toast.makeText(this, "订单正在支付中", Toast.LENGTH_SHORT).show()
            }
            
            PaymentErrorCode.APP_NOT_INSTALLED.code -> {
                // APP未安装，引导用户下载
                showInstallAppDialog()
            }
            
            PaymentErrorCode.NETWORK_ERROR.code -> {
                // 网络错误，提示检查网络
                Toast.makeText(this, "网络异常，请检查网络连接", Toast.LENGTH_SHORT).show()
            }
            
            PaymentErrorCode.QUERY_TIMEOUT.code -> {
                // 查询超时，引导用户查看订单列表
                navigateToOrderList()
            }
            
            else -> {
                // 其他错误，显示通用提示
                Toast.makeText(this, result.errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
```

### 4. 获取错误码枚举

```kotlin
when (result) {
    is PaymentResult.Failed -> {
        // 获取错误码枚举（如果存在）
        val errorCodeEnum = result.errorCodeEnum
        
        if (errorCodeEnum != null) {
            // 使用枚举的属性
            Log.d(TAG, "错误码: ${errorCodeEnum.code}")
            Log.d(TAG, "错误描述: ${errorCodeEnum.message}")
            Log.d(TAG, "是否可重试: ${errorCodeEnum.isRetryable}")
        }
    }
}
```

### 5. 错误码工具方法

```kotlin
// 根据错误码字符串获取枚举
val errorCode = PaymentErrorCode.fromCode("1001")
if (errorCode != null) {
    println("错误: ${errorCode.message}")
}

// 判断错误码是否可重试
val canRetry = PaymentErrorCode.isRetryable("2001")
if (canRetry) {
    showRetryButton()
}

// 获取错误描述
val message = PaymentErrorCode.getMessage("5001")
Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
```

---

## 📊 错误统计与监控

### 1. 错误统计

```kotlin
class PaymentErrorTracker {
    private val errorCounts = mutableMapOf<String, Int>()
    
    fun trackError(errorCode: String) {
        errorCounts[errorCode] = (errorCounts[errorCode] ?: 0) + 1
    }
    
    fun getTopErrors(): List<Pair<String, Int>> {
        return errorCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }
    }
    
    fun reportToAnalytics() {
        errorCounts.forEach { (code, count) ->
            // 上报到Firebase/友盟等分析平台
            Analytics.logEvent("payment_error", bundleOf(
                "error_code" to code,
                "error_count" to count,
                "error_message" to PaymentErrorCode.getMessage(code)
            ))
        }
    }
}
```

### 2. 错误上报

```kotlin
fun reportPaymentError(result: PaymentResult.Failed, orderId: String) {
    // 上报到Sentry/Bugly等崩溃统计平台
    Sentry.captureMessage("Payment Failed").apply {
        setTag("error_code", result.errorCode)
        setTag("order_id", orderId)
        setExtra("error_message", result.errorMessage)
        setExtra("is_retryable", result.isRetryable.toString())
    }
}
```

### 3. 错误告警

```kotlin
class PaymentErrorMonitor {
    fun checkErrorRate(errorCode: String, count: Int, total: Int) {
        val errorRate = count.toFloat() / total
        
        // 错误率超过阈值时告警
        when {
            errorRate > 0.1 && errorCode == PaymentErrorCode.NETWORK_ERROR.code -> {
                // 网络错误率超过10%，发送告警
                sendAlert("网络错误率异常: ${errorRate * 100}%")
            }
            
            errorRate > 0.05 && errorCode == PaymentErrorCode.QUERY_TIMEOUT.code -> {
                // 查询超时率超过5%，发送告警
                sendAlert("查询超时率异常: ${errorRate * 100}%")
            }
        }
    }
    
    private fun sendAlert(message: String) {
        // 发送钉钉/企业微信/邮件告警
    }
}
```

---

## 🎯 最佳实践

### 1. 错误提示文案

```kotlin
fun getErrorMessage(result: PaymentResult.Failed): String {
    return when (result.errorCode) {
        PaymentErrorCode.ORDER_LOCKED.code -> 
            "订单正在支付中，请稍候"
        
        PaymentErrorCode.NETWORK_ERROR.code -> 
            "网络连接失败，请检查网络后重试"
        
        PaymentErrorCode.APP_NOT_INSTALLED.code -> 
            "未安装支付APP，请先安装"
        
        PaymentErrorCode.QUERY_TIMEOUT.code -> 
            "支付结果查询超时\n请稍后在\"我的订单\"中查看"
        
        else -> 
            result.errorMessage
    }
}
```

### 2. 错误处理策略

```kotlin
fun handlePaymentError(result: PaymentResult.Failed) {
    when (result.errorCode) {
        // 可自动重试的错误
        PaymentErrorCode.NETWORK_TIMEOUT.code,
        PaymentErrorCode.QUERY_FAILED.code -> {
            if (retryCount < MAX_RETRY) {
                retryCount++
                retryPayment()
            } else {
                showErrorDialog(result.errorMessage)
            }
        }
        
        // 需要用户操作的错误
        PaymentErrorCode.APP_NOT_INSTALLED.code -> {
            showInstallAppDialog()
        }
        
        PaymentErrorCode.ORDER_LOCKED.code -> {
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_SHORT).show()
        }
        
        // 引导用户查看订单的错误
        PaymentErrorCode.QUERY_TIMEOUT.code -> {
            showCheckOrderDialog()
        }
        
        // 其他错误
        else -> {
            showErrorDialog(result.errorMessage)
        }
    }
}
```

### 3. 用户体验优化

```kotlin
// 根据错误类型显示不同的UI
when (result) {
    is PaymentResult.Failed -> {
        when {
            // 网络相关错误 - 显示网络检查提示
            result.errorCode.startsWith("2") -> {
                showNetworkErrorView()
            }
            
            // 查询超时 - 显示查看订单按钮
            result.errorCode == PaymentErrorCode.QUERY_TIMEOUT.code -> {
                showCheckOrderButton()
            }
            
            // APP未安装 - 显示下载按钮
            result.errorCode == PaymentErrorCode.APP_NOT_INSTALLED.code -> {
                showInstallAppButton()
            }
            
            // 其他错误 - 显示通用错误提示
            else -> {
                showErrorToast(result.errorMessage)
            }
        }
    }
}
```

---

## 🔍 调试与排查

### 1. 启用调试日志

```kotlin
val config = PaymentConfig.Builder()
    .setDebugMode(true)  // 启用调试模式
    .build()

PaymentSDK.init(this, config)
```

### 2. 查看错误详情

```kotlin
when (result) {
    is PaymentResult.Failed -> {
        if (BuildConfig.DEBUG) {
            // 调试模式下显示详细信息
            Log.d(TAG, """
                支付失败:
                错误码: ${result.errorCode}
                错误信息: ${result.errorMessage}
                是否可重试: ${result.isRetryable}
                错误枚举: ${result.errorCodeEnum}
            """.trimIndent())
        }
    }
}
```

### 3. 理解错误信息格式

SDK返回的错误信息格式：

```kotlin
// 格式1: 标准错误信息
PaymentResult.Failed(
    errorMessage = "订单正在支付中，请勿重复操作",
    errorCode = "1001"
)

// 格式2: 带详细信息
PaymentResult.Failed(
    errorMessage = "支付渠道不存在: wechat_pay",  // "标准信息: 详情"
    errorCode = "5001"
)

// 格式3: 异常映射
PaymentResult.Failed(
    errorMessage = "网络请求超时: Read timed out",  // "标准信息: 异常详情"
    errorCode = "2002"
)
```

### 4. 常见问题排查

| 错误码 | 可能原因 | 排查方法 |
|-------|---------|---------|
| 1001 | 订单重复支付 | 检查是否有多次调用支付接口 |
| 1002 | orderId为空 | 确认传入的orderId参数不为空 |
| 1003 | 金额无效 | 确认amount > 0 |
| 1006 | SDK未初始化 | 确认在Application中调用了init() |
| 2001 | 网络连接失败 | 检查网络连接、代理设置、DNS |
| 2002 | 网络超时 | 检查网络质量、增加超时时间 |
| 4002 | 签名验证失败 | 检查签名密钥是否正确、时间戳是否同步 |
| 4004 | 证书验证失败 | 检查证书指纹配置是否正确 |
| 5002 | APP未安装 | 确认用户已安装第三方支付APP |

### 5. 异常映射排查

如果收到网络相关错误码，可通过错误信息判断具体原因：

```kotlin
when (result) {
    is PaymentResult.Failed -> {
        when (result.errorCode) {
            PaymentErrorCode.NETWORK_TIMEOUT.code -> {
                // 错误信息示例: "网络请求超时: Read timed out"
                if (result.errorMessage.contains("Read timed out")) {
                    Log.d(TAG, "读取超时，可能是服务器响应慢")
                } else if (result.errorMessage.contains("Connect timed out")) {
                    Log.d(TAG, "连接超时，可能是网络不稳定")
                }
            }
            
            PaymentErrorCode.NETWORK_ERROR.code -> {
                // 错误信息示例: "网络请求失败: Unable to resolve host"
                if (result.errorMessage.contains("Unable to resolve host")) {
                    Log.d(TAG, "DNS解析失败，检查网络连接")
                } else if (result.errorMessage.contains("Connection refused")) {
                    Log.d(TAG, "连接被拒绝，检查服务器状态")
                }
            }
        }
    }
}
```

---

## 📚 扩展错误码

如果需要添加自定义错误码：

```kotlin
// 在PaymentErrorCode枚举中添加
enum class PaymentErrorCode {
    // ... 现有错误码
    
    /**
     * 自定义错误码
     */
    CUSTOM_ERROR("9001", "自定义错误描述", isRetryable = false);
}
```

---

## 📝 总结

- ✅ 使用标准化的错误码，便于统计和分析
- ✅ 根据`isRetryable`判断是否可重试
- ✅ 根据错误码提供差异化的用户体验
- ✅ 上报错误数据用于监控和优化
- ✅ 在调试模式下查看详细错误信息

---

**最后更新**: 2025-11-24

