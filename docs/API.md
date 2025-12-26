# 聚合支付SDK API文档

> **版本**: v3.0.0  
> **架构**: Clean Architecture + 模块化  
> **最后更新**: 2025-11-24  
> **更新者**: guichunbai

---

## 📐 v3.0.0 架构说明

PaymentSDK v3.0采用Clean Architecture设计，分为6个模块：

| 模块 | 说明 | 位置 |
|------|------|------|
| **ui-kit** | SDK入口、UI组件 | `com.xiaobai.paycore.PaymentSDK` |
| **domain** | 业务逻辑层 | Repository接口、UseCases |
| **data** | 数据访问层 | Repository实现、ErrorMapper、DI |
| **network-security** | 网络与安全 | API Service、SecuritySigner |
| **channel-spi** | 渠道接口 | IPaymentChannel |
| **core** | 核心模型 | PaymentResult、PaymentErrorCode、PaymentConfig |

**依赖注入**: 使用Koin管理依赖，支持外部容器

**Repository模式**: 抽象数据访问，业务逻辑与实现分离

---

## 🆕 v3.0.0 新特性

### 1. 支持外部Koin容器

```kotlin
// 如果宿主APP已使用Koin
val koinApp = startKoin {
    androidContext(this@MyApplication)
    modules(appModule, paymentModule(config))
}

// 传入外部Koin容器
PaymentSDK.init(this, config, koinApp)
```

### 2. 内部依赖自动注入

SDK内部使用Koin自动管理依赖：

```kotlin
// PaymentSDK内部
private val repository: PaymentRepository by lazy { koin.get() }
private val useCases: PaymentUseCases by lazy { koin.get() }
private val errorMapper: PaymentErrorMapper by lazy { koin.get() }
```

### 3. Repository接口

业务逻辑通过Repository接口访问数据：

```kotlin
interface PaymentRepository {
    suspend fun fetchPaymentChannels(businessLine: String, appId: String): Result<List<PaymentChannelMeta>>
    suspend fun createPaymentOrder(...): Result<Map<String, Any>>
    suspend fun queryOrderStatus(orderId: String): Result<OrderStatusInfo>
    
    fun registerChannel(channel: IPaymentChannel)
    fun getChannel(channelId: String): IPaymentChannel?
    fun getAllChannels(): List<IPaymentChannel>
    fun getAvailableChannels(context: Context): List<IPaymentChannel>
}
```

### 4. UseCase封装

业务逻辑封装在独立的UseCase中：

```kotlin
// 获取支付渠道列表
class FetchChannelsUseCase(private val repository: PaymentRepository)

// 创建支付订单
class CreateOrderUseCase(private val repository: PaymentRepository)

// 查询订单状态
class QueryStatusUseCase(private val repository: PaymentRepository)
```

### 5. 错误映射集中化

所有错误映射逻辑集中在`PaymentErrorMapper`：

```kotlin
class PaymentErrorMapper {
    fun buildFailure(code: PaymentErrorCode, detail: String?): PaymentResult.Failed
    fun mapExceptionToFailed(throwable: Throwable?, defaultCode: PaymentErrorCode): PaymentResult.Failed
    fun mapExceptionToErrorCode(throwable: Throwable?, defaultCode: PaymentErrorCode): PaymentErrorCode
}
```

---

## 核心类

### PaymentSDK

SDK的主入口类，提供初始化、渠道注册和支付功能。

#### init()

初始化SDK（v3.0支持外部Koin容器）。

```kotlin
fun init(
    app: Application,
    config: PaymentConfig,
    externalKoinApp: KoinApplication? = null
)
```

**参数：**
- `app`: Application实例
- `config`: SDK配置对象
- `externalKoinApp`: 外部Koin容器（可选，v3.0新增）

**说明**:
- v3.0使用Koin进行依赖注入
- 如果宿主APP已使用Koin，可传入`externalKoinApp`共享容器
- SDK不会关闭外部容器

**示例1: 基础初始化**
```kotlin
val config = PaymentConfig.Builder()
    .setAppId("your_app_id")
    .setBusinessLine("retail")
    .setApiBaseUrl("https://api.example.com")
    .setDebugMode(true)
    .build()

PaymentSDK.init(application, config)
```

