# 聚合支付SDK集成指南

## 快速开始

### 1. 添加依赖

在项目的`build.gradle`中添加依赖：

```gradle
dependencies {
    // 核心SDK（必须）
    implementation 'com.xiaobai:paymentcore:2.0.0'
    
    // 按需添加支付渠道SDK
    implementation 'com.xiaobai:payment-channel-wechat:2.0.0'  // 微信支付
    implementation 'com.xiaobai:payment-channel-alipay:2.0.0'  // 支付宝
    implementation 'com.xiaobai:payment-channel-union:2.0.0'   // 银联支付
}
```

### 2. 初始化SDK

在Application的`onCreate()`中初始化SDK：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 配置SDK
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail") // 业务线标识
            .setApiBaseUrl("https://api.example.com")
            .setDebugMode(BuildConfig.DEBUG)
            .build()
        
        // 初始化
        PaymentSDK.init(this, config)
        
        // 注册已集成的支付渠道SDK
        PaymentSDK.registerChannel(WeChatPayChannel())
        PaymentSDK.registerChannel(AlipayChannel())
        PaymentSDK.registerChannel(UnionPayChannel())
    }
}
```

### 3. 发起支付

在Activity中调用支付：

```kotlin
class CheckoutActivity : AppCompatActivity() {
    
