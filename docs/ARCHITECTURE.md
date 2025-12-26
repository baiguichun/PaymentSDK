# 聚合支付SDK架构设计文档

> **版本**: v3.0.0  
> **架构**: Clean Architecture + 模块化  
> **最后更新**: 2025-11-24  
> **更新者**: guichunbai

---

## 📐 1. 架构概览

PaymentSDK v3.0采用**Clean Architecture**和**模块化设计**，实现了业务逻辑、数据访问和UI的完全分离。

### 1.1 Clean Architecture架构图

```
┌───────────────────────────────────────────────────────────────────┐
│                            应用层 (APP)                             │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │       Application: 初始化SDK、配置Koin（渠道自动发现）        │  │
│  │       Activity/Fragment: 调用SDK、处理支付结果                │  │
│  └──────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────┘
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                    Presentation Layer (ui-kit)                     │
│  ┌──────────────┐  ┌───────────────────┐  ┌───────────────────┐   │
│  │ PaymentSDK   │  │ PaymentSheetDialog│  │PaymentLifecycle  │   │
│  │  (SDK Entry) │  │  + ViewModel      │  │    Activity      │   │
│  └──────────────┘  └───────────────────┘  └───────────────────┘   │
│                                                                     │
│  Dependencies: domain, data, network-security, channel-spi, core   │
└───────────────────────────────────────────────────────────────────┘
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                        Domain Layer (domain)                       │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  PaymentRepository (Interface) - 数据访问抽象                  │  │
│  │  - fetchPaymentChannels() / createPaymentOrder() / queryOrderStatus() │
│  │  - 自动渠道注册 / getChannel() / 可用渠道过滤                  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Business Use Cases - 业务逻辑封装                            │  │
│  │  - FetchChannelsUseCase: 获取支付渠道列表                      │  │
│  │  - CreateOrderUseCase: 创建支付订单                           │  │
│  │  - QueryStatusUseCase: 查询订单状态                           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Domain Models                                                │  │
│  │  - OrderStatusInfo (订单状态信息)                             │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Dependencies: core, channel-spi (NO implementation dependencies)  │
└───────────────────────────────────────────────────────────────────┘
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                        Data Layer (data)                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  PaymentRepositoryImpl - Repository接口实现                   │  │
│  │  - 调用网络服务获取数据                                         │  │
│  │  - 读取渠道注册表并注册懒加载代理，管理渠道查询                │  │
│  └──────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  PaymentErrorMapper - 错误映射器                              │  │
│  │  - buildFailure(): 构建标准错误                               │  │
│  │  - mapExceptionToFailed(): 异常 → PaymentResult.Failed       │  │
│  │  - mapExceptionToErrorCode(): 异常 → PaymentErrorCode        │  │
│  └──────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Dependency Injection (Koin)                                  │  │
│  │  - paymentModule(): 定义所有依赖注入                           │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Dependencies: domain, network-security, channel-spi, core         │
└───────────────────────────────────────────────────────────────────┘
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                             │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Network (network-security)                                   │  │
│  │  - PaymentApiService: Retrofit网络服务                        │  │
│  │  - SecuritySigner: HMAC-SHA256签名/验签                       │  │
│  └──────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Channel SPI (channel-spi)                                    │  │
│  │  - IPaymentChannel: 渠道接口定义                              │  │
│  │  - PaymentChannelManager: 渠道管理                            │  │
│  │  - PaymentChannelService 注解 + KSP 处理器：生成渠道注册表      │  │
│  │  - LazyPaymentChannel: 懒加载代理，实例在 pay() 时由工厂创建    │  │
│  └──────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  Core (core)                                                  │  │
│  │  - PaymentResult: 支付结果模型                                │  │
│  │  - PaymentErrorCode: 错误码枚举                               │  │
│  │  - PaymentConfig: SDK配置                                     │  │
│  │  - PaymentLockManager: 订单锁                                 │  │
│  └──────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────┘
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                      External Services                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │ 后端服务API    │  │ 第三方支付SDK  │  │ 支付渠道实现   │             │
│  │ - 渠道配置     │  │ - 微信/支付宝  │  │ (WeChatPay等) │             │
│  │ - 订单创建     │  │ - 银联等      │  │               │             │
│  │ - 状态查询     │  │              │  │               │             │
│  └──────────────┘  └──────────────┘  └──────────────┘             │
└───────────────────────────────────────────────────────────────────┘
```

