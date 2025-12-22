# 项目结构说明

> **最后更新**: 2025-11-24  
> **项目版本**: v3.0.0  
> **架构**: Clean Architecture + 模块化

---

## 📂 项目结构总览

```
PaymentSDK/
├── README.md                    # 项目说明文档
├── build.gradle.kts             # 项目级构建配置
├── settings.gradle.kts          # 项目设置（包含所有模块）
├── gradle.properties            # Gradle配置
│
├── docs/                        # 📚 完整文档目录
│   ├── PROJECT_STRUCTURE.md     # 项目结构说明（本文档）
│   ├── ARCHITECTURE.md          # 架构设计文档
│   ├── API.md                   # API参考文档
│   ├── INTEGRATION_GUIDE.md     # 集成指南
│   ├── ERROR_CODE_GUIDE.md      # 错误码使用指南
│   ├── CHANNEL_IMPLEMENTATION_GUIDE.md # 渠道实现指南
│   └── ...                      # 其他文档
│
├── core/                        # ⚙️ 核心模型模块
│   └── src/main/java/com/xiaobai/paycore/
│       ├── PaymentResult.kt     # 支付结果封装
│       ├── PaymentErrorCode.kt  # 标准错误码枚举
│       ├── config/
│       │   └── PaymentConfig.kt # SDK配置
│       └── concurrent/
│           └── PaymentLockManager.kt # 订单锁管理
│
├── channel-spi/                 # 🔌 渠道接口模块
│   └── src/main/java/com/xiaobai/paycore/channel/
│       ├── IPaymentChannel.kt   # 支付渠道接口
│       ├── PaymentChannelManager.kt # 渠道管理器
│       └── PaymentChannelMeta.kt # 渠道元数据
│
├── domain/                      # 💼 业务领域模块（Clean Architecture核心）
│   └── src/main/java/com/xiaobai/paycore/domain/
│       ├── PaymentRepository.kt # Repository接口
│       ├── model/
│       │   └── OrderStatusInfo.kt # 订单状态模型
│       └── usecase/
│           ├── PaymentUseCases.kt # UseCase聚合
│           ├── FetchChannelsUseCase.kt # 获取渠道列表
│           ├── CreateOrderUseCase.kt # 创建订单
│           └── QueryStatusUseCase.kt # 查询订单状态
│
├── data/                        # 🗄️ 数据层模块
│   └── src/main/java/com/xiaobai/paycore/
│       ├── data/
│       │   ├── PaymentRepositoryImpl.kt # Repository实现
│       │   └── PaymentErrorMapper.kt # 错误映射器
│       └── di/
│           └── PaymentModules.kt # Koin依赖注入模块
│
├── network-security/            # 🌐 网络与安全模块
│   └── src/main/java/com/xiaobai/paycore/
│       ├── network/
│       │   └── PaymentApiService.kt # 网络服务（Retrofit）
│       └── security/
│           └── SecuritySigner.kt # 签名/验签工具
│
├── ui-kit/                      # 🎨 UI组件模块（SDK入口）
│   └── src/main/java/com/xiaobai/paycore/
│       ├── PaymentSDK.kt        # ✨ SDK主入口类
│       └── ui/
│           ├── PaymentSheetDialog.kt # 支付渠道选择对话框
│           ├── PaymentSheetViewModel.kt # Dialog的ViewModel
│           ├── PaymentChannelAdapter.kt # 渠道列表适配器
│           └── PaymentProcessLifecycleObserver.kt # 进程级生命周期监听
│
└── app/                         # 📱 示例/演示应用
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/example/paymentsdk/
            └── MainActivity.kt  # 示例Activity
```

---

## 🏗️ 模块说明

### 1. core（核心模型模块）

**状态**: ✅ 必须依赖  
**作用**: 定义SDK的核心数据模型和基础工具

**包含内容**:
- `PaymentResult`: 支付结果封装（Success、Failed、Cancelled、Processing）
- `PaymentErrorCode`: 40+标准化错误码枚举（1xxx-6xxx）
- `PaymentConfig`: SDK配置类（API地址、超时时间、安全配置等）
- `PaymentLockManager`: 订单锁管理（防止重复支付）

**特点**:
- 纯Kotlin模块，无Android依赖
- 所有其他模块都依赖此模块
- 提供SDK的基础类型定义

