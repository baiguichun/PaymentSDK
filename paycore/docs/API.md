# 聚合支付SDK API文档

## 核心类

### PaymentSDK

SDK的主入口类，提供初始化、渠道注册和支付功能。

#### init()

初始化SDK。

```kotlin
fun init(app: Application, config: PaymentConfig)
```

**参数：**
- `app`: Application实例
- `config`: SDK配置对象

**示例：**
```kotlin
val config = PaymentConfig.Builder()
    .setAppId("your_app_id")
    .setBusinessLine("retail")
    .setApiBaseUrl("https://api.example.com")
    .setDebugMode(true)
    .build()

PaymentSDK.init(application, config)
```

---

#### registerChannel()

注册支付渠道。

```kotlin
fun registerChannel(channel: IPaymentChannel)
```

**参数：**
- `channel`: 支付渠道实例

**示例：**
```kotlin
PaymentSDK.registerChannel(WeChatPayChannel())
PaymentSDK.registerChannel(AlipayChannel())
```

---

#### registerChannels()

批量注册支付渠道。

```kotlin
fun registerChannels(channels: List<IPaymentChannel>)
```

**参数：**
- `channels`: 支付渠道列表

**示例：**
```kotlin
PaymentSDK.registerChannels(listOf(
    WeChatPayChannel(),
    AlipayChannel(),
    UnionPayChannel()
))
```

---

#### showPaymentSheet()

显示支付渠道选择半屏弹窗。用户选择渠道后，SDK自动调起支付、监听用户返回、查询后端结果。

> 💡 支持任何类型的Activity，自动完成整个支付流程

```kotlin
fun showPaymentSheet(
    activity: Activity,  // 支持任何类型的Activity
    orderId: String,
    amount: BigDecimal,
    extraParams: Map<String, Any> = emptyMap(),
    businessLine: String? = null,
    onPaymentResult: (PaymentResult) -> Unit,  // ✅ v2.0: 直接返回支付结果
    onCancelled: () -> Unit
)
```

**参数：**
- `activity`: Activity实例（支持任何类型：Activity、AppCompatActivity、ComponentActivity）
- `orderId`: 订单ID
- `amount`: 支付金额
- `extraParams`: 额外参数（可选），会传递给支付渠道
- `businessLine`: 业务线标识（可选，默认使用配置中的业务线）
- `onPaymentResult`: 支付结果回调（SDK已完成支付并查询结果）
- `onCancelled`: 用户取消选择的回调

**工作流程：**
1. 从后端获取可用支付渠道
2. 显示半屏弹窗供用户选择
3. 用户选择渠道后自动调起支付
4. 启动透明Activity监听生命周期
5. 用户从第三方APP返回后自动查询结果
6. 通过 `onPaymentResult` 返回最终结果

**示例：**
```kotlin
PaymentSDK.showPaymentSheet(
    activity = this,
    orderId = "ORDER123",
    amount = BigDecimal("99.99"),
    onPaymentResult = { result ->
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
    },
    onCancelled = {
        Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show()
    }
)
```

---

#### payWithChannel() ⭐️

直接使用指定渠道发起支付（不显示选择弹窗）。

**SDK会自动：**
1. 为订单加锁，阻止同一订单重复支付（锁会在 `orderLockTimeoutMs` 后自动释放）
2. 校验渠道是否已注册且可用（需要APP时会检查安装状态）
3. 启动透明Activity监听用户从第三方APP返回
4. 返回后按 `maxQueryRetries` / `queryIntervalMs` / `queryTimeoutMs` 查询后端并返回最终 `PaymentResult`

```kotlin
fun payWithChannel(
    channelId: String,
    context: Context,
    orderId: String,
    amount: BigDecimal,
    extraParams: Map<String, Any> = emptyMap(),
    onResult: (PaymentResult) -> Unit
)
```

**参数：**
- `channelId`: 支付渠道ID
- `context`: Context实例
- `orderId`: 订单ID
- `amount`: 支付金额
- `extraParams`: 额外参数（传给渠道实现，例如预支付信息）
- `onResult`: 支付结果回调

**示例：**
```kotlin
PaymentSDK.payWithChannel(
    channelId = "wechat_pay",
    context = this,
    orderId = "ORDER123",
    amount = BigDecimal("99.99"),
    onResult = { result ->
        when (result) {
            is PaymentResult.Success -> {
                Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
            }
            is PaymentResult.Failed -> {
                Toast.makeText(this, "支付失败", Toast.LENGTH_SHORT).show()
            }
            is PaymentResult.Cancelled -> {
                Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
            }
            is PaymentResult.Processing -> {
                Toast.makeText(this, "支付处理中", Toast.LENGTH_SHORT).show()
            }
        }
    }
)
```

