# PaymentSDK - Android聚合支付SDK

> **版本**: v3.0.0  
> **架构**: Clean Architecture + 模块化  
> **最低Android版本**: API 21 (Android 5.0)

一个基于Clean Architecture设计的Android聚合支付SDK，支持多种支付渠道（微信、支付宝、银联等），提供统一的API接口和完善的错误处理机制。

---

## ✨ 核心特性

### 🏗️ Clean Architecture设计
- **模块化架构**: 6个独立模块（core、channel-spi、domain、data、network-security、ui-kit）
- **依赖注入**: 使用Koin管理依赖，支持外部容器
- **Repository模式**: 抽象数据访问，易于测试和替换
- **UseCase封装**: 业务逻辑清晰，职责单一

### 🎯 简洁易用的API
- **一行代码发起支付**: `PaymentSDK.showPaymentSheet()`
- **自动化流程**: 自动调起支付、监听返回、查询结果
- **支持任何Activity**: 不限于FragmentActivity
- **回调式设计**: 符合Android开发习惯

### 🔒 生产级安全特性
- **请求签名**: HMAC-SHA256签名机制
- **响应验签**: 防止数据篡改
- **证书绑定**: Certificate Pinning防中间人攻击
- **防重放攻击**: 时间戳+随机数机制

### 🚀 高性能与可靠性
- **并发控制**: 订单锁防止重复支付
- **查询去重**: 避免重复网络请求
- **自动重试**: 智能轮询查询支付结果
- **异常兜底**: Activity回收自动处理

### 📊 标准化错误处理
- **40+标准错误码**: 分类清晰（1xxx-6xxx）
- **智能异常映射**: 网络异常自动映射到标准错误码
- **详细错误信息**: 包含底层异常详情
- **可重试标记**: 自动判断错误是否可重试

---

## 🚀 快速开始

### 1. 添加依赖

```gradle
dependencies {
    // 只需依赖ui-kit模块（包含所有必需模块）
    implementation(project(":ui-kit"))
}
```

或使用远程依赖（发布后）：
```gradle
dependencies {
    implementation("com.xiaobai:payment-sdk:3.0.0")
}
```

### 2. 初始化SDK

在Application中初始化：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 构建配置
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")
            .setApiBaseUrl("https://api.example.com")
            .setDebugMode(BuildConfig.DEBUG)
            .setNetworkTimeout(30)
            .setSecurityConfig(
                SecurityConfig(
                    enableSignature = true,
                    signingSecret = "your_secret_key",
                    enableResponseVerification = true,
                    enableCertificatePinning = true,
                    certificatePins = mapOf(
                        "api.example.com" to listOf("sha256/AAAA...")
                    )
                )
        )
            .build()
        
        // 初始化SDK
        PaymentSDK.init(this, config)

        // 渠道发现：为渠道实现添加 @PaymentChannelService 注解并开启 KSP（ksp(project(":channel-spi-processor"))）。
        // SDK 初始化时只注册懒加载代理，真实渠道实例会在调用 pay() 时才反射创建；
        // 渠道名/图标请使用后端返回的渠道元数据进行展示。
    }
}
```

**支持外部Koin容器**（可选）：
```kotlin
// 如果宿主APP已使用Koin，可共享容器
val koinApp = startKoin {
    androidContext(this@MyApplication)
    modules(appModule, paymentModule(config))
}