**依赖**:
```kotlin
implementation(project(":core"))
```

---

### 2. channel-spi（渠道接口模块）

**状态**: ✅ 必须依赖  
**作用**: 定义支付渠道的标准接口和管理逻辑

**包含内容**:
- `IPaymentChannel`: 支付渠道接口（所有渠道必须实现）
- `PaymentChannelManager`: 渠道注册、查询、过滤管理器
- `PaymentChannelMeta`: 从后端返回的渠道元数据

**特点**:
- 定义了渠道的标准能力（pay、isAppInstalled、priority等）
- 支持渠道优先级排序
- 自动过滤未安装APP的渠道

**依赖**:
```kotlin
implementation(project(":core"))
implementation(project(":channel-spi"))
```

---

### 3. domain（业务领域模块）

**状态**: ✅ 必须依赖  
**作用**: Clean Architecture的业务层，定义业务逻辑和数据访问接口

**包含内容**:
- `PaymentRepository`: Repository接口（数据访问抽象）
- `PaymentUseCases`: 业务用例聚合
  - `FetchChannelsUseCase`: 获取支付渠道列表
  - `CreateOrderUseCase`: 创建支付订单
  - `QueryStatusUseCase`: 查询订单状态
- `OrderStatusInfo`: 订单状态信息模型

**特点**:
- 只定义接口，不包含实现（依赖倒置原则）
- 业务逻辑封装在UseCase中
- 便于单元测试（可Mock Repository）

**依赖**:
```kotlin
implementation(project(":core"))
implementation(project(":channel-spi"))
implementation(project(":domain"))
```

---

### 4. data（数据层模块）

**状态**: ✅ 必须依赖  
**作用**: 实现数据访问层和错误处理

**包含内容**:
- `PaymentRepositoryImpl`: Repository接口的实现
- `PaymentErrorMapper`: 统一的错误映射器
  - `buildFailure()`: 构建标准错误
  - `mapExceptionToFailed()`: 异常 → 标准错误码
  - `mapExceptionToErrorCode()`: 异常 → 错误码枚举
- `PaymentModules`: Koin依赖注入模块定义

**特点**:
- 实现了domain层定义的接口
- 集中管理错误映射逻辑
- 使用Koin进行依赖注入配置

**依赖**:
```kotlin
implementation(project(":core"))
implementation(project(":channel-spi"))
implementation(project(":domain"))
implementation(project(":network-security"))
implementation(project(":data"))
```

---

### 5. network-security（网络与安全模块）

**状态**: ✅ 必须依赖  
**作用**: 处理网络通信和安全验证

**包含内容**:
- `PaymentApiService`: Retrofit网络服务
  - `getPaymentChannels()`: 获取支付渠道配置
  - `createPaymentOrder()`: 创建支付订单
  - `queryOrderStatus()`: 查询订单状态
- `SecuritySigner`: 安全签名/验签工具
  - HMAC-SHA256签名
  - 请求/响应验签
  - 证书绑定（Certificate Pinning）

**特点**:
- 使用Retrofit + OkHttp
- 支持请求签名和响应验签
- 支持证书绑定防中间人攻击
- 自动添加签名头（X-Signature、X-Timestamp、X-Nonce）

**依赖**:
```kotlin
implementation(project(":core"))
implementation(project(":network-security"))
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.11.0")
```

---

### 6. ui-kit（UI组件模块 - SDK入口）

**状态**: ✅ **必须依赖（对外唯一入口）**  
**作用**: 提供SDK的公开API和UI组件

**包含内容**:
- `PaymentSDK`: ✨ **SDK主入口类**
  - `init()`: 初始化SDK和Koin容器
  - `registerChannel()`: 注册支付渠道
  - `showPaymentSheet()`: 显示支付渠道选择对话框
  - `payWithChannel()`: 使用指定渠道支付
  - `queryOrderStatus()`: 手动查询订单状态
  - `getAvailableChannels()`: 获取可用渠道列表
- `PaymentSheetDialog`: 半屏弹窗（支持任何Activity）
- `PaymentSheetViewModel`: Dialog的ViewModel（管理状态和数据）
- `PaymentProcessLifecycleObserver`: 基于进程生命周期的监听器
- `PaymentChannelAdapter`: 渠道列表适配器