    private fun startPayment() {
        PaymentSDK.showPaymentSheet(
            activity = this,
            orderId = "ORDER123",
            amount = BigDecimal("99.99"),
            onPaymentResult = { result ->
                handlePaymentResult(result)
            },
            onCancelled = {
                Toast.makeText(this, "已取消支付", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun handlePaymentResult(result: PaymentResult) {
        when (result) {
            is PaymentResult.Success -> {
                Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
                navigateToSuccessPage()
            }
            is PaymentResult.Failed -> {
                Toast.makeText(this, "支付失败: ${result.errorMessage}", Toast.LENGTH_SHORT).show()
            }
            is PaymentResult.Cancelled -> {
                Toast.makeText(this, "已取消支付", Toast.LENGTH_SHORT).show()
            }
            is PaymentResult.Processing -> {
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                navigateToOrderList()
            }
        }
    }
    
    private fun navigateToSuccessPage() {
        // 跳转到支付成功页面
    }
    
    private fun navigateToOrderList() {
        // 跳转到订单列表
    }
}
```

### 4. 错误处理与回调保障

- 渠道列表/创单响应如果解析失败，SDK 会返回 `PaymentResult.Failed`，请在回调里向用户展示友好提示或重试。
- 支付流程中如果透明 Activity 被系统回收且未能正常结束，SDK 会兜底回调失败（错误文案：支付流程已中断，请重试），避免回调悬挂。
- 建议在 `PaymentResult.Failed` 场景记录日志/上报，便于排查后端数据格式或网络异常。

## 详细集成步骤

### 步骤1：选择支付渠道

根据业务需求，选择需要集成的支付渠道：

| 支付渠道 | 模块名称 | 第三方SDK | 适用场景 |
|---------|---------|----------|---------|
| 微信支付 | payment-channel-wechat | 微信开放SDK | 社交、电商、O2O |
| 支付宝 | payment-channel-alipay | 支付宝SDK | 电商、生活服务 |
| 银联支付 | payment-channel-union | 银联SDK | 金融、企业支付 |

### 步骤2：添加Gradle依赖

在app模块的`build.gradle`中添加依赖：

```gradle
dependencies {
    // 核心SDK（必须）
    implementation 'com.payment:payment-core:1.0.0'
    
    // 根据需要添加支付渠道
    implementation 'com.payment:payment-channel-wechat:1.0.0'
    implementation 'com.payment:payment-channel-alipay:1.0.0'
}
```

### 步骤3：配置AndroidManifest.xml

添加必要的权限和Activity声明：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.app">

    <!-- 网络权限 -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application>
        <!-- 如果使用微信支付，需要添加微信的回调Activity -->
        <activity
            android:name=".wxapi.WXPayEntryActivity"
            android:exported="true"
            android:launchMode="singleTop" />

        <!-- 如果使用支付宝，需要添加支付宝的Activity -->
        <activity
            android:name="com.alipay.sdk.app.PayTask"
            android:exported="false"
            android:screenOrientation="behind" />
    </application>

</manifest>
```

### 步骤4：初始化配置

创建`PaymentConfig`并初始化SDK：

```kotlin
val config = PaymentConfig.Builder()
    .setAppId("your_app_id")              // 应用ID
    .setBusinessLine("retail")             // 业务线标识
    .setApiBaseUrl("https://api.example.com") // API基础URL
    .setDebugMode(BuildConfig.DEBUG)       // 调试模式
    .setNetworkTimeout(30)                 // 网络超时（秒）
    .setAutoCheckAppInstall(true)          // 自动检测APP安装
    .setAutoQueryResult(true)              // 自动查询支付结果
    .setMaxQueryRetries(3)                 // 最大查询重试次数
    .setQueryIntervalMs(2000)              // 查询间隔（毫秒）
    .setQueryTimeoutMs(10000)              // 查询超时时间（毫秒）
    .build()

PaymentSDK.init(application, config)
```

**参数说明：**

- `appId`: 后端分配的应用唯一标识
- `businessLine`: 业务线标识，用于区分不同业务的支付渠道配置
- `apiBaseUrl`: 后端API的基础URL
- `debugMode`: 是否开启调试模式，建议使用`BuildConfig.DEBUG`
- `networkTimeout`: 网络请求超时时间（秒）
- `maxQueryRetries`: 查询结果最大重试次数（默认3次）
- `queryIntervalMs`: 每次查询的间隔时间（默认2000ms）
- `queryTimeoutMs`: 查询超时时间（默认10000ms），超时后返回Processing状态

**注意：** SDK已默认启用自动APP安装检测和支付成功后自动查询后端结果功能，无需额外配置。

### 步骤5：注册支付渠道

在Application中注册已集成的支付渠道：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        initPaymentSDK()
        registerPaymentChannels()
    }
    
    private fun registerPaymentChannels() {
        // 方式1：逐个注册
        PaymentSDK.registerChannel(WeChatPayChannel())
        PaymentSDK.registerChannel(AlipayChannel())
        
        // 方式2：批量注册
        PaymentSDK.registerChannels(listOf(
            WeChatPayChannel(),
            AlipayChannel(),
            UnionPayChannel()
        ))
    }
}
```

## 使用场景

### 场景1：显示支付渠道选择弹窗（推荐）

最常用的方式，由SDK自动处理渠道选择和支付流程：

```kotlin
PaymentSDK.showPaymentSheet(
    activity = this,
    orderId = orderId,
    amount = amount,
    extraParams = emptyMap(), // 可选的额外参数
    businessLine = "retail", // 可选，默认使用配置中的业务线
    onPaymentResult = { result ->
        // SDK自动完成支付并返回结果
        handlePaymentResult(result)
    },
    onCancelled = {
        // 用户取消选择
        Toast.makeText(this, "已取消支付", Toast.LENGTH_SHORT).show()
    }
)

private fun handlePaymentResult(result: PaymentResult) {
    when (result) {
        is PaymentResult.Success -> {
            Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
        }
        is PaymentResult.Failed -> {
            Toast.makeText(this, "支付失败: ${result.errorMessage}", Toast.LENGTH_SHORT).show()
        }
        is PaymentResult.Cancelled -> {
            Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
        }
        is PaymentResult.Processing -> {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        }
    }
}
```

### 场景2：直接使用指定渠道支付

适用于已经确定支付方式的场景（如用户设置了默认支付方式）。

**SDK会自动处理支付流程：**
1. 调用支付渠道SDK
2. 自动查询后端支付结果
3. 返回最终的支付状态

```kotlin
lifecycleScope.launch {
    val result = PaymentSDK.payWithChannel(
        channelId = "wechat_pay",
        context = this@Activity,
        orderId = orderId,
        amount = amount,
        extraParams = emptyMap()
    )
    
    // SDK已自动查询后端结果，直接处理最终状态
    handlePaymentResult(result)
}
```

### 场景3：自定义渠道选择UI

不使用SDK提供的半屏弹窗，自己实现选择界面：

```kotlin
// 1. 获取可用渠道
val channels = PaymentSDK.getAvailableChannels(this)

// 2. 展示自定义UI
showCustomChannelSelector(channels) { selectedChannel ->
    // 3. 执行支付
    lifecycleScope.launch {
        val result = selectedChannel.pay(...)
        handlePaymentResult(result)
    }
}
```

### 场景4：查询渠道状态

查询支付渠道的注册和可用状态：

```kotlin
// 获取所有已注册的渠道
val registeredChannels = PaymentSDK.getRegisteredChannels()
println("已注册 ${registeredChannels.size} 个支付渠道")

// 获取可用的渠道（已注册且APP已安装）
val availableChannels = PaymentSDK.getAvailableChannels(this)
println("可用 ${availableChannels.size} 个支付渠道")

// 检查特定渠道是否可用
val wechatChannel = registeredChannels.find { it.channelId == "wechat_pay" }
if (wechatChannel != null && wechatChannel.isAppInstalled(this)) {
    println("微信支付可用")
}
```

## 支付流程完整示例

```kotlin
class OrderPaymentActivity : AppCompatActivity() {
    
    private lateinit var orderId: String
    private lateinit var orderAmount: BigDecimal
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_payment)
        
        // 获取订单信息
        orderId = intent.getStringExtra("order_id") ?: ""
        orderAmount = BigDecimal(intent.getStringExtra("amount") ?: "0")
        
        // 设置支付按钮点击事件
        findViewById<Button>(R.id.btn_pay).setOnClickListener {
            startPayment()
        }
    }
    