### 1.2 模块依赖关系

```
          ┌───────────┐
          │    app    │ (示例应用)
          └─────┬─────┘
                │
                ↓
          ┌───────────┐
          │  ui-kit   │ ← SDK入口（对外唯一暴露）
          └─────┬─────┘
                │
      ┌─────────┼─────────┬──────────┐
      ↓         ↓         ↓          ↓
  [domain] ← [data] ← [network]  [channel-spi]
      ↓         ↓         ↓          ↓
      └─────────┴─────────┴──────────┘
                    ↓
               [core] ← 所有模块的基础
```

**依赖规则**:
- ✅ **domain** 只依赖 **core** 和 **channel-spi**（业务逻辑独立）
- ✅ **data** 实现 **domain** 的接口（依赖倒置原则）
- ✅ **ui-kit** 依赖所有模块，提供统一入口
- ✅ 依赖方向：外层 → 内层（Clean Architecture核心原则）

---

## 🏗️ 2. 核心模块设计

### 2.1 core（核心模型层）

**职责**: 定义SDK的核心数据模型和基础工具

**包含内容**:

#### 2.1.1 PaymentResult
```kotlin
sealed class PaymentResult {
    data class Success(val transactionId: String) : PaymentResult()
    
    data class Failed(
        val errorMessage: String,
        val errorCode: String = PaymentErrorCode.UNKNOWN_ERROR.code
    ) : PaymentResult() {
        val isRetryable: Boolean  // 自动判断是否可重试
        val errorCodeEnum: PaymentErrorCode?  // 获取错误码枚举
    }
    
    object Cancelled : PaymentResult()
    
    data class Processing(
        val message: String,
        val errorCode: String = PaymentErrorCode.QUERY_TIMEOUT.code
    ) : PaymentResult()
}
```

#### 2.1.2 PaymentErrorCode
```kotlin
enum class PaymentErrorCode(
    val code: String,
    val message: String,
    val isRetryable: Boolean
) {
    // 1xxx: 客户端错误
    ORDER_LOCKED("1001", "订单正在支付中", false),
    ORDER_ID_EMPTY("1002", "订单ID不能为空", false),
    ORDER_AMOUNT_INVALID("1003", "订单金额无效", false),
    // ...
    
    // 2xxx: 网络错误
    NETWORK_ERROR("2001", "网络请求失败", true),
    NETWORK_TIMEOUT("2002", "网络请求超时", true),
    // ...
    
    // 3xxx: 查询错误
    QUERY_TIMEOUT("3001", "支付结果查询超时", true),
    // ...
    
    // 4xxx: 安全错误
    SIGNATURE_VERIFY_FAILED("4002", "签名验证失败", false),
    // ...
    
    // 5xxx: 渠道错误
    CHANNEL_NOT_FOUND("5001", "支付渠道不存在", false),
    APP_NOT_INSTALLED("5002", "未安装支付APP", false),
    // ...
    
    // 6xxx: 系统错误
    PAYMENT_INTERRUPTED("6001", "支付流程已中断", true),
    UNKNOWN_ERROR("6002", "发生未知错误", false)
}
```

#### 2.1.3 PaymentConfig
```kotlin
data class PaymentConfig(
    val appId: String,
    val businessLine: String,
    val apiBaseUrl: String,
    val debugMode: Boolean = false,
    val networkTimeout: Long = 30,
    val initialQueryDelayMs: Long = 3000,
    val maxQueryRetries: Int = 3,
    val queryIntervalMs: Long = 2000,
    val queryTimeoutMs: Long = 10000,
    val orderLockTimeoutMs: Long = 300000,
    val securityConfig: SecurityConfig = SecurityConfig()
)
```

#### 2.1.4 PaymentLockManager
```kotlin
object PaymentLockManager {
    fun tryLockOrder(orderId: String, timeoutMs: Long): Boolean
    fun unlockOrder(orderId: String)
    fun isOrderPaying(orderId: String): Boolean
    fun getPayingOrders(): List<String>
    fun setOnTimeoutCallback(callback: (String) -> Unit)
}
```

---

### 2.2 channel-spi（渠道接口层）

**职责**: 定义支付渠道的标准接口

#### 2.2.1 IPaymentChannel
```kotlin
interface IPaymentChannel {
    val channelId: String
    val channelName: String
    
    fun isAppInstalled(context: Context): Boolean
    
    fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult
    
    fun getSupportedFeatures(): List<PaymentFeature>
}
```

