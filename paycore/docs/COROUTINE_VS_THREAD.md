# Kotlin 协程：为什么它适合支付SDK？

## 📋 概述

本文档解释为什么支付SDK全面采用 Kotlin 协程，而不是传统的手动多线程管理。

---

## 🎯 核心结论

**Kotlin 协程完美适合移动支付场景，无需自定义额外的线程管理组件。**

---

## 💡 协程 vs 线程：核心区别

### 1. 概念理解

```kotlin
// 线程（Thread）
// - 操作系统级别的资源
// - 创建成本高（~1MB内存/线程）
// - 切换成本高（上下文切换）
// - 数量有限（几十到几百个）

// 协程（Coroutine）
// - 用户级别的任务
// - 创建成本极低（~几KB/协程）
// - 切换成本极低（函数调用级别）
// - 数量几乎无限（成千上万个）
```

### 2. 形象比喻

```
线程 = 工人
协程 = 任务

传统方式：
- 有100个任务
- 需要雇佣100个工人
- 成本高，难管理

协程方式：
- 有1000个任务
- 只需要10个工人
- 每个工人可以处理多个任务（通过暂停/恢复）
- 成本低，易管理
```

---

---

## 🎬 实际场景演示

### 场景1：1000个订单并发支付

```kotlin
// 创建1000个协程
repeat(1000) { i ->
    lifecycleScope.launch {
        PaymentSDK.payWithChannel(
            orderId = "ORDER_$i",
            channelId = "alipay",
            amount = 100L,
            onResult = { result ->
                println("订单${i}完成")
            }
        )
    }
}

// ✅ 结果：
// - 1000个协程瞬间创建（~5MB内存）
// - 协程调度自动并发执行
// - 无需担心OOM
```

**如果用传统线程：**
```kotlin
// ❌ 创建1000个线程
repeat(1000) { i ->
    Thread {
        // ...
    }.start()
}

// ❌ 结果：
// - 内存占用：~1000MB（1GB）
// - 系统负担极重
// - 可能导致OOM
// - 线程调度效率低
```

### 场景2：同一订单重复查询

```kotlin
// 10个协程同时查询同一订单
repeat(10) {
    lifecycleScope.launch {
        val result = PaymentSDK.queryOrderStatus("ORDER_001")
    }
}

// ✅ SDK 内部的查询去重：
private val activeQueries = ConcurrentHashMap<String, CompletableDeferred<PaymentResult>>()

suspend fun queryOrderStatus(orderId: String): PaymentResult {
    // 检查是否已有查询
    val existingQuery = activeQueries[orderId]
    if (existingQuery != null) {
        return existingQuery.await()  // ✅ 等待并复用
    }
    
    // 创建新查询
    val queryDeferred = CompletableDeferred<PaymentResult>()
    activeQueries[orderId] = queryDeferred
    
    // 执行实际查询
    val result = withContext(Dispatchers.IO) {
        apiService.queryOrderStatus(orderId)
    }
    
    queryDeferred.complete(result)
    return result
}

// ✅ 结果：
// - 只执行1次实际查询
// - 其他9个协程等待并复用结果
// - 没有阻塞任何线程（suspend）
```

---

## 🏗️ SDK 中的协程应用

### 1. PaymentLifecycleActivity

```kotlin
class PaymentLifecycleActivity : Activity() {
    // ✅ 创建协程作用域
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private fun onUserReturnedFromPayment() {
        // ✅ 在作用域内启动协程
        activityScope.launch {
            // 延迟（不阻塞线程）
            delay(200)
            
            // 网络请求（自动切换到 IO 线程）
            val result = withContext(Dispatchers.IO) {
                PaymentSDK.queryOrderStatus(orderId)
            }
            
            // 返回主线程更新UI
            withContext(Dispatchers.Main) {
                deliverResult(result)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // ✅ 自动取消所有协程（防止泄漏）
        activityScope.cancel()
    }
}
```

### 2. PaymentSheetDialog

```kotlin
class PaymentSheetDialog(activity: Activity) {
    private val dialog = BottomSheetDialog(activity)
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    init {
        dialog.setOnDismissListener {
            // ✅ 关闭时自动取消协程
            dialogScope.cancel()
        }
    }
    
    private fun loadChannels() {
        dialogScope.launch {
            try {
                // ✅ 显示加载中
                showLoading()
                
                // ✅ 网络请求
                val channels = withContext(Dispatchers.IO) {
                    PaymentSDK.getApiService().getPaymentChannels()
                }
                
                // ✅ 更新UI
                updateChannelList(channels)
                
            } catch (e: Exception) {
                showError(e.message)
            } finally {
                hideLoading()
            }
        }
    }
}
```

### 3. 网络请求

```kotlin
suspend fun queryOrderStatus(orderId: String): PaymentResult = withContext(Dispatchers.IO) {
    // ✅ 在 IO 调度器执行
    
    val url = URL("$baseUrl/order/status?orderId=$orderId")
    val connection = url.openConnection() as HttpURLConnection
    
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            parseResult(response)
        } else {
            PaymentResult.Failed(orderId, "HTTP $responseCode")
        }
    } finally {
        connection.disconnect()
    }
}
```