**示例2: 使用外部Koin容器（v3.0新增）**
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")
            .setApiBaseUrl("https://api.example.com")
            .build()
        
        // 启动Koin并共享容器
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
    onCancelled: () -> Unit
)
```

**参数：**
- `activity`: Activity实例（支持任何类型：Activity、AppCompatActivity、ComponentActivity）
- `orderId`: 订单ID
- `amount`: 支付金额
- `extraParams`: 额外参数（可选），会传递给支付渠道
- `businessLine`: 业务线标识（可选，默认使用配置中的业务线）
- `onCancelled`: 用户取消选择的回调

**工作流程：**
1. 从后端获取可用支付渠道
2. 显示半屏弹窗供用户选择
3. 用户选择渠道后自动调起支付
4. 基于进程生命周期监听前后台切换
5. 用户从第三方APP返回后自动查询结果（含兜底定时）
6. 最终结果通过 UI 层 ViewModel 状态分发（订阅状态处理）

**示例：**
```kotlin
PaymentSDK.showPaymentSheet(
    activity = this,
    orderId = "ORDER123",
    amount = BigDecimal("99.99"),
    onCancelled = { Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show() }
)
```

---

#### payWithChannel() ⭐️

直接使用指定渠道发起支付（不显示选择弹窗）。

**SDK会自动：**
1. 为订单加锁，阻止同一订单重复支付（锁会在 `orderLockTimeoutMs` 后自动释放，默认5分钟）
2. 校验渠道是否已注册且可用（需要APP时会检查安装状态）
3. 基于 `ProcessLifecycleOwner` 监听前后台切换（无需额外Activity）
4. 返回前台后固定延迟200ms（带兜底定时器），按 `maxQueryRetries` / `queryIntervalMs` / `queryTimeoutMs` 查询后端并返回最终 `PaymentResult`
5. 查询同一订单时自动去重,避免重复网络请求
6. 支付完成后自动释放订单锁

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

#### resumePendingPayment() 🆕

应用启动后，如果业务侧先从后端拿到“未完成的支付订单”（例如上次被系统回收），可调用该方法继续支付流程。内部沿用 `payWithChannel` 的校验、锁和生命周期监听，防止重复支付。

```kotlin
PaymentSDK.resumePendingPayment(
    context = appContext,
    orderId = pending.orderId,
    channelId = pending.channelId,
    amount = pending.amount,
    extraParams = pending.extraParams,
    onResult = { result ->
        // 根据业务需要处理：如通知、落库、刷新首页角标等
    }
)
```

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
    fun setSecurityConfig(config: SecurityConfig): Builder
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
| `networkTimeout` | Long | 30(秒) | 网络请求超时时间,实际使用时会转换为毫秒并限制在Int范围内 |
| `initialQueryDelayMs` | Long | 3000 | 预留配置(未使用),实际自动查询使用固定200ms延迟 |
| `maxQueryRetries` | Int | 3 | 最大查询重试次数 |
| `queryIntervalMs` | Long | 2000 | 查询间隔（毫秒） |
| `queryTimeoutMs` | Long | 10000 | 查询超时时间（毫秒） |
| `orderLockTimeoutMs` | Long | 300000 | 订单锁超时时间（毫秒），默认5分钟，超过此时间自动释放锁 |
| `securityConfig` | SecurityConfig | 默认关闭 | 安全配置：请求签名/验签、时间戳/随机数、防重放、证书Pinning |

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
    .setSecurityConfig(
        SecurityConfig(
            enableSignature = true,
            enableResponseVerification = true,
            signingSecret = "shared_secret_from_server",
            enableCertificatePinning = true,
            certificatePins = mapOf(
                "api.example.com" to listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            )
        )
    )
    .build()
```

### SecurityConfig

安全相关配置。

| 参数 | 类型 | 默认值 | 说明 |
|-----|------|--------|------|
| `enableSignature` | Boolean | false | 启用请求签名（HMAC-SHA256） |
| `signingSecret` | String? | null | 签名密钥，开启签名时必填 |
| `signatureHeader` | String | `X-Signature` | 请求签名头 |
| `timestampHeader` | String | `X-Timestamp` | 请求时间戳头（毫秒） |
| `nonceHeader` | String | `X-Nonce` | 请求随机数头 |
| `enableResponseVerification` | Boolean | false | 启用响应验签 |
| `serverSignatureHeader` | String | `X-Server-Signature` | 响应签名头 |
| `serverTimestampHeader` | String | `X-Server-Timestamp` | 响应时间戳头 |
| `maxServerClockSkewMs` | Long | 300000 | 允许的服务端时间偏差（毫秒） |
| `enableCertificatePinning` | Boolean | false | 启用 HTTPS 证书绑定 |
| `certificatePins` | Map<String, List<String>> | 空 | 证书指纹配置：host -> pins（如 `sha256/xxxx`） |

