# 聚合支付SDK

一个现代化、生产级别的Android聚合支付SDK，支持多渠道支付接入。

## ✨ 核心特性

### 🎯 智能生命周期管理
✅ **进程级生命周期监听** - 基于 `ProcessLifecycleOwner` 自动检测前后台切换  
✅ **自动查询结果** - 返回后自动查询后端支付状态  
✅ **一键集成** - `showPaymentSheet()` 自动完成整个支付流程  
✅ **启动恢复** - 宿主可在应用启动时查询后端未完成订单，调用 `resumePendingPayment()` 继续流程

### 🏗️ 现代化架构
✅ **模块化设计** - 支付渠道作为独立SDK，按需集成  
✅ **动态配置** - 从后端获取可用支付渠道  
✅ **灵活兼容** - 支持普通Activity、AppCompatActivity、Compose

### 🔒 企业级安全
✅ **防重复支付** - 订单级锁机制，100%拦截重复支付  
✅ **超时自动释放** - 订单锁5分钟后自动释放，防止死锁  
✅ **内存安全** - 无内存泄漏风险  
✅ **线程安全** - 完整的并发控制
✅ **可选签名/验签** - HMAC-SHA256 + 时间戳/随机数防篡改、防重放  
✅ **可选证书绑定** - Certificate Pinning 防中间人攻击

### 🎨 优秀的用户体验
✅ **半屏弹窗UI** - 友好的支付渠道选择界面，结果通过回调返回  
✅ **APP自动检测** - 自动验证第三方APP是否安装  
✅ **透明无感** - 生命周期监听对用户完全透明  

## 🏗️ 架构设计

### 核心组件

```
paymentcore/
├── PaymentSDK.kt                    # SDK入口类
├── channel/
│   ├── IPaymentChannel.kt          # 支付渠道接口
│   ├── PaymentChannelManager.kt   # 渠道管理器
│   └── AppInstallChecker.kt        # APP安装检测
├── config/
│   └── PaymentConfig.kt            # 配置类
├── network/
│   └── PaymentApiService.kt        # 网络API服务
├── concurrent/
│   └── PaymentLockManager.kt       # 支付锁管理（订单锁+查询去重）
└── ui/
    ├── PaymentSheetDialog.kt       # 支付选择对话框（回调返回结果）
    ├── PaymentProcessLifecycleObserver.kt # 基于进程生命周期的监听器
    └── PaymentChannelAdapter.kt    # 渠道列表适配器
```

### 工作流程

```
用户点击支付
    ↓
showPaymentSheet() 显示渠道选择
    ↓
用户选择支付渠道
    ↓
调用 `showPaymentSheet()` 或 `payWithChannel()` / `resumePendingPayment()`
    ↓
调起第三方支付APP（微信/支付宝）
    ↓
ProcessLifecycleOwner 监听前后台切换（离开前台 → 返回前台）
    ↓
自动查询后端支付结果（含兜底轮询）
    ↓
返回最终 PaymentResult
```

## 🚀 快速开始

### 1. 添加依赖

```gradle
dependencies {
    // 核心SDK（本仓库 paycore 模块）
    implementation project(":paycore")

    // 按需集成自定义的支付渠道实现
    // implementation project(":your-channel-module")
}
```

### 2. 初始化SDK

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 配置SDK
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")  // 业务线标识
            .setApiBaseUrl("https://api.example.com")
            .setDebugMode(BuildConfig.DEBUG)
            .setMaxQueryRetries(3)       // 查询重试次数（默认3次）
            .setQueryIntervalMs(2000)    // 查询间隔（默认2秒）
            .setQueryTimeoutMs(10000)    // 查询超时时间（默认10秒）
            .setOrderLockTimeoutMs(5 * 60 * 1000) // 订单锁超时（默认5分钟）
            // 可选：启用签名/验签 + 证书Pinning
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
        
        // 初始化
        PaymentSDK.init(this, config)
    }
}
```

### 3. 发起支付

#### 方式1：使用支付选择弹窗（推荐）

```kotlin
class CheckoutActivity : AppCompatActivity() {
    
