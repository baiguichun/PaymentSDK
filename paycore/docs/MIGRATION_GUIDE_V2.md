# 从 v1.x 迁移到 v2.0 指南

## 🎯 概述

v2.0 是一个重大版本更新，包含多项架构优化和API改进。本指南将帮助你快速完成迁移。

## 📋 主要变更

### 1. showPaymentSheet() API变更 ⚠️

#### 变更内容

**回调参数变更：**
- ❌ `onChannelSelected: (IPaymentChannel) -> Unit`
- ✅ `onPaymentResult: (PaymentResult) -> Unit`

**Activity类型放宽：**
- ❌ `FragmentActivity`
- ✅ 任何 `Activity`

#### 迁移步骤

**v1.x 代码：**
```kotlin
PaymentSDK.showPaymentSheet(
    activity = this,
    orderId = orderId,
    amount = amount,
    onChannelSelected = { channel ->
        // 需要手动实现支付逻辑
        lifecycleScope.launch {
            val result = PaymentSDK.payWithChannel(
                channelId = channel.channelId,
                context = this@Activity,
                orderId = orderId,
                amount = amount
            )
            handleResult(result)
        }
    },
    onCancelled = { }
)
```

**v2.0 代码：**
```kotlin
PaymentSDK.showPaymentSheet(
    activity = this,  // 现在支持任何Activity
    orderId = orderId,
    amount = amount,
    onPaymentResult = { result ->
        // ✅ SDK自动完成支付，直接处理结果
        handleResult(result)
    },
    onCancelled = { }
)
```

**变化：**
- ✅ 不需要手动调用 `payWithChannel`
- ✅ 不需要 `lifecycleScope.launch`
- ✅ 代码量减少约50%
- ✅ 支持普通Activity

---

### 2. payWithChannel() 新增回调版本 ✨

#### 变更内容

新增推荐的回调版本，旧的 suspend 版本标记为 `@Deprecated`。

#### 迁移步骤

**v1.x 代码（suspend版本）：**
```kotlin
lifecycleScope.launch {
    val result = PaymentSDK.payWithChannel(
        channelId = "wechat_pay",
        context = context,
        orderId = orderId,
        amount = amount
    )
    handleResult(result)
}
```

**v2.0 代码（回调版本，推荐）：**
```kotlin
PaymentSDK.payWithChannel(
    channelId = "wechat_pay",
    context = context,
    orderId = orderId,
    amount = amount,
    onResult = { result ->
        handleResult(result)
    }
)
```

**好处：**
- ✅ 自动监听用户从第三方APP返回
- ✅ 返回后自动查询支付结果
- ✅ 更好的生命周期管理
- ✅ 不需要手动管理协程

---

### 3. IPaymentChannel.pay() 签名变更 ⚠️

#### 变更内容

`pay()` 方法从 `suspend fun` 改为普通 `fun`。

#### 迁移步骤

**v1.x 代码：**
```kotlin
class WeChatPayChannel : IPaymentChannel {
    override suspend fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 调起微信APP
        WXAPI.sendReq(req)
        return PaymentResult.Success(orderId)
    }
}
```

**v2.0 代码：**
```kotlin
class WeChatPayChannel : IPaymentChannel {
    override fun pay(  // ✅ 移除 suspend
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        // 调起微信APP
        WXAPI.sendReq(req)
        return PaymentResult.Success(orderId)
    }
}
```

**原因：**
- 大多数移动支付只是调起第三方APP，不需要挂起
- 简化实现逻辑
- 更符合实际使用场景

---

### 4. 配置项变更 ⚠️

#### 移除的配置项

以下配置项已移除，功能默认启用：

```kotlin
// ❌ v1.x
val config = PaymentConfig.Builder()
    .setAutoCheckAppInstall(true)   // 已移除
    .setAutoQueryResult(true)       // 已移除
    .build()

// ✅ v2.0
val config = PaymentConfig.Builder()
    // 这些功能现在默认启用，无需配置
    .build()
```

#### 新增的配置项

```kotlin
// ✅ v2.0 新增
val config = PaymentConfig.Builder()
    .setInitialQueryDelay(3000)  // 调起支付后延迟查询时间（默认3秒）
    .build()
```

---

## 🚀 迁移清单

### 必须修改

- [ ] 更新 `showPaymentSheet()` 的回调参数
- [ ] 移除 `IPaymentChannel.pay()` 的 `suspend` 关键字
- [ ] 移除已废弃的配置项（`autoCheckAppInstall`, `autoQueryResult`）

### 推荐修改

- [ ] 将 `payWithChannel` 的 suspend 版本改为回调版本
- [ ] 更新 Activity 类型（如果之前强制使用 FragmentActivity）
- [ ] 添加新的 `initialQueryDelayMs` 配置（可选）