**签名/验签规范（默认实现）：**
- 请求 canonical string：`path + "\n" + sortedQuery + "\n" + body + "\n" + timestamp + "\n" + nonce`  
  其中 `sortedQuery` 为 key 升序、去除 null 的 `k=v&...`，`body` 为原始字符串（为空则空串）。
- 请求头：`X-Signature`（Base64(HMAC-SHA256)）、`X-Timestamp`（毫秒）、`X-Nonce`（16字节随机数Base64）。
- 可选响应验签：`path + "\n" + sortedQuery + "\n" + body + "\n" + serverTimestamp`，对比 `X-Server-Signature`。
- 防重放：依赖服务端校验时间戳/随机数及可选缓存窗口。

---

## 接口

### IPaymentChannel

支付渠道接口，所有具体的支付渠道SDK需要实现此接口。

#### 属性

```kotlin
interface IPaymentChannel {
    val channelId: String          // 渠道唯一标识
    val channelName: String        // 渠道显示名称

    // 渠道图标/优先级/是否需要第三方APP 由后端返回的 PaymentChannelMeta 提供
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
  - SDK会通过进程级生命周期监听自动查询实际结果

**说明：**
- ✅ v2.0: 改为普通函数（非suspend）
- 调起第三方APP后立即返回
- 实际支付结果由进程生命周期监听器（`ProcessLifecycleOwner`）触发查询

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

获取渠道支持的功能(可选实现)。

```kotlin
fun getSupportedFeatures(): List<PaymentFeature> {
    return listOf(PaymentFeature.BASIC_PAY)  // 默认实现
}
```

**返回：**
- 支持的功能列表,默认返回`[BASIC_PAY]`

---

## Clean Architecture层次

### domain层（业务领域层）

#### PaymentRepository（接口，内部使用）

数据访问抽象接口，定义在`domain`模块中。注册相关方法由SDK内部在初始化时通过渠道映射自动调用，业务侧无需手动注册。

```kotlin
interface PaymentRepository {
    // 网络请求相关
    suspend fun fetchPaymentChannels(businessLine: String, appId: String): Result<List<PaymentChannelMeta>>
    suspend fun createPaymentOrder(orderId: String, channelId: String, amount: String, extraParams: Map<String, Any>): Result<Map<String, Any>>
    suspend fun queryOrderStatus(orderId: String, paymentId: String? = null): Result<OrderStatusInfo>
    
    // 渠道管理相关（内部自动注册/查询）
    fun registerChannel(channel: IPaymentChannel)
    fun getChannel(channelId: String): IPaymentChannel?
    fun getAllChannels(): List<IPaymentChannel>
    fun getAvailableChannels(context: Context): List<IPaymentChannel>
    fun filterAvailableChannels(context: Context, channelIds: List<String>): List<IPaymentChannel>
}
```

**设计原则**:
- 只定义接口，不包含实现
- 业务逻辑依赖此接口，不依赖具体实现
- 便于Mock，易于测试
- 渠道实例由 SDK 在 pay() 触发时通过生成的工厂懒加载，UI 展示的渠道名/图标请使用后端返回的渠道元数据

#### UseCases（业务用例）

封装具体的业务逻辑。

**FetchChannelsUseCase** - 获取支付渠道列表
```kotlin
class FetchChannelsUseCase(private val repository: PaymentRepository) {
    suspend operator fun invoke(businessLine: String, appId: String): Result<List<PaymentChannelMeta>> {
        return repository.fetchPaymentChannels(businessLine, appId)
    }
}
```

**CreateOrderUseCase** - 创建支付订单
```kotlin
class CreateOrderUseCase(private val repository: PaymentRepository) {
    suspend operator fun invoke(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any>
    ): Result<Map<String, Any>> {
        return repository.createPaymentOrder(orderId, channelId, amount, extraParams)
    }
}
```

**QueryStatusUseCase** - 查询订单状态
```kotlin
class QueryStatusUseCase(private val repository: PaymentRepository) {
    suspend operator fun invoke(orderId: String): Result<OrderStatusInfo> {
        return repository.queryOrderStatus(orderId)
    }
}
```

**PaymentUseCases** - UseCase聚合
```kotlin
data class PaymentUseCases(
    val fetchChannels: FetchChannelsUseCase,
    val createOrder: CreateOrderUseCase,
    val queryStatus: QueryStatusUseCase
)
```

---

### data层（数据访问层）

#### PaymentRepositoryImpl

`PaymentRepository`接口的实现，位于`data`模块。

```kotlin
class PaymentRepositoryImpl(
    private val apiService: PaymentApiService,
    private val channelManager: PaymentChannelManager
) : PaymentRepository {
    
    override suspend fun fetchPaymentChannels(businessLine: String, appId: String) = runCatching {
        apiService.getPaymentChannels(businessLine, appId)
    }
    
    override suspend fun createPaymentOrder(...) = runCatching {
        apiService.createPaymentOrder(...)
    }
    
    override suspend fun queryOrderStatus(orderId: String, paymentId: String?) = runCatching {
        val response = apiService.queryOrderStatus(orderId, paymentId)
        OrderStatusInfo(...)
    }
    
    // 渠道管理委托给ChannelManager
    override fun registerChannel(channel: IPaymentChannel) {
        channelManager.registerChannel(channel)
    }
    
    override fun getChannel(channelId: String) = channelManager.getChannel(channelId)
}
```

#### PaymentErrorMapper

错误映射器，集中管理所有错误转换逻辑。

```kotlin
class PaymentErrorMapper {
    /**
     * 构建标准错误
     */
    fun buildFailure(code: PaymentErrorCode, detail: String? = null): PaymentResult.Failed {
        val msg = detail?.takeIf { it.isNotBlank() }?.let { "${code.message}: $it" } ?: code.message
        return PaymentResult.Failed(msg, code.code)
    }
    