**特点**:
- 依赖所有其他模块
- 提供简洁的API接口
- 支持外部Koin容器（不干扰宿主APP）
- UI组件使用ViewModel管理状态

**依赖**:
```kotlin
// 宿主APP只需依赖ui-kit模块
implementation(project(":ui-kit"))
// ui-kit内部依赖所有其他模块
```

---

### 7. app（示例应用）

**状态**: 📘 仅供参考  
**作用**: 演示SDK的集成和使用

**包含内容**:
- 完整的集成示例
- 各种使用场景演示
- 最佳实践参考

---

## 🔄 模块依赖关系图

```
┌─────────────┐
│     app     │ (示例应用)
└──────┬──────┘
       │
       ↓
┌─────────────┐
│   ui-kit    │ ← SDK入口，对外唯一暴露模块
└──────┬──────┘
       │
       ├─→ [domain] ←─────┐
       │                  │
       ├─→ [data] ────────┤
       │                  │
       ├─→ [network-security]
       │                  │
       ├─→ [channel-spi]  │
       │                  │
       └─→ [core] ←───────┘
                (所有模块的基础)
```

**依赖原则**:
- ✅ `domain` 只依赖 `core` 和 `channel-spi`（业务逻辑独立）
- ✅ `data` 实现 `domain` 的接口（依赖倒置）
- ✅ `ui-kit` 依赖所有模块（对外统一入口）
- ✅ 遵循Clean Architecture的依赖方向

---

## 📦 对外集成

### 集成方式

**方式1: 本地模块依赖**
```gradle
dependencies {
    implementation(project(":ui-kit"))
}
```

**方式2: Maven/远程依赖（发布后）**
```gradle
dependencies {
    implementation("com.xiaobai:payment-sdk:3.0.0")
}
```

### 初始化示例

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")
            .setApiBaseUrl("https://api.example.com")
            .setDebugMode(BuildConfig.DEBUG)
            .build()
        
        // 初始化SDK（可选传入外部Koin容器）
        PaymentSDK.init(this, config)
        
        // 注册支付渠道
        PaymentSDK.registerChannel(WeChatPayChannel())
        PaymentSDK.registerChannel(AlipayChannel())
    }
}
```

---

## 🎯 关键设计原则

### 1. Clean Architecture
- **依赖方向**: 外层 → 内层（ui-kit → domain ← data）
- **业务逻辑**: 封装在UseCase中，易于测试
- **数据访问**: 通过Repository抽象，便于替换实现

### 2. 单一职责原则（SRP）
- 每个模块只负责一个领域
- `core`: 模型定义
- `domain`: 业务逻辑
- `data`: 数据访问
- `ui-kit`: 用户界面

### 3. 依赖倒置原则（DIP）
- `domain` 定义接口（Repository）
- `data` 实现接口
- 高层模块（ui-kit）依赖抽象（domain）

### 4. 开闭原则（OCP）
- 新增渠道：实现`IPaymentChannel`接口
- 替换数据源：实现`PaymentRepository`接口
- 无需修改核心代码

---

## 🔧 技术栈

| 模块 | 主要技术 |
|------|---------|
| core | Kotlin, Coroutines |
| channel-spi | Kotlin, Android SDK |
| domain | Kotlin (纯业务逻辑) |
| data | Kotlin, Koin (DI) |
| network-security | Retrofit, OkHttp, HMAC-SHA256 |
| ui-kit | Android, ViewModel, Coroutines |

---

## 📝 版本信息

- **SDK版本**: v3.0.0
- **最低Android版本**: API 21 (Android 5.0)
- **目标Android版本**: API 34 (Android 14)
- **Kotlin版本**: 2.0+
- **Gradle版本**: 8.5+

---

## 📚 相关文档

- [架构设计文档](./ARCHITECTURE.md) - 详细的架构设计说明
- [API参考文档](./API.md) - 完整的API文档
- [集成指南](./INTEGRATION_GUIDE.md) - 快速集成指南
- [渠道实现指南](./CHANNEL_IMPLEMENTATION_GUIDE.md) - 如何实现自定义渠道
- [错误码指南](./ERROR_CODE_GUIDE.md) - 标准错误码说明

---

**最后更新者**: guichunbai  
**更新日期**: 2025-11-24
