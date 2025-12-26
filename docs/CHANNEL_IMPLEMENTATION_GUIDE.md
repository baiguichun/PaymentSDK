# 支付渠道实现指南

本文档详细介绍如何实现自定义支付渠道，包括微信支付、支付宝、银联支付的完整示例。

---

## 📚 目录

- [接口说明](#接口说明)
- [实现步骤](#实现步骤)
- [微信支付示例](#微信支付示例)
- [支付宝示例](#支付宝示例)
- [银联支付示例](#银联支付示例)
- [H5支付示例](#h5支付示例)
- [最佳实践](#最佳实践)

---

## 接口说明

所有支付渠道需要实现`IPaymentChannel`接口：

```kotlin
interface IPaymentChannel {
    // 渠道唯一标识
    val channelId: String
    
    // 渠道显示名称（UI 展示使用后端返回的 PaymentChannelMeta，懒代理返回 channelId 占位）
    val channelName: String
    
    // 执行支付(普通函数,非suspend)
    fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult
    
    // 检查APP是否已安装（仅 requiresApp=true 的渠道需要实现）
    fun isAppInstalled(context: Context): Boolean
}
```

---

## 实现步骤

### 步骤1: 创建渠道模块

```
payment-channel-wechat/
├── build.gradle.kts
├── proguard-rules.pro
└── src/
    └── main/
        ├── AndroidManifest.xml
        ├── java/com/xiaobai/payment/channel/wechat/
        │   ├── WeChatPayChannel.kt
        │   └── wxapi/
        │       └── WXPayEntryActivity.kt
        └── res/
            └── drawable/
                └── ic_wechat_pay.xml
```

### 步骤2: 配置build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.xiaobai.payment.channel.wechat"
    compileSdk = 36
    
    defaultConfig {
        minSdk = 24
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // 依赖核心SDK
    api(project(":paycore"))
    
    // 微信开放SDK
    implementation("com.tencent.mm.opensdk:wechat-sdk-android:6.8.0")
}
```

### 步骤3: 实现IPaymentChannel接口

见下面的具体示例。

---

## 微信支付示例

### 完整实现代码

```kotlin
package com.xiaobai.payment.channel.wechat

import android.content.Context
import com.tencent.mm.opensdk.modelpay.PayReq
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import com.xiaobai.paycore.PaymentResult
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.channel.PaymentChannelService
import java.math.BigDecimal

/**
 * 微信支付渠道实现
 * 
 * 需要的extraParams参数:
 * - appId: 微信开放平台AppID
 * - partnerId: 商户号
 * - prepayId: 预支付交易会话ID
 * - packageValue: 扩展字段(固定值"Sign=WXPay")
 * - nonceStr: 随机字符串
 * - timeStamp: 时间戳
 * - sign: 签名
 */
@PaymentChannelService(channelId = "wechat_pay")
class WeChatPayChannel : IPaymentChannel {
    
    override val channelId: String = "wechat_pay"
    
    override val channelName: String = "微信支付"
    
    private var wxApi: IWXAPI? = null
    
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        return try {
            // 1. 初始化微信API
            val appId = extraParams["appId"] as? String
                ?: return PaymentResult.Failed("缺少appId参数")
            
            if (wxApi == null) {
                wxApi = WXAPIFactory.createWXAPI(context, appId)
                wxApi?.registerApp(appId)
            }
            
            // 2. 检查微信是否安装
            if (!isAppInstalled(context)) {
                return PaymentResult.Failed("未安装微信APP")
            }
            
            // 3. 构造支付请求
            val request = PayReq().apply {
                this.appId = appId
                this.partnerId = extraParams["partnerId"] as? String ?: ""
                this.prepayId = extraParams["prepayId"] as? String ?: ""
                this.packageValue = extraParams["packageValue"] as? String ?: "Sign=WXPay"
                this.nonceStr = extraParams["nonceStr"] as? String ?: ""
                this.timeStamp = extraParams["timeStamp"] as? String ?: ""
                this.sign = extraParams["sign"] as? String ?: ""
            }
            
            // 4. 调起微信支付
            val result = wxApi?.sendReq(request) ?: false
            
            if (result) {
                // 调起成功,实际结果由SDK通过后端查询获取
                PaymentResult.Success(orderId)
            } else {
                PaymentResult.Failed("调起微信支付失败")
            }
        } catch (e: Exception) {
            PaymentResult.Failed("微信支付异常: ${e.message}")
        }
    }
    
    override fun isAppInstalled(context: Context): Boolean {
        return try {
            val wxApi = wxApi ?: WXAPIFactory.createWXAPI(context, null)
            wxApi.isWXAppInstalled
        } catch (e: Exception) {
            false
        }
    }
}
```

### 微信回调Activity

```kotlin
package com.xiaobai.payment.channel.wechat.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory

/**
 * 微信支付回调Activity
 * 
 * 注意:
 * 1. 必须放在包名.wxapi目录下
 * 2. 类名必须是WXPayEntryActivity
 * 3. 在AndroidManifest.xml中声明
 */
class WXPayEntryActivity : Activity(), IWXAPIEventHandler {
    
    private var wxApi: IWXAPI? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化微信API
        wxApi = WXAPIFactory.createWXAPI(this, null)
        wxApi?.handleIntent(intent, this)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        wxApi?.handleIntent(intent, this)
    }
    
    override fun onReq(req: BaseReq?) {
        // 微信发送的请求,暂不处理
    }
    
    override fun onResp(resp: BaseResp?) {
        if (resp?.type == ConstantsAPI.COMMAND_PAY_BY_WX) {
            // 支付结果回调
            // 注意: 这里只是微信APP返回的结果,不是最终的支付结果
            // SDK会通过后端查询获取实际结果,这里无需处理
            
            // 关闭Activity
            finish()
        }
    }
}
```

### AndroidManifest.xml配置

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <uses-permission android:name="android.permission.INTERNET" />
    
    <application>
        <!-- 微信支付回调Activity -->
        <activity
            android:name=".wxapi.WXPayEntryActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />
    </application>
    
</manifest>
```

---

## 支付宝示例

### 完整实现代码

```kotlin
package com.xiaobai.payment.channel.alipay

import android.content.Context
import com.alipay.sdk.app.PayTask
import com.xiaobai.paycore.PaymentResult
import com.xiaobai.paycore.channel.IPaymentChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/**
 * 支付宝支付渠道实现
 * 
 * 需要的extraParams参数:
 * - orderInfo: 完整的订单信息字符串(服务端生成)
 */
class AlipayChannel : IPaymentChannel {
    
    override val channelId: String = "alipay"
    
    override val channelName: String = "支付宝"
    
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        return try {
            // 1. 获取订单信息
            val orderInfo = extraParams["orderInfo"] as? String
                ?: return PaymentResult.Failed("缺少orderInfo参数")
            
            // 2. 检查支付宝是否安装
            if (!isAppInstalled(context)) {
                return PaymentResult.Failed("未安装支付宝APP")
            }
            
            // 3. 调起支付宝支付
            // 注意: 支付宝SDK要求在子线程调用
            runBlocking {
                withContext(Dispatchers.IO) {
                    val payTask = PayTask(context as android.app.Activity)
                    val result = payTask.payV2(orderInfo, true)
                    
                    // 解析支付结果(这里只是支付宝APP的返回,不是最终结果)
                    val resultStatus = result["resultStatus"] ?: ""
                    
                    when (resultStatus) {
                        "9000" -> {
                            // 支付成功(仅表示调起成功)
                            PaymentResult.Success(orderId)
                        }
                        "8000" -> {
                            // 正在处理中
                            PaymentResult.Success(orderId)
                        }
                        "6001" -> {
                            // 用户取消
                            PaymentResult.Cancelled
                        }
                        else -> {
                            // 支付失败
                            PaymentResult.Failed("支付失败: $resultStatus")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            PaymentResult.Failed("支付宝支付异常: ${e.message}")
        }
    }
    
    override fun isAppInstalled(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo("com.eg.android.AlipayGphone", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

### build.gradle.kts配置

```kotlin
dependencies {
    api(project(":paycore"))
    
    // 支付宝SDK
    implementation("com.alipay.sdk:alipaysdk-android:15.8.11")
}
```

### AndroidManifest.xml配置

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <application>
        <!-- 支付宝SDK需要的Activity -->
        <activity
            android:name="com.alipay.sdk.app.PayResultActivity"
            android:exported="false"
            android:screenOrientation="behind"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />
            
        <activity
            android:name="com.alipay.sdk.app.AlipayResultActivity"
            android:exported="false"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />
    </application>
    
</manifest>
```

---

## 银联支付示例

### 完整实现代码

```kotlin
package com.xiaobai.payment.channel.unionpay

import android.content.Context
import com.unionpay.UPPayAssistEx
import com.xiaobai.paycore.PaymentResult
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.channel.PaymentChannelService
import java.math.BigDecimal

/**
 * 银联支付渠道实现
 * 
 * 需要的extraParams参数:
 * - tn: 交易流水号(服务端获取)
 * - mode: 支付模式("00"表示正式环境,"01"表示测试环境)
 */
@PaymentChannelService(channelId = "union_pay")
class UnionPayChannel : IPaymentChannel {
    
    override val channelId: String = "union_pay"
    
    override val channelName: String = "银联支付"
    
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        return try {
            // 1. 获取交易流水号
            val tn = extraParams["tn"] as? String
                ?: return PaymentResult.Failed("缺少tn参数")
            
            val mode = extraParams["mode"] as? String ?: "00"  // 默认正式环境
            
            // 2. 检查银联是否安装
            if (!isAppInstalled(context)) {
                return PaymentResult.Failed("未安装银联APP")
            }
            
            // 3. 调起银联支付
            val success = UPPayAssistEx.startPay(
                context as android.app.Activity,
                null,  // serverMode
                null,  // SEMode
                tn,
                mode
            )
            
            if (success) {
                // 调起成功,实际结果由SDK通过后端查询获取
                PaymentResult.Success(orderId)
            } else {
                PaymentResult.Failed("调起银联支付失败")
            }
        } catch (e: Exception) {
            PaymentResult.Failed("银联支付异常: ${e.message}")
        }
    }
    
    override fun isAppInstalled(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo("com.unionpay", 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

### build.gradle.kts配置

```kotlin
dependencies {
    api(project(":paycore"))
    
    // 银联SDK
    implementation("com.unionpay:upsdk:3.5.2")
}
```

---

## H5支付示例

对于不需要第三方APP的支付方式(如H5网页支付)：

```kotlin
package com.xiaobai.payment.channel.h5

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xiaobai.paycore.PaymentResult
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.channel.PaymentChannelService
import java.math.BigDecimal

/**
 * H5网页支付渠道实现
 * 
 * 需要的extraParams参数:
 * - payUrl: 支付页面URL
 */
@PaymentChannelService(channelId = "h5_pay")
class H5PayChannel : IPaymentChannel {
    
    override val channelId: String = "h5_pay"
    
    override val channelName: String = "网页支付"
    
    override fun pay(
        context: Context,
        orderId: String,
        amount: BigDecimal,
        extraParams: Map<String, Any>
    ): PaymentResult {
        return try {
            // 1. 获取支付URL
            val payUrl = extraParams["payUrl"] as? String
                ?: return PaymentResult.Failed("缺少payUrl参数")
            
            // 2. 打开浏览器或WebView
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payUrl))
            context.startActivity(intent)
            
            // 3. 返回成功(实际结果由SDK查询后端获取)
            PaymentResult.Success(orderId)
        } catch (e: Exception) {
            PaymentResult.Failed("打开支付页面失败: ${e.message}")
        }
    }
    
    override fun isAppInstalled(context: Context): Boolean {
        // H5支付不需要检查APP安装
        return true
    }
}
```

---

## 最佳实践

### 1. 错误处理

```kotlin
override fun pay(...): PaymentResult {
    return try {
        // 支付逻辑
        
    } catch (e: SecurityException) {
        PaymentResult.Failed("权限不足: ${e.message}")
    } catch (e: ActivityNotFoundException) {
        PaymentResult.Failed("无法打开支付APP: ${e.message}")
    } catch (e: Exception) {
        PaymentResult.Failed("支付异常: ${e.message}")
    }
}
```

### 2. 参数验证

```kotlin
override fun pay(...): PaymentResult {
    // 验证必需参数
    val appId = extraParams["appId"] as? String
        ?: return PaymentResult.Failed("缺少appId参数", "PARAM_MISSING")
    
    if (appId.isBlank()) {
        return PaymentResult.Failed("appId不能为空", "PARAM_INVALID")
    }
    
    // 继续支付逻辑...
}
```

### 3. 日志记录

```kotlin
override fun pay(...): PaymentResult {
    if (BuildConfig.DEBUG) {
        Log.d(TAG, "开始支付: orderId=$orderId, amount=$amount")
        Log.d(TAG, "支付参数: $extraParams")
    }
    
    return try {
        // 支付逻辑
        val result = doPayment()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "支付结果: $result")
        }
        
        result
    } catch (e: Exception) {
        Log.e(TAG, "支付异常", e)
        PaymentResult.Failed("支付异常: ${e.message}")
    }
}
```

### 4. 资源清理

```kotlin
class WeChatPayChannel : IPaymentChannel {
    private var wxApi: IWXAPI? = null
    
    override fun pay(...): PaymentResult {
        // 使用wxApi
    }
    
    // 提供清理方法(可选)
    fun release() {
        wxApi?.detach()
        wxApi = null
    }
}
```

### 5. 线程安全

```kotlin
class AlipayChannel : IPaymentChannel {
    // 支付宝SDK需要在子线程调用
    override fun pay(...): PaymentResult {
        return runBlocking {
            withContext(Dispatchers.IO) {
                // 在IO线程执行支付
                doAlipayPayment()
            }
        }
    }
}
```

### 6. 版本兼容

```kotlin
override fun pay(...): PaymentResult {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+的实现
            doPaymentV2()
        } else {
            // 旧版本的实现
            doPaymentV1()
        }
    } catch (e: Exception) {
        PaymentResult.Failed("支付异常: ${e.message}")
    }
}
```

---

## 注册渠道

### 方案1：自动注册（KSP + 渠道注册表 + 懒代理，推荐）

1) 在渠道实现类上添加注解：

```kotlin
import com.xiaobai.paycore.channel.IPaymentChannel
import com.xiaobai.paycore.channel.PaymentChannelService