    /**
     * 发起支付
     */
    private fun startPayment() {
        PaymentSDK.showPaymentSheet(
            activity = this,
            orderId = orderId,
            amount = orderAmount,
            onPaymentResult = { result ->
                // SDK自动处理支付流程并返回结果
                handlePaymentResult(result)
            },
            onCancelled = {
                Toast.makeText(this, "已取消支付", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    /**
     * 处理支付结果
     * 
     * SDK已自动查询后端结果，此处只需处理最终状态
     */
    private fun handlePaymentResult(result: PaymentResult) {
        when (result) {
            is PaymentResult.Success -> {
                // 支付成功（SDK已验证后端结果）
                showSuccessDialog(result.transactionId)
                navigateToSuccessPage()
            }
            
            is PaymentResult.Failed -> {
                // 支付失败
                showError("支付失败\n${result.errorMessage}")
            }
            
            is PaymentResult.Cancelled -> {
                // 用户取消
                Toast.makeText(this, "已取消支付", Toast.LENGTH_SHORT).show()
            }
            
            is PaymentResult.Processing -> {
                // 支付处理中（SDK查询超时）
                // 此时支付可能成功也可能失败，需要稍后查询
                showWarning(result.message)
                navigateToOrderListPage()
            }
        }
    }
    
    private fun showLoading(message: String) {
        // 显示加载对话框
    }
    
    private fun hideLoading() {
        // 隐藏加载对话框
    }
    
    private fun showSuccessDialog(transactionId: String) {
        // 显示支付成功对话框
    }
    
    private fun showError(message: String) {
        // 显示错误提示
    }
    
    private fun showWarning(message: String) {
        // 显示警告提示
    }
    
    private fun navigateToSuccessPage() {
        // 跳转到支付成功页面
    }
}
```

## 不同APP接入示例

### 电商APP（需要微信、支付宝、银联）

```gradle
dependencies {
    implementation 'com.payment:payment-core:1.0.0'
    implementation 'com.payment:payment-channel-wechat:1.0.0'
    implementation 'com.payment:payment-channel-alipay:1.0.0'
    implementation 'com.payment:payment-channel-union:1.0.0'
}
```

```kotlin
class EcommerceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("ecommerce_app_001")
            .setBusinessLine("ecommerce")
            .setApiBaseUrl("https://api.ecommerce.com")
            .build()
        
        PaymentSDK.init(this, config)
        
        PaymentSDK.registerChannels(listOf(
            WeChatPayChannel(),
            AlipayChannel(),
            UnionPayChannel()
        ))
    }
}
```

### O2O APP（只需要微信、支付宝）

```gradle
dependencies {
    implementation 'com.payment:payment-core:1.0.0'
    implementation 'com.payment:payment-channel-wechat:1.0.0'
    implementation 'com.payment:payment-channel-alipay:1.0.0'
}
```

```kotlin
class O2OApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("o2o_app_001")
            .setBusinessLine("o2o")
            .setApiBaseUrl("https://api.o2o.com")
            .build()
        
        PaymentSDK.init(this, config)
        
        PaymentSDK.registerChannels(listOf(
            WeChatPayChannel(),
            AlipayChannel()
        ))
    }
}
```

### 企业APP（只需要银联）

```gradle
dependencies {
    implementation 'com.payment:payment-core:1.0.0'
    implementation 'com.payment:payment-channel-union:1.0.0'
}
```

```kotlin
class EnterpriseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("enterprise_app_001")
            .setBusinessLine("enterprise")
            .setApiBaseUrl("https://api.enterprise.com")
            .build()
        
        PaymentSDK.init(this, config)
        
        PaymentSDK.registerChannel(UnionPayChannel())
    }
}
```

## 常见问题

### Q1: 如何处理支付回调？

**A:** 支付结果通过`PaymentResult`同步返回，不需要额外处理回调。第三方SDK的回调由支付渠道SDK内部处理。

### Q2: 如何验证支付结果？

**A:** SDK已自动处理支付结果验证（当`autoQueryResult=true`时）：

```kotlin
when (result) {
    is PaymentResult.Success -> {
        // SDK已自动查询后端确认支付成功
        // 可以直接跳转到成功页面
        navigateToSuccessPage()
    }
    is PaymentResult.Processing -> {
        // SDK查询超时，支付状态待确认
        // 建议跳转到订单列表，稍后查询
        navigateToOrderList()
    }
}
```

如果关闭了自动查询（`autoQueryResult=false`），则需要手动查询：

```kotlin
when (result) {
    is PaymentResult.Success -> {
        // 手动查询后端确认
        lifecycleScope.launch {
            val status = PaymentSDK.getApiService().queryOrderStatus(orderId)
            if (status.isSuccess && status.getOrNull()?.status == "paid") {
                // 确认支付成功
                navigateToSuccessPage()
            }
        }
    }
}
```

### Q3: 支付渠道弹窗显示为空怎么办？

**A:** 可能的原因：
1. 没有注册任何支付渠道
2. 注册的渠道对应的APP都未安装
3. 后端返回的渠道配置为空

检查方法：
```kotlin
val registered = PaymentSDK.getRegisteredChannels()
val available = PaymentSDK.getAvailableChannels(this)
println("已注册: ${registered.size}, 可用: ${available.size}")
```

### Q4: 如何添加自定义支付渠道？

**A:** 实现`IPaymentChannel`接口即可：

```kotlin
class CustomPayChannel : IPaymentChannel {
    override val channelId = "custom_pay"
    override val channelName = "自定义支付"
    override val channelIcon = R.drawable.ic_custom
    override val requiresApp = false
    override val packageName = null
    
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 实现支付逻辑
        // 例如：打开WebView、调起第三方APP等
        return PaymentResult.Success(orderId)
    }
}