#### 2.2.2 PaymentChannelManager
```kotlin
class PaymentChannelManager {
    fun registerChannel(channel: IPaymentChannel)
    fun getChannel(channelId: String): IPaymentChannel?
    fun getAllChannels(): List<IPaymentChannel>
    fun getAvailableChannels(context: Context): List<IPaymentChannel>
    fun filterByIds(channelIds: List<String>): List<IPaymentChannel>
}
```

---

### 2.3 domain（业务领域层）

**职责**: 定义业务逻辑和数据访问接口

#### 2.3.1 PaymentRepository (接口)
```kotlin
interface PaymentRepository {
    suspend fun fetchPaymentChannels(
        businessLine: String,
        appId: String
    ): Result<List<PaymentChannelMeta>>
    
    suspend fun createPaymentOrder(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any>
    ): Result<Map<String, Any>>
    
    suspend fun queryOrderStatus(
        orderId: String,
        paymentId: String? = null
    ): Result<OrderStatusInfo>
    
    // 渠道管理
    fun registerChannel(channel: IPaymentChannel)
    fun getChannel(channelId: String): IPaymentChannel?
    fun getAllChannels(): List<IPaymentChannel>
    fun getAvailableChannels(context: Context): List<IPaymentChannel>
}
```

#### 2.3.2 Use Cases
```kotlin
// 获取支付渠道列表
class FetchChannelsUseCase(
    private val repository: PaymentRepository
) {
    suspend operator fun invoke(
        businessLine: String,
        appId: String
    ): Result<List<PaymentChannelMeta>>
}

// 创建支付订单
class CreateOrderUseCase(
    private val repository: PaymentRepository
) {
    suspend operator fun invoke(...): Result<Map<String, Any>>
}

// 查询订单状态
class QueryStatusUseCase(
    private val repository: PaymentRepository
) {
    suspend operator fun invoke(orderId: String): Result<OrderStatusInfo>
}

// Use Cases聚合
data class PaymentUseCases(
    val fetchChannels: FetchChannelsUseCase,
    val createOrder: CreateOrderUseCase,
    val queryStatus: QueryStatusUseCase
)
```

---

### 2.4 data（数据访问层）

**职责**: 实现Repository接口、错误映射、依赖注入

#### 2.4.1 PaymentRepositoryImpl
```kotlin
class PaymentRepositoryImpl(
    private val apiService: PaymentApiService,
    private val channelManager: PaymentChannelManager
) : PaymentRepository {
    
    override suspend fun fetchPaymentChannels(...): Result<...> {
        return runCatching {
            apiService.getPaymentChannels(...)
        }
    }
    
    override suspend fun createPaymentOrder(...): Result<...> {
        return runCatching {
            apiService.createPaymentOrder(...)
        }
    }
    
    override suspend fun queryOrderStatus(...): Result<OrderStatusInfo> {
        return runCatching {
            val response = apiService.queryOrderStatus(...)
            OrderStatusInfo(
                status = response["status"] as String,
                transactionId = response["transactionId"] as? String
            )
        }
    }
    
    // 渠道管理委托给ChannelManager
    override fun registerChannel(channel: IPaymentChannel) {
        channelManager.registerChannel(channel)
    }
    
    override fun getChannel(channelId: String): IPaymentChannel? {
        return channelManager.getChannel(channelId)
    }
}
```

#### 2.4.2 PaymentErrorMapper
```kotlin
class PaymentErrorMapper {
    fun buildFailure(
        code: PaymentErrorCode,
        detail: String? = null
    ): PaymentResult.Failed {
        val msg = detail?.let { "${code.message}: $it" } ?: code.message
        return PaymentResult.Failed(msg, code.code)
    }
    
    fun mapExceptionToFailed(
        throwable: Throwable?,
        defaultCode: PaymentErrorCode
    ): PaymentResult.Failed {
        val code = mapExceptionToErrorCode(throwable, defaultCode)
        return buildFailure(code, throwable?.message)
    }
    
    fun mapExceptionToErrorCode(
        throwable: Throwable?,
        defaultCode: PaymentErrorCode
    ): PaymentErrorCode {
        return when (throwable) {
            is SocketTimeoutException -> PaymentErrorCode.NETWORK_TIMEOUT
            is UnknownHostException -> PaymentErrorCode.NETWORK_ERROR
            is SSLException -> PaymentErrorCode.CERTIFICATE_VERIFY_FAILED
            else -> {
                val msg = throwable?.message?.lowercase() ?: ""
                when {
                    "signature" in msg -> PaymentErrorCode.SIGNATURE_VERIFY_FAILED
                    "timestamp" in msg -> PaymentErrorCode.TIMESTAMP_INVALID
                    else -> defaultCode
                }
            }
        }
    }
}
```

