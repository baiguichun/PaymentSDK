# 更新日志

## 未发布

### 🐛 Bug修复

- 渠道列表解析失败不再被吞掉，网络层会返回 `Result.failure`，UI 能正确展示错误而不是误判为“无可用渠道”。
- 创单响应解析异常会直接失败，避免返回空参数导致后续支付环节静默失败。
- 替换透明 Activity 为进程级生命周期监听，避免 Activity 被系统回收导致回调悬挂。

## v2.0.3 - 2025-11-23

### ✨ 新功能

#### 订单锁超时自动释放

添加了订单锁超时自动释放机制，防止因异常导致的死锁。

**功能说明：**
- ✅ 订单锁在5分钟后自动释放（可配置）
- ✅ 使用 Kotlin 协程实现超时机制
- ✅ 防止 APP 崩溃或进程被杀死导致的锁永久持有
- ✅ 支持超时回调（用于日志记录）

**配置示例：**
```kotlin
val config = PaymentConfig.Builder()
    .setOrderLockTimeoutMs(300000L)  // 5分钟超时（默认值）
    .build()
```

**工作原理：**
```kotlin
// 加锁时自动启动超时任务
tryLockOrder(orderId, timeoutMs)
    ↓
启动超时协程任务
    ↓
delay(timeoutMs)  // 等待超时
    ↓
自动释放订单锁
    ↓
触发超时回调（日志记录）
```

**效果：**
- ✅ 防止死锁：即使异常，锁也会自动释放
- ✅ 提升用户体验：用户可以在超时后重新支付
- ✅ 降低客服成本：减少因锁无法释放导致的投诉

---

## v2.0.2 - 2025-11-23

### 🔧 架构优化

#### 1. 删除废弃并发辅助代码

移除了未使用的旧并发工具代码，全面拥抱 Kotlin 协程。

**删除的内容：**
- ❌ 废弃的并发辅助类（含 executePaymentWithQueue 等）
- ❌ 相关的导入和调用

**原因：**
1. **完全未被使用** - 唯一使用它的 `executePaymentWithQueue` 方法是废代码
2. **Kotlin 协程更好** - 现代 Android 开发的标准做法
3. **架构已变化** - 进程级生命周期监听器和 `PaymentSheetDialog` 都使用协程
4. **降低复杂度** - 减少维护成本

**现在使用：**
```kotlin
// ✅ PaymentProcessLifecycleObserver
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

// ✅ PaymentSheetDialog
private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

// ✅ 网络请求
suspend fun queryOrderStatus(...) = withContext(Dispatchers.IO) { ... }
```

**效果：**
- ✅ 代码量减少 ~285行
- ✅ 更符合 Kotlin/Android 最佳实践
- ✅ 更易维护和理解
- ✅ 协程提供更好的生命周期管理

#### 2. 删除 PaymentBottomSheet

移除了已被 `PaymentSheetDialog` 替代的旧版本 UI 组件。

**删除的内容：**
- ❌ `PaymentBottomSheet.kt` (~233行代码)

**原因：**
1. **已被替代** - `PaymentSheetDialog` 提供更强大的功能
2. **限制过多** - 只支持 `FragmentActivity`
3. **功能单一** - 只能选择渠道，不能直接支付

**对比：**

| 特性 | PaymentBottomSheet (旧) | PaymentSheetDialog (新) |
|------|------------------------|------------------------|
| 基类 | `BottomSheetDialogFragment` | `BottomSheetDialog` |
| Activity 支持 | ❌ 仅 `FragmentActivity` | ✅ 任何 `Activity` |
| 功能 | 只选择渠道 | ✅ 选择 + 创单 + 支付 |
| 支付按钮 | ❌ 无 | ✅ 有 |
| 协程管理 | 依赖外部 | ✅ 自动管理 |

**效果：**
- ✅ 代码量减少 ~233行
- ✅ 更灵活，支持所有类型的 Activity
- ✅ 用户体验更好（先选择后支付）
- ✅ 减少维护成本

---

## v2.0.1 - 2025-11-23

### 🐛 Bug修复

#### 查询去重机制

修复了自动查询和手动查询可能发生冲突的问题。

**问题：**
- 进程级自动查询正在轮询
- 用户同时调用 `queryOrderStatus()` 手动查询
- 导致同一订单被重复查询，浪费资源

