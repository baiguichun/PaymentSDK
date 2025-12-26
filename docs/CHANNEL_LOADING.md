# 渠道动态加载方案

本文说明当前 SDK 的渠道发现与懒加载方案，基于 KSP 生成静态注册表，而非 `ServiceLoader`，以避免启动时一次性实例化所有渠道。

## 核心组件
- 注解：`@PaymentChannelService(channelId)`（channel-spi/src/main/java/com/xiaobai/paycore/channel/PaymentChannelService.kt），标记实现了 `IPaymentChannel` 的具体类。
- 处理器：`PaymentChannelProcessor`（channel-spi-processor/src/main/kotlin/com/xiaobai/paycore/channel/processor/PaymentChannelProcessor.kt）在编译期收集标注类，校验非抽象且实现 `IPaymentChannel`，并将 `channelId` + 全限定类名写入注册表。
- 生成物：`com.xiaobai.paycore.channel.generated.GeneratedPaymentChannelRegistry`，包含 `@JvmField val factories: List<PaymentChannelFactory>`，每个工厂仅保存 `channelId` 和创建函数，不实例化渠道。
- 运行时加载：`PaymentChannelServiceLoader.createLazyChannels(classLoader)`（channel-spi/src/main/java/com/xiaobai/paycore/channel/PaymentChannelServiceLoader.kt）反射读取注册表的 `factories`，并包装成 `LazyPaymentChannel` 懒代理（channel-spi/src/main/java/com/xiaobai/paycore/channel/LazyPaymentChannel.kt）。
- 注册：`PaymentSDK.init()` 调用 `autoRegisterServiceChannels()` 将懒代理注册进仓库，后续通过仓库查找渠道并在 `pay()/isAppInstalled()` 时才真实构造渠道实例。

## 编译期流程
1) 开发者在渠道实现类上添加 `@PaymentChannelService(channelId = "...")`。
2) KSP 运行 `PaymentChannelProcessor`，验证类定义、`channelId` 非空且实现接口。
3) 根据收集到的渠道生成 `GeneratedPaymentChannelRegistry`，写入 `PaymentChannelFactory("id") { ImplClass() }` 列表。

## 运行时流程
1) SDK 初始化时读取生成的注册表，获取所有 `PaymentChannelFactory`。
2) 为每个工厂创建 `LazyPaymentChannel` 代理，并注册到 `PaymentChannelManager`。
3) UI 拉取后端返回的渠道元数据后，仅展示已注册的渠道代理；真正的渠道实例在第一次调用时才通过工厂创建。

## 优势
- 启动/内存友好：初始化阶段不实例化渠道，实现按需创建。
- 无 `ServiceLoader` 反射依赖：直接读取生成类，不依赖 `META-INF/services` 资源合并，对 R8/ProGuard 更稳。
- 可扩展：注册表可携带更多字段或校验逻辑，便于后续扩展或去重。
- Android 兼容性好：避免 `ServiceLoader` 在 Android 上的 Provider API 缺失、分包/热修复 ClassLoader 问题。

## 与 ServiceLoader/AutoService 对比
- `ServiceLoader.load(IPaymentChannel)` 在迭代时会直接实例化所有实现，无法实现懒加载。
- Java 9 的 Provider API（只拿类型不实例化）在 Android 标准库不可用。
- 采用静态注册表 + 懒工厂可以保留“编译期发现”能力，同时避免一次性实例化和反射开销；若需兼容第三方库，可在处理器里额外生成 `META-INF/services` 作为兜底，但运行时仍建议走注册表。

## 新增渠道步骤
1) 实现 `IPaymentChannel`，保证类非抽象且公开。
2) 在类上添加 `@PaymentChannelService(channelId = "your_id")`，`channelId` 唯一且与后端约定一致。
3) 运行编译，KSP 会生成/更新 `GeneratedPaymentChannelRegistry`。
4) 调用 `PaymentSDK.init()` 后，渠道将以懒代理方式自动注册，无需手动实例化。