#### 2.4.3 Koin依赖注入模块
```kotlin
fun paymentModule(config: PaymentConfig): Module = module {
    single { config }
    single { PaymentErrorMapper() }
    single { PaymentChannelManager() }
    single {
        PaymentApiService(
            baseUrl = config.apiBaseUrl,
            timeoutMs = config.networkTimeout * 1000,
            securityConfig = config.securityConfig
        )
    }
    single<PaymentRepository> { PaymentRepositoryImpl(get(), get()) }
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

### 2.5 network-security（网络与安全层）

**职责**: 网络通信、签名验签、证书绑定

#### 2.5.1 PaymentApiService
```kotlin
class PaymentApiService(
    private val baseUrl: String,
    private val timeoutMs: Long,
    private val securityConfig: SecurityConfig
) {
    private val signer: SecuritySigner
    private val okHttpClient: OkHttpClient
    private val retrofit: Retrofit
    
    suspend fun getPaymentChannels(
        businessLine: String,
        appId: String
    ): List<PaymentChannelMeta>
    
    suspend fun createPaymentOrder(
        orderId: String,
        channelId: String,
        amount: String,
        extraParams: Map<String, Any>
    ): Map<String, Any>
    
    suspend fun queryOrderStatus(
        orderId: String,
        paymentId: String? = null
    ): Map<String, Any>
}
```

#### 2.5.2 SecuritySigner
```kotlin
class SecuritySigner(private val config: SecurityConfig) {
    fun buildRequestHeaders(
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String? = null
    ): Map<String, String>
    
    fun verifyResponseSignature(
        responseSignature: String?,
        responseTimestamp: String?,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: String? = null
    ): Result<Unit>
    
    private fun generateSignature(
        canonicalString: String
    ): String  // HMAC-SHA256
}
```

---

### 2.6 ui-kit（表现层）

**职责**: SDK入口、UI组件、生命周期管理

#### 2.6.1 PaymentSDK (SDK入口)
```kotlin
object PaymentSDK {
    fun init(
        app: Application,
        config: PaymentConfig,
        externalKoinApp: KoinApplication? = null  // 支持外部Koin容器
    )
    
    fun showPaymentSheet(
        activity: Activity,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any> = emptyMap(),
        businessLine: String? = null,
        onPaymentResult: (PaymentResult) -> Unit,
        onCancelled: () -> Unit
    )
    
    fun payWithChannel(
        channelId: String,
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any> = emptyMap(),
        onResult: (PaymentResult) -> Unit
    )
    
    suspend fun queryOrderStatus(orderId: String): PaymentResult
    
    fun getRegisteredChannels(): List<IPaymentChannel>
    fun getAvailableChannels(context: Context): List<IPaymentChannel>
    fun isOrderPaying(orderId: String): Boolean
    fun cancelPayment(orderId: String): Boolean
}
```

**职责/特性**:
- SDK入口、依赖注入、支付流程编排
- 渠道注册：读取编译期生成的渠道注册表，注册懒加载代理；真实渠道实例在 `pay()` 调用时由生成的工厂创建
- 展示数据：UI 渠道名/图标依赖后端返回的 `PaymentChannelMeta`，懒代理返回占位值避免提前实例化

#### 2.6.2 PaymentProcessLifecycleObserver（进程级监听）
```kotlin
object PaymentProcessLifecycleObserver : DefaultLifecycleObserver {
    // 基于 ProcessLifecycleOwner 监听前后台切换
    // onStop: 用户跳转到第三方APP
    // onStart 或兜底定时：用户返回后自动查询并回调
}
```

---

## 🔄 3. 数据流设计

### 3.1 支付流程（完整流程）

```
应用启动 → PaymentSDK.init()
    ↓
[Init] 读取编译期生成的渠道注册表，注册 LazyPaymentChannel 代理（保存 channelId + 工厂，不实例化真实渠道）
    ↓
用户点击支付
    ↓
[UI] PaymentSDK.showPaymentSheet()
    ↓
[UI] PaymentSheetDialog 显示
    ↓