### 无需修改

- ✅ 初始化代码
- ✅ 渠道注册代码
- ✅ 结果处理逻辑

---

## 📝 完整迁移示例

### Application 初始化

**v1.x:**
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")
            .setApiBaseUrl("https://api.example.com")
            .setAutoCheckAppInstall(true)   // ❌ 移除
            .setAutoQueryResult(true)       // ❌ 移除
            .build()
        
        PaymentSDK.init(this, config)
        PaymentSDK.registerChannel(WeChatPayChannel())
    }
}
```

**v2.0:**
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = PaymentConfig.Builder()
            .setAppId("your_app_id")
            .setBusinessLine("retail")
            .setApiBaseUrl("https://api.example.com")
            .setInitialQueryDelay(3000)     // ✅ 新增（可选）
            .build()
        
        PaymentSDK.init(this, config)
        PaymentSDK.registerChannel(WeChatPayChannel())
    }
}
```

### Activity 中使用

**v1.x:**
```kotlin
class PaymentActivity : AppCompatActivity() {  // 必须是 FragmentActivity
    
    private fun pay() {
        PaymentSDK.showPaymentSheet(
            activity = this,
            orderId = orderId,
            amount = amount,
            onChannelSelected = { channel ->
                lifecycleScope.launch {
                    val result = PaymentSDK.payWithChannel(
                        channelId = channel.channelId,
                        context = this@PaymentActivity,
                        orderId = orderId,
                        amount = amount
                    )
                    handleResult(result)
                }
            },
            onCancelled = { }
        )
    }
}
```

**v2.0:**
```kotlin
class PaymentActivity : Activity() {  // ✅ 任何Activity都可以
    
    private fun pay() {
        PaymentSDK.showPaymentSheet(
            activity = this,
            orderId = orderId,
            amount = amount,
            onPaymentResult = { result ->
                // ✅ SDK自动完成支付
                handleResult(result)
            },
            onCancelled = { }
        )
    }
}
```

### 自定义支付渠道

**v1.x:**
```kotlin
class CustomChannel : IPaymentChannel {
    override suspend fun pay(...): PaymentResult {
        // 实现
    }
}
```

**v2.0:**
```kotlin
class CustomChannel : IPaymentChannel {
    override fun pay(...): PaymentResult {  // ✅ 移除 suspend
        // 实现（其他代码不变）
    }
}
```

---

## 🔧 常见问题

### Q1: 为什么要移除 suspend？

**A:** 大多数移动支付只是调起第三方APP后立即返回，不需要真正的协程挂起。实际的支付结果由SDK通过生命周期监听和后端查询获得。

### Q2: 旧的 suspend 版本还能用吗？

**A:** 可以，但已标记为 `@Deprecated(level = DeprecationLevel.WARNING)`。强烈建议迁移到新的回调版本，因为它提供了更好的生命周期管理。

### Q3: 为什么要自动执行支付？

**A:** 简化API使用，减少样板代码。现在SDK内部使用透明Activity自动监听用户从第三方APP返回，并查询支付结果。

### Q4: 如果我不想自动查询怎么办？

**A:** v2.0 的自动查询机制是通过生命周期监听实现的，无法关闭。但你可以使用 `PaymentSDK.queryOrderStatus()` 手动查询特定订单。

### Q5: 迁移需要多长时间？

**A:** 对于中小型项目，通常在30分钟内完成。主要工作是：
1. 更新 `showPaymentSheet` 回调（5分钟）
2. 移除自定义渠道的 `suspend`（5分钟）
3. 移除废弃配置项（5分钟）
4. 测试验证（15分钟）

---

## ✅ 迁移验证

完成迁移后，请验证以下功能：

- [ ] SDK初始化成功
- [ ] 支付渠道正确注册
- [ ] 支付选择弹窗正常显示
- [ ] 选择渠道后自动跳转第三方APP
- [ ] 从第三方APP返回后自动查询结果
- [ ] 支付结果正确回调
- [ ] 所有支付状态（Success/Failed/Cancelled/Processing）处理正确

---

## 📚 相关文档

- [完整CHANGELOG](./CHANGELOG.md)
- [API文档](./API.md)
- [集成指南](./INTEGRATION_GUIDE.md)
- [架构文档](./ARCHITECTURE.md)

---

## 💡 需要帮助？

如果在迁移过程中遇到问题，请：

1. 查看 [CHANGELOG.md](./CHANGELOG.md) 了解详细变更
2. 参考 [INTEGRATION_GUIDE.md](./INTEGRATION_GUIDE.md) 获取完整示例
3. 查看 [API.md](./API.md) 了解最新API文档

---

**祝迁移顺利！** 🎉

