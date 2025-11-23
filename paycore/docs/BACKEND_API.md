# 后端API接口规范

本文档定义了聚合支付SDK与后端服务的接口规范。

## 概述

聚合支付SDK需要后端提供以下核心接口：

1. **获取支付渠道配置**：根据业务线返回可用的支付渠道
2. **创建支付订单**：创建支付订单并返回第三方支付参数
3. **支付结果通知**：接收第三方支付的异步通知
4. **查询订单状态**：查询订单的支付状态

## 通用规范

### 请求头

所有请求应包含以下Header：

```
Content-Type: application/json
Authorization: Bearer {access_token}
X-App-Id: {app_id}
X-Request-Id: {unique_request_id}
```

### 响应格式

所有接口统一使用以下响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1234567890
}
```

**字段说明：**
- `code`: 状态码，0表示成功，非0表示失败
- `message`: 响应消息
- `data`: 响应数据
- `timestamp`: 服务器时间戳

### 错误码

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 1000 | 参数错误 |
| 1001 | 签名错误 |
| 1002 | 权限不足 |
| 2000 | 订单不存在 |
| 2001 | 订单已支付 |
| 2002 | 订单已取消 |
| 3000 | 支付渠道不可用 |
| 3001 | 支付金额错误 |
| 5000 | 服务器内部错误 |

## 接口详情

### 1. 获取支付渠道配置

根据业务线和应用ID返回可用的支付渠道配置。

**接口地址：**
```
GET /api/payment/channels
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| businessLine | String | 是 | 业务线标识（如：retail, ecommerce, o2o） |
| appId | String | 是 | 应用ID |
| platform | String | 否 | 平台（android/ios），默认android |

**请求示例：**
```
GET /api/payment/channels?businessLine=retail&appId=app_001&platform=android
```

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "channelId": "wechat_pay",
      "channelName": "微信支付",
      "enabled": true,
      "priority": 100,
      "iconUrl": "https://cdn.example.com/icons/wechat.png",
      "extraConfig": {
        "maxAmount": 50000,
        "minAmount": 1
      }
    },
    {
      "channelId": "alipay",
      "channelName": "支付宝",
      "enabled": true,
      "priority": 90,
      "iconUrl": "https://cdn.example.com/icons/alipay.png",
      "extraConfig": {
        "maxAmount": 50000,
        "minAmount": 1
      }
    },
    {
      "channelId": "union_pay",
      "channelName": "银联支付",
      "enabled": true,
      "priority": 80,
      "iconUrl": "https://cdn.example.com/icons/unionpay.png",
      "extraConfig": {}
    }
  ],
  "timestamp": 1234567890
}
```

**字段说明：**
- `channelId`: 渠道唯一标识，需要与客户端注册的渠道ID一致
- `channelName`: 渠道显示名称
- `enabled`: 是否启用
- `priority`: 优先级，数字越大优先级越高
- `iconUrl`: 渠道图标URL（可选）
- `extraConfig`: 额外配置信息（如金额限制等）

---

### 2. 创建支付订单

创建支付订单并返回调起第三方支付所需的参数。

**接口地址：**
```
POST /api/payment/create
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| orderId | String | 是 | 订单ID |
| channelId | String | 是 | 支付渠道ID |
| amount | String | 是 | 支付金额（单位：元） |
| subject | String | 否 | 订单标题 |
| body | String | 否 | 订单描述 |
| notifyUrl | String | 否 | 异步通知URL |
| returnUrl | String | 否 | 同步返回URL |
| extraParams | Object | 否 | 额外参数 |

**请求示例：**
```json
{
  "orderId": "ORDER_20231122_001",
  "channelId": "wechat_pay",
  "amount": "99.99",
  "subject": "测试商品",
  "body": "商品详情描述",
  "notifyUrl": "https://api.example.com/payment/notify",
  "extraParams": {
    "userId": "12345",
    "goodsId": "G001"
  }
}
```