[Domain] FetchChannelsUseCase.invoke()
    ↓
[Data] PaymentRepositoryImpl.fetchPaymentChannels()
    ↓
[Network] PaymentApiService.getPaymentChannels()
    ↓
[Backend] 返回可用渠道配置
    ↓
[UI] 展示渠道列表（RadioButton选择，文案/图标来源于后端返回的渠道元数据）
    ↓
用户选择渠道 + 点击"立即支付"
    ↓
[Domain] CreateOrderUseCase.invoke()
    ↓
[Data] PaymentRepositoryImpl.createPaymentOrder()
    ↓
[Network] PaymentApiService.createPaymentOrder()
    ↓
[Backend] 返回预支付参数
    ↓
[UI] 启动支付流程（进程生命周期监听）
    ↓
[Channel] LazyPaymentChannel 在首次 pay() 时由生成的工厂创建真实渠道实例 → IPaymentChannel.pay() 调起第三方APP
    ↓
[Lifecycle] ProcessLifecycleOwner onStop → 应用进入后台
    ↓
【用户完成支付】
    ↓
[Lifecycle] ProcessLifecycleOwner onStart 或兜底定时 → 检测到用户返回
    ↓
[Domain] QueryStatusUseCase.invoke() (自动轮询重试)
    ↓
[Data] PaymentRepositoryImpl.queryOrderStatus()
    ↓
[Network] PaymentApiService.queryOrderStatus()
    ↓
[Backend] 返回支付结果
    ↓
[Data] PaymentErrorMapper处理结果/错误
    ↓
[UI] 返回PaymentResult
    ↓
应用层处理支付结果
```

### 3.2 错误处理流程

```
异常发生（如网络超时）
    ↓
[Data] PaymentErrorMapper.mapExceptionToFailed()
    ↓
识别异常类型
  ├─ SocketTimeoutException → NETWORK_TIMEOUT (2002)
  ├─ UnknownHostException → NETWORK_ERROR (2001)
  ├─ SSLException → CERTIFICATE_VERIFY_FAILED (4004)
  └─ 其他 → 消息关键字匹配
    ↓
[Data] PaymentErrorMapper.buildFailure()
    ↓
构建PaymentResult.Failed
  ├─ errorCode: "2002"
  ├─ errorMessage: "网络请求超时: Read timed out"
  └─ isRetryable: true
    ↓
返回给调用方
```

---

## 🎯 4. 设计原则与模式

### 4.1 SOLID原则

#### 1. 单一职责原则 (SRP)
- ✅ 每个模块只负责一个领域
- ✅ `domain`: 业务逻辑
- ✅ `data`: 数据访问
- ✅ `ui-kit`: 用户界面

#### 2. 开闭原则 (OCP)
- ✅ 新增渠道：实现`IPaymentChannel`接口
- ✅ 替换数据源：实现`PaymentRepository`接口
- ✅ 无需修改核心代码

#### 3. 里氏替换原则 (LSP)
- ✅ 所有`IPaymentChannel`实现可互相替换
- ✅ `PaymentRepositoryImpl`可被其他实现替换

#### 4. 接口隔离原则 (ISP)
- ✅ 接口最小化，避免臃肿
- ✅ `IPaymentChannel`只定义必需方法

#### 5. 依赖倒置原则 (DIP)
- ✅ `domain`定义接口（Repository、UseCase）
- ✅ `data`实现接口
- ✅ 高层模块依赖抽象，不依赖具体实现

### 4.2 设计模式

#### 1. Repository模式
- 抽象数据访问逻辑
- `PaymentRepository`接口 + `PaymentRepositoryImpl`实现

#### 2. Strategy模式
- `IPaymentChannel`接口 + 多个渠道实现

#### 3. Facade模式
- `PaymentSDK`作为统一入口，隐藏内部复杂性

#### 4. Observer模式
- `PaymentProcessLifecycleObserver` 监听进程生命周期变化

#### 5. Singleton模式
- `PaymentSDK`、`PaymentLockManager`使用object实现

---

## 🔒 5. 并发控制设计

### 5.1 订单锁机制

```kotlin
// 防止同一订单重复支付
object PaymentLockManager {
    private val locks = ConcurrentHashMap<String, LockInfo>()
    
    fun tryLockOrder(orderId: String, timeoutMs: Long): Boolean {
        val lock = ReentrantLock()
        val lockInfo = LockInfo(lock, System.currentTimeMillis() + timeoutMs)
        
        return locks.putIfAbsent(orderId, lockInfo) == null
    }
    