    private fun startPayment(orderId: String, amount: BigDecimal) {
        // SDK自动处理：显示弹窗 → 调起支付 → 监听返回 → 查询结果
        PaymentSDK.showPaymentSheet(
            activity = this,
            orderId = orderId,
            amount = amount,
            onPaymentResult = { result ->
                // ✅ SDK已完成整个支付流程，直接处理结果
                handlePaymentResult(result)
            },
            onCancelled = {
                Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
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
}
```

#### 方式2：直接指定支付渠道

```kotlin
class CheckoutActivity : AppCompatActivity() {
    
    private fun startPaymentWithSpecificChannel(orderId: String, amount: BigDecimal) {
        // 使用指定渠道支付（例如：用户设置了默认支付方式）
        PaymentSDK.payWithChannel(
            channelId = "wechat_pay",
            context = this,
            orderId = orderId,
            amount = amount,
            onResult = { result ->
                // ✅ SDK自动监听生命周期并查询结果
                when (result) {
                    is PaymentResult.Success -> {
                        Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
                        navigateToSuccessPage()
                    }
                    is PaymentResult.Failed -> {
                        Toast.makeText(this, "支付失败: ${result.errorMessage}", Toast.LENGTH_SHORT).show()
                    }
                    is PaymentResult.Cancelled -> {
                        Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
                    }
                    is PaymentResult.Processing -> {
                        // SDK查询超时，引导用户查看订单列表
                        Toast.makeText(this, "支付处理中，请稍后查询订单", Toast.LENGTH_LONG).show()
                        navigateToOrderList()
                    }
                }
            }
        )
    }
}
```

#### 方式3：应用启动时恢复未完成订单

```kotlin
class MyApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // 初始化 & 渠道注册略
        // 向后端查询是否有未完成的订单
        appScope.launch {
            val pending = queryPendingOrdersFromBackend() // 自行实现
            pending.forEach { pendingOrder ->
                PaymentSDK.resumePendingPayment(
                    context = this@MyApplication,
                    orderId = pendingOrder.orderId,
                    channelId = pendingOrder.channelId,
                    amount = pendingOrder.amount,
                    extraParams = pendingOrder.extraParams,
                    onResult = { result ->
                        // 处理结果：如必要可发本地通知或持久化
                        handlePendingResult(pendingOrder.orderId, result)
                    }
                )
            }
        }
    }
}
```

## 📦 核心组件说明

### PaymentSDK
SDK入口类，提供初始化和支付流程（渠道在初始化时自动发现并注册）。

**主要方法：**
- `init()` - 初始化SDK
- `showPaymentSheet()` - 显示支付选择弹窗
- `payWithChannel()` - 指定渠道支付
- `resumePendingPayment()` - 宿主在启动时拿到“未完成订单”后恢复支付流程
- `queryOrderStatus()` - 手动查询订单状态

### PaymentProcessLifecycleObserver
基于 `ProcessLifecycleOwner` 的监听器，自动在前后台切换后发起查询。

**功能：**
- ✅ 监听用户从第三方APP返回前台
- ✅ 自动查询支付结果，含兜底定时触发
- ✅ 对用户完全透明
- ✅ 订单级锁，防止重复支付/回调悬挂

### IPaymentChannel
支付渠道接口，所有支付渠道SDK需实现。

```kotlin
interface IPaymentChannel {
    val channelId: String
    val channelName: String
    
    // 普通函数（非suspend）
    fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult
    