**响应示例（微信支付）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "paymentId": "PAY_20231122_001",
    "channelId": "wechat_pay",
    "paymentParams": {
      "appId": "wx1234567890",
      "partnerId": "1234567890",
      "prepay_id": "wx20231122abcdef",
      "package": "Sign=WXPay",
      "nonce_str": "random_str_123",
      "timestamp": "1700654321",
      "sign": "signature_string"
    }
  },
  "timestamp": 1234567890
}
```

**响应示例（支付宝）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "paymentId": "PAY_20231122_002",
    "channelId": "alipay",
    "paymentParams": {
      "order_info": "partner=\"2088123456789012\"&seller_id=\"xxx@alipay.com\"&out_trade_no=\"ORDER_20231122_001\"&subject=\"测试商品\"&body=\"商品详情\"&total_fee=\"99.99\"&notify_url=\"https://api.example.com/payment/notify\"&service=\"mobile.securitypay.pay\"&payment_type=\"1\"&_input_charset=\"utf-8\"&it_b_pay=\"30m\"&sign=\"xxx\"&sign_type=\"RSA\""
    }
  },
  "timestamp": 1234567890
}
```

**响应示例（银联支付）：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "paymentId": "PAY_20231122_003",
    "channelId": "union_pay",
    "paymentParams": {
      "tn": "202311220000000123456"
    }
  },
  "timestamp": 1234567890
}
```

**字段说明：**
- `paymentId`: 支付订单ID（后端生成）
- `channelId`: 支付渠道ID
- `paymentParams`: 调起第三方支付所需的参数（根据不同渠道返回不同格式）

---

### 3. 支付结果通知

接收第三方支付平台的异步通知。

**接口地址：**
```
POST /api/payment/notify
```

**说明：**
- 此接口由第三方支付平台调用
- 需要验证签名
- 需要返回特定格式的响应给第三方平台
- 建议支持幂等性处理

**微信支付通知示例：**
```xml
<xml>
  <appid>wx1234567890</appid>
  <mch_id>1234567890</mch_id>
  <nonce_str>random_str</nonce_str>
  <sign>signature</sign>
  <result_code>SUCCESS</result_code>
  <out_trade_no>ORDER_20231122_001</out_trade_no>
  <transaction_id>4200001234567890</transaction_id>
  <total_fee>9999</total_fee>
  <time_end>20231122120000</time_end>
</xml>
```

**支付宝通知示例：**
```
app_id=2021001234567890&
notify_time=2023-11-22 12:00:00&
notify_type=trade_status_sync&
trade_no=2023112222001234567890&
out_trade_no=ORDER_20231122_001&
trade_status=TRADE_SUCCESS&
total_amount=99.99&
sign=xxx&
sign_type=RSA2
```

**响应示例（成功）：**
```
微信支付：
<xml>
  <return_code><![CDATA[SUCCESS]]></return_code>
  <return_msg><![CDATA[OK]]></return_msg>
</xml>

支付宝：
success

银联：
ok
```

---

### 4. 查询订单状态

查询订单的支付状态。

**接口地址：**
```
GET /api/payment/order/status
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| orderId | String | 是 | 订单ID |
| paymentId | String | 否 | 支付订单ID |

**请求示例：**
```
GET /api/payment/order/status?orderId=ORDER_20231122_001
```

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": "ORDER_20231122_001",
    "paymentId": "PAY_20231122_001",
    "channelId": "wechat_pay",
    "channelName": "微信支付",
    "amount": "99.99",
    "status": "paid",
    "transactionId": "4200001234567890",
    "paidTime": 1700654321000,
    "createTime": 1700654300000
  },
  "timestamp": 1234567890
}
```

**字段说明：**
- `orderId`: 订单ID
- `paymentId`: 支付订单ID
- `channelId`: 支付渠道ID
- `channelName`: 支付渠道名称
- `amount`: 支付金额
- `status`: 订单状态（pending-待支付, paid-已支付, cancelled-已取消, failed-支付失败）
- `transactionId`: 第三方支付交易号
- `paidTime`: 支付时间（毫秒时间戳）
- `createTime`: 创建时间（毫秒时间戳）

---

### 5. 退款申请

发起退款申请。

**接口地址：**
```
POST /api/payment/refund
```

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| orderId | String | 是 | 原订单ID |
| refundAmount | String | 是 | 退款金额 |
| refundReason | String | 否 | 退款原因 |

**请求示例：**
```json
{
  "orderId": "ORDER_20231122_001",
  "refundAmount": "99.99",
  "refundReason": "用户申请退款"
}
```

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "refundId": "REFUND_20231122_001",
    "orderId": "ORDER_20231122_001",
    "refundAmount": "99.99",
    "status": "processing"
  },
  "timestamp": 1234567890
}
```