**解决方案：**

使用 `CompletableDeferred` 实现查询去重：

```kotlin
private val activeQueries = ConcurrentHashMap<String, CompletableDeferred<PaymentResult>>()

private suspend fun queryPaymentResultWithRetry(orderId: String): PaymentResult {
    // 如果该订单正在查询中，等待现有查询完成
    val existingQuery = activeQueries[orderId]
    if (existingQuery != null) {
        return existingQuery.await()
    }
    
    // 创建新查询任务
    val queryDeferred = CompletableDeferred<PaymentResult>()
    activeQueries[orderId] = queryDeferred
    
    try {
        // 执行查询
        val result = ...
        queryDeferred.complete(result)
        return result
    } finally {
        activeQueries.remove(orderId)
    }
}
```

**效果：**
- ✅ 同一订单只执行一次实际查询
- ✅ 其他协程等待首次查询结果
- ✅ 避免重复网络请求
- ✅ 自动清理，防止内存泄漏

---

## v2.0.0 - 2025-11-23

### 🎉 重大更新

这是一个重大版本更新，包含多项架构优化和API改进。

### ✨ 核心新特性

#### 1. **进程级生命周期监听**

新增 `PaymentProcessLifecycleObserver`，基于 `ProcessLifecycleOwner` 自动监听用户从第三方支付APP返回。

**解决的问题：**
- ✅ 用户从微信/支付宝返回后自动查询支付结果
- ✅ 支持有UI和无UI的场景
- ✅ 避免透明 Activity 被系统回收导致回调悬挂

**工作流程：**
```
用户选择支付 → 调起第三方APP
→ 前后台切换/兜底定时 → 自动查询结果 → 返回最终状态
```

#### 2. **支付弹窗自动执行支付**

`showPaymentSheet()` 现在内部自动调用支付接口，无需用户手动实现。

**之前：**
```kotlin
PaymentSDK.showPaymentSheet(
    onChannelSelected = { channel ->
        // ❌ 需要手动实现支付逻辑
        lifecycleScope.launch {
            val result = PaymentSDK.payWithChannel(...)
            handleResult(result)
        }
    }
)
```

**现在：**
```kotlin
PaymentSDK.showPaymentSheet(
    onPaymentResult = { result ->
        // ✅ SDK自动完成支付，直接处理结果
        handleResult(result)
    },
    onCancelled = { /* 用户取消 */ }
)
```

#### 3. **支持普通Activity**

`showPaymentSheet()` 现在支持任何类型的Activity，不再强制要求 `FragmentActivity`。

**实现方式：** 使用 `BottomSheetDialog` 替代 `BottomSheetDialogFragment`

**支持：**
- ✅ 普通 `Activity`
- ✅ `AppCompatActivity`
- ✅ `ComponentActivity`（Compose）

#### 4. **简化的支付渠道接口**

`IPaymentChannel.pay()` 改为普通函数（非suspend）。

**原因：** 大多数移动支付只是调起第三方APP，不需要协程挂起。

**之前：**
```kotlin
interface IPaymentChannel {
    suspend fun pay(...): PaymentResult
}
```

**现在：**
```kotlin
interface IPaymentChannel {
    fun pay(...): PaymentResult  // 普通函数
}
```

#### 5. **回调版本的 payWithChannel**

新增回调版本的 `payWithChannel()`（推荐使用），旧的suspend版本标记为 `@Deprecated`。

**新版（推荐）：**
```kotlin
PaymentSDK.payWithChannel(
    channelId = "wechat_pay",
    context = context,
    orderId = orderId,
    amount = amount,
    onResult = { result ->
        // 处理结果
    }
)
```

**旧版（已废弃）：**
```kotlin
@Deprecated("推荐使用回调版本")
suspend fun payWithChannel(...): PaymentResult
```

### 🔧 配置变更

#### 移除的配置项

以下配置项已移除，功能默认启用：

- ❌ `autoCheckAppInstall` - 现在总是自动检测
- ❌ `autoQueryResult` - 现在总是自动查询

#### 新增的配置项

- ✅ `initialQueryDelayMs` - 调起支付后延迟查询时间（默认3秒）

```kotlin
val config = PaymentConfig.Builder()
    .setInitialQueryDelay(3000)  // 等待3秒后开始查询
    .build()
```