    fun isAppInstalled(context: Context): Boolean
}
```

### PaymentSheetDialog
支付渠道选择对话框（支持任何Activity）。

**特性：**
- ✅ 基于 `BottomSheetDialog`
- ✅ 支持普通Activity、AppCompatActivity
- ✅ 自动管理协程作用域
- ✅ 关闭时自动取消网络请求

### 智能查询机制

SDK 在调起支付后会自动监听用户返回并查询后端，无需手动配置。

**工作流程：**
```
调起支付 → 用户跳转第三方APP → onPause
    ↓
用户完成支付返回 → onResume → 检测到返回
    ↓
延迟200ms（等待第三方同步结果） → 查询后端
    ↓
在最大重试次数或查询超时内轮询 → 返回最终结果
```

**查询去重：**
- 同一订单的并发查询会复用同一个协程（`activeQueries`），避免重复请求
- 查询完成后自动清理，避免内存泄漏
- 解析渠道/订单响应失败会直接返回 `Result.failure`，业务可据此提示用户

**配置参数：**
```kotlin
val config = PaymentConfig.Builder()
    .setMaxQueryRetries(3)         // 最大重试次数（默认3次）
    .setQueryIntervalMs(2000)      // 查询间隔（默认2秒）
    .setQueryTimeoutMs(10000)      // 查询超时（默认10秒）
    .build()
```

> `initialQueryDelayMs` 目前作为预留配置，自动查询流程使用固定 200ms 延迟。

**支付结果状态：**
- `PaymentResult.Success` - 支付成功（后端已确认）
- `PaymentResult.Failed` - 支付失败
- `PaymentResult.Cancelled` - 用户取消
- `PaymentResult.Processing` - 处理中（查询超时，需稍后查询）

## 自定义支付渠道SDK

实现 `IPaymentChannel` 接口即可集成新的支付渠道：

```kotlin
class CustomPayChannel : IPaymentChannel {
    override val channelId: String = "custom_pay"
    override val channelName: String = "自定义支付"
    
    override fun isAppInstalled(context: Context): Boolean {
        // 如果依赖第三方APP，在此检查包名
        return true
    }
    
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 调起第三方支付APP
        val intent = Intent().apply {
            // 设置支付参数
        }
        context.startActivity(intent)
        
        // 立即返回Success，实际结果由SDK通过后端查询获取
        return PaymentResult.Success(orderId)
    }
    
    override fun isAppInstalled(context: Context): Boolean {
        return AppInstallChecker.isPackageInstalled(context, packageName)
    }
}
```

## 并发控制与线程安全 🔒

- 订单锁：`PaymentLockManager.tryLockOrder()` 阻止同一订单重复支付，超时时间由 `orderLockTimeoutMs` 控制（默认5分钟）并自动释放
- 查询去重：同一订单的查询通过 `activeQueries` 共享结果，避免重复网络请求
- 渠道过滤：仅展示“已注册 + 已安装”的渠道（或不需要APP的渠道），减少无效调起
- 生命周期安全：基于进程生命周期监听，查询协程随流程结束自动取消，避免泄漏

> 当前未内置支付队列或专用后台执行器，支付与查询运行在调用方提供的协程/线程环境中。

## 生产环境就绪 ✅

- 防重复：订单级锁 + 超时回收，避免卡死和重复支付
- 查询兜底：重试 + 超时后返回 `Processing`，可引导用户手动查询
- 渠道兜底：拉取远端渠道失败时自动回退到本地可用渠道列表
- 调试友好：`debugMode` 输出关键日志，便于线上问题排查

**详细文档：** [并发控制与线程安全](docs/CONCURRENT_CONTROL.md)、[生产环境特性说明](docs/PRODUCTION_READY_IMPROVEMENTS.md)

## 📚 文档索引
- [项目结构](docs/PROJECT_STRUCTURE.md) / [架构设计](docs/ARCHITECTURE.md) / [API 参考](docs/API.md)
- [集成指南](docs/INTEGRATION_GUIDE.md) / [渠道实现指南](docs/CHANNEL_IMPLEMENTATION_GUIDE.md)
- [渠道加载方案](docs/CHANNEL_LOADING.md) - KSP 生成注册表 + 懒代理
- [渠道实例化流程](docs/CHANNEL_INSTANTIATION_FLOW.md) - 工厂闭包何时执行、懒加载触发点

## License

MIT License
