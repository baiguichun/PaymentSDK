# 项目结构说明

```
payment-sdk/
├── README.md                                    # 项目说明文档
├── build.gradle.kts                             # 项目级构建配置
├── settings.gradle.kts                          # 项目设置
├── gradle.properties                            # Gradle配置
│
├── docs/                                        # 文档目录
│   ├── ARCHITECTURE.md                          # 架构设计文档
│   ├── API.md                                   # API文档
│   ├── INTEGRATION_GUIDE.md                     # 集成指南
│   └── ...                                      # 其他说明
│
├── paycore/                                     # 核心SDK模块（必须集成）
│   ├── build.gradle.kts                         # 模块构建配置
│   ├── proguard-rules.pro                       # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/xiaobai/paycore/
│       │   ├── PaymentSDK.kt                    # SDK入口类
│       │   ├── PaymentResult (内联定义于 PaymentSDK.kt)
│       │   ├── config/                          # PaymentConfig 等
│       │   ├── channel/                         # 渠道接口与管理
│       │   ├── network/                         # PaymentApiService
│       │   ├── concurrent/                      # 订单锁等并发控制
│       │   └── ui/                              # PaymentSheetDialog、PaymentLifecycleActivity 等
│       └── res/                                 # 布局、主题、图标资源
│
└── app/                                         # 示例/集成演示应用
    ├── build.gradle.kts                         # 应用构建配置
    ├── proguard-rules.pro                       # 混淆规则
    └── src/main/                                # 示例 Activity、布局与资源
```

## 模块说明

### payment-core（核心SDK）
**状态：** 必须集成  
**功能：**
- SDK初始化和配置管理
- 支付渠道接口定义
- 渠道管理和验证
- 半屏弹窗UI组件
- 网络服务
- APP安装检测

**依赖：**
- Kotlin标准库
- Kotlin Coroutines
- AndroidX核心库
- Material Design组件

**对外暴露的主要API：**
- `PaymentSDK`: SDK入口类
- `IPaymentChannel`: 支付渠道接口
- `PaymentConfig`: 配置类
- `PaymentResult`: 支付结果

### payment-channel-wechat（微信支付SDK）
**状态：** 可选集成  
**功能：**
- 实现微信支付功能
- 封装微信SDK调用

**依赖：**
- payment-core
- 微信开放平台SDK（需要额外添加）

**适用场景：**
- 社交类应用
- 电商应用
- O2O应用

### payment-channel-alipay（支付宝SDK）
**状态：** 可选集成  
**功能：**
- 实现支付宝支付功能
- 封装支付宝SDK调用

**依赖：**
- payment-core
- 支付宝SDK（需要额外添加）

**适用场景：**
- 电商应用
- 生活服务应用
- 金融应用

### payment-channel-union（银联支付SDK）
**状态：** 可选集成  
**功能：**
- 实现银联支付功能
- 封装银联SDK调用

**依赖：**
- payment-core
- 银联SDK（需要额外添加）

**适用场景：**
- 企业支付
- 金融应用
- 大额支付场景

### sample（示例应用）
**状态：** 仅供参考  
**功能：**
- 演示SDK的集成方法
- 展示完整的支付流程
- 提供最佳实践参考

**依赖：**
- payment-core
- payment-channel-wechat
- payment-channel-alipay
- payment-channel-union

## 关键文件说明

### PaymentSDK.kt
SDK的主入口类，提供以下功能：
- `init()`: 初始化SDK
- `registerChannel()`: 注册支付渠道
- `showPaymentSheet()`: 显示支付渠道选择弹窗
- `payWithChannel()`: 使用指定渠道支付
- `getAvailableChannels()`: 获取可用渠道列表

### IPaymentChannel.kt
支付渠道接口，定义了所有支付渠道必须实现的方法：
- `pay()`: 执行支付
- `isAppInstalled()`: 检查APP是否安装
- `getSupportedFeatures()`: 获取支持的功能

### PaymentChannelManager.kt
渠道管理器，负责：
- 注册和注销渠道
- 查询已注册渠道
- 过滤可用渠道
- 按优先级排序

### PaymentSheetDialog.kt
支付选择对话框组件（v2.0），负责：
- 从后端获取渠道配置
- 展示可用渠道列表
- 用户选择渠道（RadioButton）
- 点击支付按钮自动创单和支付
- 支持任何 Activity（不限于 FragmentActivity）

### AppInstallChecker.kt
APP安装检测工具，提供：
- 单个APP安装检测
- 批量APP安装检测
- 常用支付APP包名常量

### PaymentApiService.kt
网络服务类，负责：
- 获取支付渠道配置
- 创建支付订单
- 与后端通信

## 数据流向

```
用户点击支付
    ↓
PaymentSDK.showPaymentSheet()
    ↓
PaymentSheetDialog 显示
    ↓
PaymentApiService.getPaymentChannels() → 后端API
    ↓
PaymentChannelManager.filterAvailableChannels()
    ↓
展示可用渠道列表（RadioButton 选择）
    ↓
用户选择渠道 + 点击"立即支付"按钮
    ↓
PaymentApiService.createPaymentOrder() → 创单
    ↓
启动 PaymentLifecycleActivity（透明）
    ↓
IPaymentChannel.pay() → 调起第三方APP
    ↓
onPause → 用户跳转到支付APP
    ↓
【用户完成支付】
    ↓
onResume → 检测到用户返回
    ↓
自动查询后端支付状态
    ↓
返回 PaymentResult
    ↓
应用处理支付结果
```

## 扩展点

### 1. 新增支付渠道
创建新的渠道SDK模块，实现`IPaymentChannel`接口。

### 2. 自定义UI
不使用 `PaymentSheetDialog`，直接调用 `PaymentSDK.payWithChannel()` 自己实现渠道选择界面。

### 3. 自定义渠道管理策略
继承`PaymentChannelManager`，重写渠道过滤逻辑。

### 4. 自定义网络请求
实现自己的`PaymentApiService`，替换默认的网络服务。

## 版本管理

- 核心SDK版本：1.0.0
- 微信支付SDK版本：1.0.0
- 支付宝SDK版本：1.0.0
- 银联支付SDK版本：1.0.0

各模块独立版本管理，可以单独升级。

## 构建和发布

### 本地构建
```bash
./gradlew assembleRelease
```

### 生成AAR
```bash
./gradlew :payment-core:assembleRelease
./gradlew :payment-channel-wechat:assembleRelease
./gradlew :payment-channel-alipay:assembleRelease
./gradlew :payment-channel-union:assembleRelease
```

### 发布到Maven
```bash
./gradlew :payment-core:publish
./gradlew :payment-channel-wechat:publish
./gradlew :payment-channel-alipay:publish
./gradlew :payment-channel-union:publish
```

## 许可证

MIT License