### 🆕 新增API

#### 1. `queryOrderStatus()` - 手动查询订单状态

```kotlin
suspend fun queryOrderStatus(orderId: String): PaymentResult
```

**使用场景：**
- 用户从第三方APP返回后手动刷新
- 订单列表中查询订单状态
- 支付返回Processing状态后主动查询

#### 2. `getPaymentStatus()` - 获取支付状态（调试用）

替代原来的 `getPaymentQueueStatus()`。

```kotlin
fun getPaymentStatus(): String
```

### 🔨 架构优化

#### 1. **删除 PaymentTaskQueue**

移除了未使用的任务队列系统，简化架构。

**原因：**
- 移动支付每次都跳转第三方APP，不需要复杂的队列管理
- `PaymentLockManager` 已提供足够的并发控制
- 降低代码复杂度和维护成本

#### 2. **修复内存泄漏**

`PaymentProcessLifecycleObserver` 使用单次会话状态并在流程结束时清理回调，避免静态回调导致的潜在泄漏。

#### 3. **SDK初始化检查**

所有公开API都会检查SDK是否已初始化。

```kotlin
private fun checkInitialized() {
    if (!isInitialized) {
        throw IllegalStateException(
            "PaymentSDK 未初始化！请先在 Application.onCreate() 中调用 PaymentSDK.init()"
        )
    }
}
```

### 📱 新增组件

#### PaymentProcessLifecycleObserver
基于进程生命周期的监听器，自动处理支付前后台切换。

**特性：**
- 无需透明 Activity，降低被系统回收风险
- 自动检测用户返回
- 自动查询支付结果

#### PaymentSheetDialog
基于 `BottomSheetDialog` 的支付选择对话框。

**特性：**
- 支持任何Activity
- 自动管理协程作用域
- Dialog关闭时自动取消网络请求

### 💥 破坏性变更

#### 1. `showPaymentSheet()` API变更

**之前：**
```kotlin
fun showPaymentSheet(
    activity: FragmentActivity,
    onChannelSelected: (IPaymentChannel) -> Unit,
    onCancelled: () -> Unit
)
```

**现在：**
```kotlin
fun showPaymentSheet(
    activity: Activity,  // 支持任何Activity
    onPaymentResult: (PaymentResult) -> Unit,  // 返回支付结果
    onCancelled: () -> Unit
)
```

#### 2. `IPaymentChannel.pay()` 签名变更

**之前：**
```kotlin
suspend fun pay(...): PaymentResult
```

**现在：**
```kotlin
fun pay(...): PaymentResult  // 非suspend
```

#### 3. 配置项移除

`autoCheckAppInstall` 和 `autoQueryResult` 配置项已移除。

### 🔄 迁移指南

#### 更新支付渠道实现

```kotlin
// 之前
class MyPayChannel : IPaymentChannel {
    override suspend fun pay(...): PaymentResult {
        // ...
    }
}

// 现在
class MyPayChannel : IPaymentChannel {
    override fun pay(...): PaymentResult {
        // 移除 suspend 关键字
        // 其他代码不变
    }
}
```

#### 更新 showPaymentSheet 调用

```kotlin
// 之前
PaymentSDK.showPaymentSheet(
    activity = this,
    orderId = orderId,
    amount = amount,
    onChannelSelected = { channel ->
        lifecycleScope.launch {
            val result = PaymentSDK.payWithChannel(...)
            handleResult(result)
        }
    },
    onCancelled = { }
)

// 现在
PaymentSDK.showPaymentSheet(
    activity = this,
    orderId = orderId,
    amount = amount,
    onPaymentResult = { result ->
        // SDK自动完成支付
        handleResult(result)
    },
    onCancelled = { }
)
```

#### 更新 payWithChannel 调用（推荐）

```kotlin
// 之前（suspend版本）
lifecycleScope.launch {
    val result = PaymentSDK.payWithChannel(
        channelId = "wechat_pay",
        context = context,
        orderId = orderId,
        amount = amount
    )
    handleResult(result)
}

// 现在（回调版本，推荐）
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

#### 移除配置项

```kotlin
// 之前
val config = PaymentConfig.Builder()
    .setAutoCheckAppInstall(true)   // ❌ 移除
    .setAutoQueryResult(true)       // ❌ 移除
    .build()

