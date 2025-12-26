# PaymentSDK 集成指南

> **版本**: v3.0.0  
> **架构**: Clean Architecture + 模块化  
> **最后更新**: 2025-11-24  
> **更新者**: guichunbai

本文档提供PaymentSDK v3.0的完整集成指南。

---

## 📋 目录

1. [环境要求](#环境要求)
2. [添加依赖](#添加依赖)
3. [初始化SDK](#初始化sdk)
4. [注册支付渠道](#注册支付渠道)
5. [发起支付](#发起支付)
6. [处理支付结果](#处理支付结果)
7. [高级功能](#高级功能)
8. [常见问题](#常见问题)

---

## 🛠️ 环境要求

- **最低Android版本**: API 21 (Android 5.0)
- **目标Android版本**: API 34 (Android 14)
- **Kotlin版本**: 2.0+
- **Gradle版本**: 8.5+
- **AGP版本**: 8.1+

---

## 📦 添加依赖

### 方式1: 本地模块依赖

```gradle
// settings.gradle.kts
include(":ui-kit")
project(":ui-kit").projectDir = file("path/to/PaymentSDK/ui-kit")

// app/build.gradle.kts
dependencies {
    implementation(project(":ui-kit"))
}
```

### 方式2: 远程依赖（发布后）

```gradle
dependencies {
    implementation("com.xiaobai:payment-sdk:3.0.0")
}
```

### 权限配置

在`AndroidManifest.xml`中添加必要权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 🚀 初始化SDK

### 基础初始化

在Application中初始化SDK：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 1. 构建配置
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")
            .setApiBaseUrl("https://api.example.com")
            .setDebugMode(BuildConfig.DEBUG)
            .build()
        
        // 2. 初始化SDK（渠道会通过 @PaymentChannelService + KSP 自动发现并注册）
        PaymentSDK.init(this, config)
    }
}
```

### 完整配置示例

```kotlin
val config = PaymentConfig.Builder()
    // === 基础配置 ===
    .setAppId("your_app_id")
    .setBusinessLine("retail")
    .setApiBaseUrl("https://api.example.com")
    .setDebugMode(BuildConfig.DEBUG)
    
    // === 网络配置 ===
    .setNetworkTimeout(30)  // 网络超时时间（秒）
    
    // === 查询配置 ===
    .setInitialQueryDelay(3000)  // 调起支付后延迟查询（毫秒）
    .setMaxQueryRetries(3)       // 最大重试次数
    .setQueryIntervalMs(2000)    // 查询间隔（毫秒）
    .setQueryTimeoutMs(10000)    // 查询总超时（毫秒）
    
    // === 订单锁配置 ===
    .setOrderLockTimeoutMs(300000)  // 订单锁超时时间（毫秒）
    
    // === 安全配置 ===
    .setSecurityConfig(
        SecurityConfig(
            // 请求签名
            enableSignature = true,
            signingSecret = "your_secret_key",
            
            // 响应验签
            enableResponseVerification = true,
            maxServerClockSkewMs = 300000,  // 允许5分钟时间偏差
            
            // 证书绑定
            enableCertificatePinning = true,
            certificatePins = mapOf(
                "api.example.com" to listOf(
                    "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
                )
            )
        )
    )
    .build()

PaymentSDK.init(this, config)
```

### 使用外部Koin容器（可选）

如果宿主APP已使用Koin，可共享容器：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")
            .setApiBaseUrl("https://api.example.com")
            .build()
        
        // 启动Koin容器
        val koinApp = startKoin {
            androidContext(this@MyApplication)
            modules(
                appModule,  // 宿主APP的模块
                paymentModule(config)  // 支付SDK的模块
            )
        }
        
        // 传入外部Koin容器
        PaymentSDK.init(this, config, koinApp)
    }
}
```

---

## 🔌 渠道发现与注册

- 在渠道实现类上添加 `@PaymentChannelService(channelId = "...")`。
- 渠道模块引入 `alias(libs.plugins.ksp)` 插件并添加 `ksp(project(":channel-spi-processor"))` 依赖。
- 处理器会在编译期生成注册表，`PaymentSDK.init()` 时自动发现并注册懒加载渠道代理；真实渠道实例在调用 `pay()` 时由生成的工厂直接创建。
- 渠道列表 UI 文案/图标需使用后端返回的渠道元数据（`PaymentChannelMeta`），懒代理本身不承载展示信息。

---

## 💰 发起支付

### 方式1: 支付渠道选择对话框（推荐）

SDK自动显示渠道选择对话框，用户选择后自动完成支付：

```kotlin
class OrderPaymentActivity : AppCompatActivity() {
    
    private fun startPayment() {
        PaymentSDK.showPaymentSheet(
            activity = this,
            orderId = "ORDER_20250124_001",
            amount = BigDecimal("99.99"),
            extraParams = mapOf(
                "productId" to "PROD_001",
                "userId" to "USER_123"
            ),
            businessLine = "retail",  // 可选，默认使用config中的
            onPaymentResult = { result ->
                handlePaymentResult(result)
            },
            onCancelled = {
                Toast.makeText(this, "已取消选择支付方式", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun handlePaymentResult(result: PaymentResult) {
        when (result) {
            is PaymentResult.Success -> {
                // 支付成功
                Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
                navigateToSuccessPage(result.transactionId)
            }
            
            is PaymentResult.Failed -> {
                // 支付失败
                handlePaymentFailure(result)
            }
            
            is PaymentResult.Cancelled -> {
                // 用户取消
                Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
            }
            
            is PaymentResult.Processing -> {
                // 查询超时，建议稍后查看订单列表
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                navigateToOrderList()
            }
        }
    }
}
```

### 方式2: 使用指定渠道支付

适用于已知用户支付偏好的场景：

```kotlin
PaymentSDK.payWithChannel(
    channelId = "wechat_pay",
    context = this,
    orderId = "ORDER_20250124_001",
    amount = BigDecimal("99.99"),
    extraParams = mapOf("productId" to "PROD_001"),
    onResult = { result ->
        handlePaymentResult(result)
    }
)
```

### 方式3: 自定义UI + 指定渠道

```kotlin
class CustomPaymentActivity : AppCompatActivity() {
    
    private fun showCustomChannelSelector() {
        // 1. 获取可用渠道
        val channels = PaymentSDK.getAvailableChannels(this)
        
        // 2. 显示自定义UI
        val adapter = CustomChannelAdapter(channels) { selectedChannel ->
            // 3. 使用选中的渠道支付
            PaymentSDK.payWithChannel(
                channelId = selectedChannel.channelId,
                context = this,
                orderId = orderId,
                amount = amount,
                onResult = { result ->
                    handlePaymentResult(result)
                }
            )
        }
        
        recyclerView.adapter = adapter
    }
}
```

---

## 📊 处理支付结果

### 基础处理

```kotlin
private fun handlePaymentResult(result: PaymentResult) {
    when (result) {
        is PaymentResult.Success -> {
            // 支付成功
            val transactionId = result.transactionId
            showSuccessDialog(transactionId)
        }
        
        is PaymentResult.Failed -> {
            // 支付失败
            val errorCode = result.errorCode
            val errorMessage = result.errorMessage
            val isRetryable = result.isRetryable
            
            if (isRetryable) {
                showRetryDialog(errorMessage)
            } else {
                showErrorDialog(errorMessage)
            }
        }
        
        is PaymentResult.Cancelled -> {
            // 用户取消
            Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
        }
        
        is PaymentResult.Processing -> {
            // 支付处理中
            showProcessingDialog(result.message)
        }
    }
}
```

### 高级错误处理

```kotlin
private fun handlePaymentFailure(result: PaymentResult.Failed) {
    val errorCode = result.errorCode
    val errorMessage = result.errorMessage
    val errorCodeEnum = result.errorCodeEnum
    
    // 根据错误类型分类处理
    when (errorCodeEnum) {
        // 网络错误 - 可重试
        PaymentErrorCode.NETWORK_TIMEOUT,
        PaymentErrorCode.NETWORK_ERROR -> {
            showDialog(
                title = "网络异常",
                message = errorMessage,
                positiveButton = "重试" to { retryPayment() },
                negativeButton = "取消" to {}
            )
        }
        
        // APP未安装 - 引导安装
        PaymentErrorCode.APP_NOT_INSTALLED -> {
            showDialog(
                title = "未安装支付APP",
                message = errorMessage,
                positiveButton = "去安装" to { openAppStore() },
                negativeButton = "取消" to {}
            )
        }
        
        // 订单锁定 - 不应重试
        PaymentErrorCode.ORDER_LOCKED -> {
            showDialog(
                title = "订单处理中",
                message = "该订单正在支付，请勿重复操作",
                positiveButton = "确定" to {}
            )
        }
        
        // 参数错误 - 不可重试
        PaymentErrorCode.ORDER_ID_EMPTY,
        PaymentErrorCode.ORDER_AMOUNT_INVALID,
        PaymentErrorCode.PARAMS_INVALID -> {
            showDialog(
                title = "参数错误",
                message = errorMessage,
                positiveButton = "确定" to {}
            )
            // 上报错误日志
            reportError(errorCode, errorMessage)
        }
        
        // 其他错误
        else -> {
            showDialog(
                title = "支付失败",
                message = errorMessage,
                positiveButton = "确定" to {}
            )
        }
    }
    
    // 上报错误统计
    analytics.logEvent("payment_error", bundleOf(
        "error_code" to errorCode,
        "error_message" to errorMessage,
        "is_retryable" to result.isRetryable,
        "order_id" to currentOrderId
    ))
}
```

### 处理Processing状态

```kotlin
when (result) {
    is PaymentResult.Processing -> {
        // 查询超时，但支付可能已完成
        showDialog(
            title = "支付处理中",
            message = result.message,
            positiveButton = "查看订单" to {
                navigateToOrderList()
            },
            negativeButton = "稍后查询" to {}
        )
        
        // 可选：延迟后自动查询
        lifecycleScope.launch {
            delay(5000)
            queryOrderStatus()
        }
    }
}
```

---

## 🔍 高级功能

### 1. 手动查询订单状态

```kotlin
class OrderListActivity : AppCompatActivity() {
    
    private fun queryOrderStatus(orderId: String) {
        lifecycleScope.launch {
            showLoading()
            
            val result = PaymentSDK.queryOrderStatus(orderId)
            
            hideLoading()
            
            when (result) {
                is PaymentResult.Success -> {
                    updateOrderStatus(orderId, "已支付")
                    Toast.makeText(this@OrderListActivity, "支付成功", Toast.LENGTH_SHORT).show()
                }
                
                is PaymentResult.Failed -> {
                    updateOrderStatus(orderId, "支付失败")
                    Toast.makeText(this@OrderListActivity, result.errorMessage, Toast.LENGTH_SHORT).show()
                }
                
                is PaymentResult.Processing -> {
                    updateOrderStatus(orderId, "处理中")
                }
                
                is PaymentResult.Cancelled -> {
                    updateOrderStatus(orderId, "已取消")
                }
            }
        }
    }
}
```

### 2. 获取渠道列表

```kotlin
// 获取所有已注册渠道
val allChannels = PaymentSDK.getRegisteredChannels()
println("已注册渠道数: ${allChannels.size}")

// 获取可用渠道（已注册且APP已安装）
val availableChannels = PaymentSDK.getAvailableChannels(this)
println("可用渠道数: ${availableChannels.size}")

// 检查特定渠道是否可用
val wechatChannel = allChannels.find { it.channelId == "wechat_pay" }
if (wechatChannel != null && wechatChannel.isAppInstalled(this)) {
    println("微信支付可用")
}
```

### 3. 检查订单支付状态

```kotlin
// 检查订单是否正在支付中
if (PaymentSDK.isOrderPaying(orderId)) {
    Toast.makeText(this, "订单正在支付中", Toast.LENGTH_SHORT).show()
    return
}

// 取消正在支付的订单
if (PaymentSDK.cancelPayment(orderId)) {
    Toast.makeText(this, "已取消支付", Toast.LENGTH_SHORT).show()
}
```

### 4. 调试信息

```kotlin
// 获取当前支付状态（调试用）
if (BuildConfig.DEBUG) {
    val status = PaymentSDK.getPaymentStatus()
    Log.d("PaymentSDK", status)
    /*
    输出示例:
    === 支付状态 ===
    正在支付订单数: 2
    正在支付订单: ORDER_001, ORDER_002
    
    === 查询状态 ===
    正在查询订单数: 1
    正在查询订单: ORDER_003
    */
}
```

---

## ⚙️ 配置说明

### PaymentConfig参数详解

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| appId | String | ✅ | - | 应用ID |
| businessLine | String | ✅ | - | 业务线标识 |
| apiBaseUrl | String | ✅ | - | API基础URL |
| debugMode | Boolean | ❌ | false | 调试模式 |
| networkTimeout | Long | ❌ | 30 | 网络超时（秒） |
| initialQueryDelayMs | Long | ❌ | 3000 | 初始查询延迟（毫秒） |
| maxQueryRetries | Int | ❌ | 3 | 最大重试次数 |
| queryIntervalMs | Long | ❌ | 2000 | 查询间隔（毫秒） |
| queryTimeoutMs | Long | ❌ | 10000 | 查询总超时（毫秒） |
| orderLockTimeoutMs | Long | ❌ | 300000 | 订单锁超时（毫秒） |
| securityConfig | SecurityConfig | ❌ | SecurityConfig() | 安全配置 |

### SecurityConfig参数详解

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| enableSignature | Boolean | ❌ | false | 启用请求签名 |
| signingSecret | String? | ⚠️ | null | 签名密钥（enableSignature=true时必需） |
| enableResponseVerification | Boolean | ❌ | false | 启用响应验签 |
| maxServerClockSkewMs | Long | ❌ | 300000 | 允许的服务器时间偏差（毫秒） |
| enableCertificatePinning | Boolean | ❌ | false | 启用证书绑定 |
| certificatePins | Map<String, List<String>> | ⚠️ | emptyMap() | 证书指纹（enableCertificatePinning=true时必需） |

---

## ❓ 常见问题

### Q1: 如何处理支付回调？

**A**: SDK自动处理回调，无需额外配置。结果通过`onPaymentResult`回调返回。

### Q2: 支付结果如何验证？

**A**: SDK自动查询后端确认支付状态，返回`PaymentResult.Success`时表示已验证成功。

### Q3: 如何处理用户取消支付？

**A**: 用户取消会返回`PaymentResult.Cancelled`，建议提示用户并返回订单页面。

### Q4: 支付渠道弹窗为空怎么办？

**A**: 可能原因：
1. 未注册任何渠道
2. 已注册渠道的APP都未安装
3. 后端未返回可用渠道

检查方法：
```kotlin
val registered = PaymentSDK.getRegisteredChannels()
val available = PaymentSDK.getAvailableChannels(this)
Log.d("PaymentSDK", "已注册: ${registered.size}, 可用: ${available.size}")
```

### Q5: 如何添加自定义支付渠道？

**A**: 实现`IPaymentChannel`接口即可。详见 [渠道实现指南](./CHANNEL_IMPLEMENTATION_GUIDE.md)

### Q6: 如何获取证书指纹？

**A**: 使用OpenSSL命令：
```bash
openssl s_client -connect api.example.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
```

### Q7: Koin依赖冲突怎么办？

**A**: 使用外部Koin容器：
```kotlin
PaymentSDK.init(this, config, externalKoinApp)
```

### Q8: 如何升级到v3.0？

**A**: 参考 [迁移指南](./MIGRATION_GUIDE_V3.md)（待补充）

---

## 📚 相关文档

- [项目结构说明](./PROJECT_STRUCTURE.md)
- [架构设计文档](./ARCHITECTURE.md)
- [API参考文档](./API.md)
- [错误码指南](./ERROR_CODE_GUIDE.md)
- [渠道实现指南](./CHANNEL_IMPLEMENTATION_GUIDE.md)

---

## 📞 技术支持

如遇到问题，请：
1. 查阅文档
2. 提交Issue
3. 联系技术支持

---

**最后更新者**: guichunbai  
**更新日期**: 2025-11-24  
**版本**: v3.0.0