---

## 📈 性能对比

### 内存占用

| 场景 | 传统线程 | Kotlin 协程 | 节省 |
|------|---------|-------------|------|
| 100个并发任务 | ~100MB | ~1MB | **99%** ✅ |
| 1000个并发任务 | ~1000MB | ~5MB | **99.5%** ✅ |

### 创建速度

| 场景 | 传统线程 | Kotlin 协程 |
|------|---------|-------------|
| 创建100个 | ~100ms | **< 1ms** ✅ |
| 创建1000个 | ~1000ms | **< 5ms** ✅ |

### 切换成本

| 操作 | 传统线程 | Kotlin 协程 |
|------|---------|-------------|
| 上下文切换 | ~1-100μs | **~10ns** ✅ |
| 暂停/恢复 | 不支持 | **内置** ✅ |

---

## 🔥 协程的杀手级特性

### 1. 结构化并发

```kotlin
// ✅ 父协程取消，子协程自动取消
activityScope.launch {
    // 父协程
    
    launch { /* 子协程1 */ }
    launch { /* 子协程2 */ }
    launch { /* 子协程3 */ }
}

activityScope.cancel()  // 所有协程都被取消 ✅
```

### 2. 自动生命周期管理

```kotlin
// ✅ Activity 销毁时自动取消
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            // 这个协程会在 Activity 销毁时自动取消
        }
    }
}
```

### 3. 异常处理

```kotlin
// ✅ SupervisorJob: 子协程异常不影响其他协程
val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

scope.launch {
    // 协程1：可能抛异常
    throw Exception("Error!")
}

scope.launch {
    // 协程2：不受影响，继续执行 ✅
}
```

### 4. suspend 关键字

```kotlin
// ✅ suspend 函数：可以暂停，不阻塞线程
suspend fun queryOrderStatus(orderId: String): PaymentResult {
    delay(1000)  // 暂停1秒，但不阻塞线程 ✅
    return withContext(Dispatchers.IO) {
        // 执行网络请求
    }
}

// ❌ 传统方式：阻塞线程
fun queryOrderStatusBlocking(orderId: String): PaymentResult {
    Thread.sleep(1000)  // 阻塞线程 ❌
    return // ...
}
```

---

## ❓ FAQ

### Q1: Dispatchers.IO 的 64 个线程会不会太少？

**A:** 不会。移动支付主要是 IO 操作，大部分时间在等待网络响应。64个线程可以支持数千个并发协程。

### Q2: 如果需要更多线程怎么办？

**A:** 可以配置：
```kotlin
System.setProperty("kotlinx.coroutines.io.parallelism", "128")
```

但通常不需要，因为：
- 协程可以暂停/恢复，不占用线程
- IO 操作主要是等待，不需要线程一直执行

### Q3: 协程会不会导致内存泄漏？

**A:** 不会，前提是正确使用 `CoroutineScope`:
```kotlin
// ✅ 正确：使用 CoroutineScope
class MyActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onDestroy() {
        scope.cancel()  // 取消所有协程
    }
}

// ❌ 错误：使用 GlobalScope
GlobalScope.launch {
    // 这个协程永远不会被取消
}
```

### Q4: 协程适合 CPU 密集型任务吗？

**A:** 对于 CPU 密集型任务，使用 `Dispatchers.Default`:
```kotlin
// CPU 密集型任务
withContext(Dispatchers.Default) {
    // 大量计算
}

// IO 密集型任务
withContext(Dispatchers.IO) {
    // 网络请求、文件读写
}
```

### Q5: 协程和线程能混用吗？

**A:** 可以，但不推荐：
```kotlin
// ❌ 不推荐
Thread {
    runBlocking {
        // 协程代码
    }
}.start()

// ✅ 推荐：全部使用协程
lifecycleScope.launch {
    withContext(Dispatchers.IO) {
        // 协程代码
    }
}
```

---

## 📚 相关资源

### 官方文档
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Coroutines on Android](https://developer.android.com/kotlin/coroutines)

### SDK 相关文档
- [并发控制详解](./CONCURRENT_CONTROL.md)
- [架构设计](./ARCHITECTURE.md)
- [生产环境改进](./PRODUCTION_READY_IMPROVEMENTS.md)

---

## 🎯 总结

### 为什么支付SDK使用 Kotlin 协程？

| 优势 | 说明 |
|------|------|
| ✅ **轻量** | 每个协程仅几KB，可创建成千上万个 |
| ✅ **高效** | 自动管理线程，无需手动调度 |
| ✅ **安全** | 结构化并发，自动取消，防止泄漏 |
| ✅ **简洁** | suspend 关键字，代码易读易维护 |
| ✅ **现代** | Android 官方推荐，社区支持好 |

**协程是现代 Android 开发的标准做法，完美适合支付SDK！** 🚀