// 现在
val config = PaymentConfig.Builder()
    // 这些功能现在默认启用，无需配置
    .setInitialQueryDelay(3000)     // ✅ 新增：延迟查询时间
    .build()
```

### 📚 文档更新

所有文档已更新以反映新的API和架构：

- ✅ `README.md` - 主要说明和快速开始
- ✅ `API.md` - 完整API文档
- ✅ `INTEGRATION_GUIDE.md` - 详细集成指南
- ✅ `ARCHITECTURE.md` - 架构设计文档
- ✅ `DESIGN_SUMMARY.md` - 设计概要
- ✅ `PROJECT_STRUCTURE.md` - 项目结构

### 🐛 Bug修复

- ✅ 修复进程级监听流程的回调清理，避免回调被覆盖
- ✅ 修复 Dialog 协程作用域未正确取消的问题
- ✅ 修复 SDK 未初始化时的错误提示不明确

### ⚡ 性能优化

- ✅ 移除未使用的 PaymentTaskQueue（~200行代码）
- ✅ 简化并发控制逻辑
- ✅ 优化协程作用域管理
- ✅ 减少不必要的线程切换

### 🎯 架构改进

**之前的架构：**
```
PaymentSDK → PaymentTaskQueue → PaymentLockManager
```

**现在的架构（v2.0）：**
```
PaymentSDK → PaymentLockManager → PaymentProcessLifecycleObserver
         ↓
    Kotlin Coroutines (协程)
```

**好处：**
- 更清晰的职责划分
- 更低的复杂度
- 更好的生命周期管理
- 更容易测试和维护

### 📊 代码统计

- 总代码行数：~1500行 → ~1300行（-200行）
- 核心文件数：12个
- 文档文件数：14个
- 测试覆盖率：待补充

### 🙏 致谢

感谢所有贡献者和使用者的反馈！

---

## v1.1.0 - 2025-11-22

### 新增功能

#### ✨ 自动查询支付结果

SDK现在支持在支付渠道返回成功后，**自动查询后端支付结果**，调用方只需要处理最终结果即可。

**主要特性：**

1. **自动轮询查询** - 支付渠道返回成功后，SDK自动查询后端确认支付状态
2. **可配置参数** - 支持自定义查询次数、间隔、超时时间
3. **新增Processing状态** - 查询超时时返回Processing状态，提示用户稍后查询订单
4. **简化调用方逻辑** - 调用方无需手动查询后端，直接使用最终结果

### API变更

#### 新增配置参数

`PaymentConfig.Builder`新增以下配置方法：

```kotlin
fun setAutoQueryResult(autoQuery: Boolean): Builder
fun setMaxQueryRetries(retries: Int): Builder
fun setQueryIntervalMs(intervalMs: Long): Builder
fun setQueryTimeoutMs(timeoutMs: Long): Builder
```

**默认值：**
- `autoQueryResult`: true（启用自动查询）
- `maxQueryRetries`: 3（最多查询3次）
- `queryIntervalMs`: 2000（每次间隔2秒）
- `queryTimeoutMs`: 10000（总超时10秒）

#### 新增支付结果状态

`PaymentResult`新增`Processing`状态：

```kotlin
data class Processing(val message: String) : PaymentResult()
```

当SDK查询超时或达到最大重试次数仍未得到明确结果时返回此状态。

#### 新增API方法

`PaymentApiService`新增查询订单状态方法：

```kotlin
suspend fun queryOrderStatus(
    orderId: String,
    paymentId: String? = null
): Result<OrderStatusInfo>
```

#### 新增数据类

```kotlin
data class OrderStatusInfo(
    val orderId: String,
    val paymentId: String?,
    val channelId: String?,
    val channelName: String?,
    val amount: String?,
    val status: String,
    val transactionId: String?,
    val paidTime: Long,
    val createTime: Long
)
```

### 使用示例

#### 基本用法（推荐）

```kotlin
// 1. 配置SDK时启用自动查询
val config = PaymentConfig.Builder()
    .setAppId("your_app_id")
    .setBusinessLine("retail")
    .setApiBaseUrl("https://api.example.com")
    .setAutoQueryResult(true)  // 启用自动查询
    .build()

PaymentSDK.init(application, config)