// 注册
PaymentSDK.registerChannel(CustomPayChannel())
```

### Q5: 如何自定义UI？

**A:** 不调用`showPaymentSheet()`，自己获取渠道列表并实现UI：

```kotlin
val channels = PaymentSDK.getAvailableChannels(this)

// 使用自定义UI展示channels
customUI.showChannels(channels) { selectedChannel ->
    // 执行支付
    selectedChannel.pay(...)
}
```

## 自动查询支付结果（重要）

### 功能说明

SDK支持在支付渠道返回成功后，**自动查询后端支付结果**，确保返回给调用方的是最终的支付状态。

**为什么需要自动查询？**
- 第三方支付SDK返回成功，不代表支付真正成功
- 后端可能还未收到第三方的异步通知
- 需要查询后端确认支付状态，避免订单状态不一致

### 工作流程

```
1. 调用 PaymentSDK.payWithChannel()
   ↓
2. SDK调起支付渠道（微信/支付宝等）
   ↓
3. 支付渠道返回成功
   ↓
4. SDK自动查询后端结果（轮询）
   ↓
5. 返回最终支付状态给调用方
```

### 配置参数

```kotlin
val config = PaymentConfig.Builder()
    // 是否启用自动查询（默认true，推荐开启）
    .setAutoQueryResult(true)
    
    // 最大重试次数（默认3次）
    // 支付渠道返回成功后，最多查询3次后端
    .setMaxQueryRetries(3)
    
    // 查询间隔（默认2000ms）
    // 每次查询之间等待2秒
    .setQueryIntervalMs(2000)
    
    // 查询总超时时间（默认10000ms）
    // 超过10秒仍未得到明确结果，返回Processing状态
    .setQueryTimeoutMs(10000)
    
    .build()