> 同一订单的并发调用会被订单锁拦截并直接回调 `PaymentResult.Failed`。

---

#### queryOrderStatus() 🆕

手动查询订单支付状态（v2.0新增）。

```kotlin
suspend fun queryOrderStatus(orderId: String): PaymentResult
```

**参数：**
- `orderId`: 订单ID

**返回：**
- `PaymentResult`: 支付结果

**使用场景：**
1. 用户主动刷新订单状态
2. 订单列表中查询订单状态
3. 支付返回 `Processing` 状态后主动查询

**示例：**
```kotlin
lifecycleScope.launch {
    val result = PaymentSDK.queryOrderStatus("ORDER123")
    when (result) {
        is PaymentResult.Success -> {
            updateOrderStatus("已支付")
        }
        is PaymentResult.Failed -> {
            updateOrderStatus("支付失败")
        }
        is PaymentResult.Cancelled -> {
            updateOrderStatus("已取消")
        }
        is PaymentResult.Processing -> {
            updateOrderStatus("处理中")
        }
    }
}
```

---

#### getRegisteredChannels()

获取所有已注册的支付渠道。

```kotlin
fun getRegisteredChannels(): List<IPaymentChannel>
```

**返回：**
- 已注册的支付渠道列表

**示例：**
```kotlin
val channels = PaymentSDK.getRegisteredChannels()
channels.forEach { channel ->
    println("${channel.channelName} (${channel.channelId})")
}
```

---

#### getAvailableChannels()

获取可用的支付渠道（已注册且APP已安装）。

```kotlin
fun getAvailableChannels(context: Context): List<IPaymentChannel>
```

**参数：**
- `context`: Context实例

**返回：**
- 可用的支付渠道列表

**示例：**
```kotlin
val availableChannels = PaymentSDK.getAvailableChannels(this)
println("可用渠道数: ${availableChannels.size}")
```

---

#### isOrderPaying()

检查订单是否处于支付中（是否被订单锁占用）。

```kotlin
fun isOrderPaying(orderId: String): Boolean
```

**返回：**
- `true` 表示订单正在支付（已被锁定）
- `false` 表示未占用锁

---

#### cancelPayment()

取消指定订单的支付（释放订单锁）。

```kotlin
fun cancelPayment(orderId: String): Boolean
```

**返回：**
- `true` 表示存在锁并已释放
- `false` 表示订单未被锁定

---

#### getPaymentStatus()

获取当前支付/查询的调试信息。

```kotlin
fun getPaymentStatus(): String
```

---

#### shutdown()

关闭SDK并释放资源（清理订单锁与查询协程）。可在 `Application.onTerminate()` 中调用。

```kotlin
fun shutdown()
```

---

## 配置类

### PaymentConfig

SDK配置类。

#### Builder

使用Builder模式构建配置。

```kotlin
class Builder {
    fun setAppId(appId: String): Builder
    fun setBusinessLine(businessLine: String): Builder
    fun setApiBaseUrl(url: String): Builder
    fun setDebugMode(debug: Boolean): Builder
    fun setNetworkTimeout(timeout: Long): Builder        // 预留配置
    fun setInitialQueryDelay(delayMs: Long): Builder     // 预留配置
    fun setMaxQueryRetries(retries: Int): Builder
    fun setQueryIntervalMs(intervalMs: Long): Builder
    fun setQueryTimeoutMs(timeoutMs: Long): Builder
    fun setOrderLockTimeoutMs(timeoutMs: Long): Builder
    fun build(): PaymentConfig
}
```

**配置参数说明：**

| 参数 | 类型 | 默认值 | 说明 |
|-----|------|--------|------|
| `appId` | String | 必填 | 应用ID |
| `businessLine` | String | 必填 | 业务线标识 |
| `apiBaseUrl` | String | 必填 | API基础URL |
| `debugMode` | Boolean | false | 调试模式 |
| `networkTimeout` | Long | 30 | 预留配置，当前实现使用固定10s HTTP超时 |
| `initialQueryDelayMs` | Long | 3000 | 自动查询前的等待时间（毫秒） |
| `maxQueryRetries` | Int | 3 | 最大查询重试次数 |
| `queryIntervalMs` | Long | 2000 | 查询间隔（毫秒） |
| `queryTimeoutMs` | Long | 10000 | 查询超时时间（毫秒） |
| `orderLockTimeoutMs` | Long | 300000 | 订单锁超时时间（毫秒），默认5分钟，超过此时间自动释放锁 |