// 2. 发起支付
lifecycleScope.launch {
    val result = PaymentSDK.payWithChannel(
        channelId = "wechat_pay",
        context = this@Activity,
        orderId = orderId,
        amount = amount
    )
    
    // 3. 处理最终结果
    when (result) {
        is PaymentResult.Success -> {
            // SDK已确认后端支付成功
            navigateToSuccessPage()
        }
        is PaymentResult.Failed -> {
            showError("支付失败")
        }
        is PaymentResult.Cancelled -> {
            showMessage("已取消支付")
        }
        is PaymentResult.Processing -> {
            // SDK查询超时，引导用户查看订单列表
            navigateToOrderList()
        }
    }
}
```

#### 自定义查询参数

```kotlin
val config = PaymentConfig.Builder()
    .setAutoQueryResult(true)
    .setMaxQueryRetries(5)        // 查询5次
    .setQueryIntervalMs(3000)     // 每次间隔3秒
    .setQueryTimeoutMs(15000)     // 总超时15秒
    .build()
```

#### 关闭自动查询

如果不需要自动查询功能，可以关闭：

```kotlin
val config = PaymentConfig.Builder()
    .setAutoQueryResult(false)  // 关闭自动查询
    .build()
```

关闭后需要手动查询：

```kotlin
when (result) {
    is PaymentResult.Success -> {
        // 手动查询后端
        val apiService = PaymentSDK.getApiService()
        val statusResult = apiService.queryOrderStatus(orderId)
        // 处理查询结果...
    }
}
```

### 工作流程

```
用户发起支付
    ↓
调用 PaymentSDK.payWithChannel()
    ↓
SDK调起支付渠道（微信/支付宝/银联）
    ↓
支付渠道返回成功
    ↓
SDK自动查询后端结果（轮询，最多N次）
    ↓
后端返回支付状态
    ├─ paid → 返回 PaymentResult.Success
    ├─ failed → 返回 PaymentResult.Failed
    ├─ cancelled → 返回 PaymentResult.Cancelled
    └─ 超时/pending → 返回 PaymentResult.Processing
    ↓
调用方处理最终结果
```

### 为什么需要自动查询？

1. **避免状态不一致** - 第三方支付SDK返回成功，不代表支付真正成功
2. **简化调用方逻辑** - 无需手动查询后端，SDK自动处理
3. **提高可靠性** - 通过轮询确保获取最终的支付状态
4. **更好的用户体验** - 用户等待时间更短，结果更准确

### 文档更新

已更新以下文档：

- ✅ `README.md` - 添加自动查询功能说明
- ✅ `docs/INTEGRATION_GUIDE.md` - 详细的集成指南和配置说明
- ✅ `docs/API.md` - API文档更新
- ✅ `sample/MainActivity.kt` - 示例代码更新

### 向后兼容性

✅ **完全向后兼容**

- 自动查询功能默认启用，但不影响现有代码
- 新增的`Processing`状态可选处理
- 所有现有API保持不变

### 最佳实践

1. **推荐启用自动查询** - 适用于大多数场景
2. **合理配置参数** - 根据网络环境调整重试次数和间隔
3. **处理Processing状态** - 提供"查看订单"功能
4. **后端优化** - 确保订单状态查询接口响应快速

### 已知限制

- 查询超时后返回`Processing`状态，此时支付可能成功也可能失败，需要用户稍后查询订单
- 自动查询依赖网络连接，网络不稳定时可能影响查询结果

### 下一步计划

- [ ] 支持自定义查询策略
- [ ] 添加查询结果缓存
- [ ] 支持订单状态变更通知

---

## 升级指南

### 从 v1.0.0 升级到 v1.1.0

无需修改任何代码，自动查询功能默认启用。

如果需要配置查询参数，在初始化时添加：

```kotlin
val config = PaymentConfig.Builder()
    // ... 现有配置 ...
    .setAutoQueryResult(true)
    .setMaxQueryRetries(3)
    .setQueryIntervalMs(2000)
    .setQueryTimeoutMs(10000)
    .build()
```

如果需要处理`Processing`状态：

```kotlin
when (result) {
    // ... 现有的Success/Failed/Cancelled处理 ...
    is PaymentResult.Processing -> {
        // 新增：处理支付处理中状态
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        navigateToOrderList()
    }
}
```