---

## 安全规范

### 1. 签名验证

所有请求建议使用签名验证，防止数据篡改。

**签名算法：**
```
sign = MD5(key1=value1&key2=value2&...&key=api_secret)
```

**示例：**
```python
# 待签名参数
params = {
    "orderId": "ORDER_001",
    "amount": "99.99",
    "channelId": "wechat_pay"
}

# 按key排序并拼接
sorted_params = sorted(params.items())
sign_str = "&".join([f"{k}={v}" for k, v in sorted_params])
sign_str += f"&key={api_secret}"

# 计算签名
import hashlib
sign = hashlib.md5(sign_str.encode()).hexdigest().upper()
```

### 2. HTTPS

所有接口必须使用HTTPS协议，保证数据传输安全。

### 3. 频率限制

建议对接口进行频率限制，防止恶意调用：
- 获取支付渠道配置：每分钟最多60次
- 创建支付订单：每分钟最多30次
- 查询订单状态：每分钟最多100次

### 4. IP白名单

对于支付结果通知接口，建议配置第三方支付平台的IP白名单。

---

## 测试环境

建议提供测试环境用于开发调试：

**测试环境URL：**
```
https://api-test.example.com
```

**测试账号：**
- AppId: test_app_001
- API Secret: test_secret_123

**测试支付渠道：**
- 微信支付：使用微信提供的沙箱环境
- 支付宝：使用支付宝提供的沙箱环境
- 银联：使用银联提供的测试环境

---

## 最佳实践

### 1. 幂等性

支付订单创建和退款接口应该支持幂等性，相同参数的重复请求应该返回相同结果。

### 2. 异步通知重试

支付结果通知接口应该支持重试机制，如果商户服务器返回失败，第三方支付平台会重试通知。

### 3. 订单状态流转

```
待支付(pending) → 已支付(paid)
待支付(pending) → 已取消(cancelled)
待支付(pending) → 支付失败(failed)
已支付(paid) → 退款中(refunding) → 已退款(refunded)
```

### 4. 日志记录

建议记录所有支付相关的请求和响应日志，便于排查问题。

### 5. 监控告警

建议对以下情况设置监控告警：
- 支付成功率异常
- 支付接口响应时间过长
- 第三方支付通知失败率高

---

## 附录

### A. 业务线示例

| 业务线标识 | 说明 | 支持渠道 |
|-----------|------|---------|
| retail | 零售 | 微信、支付宝、银联 |
| ecommerce | 电商 | 微信、支付宝、银联 |
| o2o | O2O | 微信、支付宝 |
| enterprise | 企业 | 银联 |
| finance | 金融 | 银联 |

### B. 支付渠道ID规范

| 渠道 | channelId | 说明 |
|------|-----------|------|
| 微信支付 | wechat_pay | 微信APP支付 |
| 支付宝 | alipay | 支付宝APP支付 |
| 银联 | union_pay | 银联APP支付 |
| QQ钱包 | qq_wallet | QQ钱包支付 |
| 京东支付 | jd_pay | 京东支付 |

### C. 常见问题

**Q: 如何处理支付超时？**
A: 客户端应该在一定时间后查询订单状态，不要仅依赖支付结果回调。

**Q: 支付金额如何传递？**
A: 建议使用字符串类型，精确到分（或元），避免浮点数精度问题。

**Q: 如何防止订单重复支付？**
A: 后端需要检查订单状态，已支付的订单不允许再次支付。

**Q: 异步通知收不到怎么办？**
A: 客户端应该主动轮询查询订单状态，不要完全依赖异步通知。