**示例：**
```kotlin
val config = PaymentConfig.Builder()
    .setAppId("app_001")
    .setBusinessLine("ecommerce")
    .setApiBaseUrl("https://api.example.com")
    .setDebugMode(BuildConfig.DEBUG)
    .setMaxQueryRetries(3)
    .setQueryIntervalMs(2000)
    .setQueryTimeoutMs(10000)
    .setOrderLockTimeoutMs(300000)
    .build()
```

---

## 接口

### IPaymentChannel

支付渠道接口，所有具体的支付渠道SDK需要实现此接口。

#### 属性

```kotlin
interface IPaymentChannel {
    val channelId: String          // 渠道唯一标识
    val channelName: String        // 渠道显示名称
    val channelIcon: Int           // 渠道图标资源ID
    val requiresApp: Boolean       // 是否需要第三方APP
    val packageName: String?       // 第三方APP包名
    val priority: Int              // 渠道优先级（默认0）
}
```

#### pay()

执行支付（普通函数）。

> ✨ v2.0 变更：从 `suspend fun` 改为普通 `fun`

```kotlin
fun pay(
    context: Context,
    orderId: String,
    amount: BigDecimal,
    extraParams: Map<String, Any> = emptyMap()
): PaymentResult
```

**参数：**
- `context`: Context实例
- `orderId`: 订单ID
- `amount`: 支付金额
- `extraParams`: 额外参数（如预支付信息等）

**返回：**
- `PaymentResult`: 支付结果
  - 对于第三方APP支付（微信/支付宝），返回 `Success` 表示成功调起支付APP
  - 对于网络支付，返回实际支付结果
  - SDK会通过 `PaymentLifecycleActivity` 自动查询实际结果

**说明：**
- ✅ v2.0: 改为普通函数（非suspend）
- 调起第三方APP后立即返回
- 实际支付结果由 `PaymentLifecycleActivity` 监听用户返回并查询

**实现示例：**
```kotlin
class WeChatPayChannel : IPaymentChannel {
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 调起微信APP
        val req = PayReq()
        req.appId = extraParams["appId"] as String
        req.partnerId = extraParams["partnerId"] as String
        // ... 设置其他参数
        
        val success = IWXAPI.sendReq(req)
        
        return if (success) {
            PaymentResult.Success(orderId)  // 成功调起
        } else {
            PaymentResult.Failed("调起微信失败")
        }
    }
}
```

---

#### isAppInstalled()

检查对应的APP是否已安装。

```kotlin
fun isAppInstalled(context: Context): Boolean
```

**参数：**
- `context`: Context实例

**返回：**
- `Boolean`: true表示已安装，false表示未安装

---

#### getSupportedFeatures()

获取渠道支持的功能。

```kotlin
fun getSupportedFeatures(): List<PaymentFeature>
```

**返回：**
- 支持的功能列表

---

## 数据类

### PaymentResult

支付结果封装类。

#### Success

支付成功。

```kotlin
data class Success(val transactionId: String) : PaymentResult()
```

**属性：**
- `transactionId`: 交易流水号

---

#### Failed

支付失败。

```kotlin
data class Failed(
    val errorMessage: String,
    val errorCode: String? = null
) : PaymentResult()
```

**属性：**
- `errorMessage`: 错误信息
- `errorCode`: 错误码（可选）

---

#### Cancelled

用户取消支付。

```kotlin
object Cancelled : PaymentResult()
```

---

#### Processing

支付处理中（SDK查询超时）。

```kotlin
data class Processing(val message: String) : PaymentResult()
```

**属性：**
- `message`: 提示信息

**说明：**

SDK默认会在支付成功后自动查询后端结果，如果查询超时或达到最大重试次数仍未得到明确结果，会返回此状态。

此时支付可能成功也可能失败，调用方应：
- 提示用户"支付处理中"
- 引导用户查看订单列表
- 提供"刷新订单状态"功能

---

## 工具类

### AppInstallChecker

APP安装检测工具类。

#### isPackageInstalled()

检查指定包名的APP是否已安装。

```kotlin
fun isPackageInstalled(context: Context, packageName: String): Boolean
```

**参数：**
- `context`: Context实例
- `packageName`: APP包名

**返回：**
- `Boolean`: true表示已安装，false表示未安装

**示例：**
```kotlin
val isWeChatInstalled = AppInstallChecker.isPackageInstalled(
    context,
    AppInstallChecker.CommonPaymentApps.WECHAT
)
```

---

#### checkMultipleApps()

批量检查多个APP的安装状态。

```kotlin
fun checkMultipleApps(
    context: Context,
    packageNames: List<String>
): Map<String, Boolean>
```

**参数：**
- `context`: Context实例
- `packageNames`: 包名列表

**返回：**
- `Map<String, Boolean>`: 包名到安装状态的映射