    /**
     * 异常映射到PaymentResult.Failed
     */
    fun mapExceptionToFailed(
        throwable: Throwable?,
        defaultCode: PaymentErrorCode
    ): PaymentResult.Failed {
        val code = mapExceptionToErrorCode(throwable, defaultCode)
        val messageDetail = throwable?.message
        return buildFailure(code, messageDetail)
    }
    
    /**
     * 异常映射到PaymentErrorCode
     */
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
```

**使用示例**:
```kotlin
// 在PaymentSDK中使用
private val errorMapper: PaymentErrorMapper by lazy { koin.get() }

try {
    // 业务逻辑
} catch (e: Exception) {
    val failure = errorMapper.mapExceptionToFailed(e, PaymentErrorCode.QUERY_FAILED)
    onResult(failure)
}
```

#### Koin依赖注入模块

定义SDK的依赖注入配置。

```kotlin
fun paymentModule(config: PaymentConfig): Module = module {
    // 配置
    single { config }
    
    // 工具类
    single { PaymentErrorMapper() }
    single { PaymentChannelManager() }
    
    // 网络服务
    single {
        PaymentApiService(
            baseUrl = config.apiBaseUrl,
            timeoutMs = config.networkTimeout * 1000,
            securityConfig = config.securityConfig
        )
    }
    
    // Repository
    single<PaymentRepository> { PaymentRepositoryImpl(get(), get()) }
    
    // UseCases
    single {
        PaymentUseCases(
            fetchChannels = FetchChannelsUseCase(get()),
            createOrder = CreateOrderUseCase(get()),
            queryStatus = QueryStatusUseCase(get())
        )
    }
}
```

---

### network-security层（基础设施层）

#### PaymentApiService

支付网络API服务,基于Retrofit + OkHttp实现。

**关键特性:**
- 使用`ScalarsConverterFactory`获取原始JSON字符串,再用`JSONObject`手动解析
- 支持请求签名(HMAC-SHA256) + 响应验签
- 支持证书绑定(Certificate Pinning)
- 解析失败直接抛出异常,返回`Result.failure`便于业务处理

#### getPaymentChannels()

获取指定业务线的支付渠道配置。

```kotlin
suspend fun getPaymentChannels(
    businessLine: String,
    appId: String
): Result<List<PaymentChannelMeta>>
```

**参数:**
- `businessLine`: 业务线标识
- `appId`: 应用ID

**返回:**
- `Result.success(channels)`: 成功时返回渠道配置列表
- `Result.failure(exception)`: 失败时返回异常

---

#### createPaymentOrder()

创建支付订单,获取支付所需参数。

```kotlin
suspend fun createPaymentOrder(
    orderId: String,
    channelId: String,
    amount: String,
    extraParams: Map<String, Any> = emptyMap()
): Result<Map<String, Any>>
```

**参数:**
- `orderId`: 订单ID
- `channelId`: 支付渠道ID
- `amount`: 支付金额(字符串格式)
- `extraParams`: 额外参数

**返回:**
- `Result.success(params)`: 创建成功,返回支付参数(如微信prepay_id、支付宝order_info等)
- `Result.failure(exception)`: 创建失败

---

#### queryOrderStatus()

查询订单支付状态。

```kotlin
suspend fun queryOrderStatus(
    orderId: String,
    paymentId: String? = null
): Result<OrderStatusInfo>
```

**参数:**
- `orderId`: 订单ID
- `paymentId`: 支付ID(可选)

**返回:**
- `Result.success(orderStatus)`: 查询成功
- `Result.failure(exception)`: 查询失败

**OrderStatusInfo数据结构:**
```kotlin
data class OrderStatusInfo(
    val orderId: String,
    val paymentId: String?,
    val channelId: String?,
    val channelName: String?,
    val amount: String?,
    val status: String, // "pending"-待支付, "paid"-已支付, "cancelled"-已取消, "failed"-支付失败
    val transactionId: String?,
    val paidTime: Long,
    val createTime: Long
)
```

---

## 并发控制类

### PaymentLockManager

订单锁管理器,防止重复支付。

**特性:**
- 使用`ConcurrentHashMap.newKeySet()`存储支付中的订单
- 使用`ReentrantLock`保证并发安全
- 支持订单锁超时自动释放(防止死锁)
- 可设置超时回调用于日志记录

#### tryLockOrder()

尝试锁定订单。

```kotlin
fun tryLockOrder(orderId: String, timeoutMs: Long = 300000): Boolean
```

**参数:**
- `orderId`: 订单ID
- `timeoutMs`: 超时时间(毫秒),默认300000(5分钟)

**返回:**
- `true`: 锁定成功,可以继续支付
- `false`: 订单已被锁定,拒绝重复支付

**示例:**
```kotlin
if (!PaymentLockManager.tryLockOrder(orderId, 300000)) {
    return PaymentResult.Failed("订单正在支付中,请勿重复操作")
}
```

---

#### unlockOrder()

释放订单锁。

```kotlin
fun unlockOrder(orderId: String)
```

**说明:**
- 支付完成后(无论成功失败)都应该释放订单锁
- SDK会自动调用,通常不需要手动调用

---

#### isOrderPaying()

检查订单是否正在支付中。

```kotlin
fun isOrderPaying(orderId: String): Boolean
```

---

#### setOnTimeoutCallback()

设置超时回调(可选,用于日志记录)。

```kotlin
fun setOnTimeoutCallback(callback: ((orderId: String) -> Unit)?)
```

**示例:**
```kotlin
PaymentLockManager.setOnTimeoutCallback { orderId ->
    Log.w("Payment", "订单锁超时自动释放: $orderId")
}
```

---

## 安全类

### SecuritySigner

请求签名/验签辅助类。

**签名规范:**
```
canonicalString = path + "\n" + sortedQuery + "\n" + body + "\n" + timestamp + "\n" + nonce
signature = Base64(HMAC-SHA256(canonicalString, signingSecret))
```

**请求头:**
- `X-Signature`: 签名(Base64编码)
- `X-Timestamp`: 时间戳(毫秒)
- `X-Nonce`: 随机数(16字节Base64编码)

**响应验签(可选):**
- 验证`X-Server-Signature`头
- 检查`X-Server-Timestamp`时间偏差不超过`maxServerClockSkewMs`
- canonical string格式: `path + "\n" + sortedQuery + "\n" + body + "\n" + serverTimestamp`

#### buildRequestHeaders()

构造请求签名头。

```kotlin
fun buildRequestHeaders(
    path: String,
    query: Map<String, String?> = emptyMap(),
    body: String? = null
): Map<String, String>
```

**返回:**
- 包含签名、时间戳、随机数的请求头Map
- 如果未启用签名则返回空Map

---

#### verifyResponseSignature()

验证响应签名。

```kotlin
fun verifyResponseSignature(
    responseSignature: String?,
    responseTimestamp: String?,
    path: String,
    query: Map<String, String?> = emptyMap(),
    body: String? = null
): Result<Unit>
```

**返回:**
- `Result.success(Unit)`: 验签成功或未启用响应验签
- `Result.failure(exception)`: 验签失败

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
    val errorCode: String = PaymentErrorCode.UNKNOWN_ERROR.code
) : PaymentResult()
```

**属性：**
- `errorMessage: String` - 错误描述（格式: "标准信息" 或 "标准信息: 详情"）
- `errorCode: String` - 标准错误码（如 "1001"，默认"5003"）

**新增计算属性**:
```kotlin
val isRetryable: Boolean  // 是否可重试（根据errorCode自动判断）
val errorCodeEnum: PaymentErrorCode?  // 错误码枚举对象
```

**错误信息格式**:
```kotlin
// 格式1: 仅标准信息
"订单正在支付中，请勿重复操作"

// 格式2: 标准信息 + 详情
"支付渠道不存在: wechat_pay"
"网络请求超时: Read timed out"
```

**SDK自动处理** (v2.0.3+):
- ✅ **参数校验**: orderId/amount/channelId自动验证
- ✅ **异常映射**: 网络/SSL异常 → 标准错误码
- ✅ **错误格式化**: 标准信息 + 底层异常详情

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
    