PaymentSDK.init(this, config, koinApp)
```

### 3. 发起支付

#### 方式1：使用支付渠道选择对话框（推荐）

```kotlin
PaymentSDK.showPaymentSheet(
    activity = this,
    orderId = "ORDER_20250124_001",
    amount = BigDecimal("99.99"),
    onPaymentResult = { result ->
        when (result) {
            is PaymentResult.Success -> {
                // 支付成功
                Toast.makeText(this, "支付成功", Toast.LENGTH_SHORT).show()
                navigateToSuccessPage()
            }
            
            is PaymentResult.Failed -> {
                // 支付失败（SDK已自动处理参数校验、异常映射）
                Toast.makeText(this, result.errorMessage, Toast.LENGTH_SHORT).show()
                
                // 判断是否可重试
                if (result.isRetryable) {
                    showRetryButton()
                }
            }
            
            is PaymentResult.Cancelled -> {
                // 用户取消
                Toast.makeText(this, "支付已取消", Toast.LENGTH_SHORT).show()
            }
            
            is PaymentResult.Processing -> {
                // 查询超时，稍后在订单列表查看
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                navigateToOrderList()
            }
        }
    },
    onCancelled = {
        // 用户关闭对话框
        Toast.makeText(this, "已取消选择", Toast.LENGTH_SHORT).show()
    }
)
```

#### 方式2：使用指定渠道支付

```kotlin
PaymentSDK.payWithChannel(
    channelId = "wechat_pay",
    context = this,
    orderId = "ORDER_20250124_001",
    amount = BigDecimal("99.99"),
    onResult = { result ->
        handlePaymentResult(result)
    }
)
```

### 4. 手动查询订单状态（可选）

```kotlin
lifecycleScope.launch {
    val result = PaymentSDK.queryOrderStatus("ORDER_20250124_001")
    when (result) {
        is PaymentResult.Success -> updateOrderStatus("已支付")
        is PaymentResult.Failed -> updateOrderStatus("支付失败")
        is PaymentResult.Processing -> updateOrderStatus("处理中")
        is PaymentResult.Cancelled -> updateOrderStatus("已取消")
    }
}
```

---

## 📦 模块说明

PaymentSDK采用模块化设计，共6个模块：

| 模块 | 说明 | 依赖要求 |
|------|------|---------|
| **ui-kit** | SDK入口、UI组件 | ✅ **对外唯一入口** |
| core | 核心模型（PaymentResult、PaymentErrorCode） | 内部依赖 |
| channel-spi | 渠道接口定义 | 内部依赖 |
| domain | 业务领域层（Repository接口、UseCases） | 内部依赖 |
| data | 数据访问层（Repository实现、ErrorMapper） | 内部依赖 |
| network-security | 网络服务、签名验签 | 内部依赖 |

**集成说明**：
- 宿主APP只需依赖 `ui-kit` 模块
- `ui-kit` 内部自动依赖所有其他模块
- 符合Clean Architecture的依赖方向原则

---

## 🏗️ 架构设计

### Clean Architecture分层

```
┌─────────────┐
│   ui-kit    │ ← Presentation Layer (SDK入口、Dialog、ViewModel)
└──────┬──────┘
       │
┌──────┴──────┐
│   domain    │ ← Business Layer (Repository接口、UseCases)
└──────┬──────┘
       │
┌──────┴──────┐
│    data     │ ← Data Layer (Repository实现、ErrorMapper、DI)
└──────┬──────┘
       │
┌──────┴──────┬───────────────┬──────────────┐
│  network    │  channel-spi  │    core      │ ← Infrastructure
└─────────────┴───────────────┴──────────────┘
```

### 核心设计原则

- **SOLID原则**: 单一职责、开闭、里氏替换、接口隔离、依赖倒置
- **依赖注入**: 使用Koin管理依赖，易于测试
- **Repository模式**: 抽象数据访问，业务逻辑与实现分离
- **UseCase封装**: 每个业务用例独立，职责清晰

---

## 💡 技术栈

| 技术 | 用途 |
|------|------|
| Kotlin 2.0+ | 主要开发语言 |
| Kotlin Coroutines | 异步编程 |
| Koin | 依赖注入 |
| Retrofit 2.9+ | 网络请求 |
| OkHttp 4.11+ | HTTP客户端 |
| AndroidX | Android基础库 |
| Material Design | UI组件 |

---

## 🔐 安全特性

### 1. 请求签名（HMAC-SHA256）

```kotlin
val config = PaymentConfig.Builder()
    .setSecurityConfig(
        SecurityConfig(
            enableSignature = true,
            signingSecret = "your_secret_key"
        )
    )
    .build()
```

自动添加签名头：
- `X-Signature`: HMAC-SHA256签名
- `X-Timestamp`: 时间戳（毫秒）
- `X-Nonce`: 随机数（16字节）

### 2. 响应验签

```kotlin
SecurityConfig(
    enableResponseVerification = true,
    maxServerClockSkewMs = 300000  // 允许5分钟时间偏差
)
```

### 3. 证书绑定（Certificate Pinning）

```kotlin
SecurityConfig(
    enableCertificatePinning = true,
    certificatePins = mapOf(
        "api.example.com" to listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
        )
    )
)
```

---

## 🎯 错误处理

### 标准化错误码

SDK提供40+标准错误码，分6大类：

| 错误码范围 | 分类 | 说明 |
|-----------|------|------|
| 1xxx | 客户端错误 | 参数错误、状态异常 |
| 2xxx | 网络错误 | 请求失败、超时、解析错误 |
| 3xxx | 查询错误 | 查询超时、失败 |
| 4xxx | 安全错误 | 签名验证、证书验证失败 |
| 5xxx | 渠道错误 | 渠道不存在、APP未安装 |
| 6xxx | 系统错误 | 未知错误、流程中断 |

### 智能错误处理

SDK自动处理以下场景：

```kotlin
// 1. 自动参数校验
validateOrderInput(orderId, amount)
→ ORDER_ID_EMPTY (1002) / ORDER_AMOUNT_INVALID (1003)

// 2. 智能异常映射
SocketTimeoutException → NETWORK_TIMEOUT (2002)
UnknownHostException → NETWORK_ERROR (2001)
SSLException → CERTIFICATE_VERIFY_FAILED (4004)