    fun unlockOrder(orderId: String) {
        locks.remove(orderId)
    }
    
    // 自动释放超时的锁
    private fun cleanupExpiredLocks()
}
```

### 5.2 查询去重机制

```kotlin
// 避免同一订单的并发查询
private val activeQueries = ConcurrentHashMap<String, CompletableDeferred<PaymentResult>>()

suspend fun queryPaymentResultWithRetry(orderId: String): PaymentResult {
    val existing = activeQueries[orderId]
    if (existing != null) {
        return existing.await()  // 复用查询结果
    }
    
    val deferred = CompletableDeferred<PaymentResult>()
    activeQueries[orderId] = deferred
    
    try {
        // 执行查询...
        val result = ...
        deferred.complete(result)
        return result
    } finally {
        activeQueries.remove(orderId)
    }
}
```

---

## 🔐 6. 安全设计

### 6.1 请求签名（HMAC-SHA256）

```
canonicalString = path + "\n" + sortedQuery + "\n" + body + "\n" + timestamp + "\n" + nonce
signature = Base64(HMAC-SHA256(canonicalString, signingSecret))

请求头:
- X-Signature: 签名值
- X-Timestamp: 时间戳（毫秒）
- X-Nonce: 随机数（16字节Base64）
```

### 6.2 响应验签

```
验证响应头:
- X-Server-Signature: 服务端签名
- X-Server-Timestamp: 服务端时间戳

防重放检查:
- 时间偏差不超过maxServerClockSkewMs（默认5分钟）
```

### 6.3 证书绑定（Certificate Pinning）

```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("api.example.com", "sha256/AAAA...")
    .build()

OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

---

## 📊 7. 性能优化

### 7.1 协程使用

- ✅ 网络请求使用`suspend`函数
- ✅ UI层使用`lifecycleScope`
- ✅ 查询重试使用`delay()`而非阻塞

### 7.2 资源管理

- ✅ Activity销毁时自动取消协程
- ✅ 订单锁超时自动释放
- ✅ Koin容器生命周期管理

### 7.3 缓存策略

- ✅ 渠道列表内存缓存（`PaymentChannelManager`）
- ✅ 查询结果去重（避免重复请求）

---

## 🧪 8. 可测试性设计

### 8.1 依赖注入

```kotlin
// 使用Koin进行依赖注入，便于Mock
val testModule = module {
    single<PaymentRepository> { MockPaymentRepository() }
    single { MockPaymentApiService() }
}
```

### 8.2 接口抽象

```kotlin
// Repository接口便于Mock
interface PaymentRepository {
    suspend fun queryOrderStatus(orderId: String): Result<OrderStatusInfo>
}

// 测试时使用Mock实现
class MockPaymentRepository : PaymentRepository {
    override suspend fun queryOrderStatus(orderId: String): Result<OrderStatusInfo> {
        return Result.success(OrderStatusInfo("paid", "TX123"))
    }
}
```

### 8.3 UseCase隔离

```kotlin
// UseCase只依赖Repository接口
class QueryStatusUseCase(
    private val repository: PaymentRepository  // 易于Mock
) {
    suspend operator fun invoke(orderId: String): Result<OrderStatusInfo> {
        return repository.queryOrderStatus(orderId)
    }
}
```

---

## 📈 9. 架构演进

### v1.0 → v2.0
- 单模块 → 模块化（paycore分离）
- 硬编码错误 → 标准化错误码
- Fragment依赖 → 支持任何Activity

### v2.0 → v3.0 ✨
- **模块化重构**: 1个模块 → 6个模块
- **Clean Architecture**: domain/data/ui分层
- **依赖注入**: 引入Koin管理依赖
- **错误映射**: 集中在`PaymentErrorMapper`
- **Repository模式**: 抽象数据访问
- **UseCase封装**: 业务逻辑清晰化

---

## 📚 相关文档

- [项目结构说明](./PROJECT_STRUCTURE.md)
- [API参考文档](./API.md)
- [集成指南](./INTEGRATION_GUIDE.md)
- [错误码指南](./ERROR_CODE_GUIDE.md)
- [渠道实现指南](./CHANNEL_IMPLEMENTATION_GUIDE.md)

---

**最后更新者**: guichunbai  
**更新日期**: 2025-11-24  
**版本**: v3.0.0
