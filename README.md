# PaymentCore - 聚合支付SDK

[![Version](https://img.shields.io/badge/version-2.0.3-blue.svg)](https://github.com/xiaobai/paymentcore)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](./LICENSE)
[![Android](https://img.shields.io/badge/platform-Android-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/language-Kotlin-orange.svg)](https://kotlinlang.org)

一个现代化、生产级别的Android聚合支付SDK，支持微信、支付宝、银联等多种支付渠道。

## ✨ 亮点特性

### 🎯 智能生命周期管理
- 自动监听用户从第三方APP返回
- 自动查询支付结果
- 完全透明，用户无感知

### 🚀 极简API
- 一行代码完成支付
- 自动执行整个支付流程
- 回调直接返回最终结果

### 🏗️ 现代化架构
- 透明Activity监听生命周期
- 订单级锁防止重复支付
- 线程安全的并发控制

### 🔌 灵活集成
- 支持任何Activity类型
- 模块化支付渠道
- 按需集成，减小APK体积

## 📱 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation project(":paycore")
}
```

### 2. 初始化

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")
            .setApiBaseUrl("https://api.example.com")
            .setDebugMode(BuildConfig.DEBUG)
            .setMaxQueryRetries(3)       // 查询重试次数(默认3次)
            .setQueryIntervalMs(2000)    // 查询间隔(默认2秒)
            .setQueryTimeoutMs(10000)    // 查询超时(默认10秒)
            .setOrderLockTimeoutMs(300000) // 订单锁超时(默认5分钟)
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
        
        PaymentSDK.init(this, config)
        
        // 注册已集成的支付渠道
        PaymentSDK.registerChannels(listOf(
            WeChatPayChannel(),
            AlipayChannel(),
            UnionPayChannel()
        ))
    }
}
```

### 3. 发起支付

```kotlin
// 显示支付选择弹窗，SDK自动完成支付
PaymentSDK.showPaymentSheet(
    activity = this,
    orderId = orderId,
    amount = amount,
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
                // SDK查询超时，引导用户查看订单列表
                Toast.makeText(this, "支付处理中，请稍后查询订单", Toast.LENGTH_LONG).show()
            }
        }
    },
    onCancelled = {
        Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show()
    }
)
```

就这么简单！✅

## 📚 文档

- [完整README](./paycore/docs/README.md) - 详细功能说明
- [API文档](./paycore/docs/API.md) - 完整API参考
- [集成指南](./paycore/docs/INTEGRATION_GUIDE.md) - 详细集成步骤
- [架构设计](./paycore/docs/ARCHITECTURE.md) - 架构说明
- [渠道实现指南](./paycore/docs/CHANNEL_IMPLEMENTATION_GUIDE.md) - 🆕 支付渠道实现示例
- [错误码指南](./paycore/docs/ERROR_CODE_GUIDE.md) - 🆕 标准化错误码说明
- [变更日志](./paycore/docs/CHANGELOG.md) - 版本历史
- [迁移指南](./paycore/docs/MIGRATION_GUIDE_V2.md) - 从v1.x迁移

## 🎯 核心特性详解

### 透明Activity生命周期监听

SDK使用透明Activity自动监听用户从第三方APP返回：

```
用户调起支付 → 启动透明Activity → 跳转第三方APP
→ 用户完成支付 → 返回APP → onResume自动触发
→ 延迟查询结果 → 返回最终状态
```

**优势：**
- ✅ 完全自动化，无需手动监听
- ✅ 对用户完全透明
- ✅ 支持有UI和无UI场景

### 防重复支付

订单级锁机制，100%防止重复支付：

```kotlin
// SDK自动处理
if (!PaymentLockManager.tryLockOrder(orderId)) {
    return PaymentResult.Failed("订单正在支付中")
}
```

### 自动APP检测

SDK自动检测第三方APP是否安装，只显示可用渠道。

## 🔄 v2.0 重大更新

### API简化

**v1.x:**
```kotlin
showPaymentSheet(
    onChannelSelected = { channel ->
        lifecycleScope.launch {
            val result = payWithChannel(...)
            handleResult(result)
        }
    }
)
```

**v2.0:**
```kotlin
showPaymentSheet(
    onPaymentResult = { result ->
        handleResult(result)  // ✅ 简化50%
    }
)
```

### 支持任何Activity

```kotlin
// ✅ v2.0支持
class MainActivity : Activity() { }
class MyActivity : AppCompatActivity() { }
class ComposeActivity : ComponentActivity() { }
```

### 生命周期自动管理

```kotlin
// v2.0: 使用回调方式,SDK自动监听用户返回并查询结果
PaymentSDK.payWithChannel(
    channelId = "wechat_pay",
    context = this,
    orderId = orderId,
    amount = amount,
    onResult = { result ->
        // ✅ SDK已完成生命周期监听和结果查询
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

## 📊 项目结构

```
paycore/
├── src/main/java/com/xiaobai/paycore/
│   ├── PaymentSDK.kt                  # SDK入口
│   ├── channel/                       # 渠道相关
│   │   ├── IPaymentChannel.kt         # 渠道接口
│   │   └── PaymentChannelManager.kt   # 渠道管理
│   ├── config/
│   │   └── PaymentConfig.kt           # 配置
│   ├── network/
│   │   └── PaymentApiService.kt       # 网络服务
│   ├── concurrent/
│   │   └── PaymentLockManager.kt      # 并发控制
│   └── ui/
│       ├── PaymentSheetDialog.kt      # 支付选择对话框
│       ├── PaymentLifecycleActivity.kt # 生命周期监听
│       └── PaymentChannelAdapter.kt   # 列表适配器
└── docs/                              # 完整文档
```

## 🤝 如何自定义支付渠道

实现 `IPaymentChannel` 接口：

```kotlin
class CustomPayChannel : IPaymentChannel {
    override val channelId = "custom_pay"
    override val channelName = "自定义支付"
    override val channelIcon = R.drawable.ic_custom
    override val requiresApp = true
    override val packageName = "com.custom.pay"
    override val priority = 50  // 优先级(可选)
    
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 调起第三方APP
        // SDK会自动监听用户返回并查询结果
        return PaymentResult.Success(orderId)
    }
    
    override fun isAppInstalled(context: Context): Boolean {
        return AppInstallChecker.isPackageInstalled(context, packageName)
    }
}
```

**📖 详细示例**：查看[渠道实现指南](./paycore/docs/CHANNEL_IMPLEMENTATION_GUIDE.md)，包含：
- 微信支付完整实现(含回调Activity)
- 支付宝支付完整实现(含线程处理)
- 银联支付完整实现
- H5网页支付实现
- 最佳实践和测试建议

## 🔒 安全性

- ✅ 订单级锁防止重复支付,超时自动释放(默认5分钟)
- ✅ ConcurrentHashMap + ReentrantLock保证线程安全
- ✅ 自动释放资源防止泄漏
- ✅ 完整的异常处理(含后端响应解析失败直接返回错误)
- ✅ 支付流程被系统回收时兜底回调失败,避免回调悬挂
- ✅ 查询去重机制,同一订单并发查询共享结果
- ✅ 可选请求签名/响应验签(HMAC-SHA256 + 时间戳/随机数)防篡改与重放
- ✅ 可选 HTTPS 证书绑定(Certificate Pinning)防中间人攻击

## ⚡ 性能优化

- ✅ Kotlin协程统一处理异步操作
- ✅ 查询去重避免重复网络请求
- ✅ 自动管理协程作用域,避免内存泄漏
- ✅ v2.0删除200行冗余代码,架构更清晰
- ✅ 基于Retrofit + OkHttp的高效网络层

## 🏗️ 技术栈

- **语言**: Kotlin 2.0.21
- **最低支持**: Android API 24 (Android 7.0)
- **编译版本**: Android API 36
- **协程**: Kotlinx Coroutines 1.10.2
- **网络**: Retrofit 3.0.0 + OkHttp 5.3.2
- **UI**: Material Design + AndroidX
- **JSON**: org.json (Android内置)
- **安全**: HMAC-SHA256 + Certificate Pinning

## 📝 License

```
MIT License

Copyright (c) 2025 baiguichun

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 💬 联系我们

- 📧 Email: support@example.com
- 🐛 Issues: [GitHub Issues](https://github.com/xiaobai/paymentcore/issues)
- 📖 Wiki: [GitHub Wiki](https://github.com/xiaobai/paymentcore/wiki)

---

**如果觉得有用，请给个 ⭐️Star！**