// 3. 详细错误信息
PaymentResult.Failed(
    errorMessage = "网络请求超时: Read timed out",
    errorCode = "2002"
)
```

### 错误处理示例

```kotlin
when (result) {
    is PaymentResult.Failed -> {
        // 获取错误信息
        val errorCode = result.errorCode
        val errorMessage = result.errorMessage
        
        // 判断是否可重试
        if (result.isRetryable) {
            showRetryDialog(errorMessage)
        } else {
            showError(errorMessage)
        }
        
        // 根据错误码分类处理
        when (result.errorCodeEnum) {
            PaymentErrorCode.NETWORK_TIMEOUT,
            PaymentErrorCode.NETWORK_ERROR -> {
                // 网络问题，建议重试
            }
            PaymentErrorCode.APP_NOT_INSTALLED -> {
                // 引导用户安装APP
                showInstallAppDialog()
            }
            else -> {
                // 其他错误
            }
        }
    }
}
```

---

## 🔧 高级配置

### 完整配置示例

```kotlin
val config = PaymentConfig.Builder()
    // 基础配置
    .setAppId("your_app_id")
    .setBusinessLine("retail")
    .setApiBaseUrl("https://api.example.com")
    .setDebugMode(BuildConfig.DEBUG)
    
    // 网络配置
    .setNetworkTimeout(30)  // 秒
    
    // 查询配置
    .setInitialQueryDelay(3000)  // 调起支付后延迟3秒查询
    .setMaxQueryRetries(3)       // 最多重试3次
    .setQueryIntervalMs(2000)    // 每次重试间隔2秒
    .setQueryTimeoutMs(10000)    // 总超时时间10秒
    
    // 订单锁配置
    .setOrderLockTimeoutMs(300000)  // 订单锁5分钟后自动释放
    
    // 安全配置
    .setSecurityConfig(
        SecurityConfig(
            enableSignature = true,
            signingSecret = "your_secret_key",
            enableResponseVerification = true,
            enableCertificatePinning = true,
            certificatePins = mapOf(
                "api.example.com" to listOf("sha256/AAAA...")
            )
        )
    )
    .build()
```

---

## 🔌 自定义支付渠道

实现`IPaymentChannel`接口创建自定义渠道：

```kotlin
class MyCustomChannel : IPaymentChannel {
    override val channelId = "custom_pay"
    override val channelName = "自定义支付"
    
    override fun isAppInstalled(context: Context): Boolean {
        // 如需依赖第三方APP，在此检查包名
        return true
    }
    
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 实现支付逻辑
        return try {
            // 调起支付...
            PaymentResult.Success("TX_123")
        } catch (e: Exception) {
            PaymentResult.Failed("支付失败: ${e.message}", "5005")
        }
    }
    
    override fun isAppInstalled(context: Context): Boolean = true
}

// 标注注解后，KSP 会生成渠道映射，SDK 初始化时自动注册懒加载代理，真实实例在 pay() 时创建
```

详见 [渠道实现指南](docs/CHANNEL_IMPLEMENTATION_GUIDE.md)

---

## 📚 文档

- [项目结构说明](docs/PROJECT_STRUCTURE.md) - 模块划分和依赖关系
- [架构设计文档](docs/ARCHITECTURE.md) - Clean Architecture详解
- [API参考文档](docs/API.md) - 完整的API文档
- [集成指南](docs/INTEGRATION_GUIDE.md) - 详细集成步骤
- [错误码指南](docs/ERROR_CODE_GUIDE.md) - 标准错误码说明
- [渠道实现指南](docs/CHANNEL_IMPLEMENTATION_GUIDE.md) - 自定义渠道开发

---

## 🤝 贡献

欢迎提交Issue和Pull Request！

---

## 📄 许可证

MIT License

---

## 📞 联系方式

- **作者**: guichunbai
- **版本**: v3.0.0
- **更新日期**: 2025-11-24

---

## 🎉 更新日志

### v3.0.0 (2025-11-24)
- ✨ **重大重构**: 采用Clean Architecture架构
- ✨ **模块化设计**: 拆分为6个独立模块
- ✨ **依赖注入**: 引入Koin管理依赖
- ✨ **Repository模式**: 抽象数据访问层
- ✨ **UseCase封装**: 业务逻辑清晰化
- ✨ **错误映射集中化**: PaymentErrorMapper统一管理
- 🚀 **可测试性提升**: 易于Mock和单元测试
- 🚀 **可维护性提升**: 职责清晰，模块独立

### v2.0.3 (2025-11-23)
- ✨ 标准化错误码（40+个）
- ✨ 智能异常映射
- ✨ 自动参数校验
- 📚 完善文档和示例

### v2.0.0 (2025-11-22)
- ✨ 支持任何Activity（移除FragmentActivity依赖）
- ✨ 自动化支付流程
- ✨ 透明Activity生命周期监听
- 🔒 增强安全特性（签名、证书绑定）

---

**Happy Coding! 🚀**