**示例：**
```kotlin
val installStatus = AppInstallChecker.checkMultipleApps(
    context,
    listOf(
        AppInstallChecker.CommonPaymentApps.WECHAT,
        AppInstallChecker.CommonPaymentApps.ALIPAY
    )
)
```

---

#### CommonPaymentApps

常用支付APP包名常量。

```kotlin
object CommonPaymentApps {
    const val WECHAT = "com.tencent.mm"
    const val ALIPAY = "com.eg.android.AlipayGphone"
    const val UNION_PAY = "com.unionpay"
    const val QQ_WALLET = "com.tencent.mobileqq"
    const val JD_PAY = "com.jd.lib.pay"
}
```

---

## 枚举

### PaymentFeature

支付功能枚举。

```kotlin
enum class PaymentFeature {
    BASIC_PAY,      // 基础支付
    REFUND,         // 退款
    QUERY_ORDER,    // 订单查询
    QUICK_PAY,      // 免密支付
    INSTALLMENT     // 分期支付
}
```

---

## 渠道实现示例

> 仓库未内置具体的微信/支付宝/银联实现，下列代码仅用于演示 `IPaymentChannel` 的实现方式。

### WeChatPayChannel

微信支付渠道。

```kotlin
class WeChatPayChannel : IPaymentChannel {
    override val channelId = "wechat_pay"
    override val channelName = "微信支付"
    override val requiresApp = true
    override val packageName = "com.tencent.mm"
    override val priority = 100
}
```

**所需extraParams：**
- `prepay_id`: 预支付交易会话ID
- `partner_id`: 商户号（可选）
- `timestamp`: 时间戳（可选）
- `nonce_str`: 随机字符串（可选）
- `sign`: 签名（可选）

---

### AlipayChannel

支付宝支付渠道。

```kotlin
class AlipayChannel : IPaymentChannel {
    override val channelId = "alipay"
    override val channelName = "支付宝"
    override val requiresApp = true
    override val packageName = "com.eg.android.AlipayGphone"
    override val priority = 90
}
```

**所需extraParams：**
- `order_info`: 完整的订单信息字符串

---

### UnionPayChannel

银联支付渠道。

```kotlin
class UnionPayChannel : IPaymentChannel {
    override val channelId = "union_pay"
    override val channelName = "银联支付"
    override val requiresApp = true
    override val packageName = "com.unionpay"
    override val priority = 80
}
```

**所需extraParams：**
- `tn`: 交易流水号

---

## 异常处理

- SDK不会向外抛异常，错误通过 `PaymentResult.Failed` 返回（包含网络/解析等错误）
- `errorMessage` 包含具体原因；`errorCode` 由渠道或后端定义（核心模块未内置固定枚举）
- 查询超时会返回 `PaymentResult.Processing`，可提示用户稍后在订单列表中查看
- 透明 Activity 若被系统回收且未能正常结束，会兜底回调 `PaymentResult.Failed("支付流程已中断，请重试")` 并清理回调，避免悬挂

---

## 线程安全

- 订单级锁：`PaymentLockManager.tryLockOrder()` 防止同一订单重复支付，超时自动释放
- 查询去重：同一订单共享同一个查询协程，避免重复请求
- UI回调：支付结果由透明Activity在主线程回调，便于直接更新界面

---

## 最佳实践

### 1. 在Application中初始化

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PaymentSDK.init(
            this,
            PaymentConfig.Builder()
                .setAppId("your_app_id")
                .setBusinessLine("retail")
                .setApiBaseUrl("https://api.example.com")
                .build()
        )
    }
}
```

### 2. 使用回调版本处理结果

```kotlin
class MainActivity : AppCompatActivity() {
    private fun pay() {
        PaymentSDK.payWithChannel(
            channelId = "wechat_pay",
            context = this@MainActivity,
            orderId = "ORDER123",
            amount = BigDecimal("99.99"),
            onResult = { result ->
                when (result) {
                    is PaymentResult.Success -> {
                        Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
                    }
                    is PaymentResult.Cancelled -> {
                        Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
                    }
                    is PaymentResult.Processing -> {
                        Toast.makeText(this, "支付处理中，请稍后查看订单状态", Toast.LENGTH_SHORT).show()
                    }
                    is PaymentResult.Failed -> {
                        Toast.makeText(this, "支付失败: ${result.errorMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}
```

### 3. 防重复点击

```kotlin
if (PaymentSDK.isOrderPaying(orderId)) {
    Toast.makeText(this, "订单正在支付，请勿重复提交", Toast.LENGTH_SHORT).show()
    return
}
```

### 4. 处理降级场景

- `showPaymentSheet` 在拉取远端渠道失败时会自动回退到本地已注册且可用的渠道
- 如果需要中断流程，可调用 `cancelPayment(orderId)` 释放订单锁
