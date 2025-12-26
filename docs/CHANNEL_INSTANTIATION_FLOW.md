# 渠道实例化流程详解

说明 KSP 如何生成渠道工厂、运行时如何懒加载渠道实例，以及 `{ ImplClass() }` 闭包何时被执行。

## 关键角色
- 注解：`@PaymentChannelService(channelId)`（channel-spi/src/main/java/com/xiaobai/paycore/channel/PaymentChannelService.kt）
- 处理器：`PaymentChannelProcessor`（channel-spi-processor/src/main/kotlin/com/xiaobai/paycore/channel/processor/PaymentChannelProcessor.kt）
- 注册表（生成物）：`com.xiaobai.paycore.channel.generated.GeneratedPaymentChannelRegistry`
- 运行时加载器：`PaymentChannelServiceLoader`（channel-spi/src/main/java/com/xiaobai/paycore/channel/PaymentChannelServiceLoader.kt）
- 懒代理：`LazyPaymentChannel`（channel-spi/src/main/java/com/xiaobai/paycore/channel/LazyPaymentChannel.kt）

## 编译期：生成工厂闭包
1) 开发者在渠道实现类上标注 `@PaymentChannelService(channelId = "...")`。
2) `PaymentChannelProcessor` 收集标注类，校验非抽象且实现 `IPaymentChannel`。
3) 处理器生成 `GeneratedPaymentChannelRegistry`，其中的工厂列表长这样：
   ```kotlin
   object GeneratedPaymentChannelRegistry {
       @JvmField
       val factories: List<PaymentChannelFactory> = listOf(
           PaymentChannelFactory("wxpay") { com.demo.WeChatPayChannel() },
           PaymentChannelFactory("alipay") { com.demo.AlipayChannel() }
       )
   }
   ```
   `{ com.demo.WeChatPayChannel() }` 是函数类型 `() -> IPaymentChannel` 的闭包，编译期只写入“如何创建”，不会执行。

> 生成文件位于构建输出：`build/generated/ksp/.../com/xiaobai/paycore/channel/generated/GeneratedPaymentChannelRegistry.kt`。

## 运行时：加载注册表并包装懒代理
1) `PaymentChannelServiceLoader.createLazyChannels(classLoader)` 通过反射加载注册表，读取 `factories`。
2) 对每个 `PaymentChannelFactory` 构造 `LazyPaymentChannel(factory.channelId, factory.create)`。此时仍未实例化渠道。
3) `PaymentSDK.init()` 调用 `autoRegisterServiceChannels()`，把这些懒代理注册进仓库；UI 和业务后续都通过仓库取渠道。

## 实例化触发点：`by lazy` 首次访问
- `LazyPaymentChannel` 内部有 `private val delegate: IPaymentChannel by lazy { instantiateDelegate() }`。
- 当外部首次调用 `pay()` 或 `isAppInstalled()` 时，会访问 `delegate`：
  1. `by lazy` 检查未初始化，于是调用 `instantiateDelegate()`。
  2. `instantiateDelegate()` 里执行 `factory.invoke()`，相当于运行闭包体 `{ ImplClass() }`，此刻才 `new` 出真实渠道实例并缓存。
  3. 后续再访问同一个 `LazyPaymentChannel` 的 `delegate`，直接复用缓存实例，不再重复创建。
- Kotlin 默认的 `by lazy { ... }` 是线程安全的（同步实现），因此并发首次访问也只会创建一次。

## 常见疑问
- **“注册表的列表不是已经创建实例了吗？”** 不是。列表里存的是 `PaymentChannelFactory` 对象和闭包，闭包体只描述如何创建，直到 `factory.invoke()` 被调用才会执行 `new`。
- **“为什么闭包会执行 new？”** 因为生成模板里写的就是 `{ ImplClass() }`，当 `factory.invoke()` 被调用时就会运行这段代码。
- **“还有反射吗？”** 注册表加载用了一次反射拿到静态字段，渠道实例化则直接调用闭包，不依赖 `ServiceLoader` 反射。