@PaymentChannelService(channelId = "wxpay")
class WeChatPayChannel : IPaymentChannel {
    // ...
}
```

2) 在渠道模块开启 KSP，并引入处理器：

```kotlin
plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":channel-spi"))
    ksp(project(":channel-spi-processor"))
}
```

> 处理器会生成渠道注册表，`PaymentSDK.init()` 会自动注册懒加载代理，无需手动调用注册方法。

> 实例化时机：SDK 只注册懒加载代理，真实渠道实例在调用 `pay()` 时由生成的工厂直接创建；UI 显示的渠道名/图标请使用后端返回的渠道元数据。详见 [渠道加载方案](./CHANNEL_LOADING.md) 与 [实例化流程](./CHANNEL_INSTANTIATION_FLOW.md)。

---

## 常见问题

### Q1: 如何处理支付宝的子线程要求？

**A**: 使用`runBlocking`或在调用前确保在IO线程：

```kotlin
override fun pay(...): PaymentResult {
    return runBlocking {
        withContext(Dispatchers.IO) {
            // 支付宝SDK调用
        }
    }
}
```

### Q2: 微信回调Activity必须放在wxapi包下吗？

**A**: 是的，必须放在`包名.wxapi.WXPayEntryActivity`，这是微信SDK的要求。

### Q3: 如何获取第三方SDK需要的支付参数？

**A**: 支付参数由服务端调用第三方支付API获取，通过`extraParams`传递给渠道实现。

### Q4: 支付结果如何判断？

**A**: 不要依赖第三方SDK的返回结果，SDK会通过后端查询获取实际支付状态。渠道实现只需成功调起支付即可返回`Success`。

---

## 参考资源

- [微信开放平台](https://open.weixin.qq.com/)
- [支付宝开放平台](https://open.alipay.com/)
- [银联开放平台](https://open.unionpay.com/)
- [PaymentSDK API文档](./API.md)
- [PaymentSDK架构文档](./ARCHITECTURE.md)

---

**最后更新**: 2025-11-24