    override fun isAppInstalled(context: Context): Boolean {
        return runCatching { context.packageManager.getPackageInfo("com.tencent.mm", 0) }.isSuccess
    }

    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 调起微信SDK...
        return PaymentResult.Success(orderId)
    }
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
    
    override fun isAppInstalled(context: Context): Boolean {
        return runCatching { context.packageManager.getPackageInfo("com.eg.android.AlipayGphone", 0) }.isSuccess
    }

    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 调起支付宝SDK...
        return PaymentResult.Success(orderId)
    }
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
    
    override fun isAppInstalled(context: Context): Boolean {
        return runCatching { context.packageManager.getPackageInfo("com.unionpay", 0) }.isSuccess
    }

    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 调起银联SDK...
        return PaymentResult.Success(orderId)
    }
}
```

**所需extraParams：**
- `tn`: 交易流水号

---

## 异常处理

- **SDK不向外抛异常**: 所有错误通过 `PaymentResult.Failed` 返回（包含网络/解析/业务错误）
- **错误信息**: `errorMessage` 包含具体原因；`errorCode` 可选,由渠道或后端定义
- **查询超时**: 返回 `PaymentResult.Processing`,建议提示用户稍后在订单列表中查看
- **生命周期兜底**: 基于进程生命周期监听，流程中断时会回调失败，避免回调悬挂
- **响应解析失败**: 渠道列表/订单状态等接口响应解析失败会直接返回 `Result.failure`,调用方可据此提示用户或重试

---

## 线程安全与并发控制

### 订单锁机制

- **订单级锁**: `PaymentLockManager.tryLockOrder()` 防止同一订单重复支付
- **超时释放**: 订单锁会在 `orderLockTimeoutMs`(默认5分钟)后自动释放,避免死锁
- **线程安全**: 使用 `ReentrantLock` + `ConcurrentHashMap` 保证并发安全
- **回调通知**: 可通过 `setOnTimeoutCallback` 监听锁超时事件

### 查询去重机制

- **共享结果**: 同一订单的并发查询会复用同一个 `CompletableDeferred`,避免重复网络请求
- **自动清理**: 查询完成后自动从 `activeQueries` 中移除,防止内存泄漏
- **等待机制**: 后发起的查询会等待首次查询完成,共享相同结果

### UI回调

- **主线程回调**: 支付结果由进程生命周期监听器在主线程回调,便于直接更新UI
- **协程作用域**: 生命周期监听协程在流程结束时自动取消

### 最佳实践

```kotlin
// ✅ 正确: 使用SDK的自动锁机制
PaymentSDK.payWithChannel(
    channelId = "wechat_pay",
    context = context,
    orderId = orderId,
    amount = amount,
    onResult = { result -> handleResult(result) }
)

// ✅ 正确: 检查订单是否正在支付
if (PaymentSDK.isOrderPaying(orderId)) {
    Toast.makeText(context, "订单正在支付中", Toast.LENGTH_SHORT).show()
    return
}

// ❌ 错误: 不要手动管理订单锁(SDK会自动处理)
// PaymentLockManager.tryLockOrder(orderId)  // 不要这样做
```

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

---

## 📚 相关文档

- [项目结构说明](./PROJECT_STRUCTURE.md) - 6个模块的详细说明
- [架构设计文档](./ARCHITECTURE.md) - Clean Architecture详解
- [集成指南](./INTEGRATION_GUIDE.md) - 详细集成步骤
- [错误码指南](./ERROR_CODE_GUIDE.md) - 标准错误码说明
- [渠道实现指南](./CHANNEL_IMPLEMENTATION_GUIDE.md) - 自定义渠道开发

---

**最后更新者**: guichunbai  
**更新日期**: 2025-11-24  
**版本**: v3.0.0