```

### 支付结果状态

| 状态 | 说明 | 调用方应如何处理 |
|------|------|-----------------|
| `PaymentResult.Success` | 支付成功（SDK已确认后端支付成功） | 跳转到成功页面 |
| `PaymentResult.Failed` | 支付失败 | 显示失败提示 |
| `PaymentResult.Cancelled` | 用户取消支付 | 显示取消提示 |
| `PaymentResult.Processing` | 支付处理中（SDK查询超时） | 提示用户稍后查询订单 |

### 使用示例

```kotlin
lifecycleScope.launch {
    val result = PaymentSDK.payWithChannel(
        channelId = "wechat_pay",
        context = this@Activity,
        orderId = orderId,
        amount = amount
    )
    
    when (result) {
        is PaymentResult.Success -> {
            // SDK已确认后端支付成功
            Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
            navigateToSuccessPage()
        }
        
        is PaymentResult.Failed -> {
            Toast.makeText(this, "支付失败", Toast.LENGTH_SHORT).show()
        }
        
        is PaymentResult.Cancelled -> {
            Toast.makeText(this, "已取消支付", Toast.LENGTH_SHORT).show()
        }
        
        is PaymentResult.Processing -> {
            // SDK查询超时，支付可能成功也可能失败
            // 建议跳转到订单列表，用户可稍后查看订单状态
            Toast.makeText(
                this, 
                "支付处理中，请在订单列表查看结果", 
                Toast.LENGTH_LONG
            ).show()
            navigateToOrderList()
        }
    }
}
```

### 关闭自动查询

如果不需要自动查询功能，可以关闭：

```kotlin
val config = PaymentConfig.Builder()
    .setAutoQueryResult(false)  // 关闭自动查询
    .build()
```

关闭后，SDK将直接返回支付渠道的结果，**调用方需要自己查询后端确认支付状态**：

```kotlin
when (result) {
    is PaymentResult.Success -> {
        // 手动查询后端
        lifecycleScope.launch {
            val apiService = PaymentSDK.getApiService()
            val statusResult = apiService.queryOrderStatus(orderId)
            
            if (statusResult.isSuccess) {
                val orderStatus = statusResult.getOrNull()
                when (orderStatus?.status) {
                    "paid" -> {
                        // 支付成功
                        navigateToSuccessPage()
                    }
                    "pending" -> {
                        // 待支付
                        showPendingMessage()
                    }
                    else -> {
                        // 其他状态
                        handleOtherStatus()
                    }
                }
            }
        }
    }
}
```

### 最佳实践

1. **推荐开启自动查询**：大多数场景下应该启用`autoQueryResult`，简化调用方逻辑

2. **合理设置重试参数**：
   - 网络良好：`maxQueryRetries=3`，`queryIntervalMs=2000`
   - 网络较差：`maxQueryRetries=5`，`queryIntervalMs=3000`

3. **处理Processing状态**：
   - 提示用户"支付处理中"
   - 跳转到订单列表页
   - 提供"刷新订单状态"功能

4. **后端优化**：确保订单状态查询接口响应快速（< 500ms）

## 注意事项

1. **必须在Application中初始化**：确保在Application的`onCreate()`中调用`PaymentSDK.init()`

2. **注册渠道顺序**：先初始化SDK，再注册支付渠道

3. **生命周期管理**：使用`lifecycleScope.launch`执行支付，避免内存泄漏

4. **验证支付结果**：启用自动查询后，SDK会自动验证；关闭后需手动验证

5. **异常处理**：支付过程可能抛出异常，使用try-catch捕获

6. **测试环境**：开发时设置`setDebugMode(true)`，生产环境设置为`false`

7. **混淆配置**：如果开启了ProGuard，参考文档添加混淆规则

## ProGuard配置

如果项目开启了代码混淆，添加以下规则：

```proguard
# PaymentSDK
-keep class com.payment.core.PaymentSDK { *; }
-keep class com.payment.core.PaymentResult { *; }
-keep class com.payment.core.config.PaymentConfig { *; }
-keep interface com.payment.core.channel.IPaymentChannel { *; }
-keep class * implements com.payment.core.channel.IPaymentChannel { *; }

# 第三方支付SDK混淆规则
# 微信支付
-keep class com.tencent.mm.opensdk.** { *; }

# 支付宝
-keep class com.alipay.android.app.IAlixPay{*;}
-keep class com.alipay.android.app.IAlixPay$Stub{*;}
-keep class com.alipay.android.app.IRemoteServiceCallback{*;}
-keep class com.alipay.android.app.IRemoteServiceCallback$Stub{*;}
-keep class com.alipay.sdk.app.PayTask{ public *;}
-keep class com.alipay.sdk.app.AuthTask{ public *;}

# 银联支付
-keep class com.unionpay.** { *; }
```

## 下一步

- 查看[API文档](API.md)了解详细的API说明
- 查看[架构设计文档](ARCHITECTURE.md)了解SDK的架构设计
- 查看示例代码`sample`模块
