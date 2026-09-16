# Stripe 本地调试与 Webhook 联调指南 (Local Development & Testing Guide)

本项目（StickyBeak 澳洲文创电商平台）在 Sprint 4（v0.4.0）全面接入了 Stripe 支付收单体系，采用 **Stripe Hosted Checkout（官方托管收银台）** + **Webhook 异步验签幂等落单** + **RabbitMQ 事件驱动解耦** 的高可靠架构。

本文档指导开发者在本地环境下进行全链路支付联调与测试，提供 **Stripe CLI 真实沙箱联调** 与 **内置 Mock 零依赖联调** 两种模式。

---

## 一、整体架构与数据流

```
[前端 /checkout]
       │
       ▼ (1) POST /api/orders/checkout (携带 addressId, currency, paymentMethod)
[stickybeak-order]
       ├─► (2) 冻结商品/价格/地址快照，创建 pending 订单
       ├─► (3) POST /payments/checkout-session
       │         │
       │         ▼
       │   [stickybeak-payment]
       │         └─► (4) 调用 Stripe API (Session.create) 生成托管收银台 URL
       │
       ◄─ (5) 返回 checkoutUrl
[浏览器跳转]
       │
       ▼ (6) 用户在 Stripe 页面完成支付 (澳洲银行卡 / 支付宝 / 微信支付)
[Stripe Gateway]
       │
       ▼ (7) POST /api/webhooks/stripe (带 Stripe-Signature 签名报文)
[stickybeak-payment: WebhookController]
       ├─► (8) 签名防伪校验 (Stripe.constructEvent)
       ├─► (9) 幂等查重 (t_payment_webhook.event_id 唯一索引)
       ├─► (10) 更新支付记录 t_payment_record (status = succeeded)
       └─► (11) 投递 OrderPaidEvent 至 RabbitMQ (order.topic -> order.paid)
                      │
        ┌─────────────┴─────────────┐
        ▼                           ▼
[stickybeak-order]          [stickybeak-notification]
(12) 订单 status 推进为 paid     (14) 异步渲染 HTML 邮件
(13) 调用 cart 服务清空已购商品    (15) 发送至 MailHog (localhost:1025)
        │                           │
        ▼                           ▼
[前端轮询 1.5s/次]           [MailHog Web 控制台]
检测到 paid，展示支付庆贺页     http://localhost:8025 查看确认信
```

---

## 二、模式 A：内置 Mock 快速联调（推荐 · 零依赖模式）

若您暂无 Stripe 开发者账号，或处于离线/自动化测试环境，系统内置了完备的 Mock 支付提供商。

### 1. 默认配置
`stickybeak-payment` 在未配置有效 `STRIPE_API_KEY` 或 API Key 包含 `mock` 时，会自动选用 `MockPaymentProvider`：
- 创建 Checkout Session 时直接返回本地成功跳转链接；
- 订单状态在数据库保存为 `pending`。

### 2. 模拟触发 Webhook 落单
通过网关向 `/api/webhooks/mock` 发送模拟支付成功事件（无需签名验证）：

```powershell
# PowerShell 示例：
$body = @{
    eventId = "evt_mock_test_001"
    orderNo = "SO202609160001"
    amount = 49.90
    currency = "AUD"
    email = "customer@example.com"
    transactionId = "pi_mock_12345"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/webhooks/mock" -Method Post -ContentType "application/json" -Body $body
```

或者使用 `curl`：
```bash
curl -X POST http://localhost:8080/api/webhooks/mock \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt_mock_test_001","orderNo":"SO202609160001","amount":49.90,"currency":"AUD","email":"customer@example.com"}'
```

### 3. 验证幂等性
再次发送相同 `eventId` 的报文，接口将返回 `{"received":true,"status":"ALREADY_PROCESSED"}`，且**不会**产生重复状态流转与重复 MQ 消息。

---

## 三、模式 B：Stripe 官方沙箱联调（真实收银台）

### 1. 获取 Stripe 测试秘钥
1. 访问 [Stripe Dashboard](https://dashboard.stripe.com/register) 注册开发者账号；
2. 确保左上角或右侧处于 **Test Mode（测试模式）**；
3. 进入 **Developers -> API keys**，复制：
   - Publishable key: `pk_test_...`
   - Secret key: `sk_test_...`

### 2. 配置环境变量
在启动微服务时注入环境变量（或在 `application.yml` 中配置）：
```bash
STRIPE_ENABLED=true
STRIPE_API_KEY=sk_test_51xxxxxxxxxxxxxxxxx
```

### 3. 使用 Stripe CLI 本地转发 Webhook
Stripe 需要向公网可访问的 Webhook 端点推送事件。本地开发可通过官方 CLI 实时建立通道转发至网关：

1. **下载安装 Stripe CLI**：
   - Windows (Scoop): `scoop install stripe`
   - macOS (Homebrew): `brew install stripe/stripe-cli/stripe`
   - 或从 GitHub 下载对应 Release 二进制。

2. **登录认证**：
   ```bash
   stripe login
   ```

3. **启动监听转发**：
   ```bash
   stripe listen --forward-to http://localhost:8080/api/webhooks/stripe
   ```
   控制台会输出 Webhook 签名秘钥：
   `> Ready! Your webhook signing secret is whsec_xxxxxxxxxxxxxxxxx`

4. **注入 Webhook Secret**：
   将该秘钥配置给 `stickybeak-payment`：
   ```bash
   STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxxxxxxxxxxx
   ```

5. **触发测试事件**：
   ```bash
   stripe trigger checkout.session.completed
   ```

---

## 四、查看订单确认信（MailHog）

本地环境中，所有微服务外发的邮件均统一投递至 Docker 运行的 `sb-mailhog` 容器：
- SMTP 服务端口：`localhost:1025`
- Web UI 管理界面：[http://localhost:8025](http://localhost:8025)

在 Web 界面中可直接查验：
1. 发件人：`StickyBeak <orders@stickybeak.com.au>`；
2. 收件人：结账时填写的邮箱；
3. 邮件标题：`[StickyBeak] Order Confirmed: SO...`；
4. HTML 渲染效果：包含商品明细、实付金额、支付时间及澳洲袋鼠品牌 Header。

---

## 五、测试用澳洲银行卡号（Stripe 沙箱）

在 Stripe Hosted Checkout 界面中，可以使用以下测试信息完成支付：

| 卡种 | 测试卡号 | 有效期 | CVC | 邮编 (Postcode) |
|---|---|---|---|---|
| Visa (AU) | `4242 4242 4242 4242` | 未来的月份 (例如 12/28) | 任意 3 位 (如 123) | `2000` |
| Mastercard | `5555 5555 5555 4444` | 未来的月份 | 任意 3 位 | `3000` |
| 余额不足失败测试 | `4000 0000 0000 0002` | 未来的月份 | 任意 3 位 | `2000` |
