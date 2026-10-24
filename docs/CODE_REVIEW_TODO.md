# 代码审查待修清单（2026-10-24）

> 来源：对 `main`（v1.1.0）的一次全量代码审查，按优先级排列。
> 修完在条目前打 ✅，并写明修法与对应测试。
> 排期：P0–P2 → `docs/SPRINT_ISSUES.md` Sprint 10（**已完成**，分支 `feature/sprint-10-security-fixes`）；
> P3 决定**补齐实现而不是删除**，见 Sprint 11。

## 审查时的构建状态

- 后端 `mvn verify`：8 模块全部编译通过，**1 个测试失败**（见 P0-0）
- 前端 `typecheck` / `vitest`（19/19）/ `build`：全部通过

---

## P0 — 资金与库存安全

### ✅ P0-0 时间炸弹测试导致 CI 红
- `AdminOrderServiceTest` 写死 `"2026-09-15"` 却断言它落在"最近 7 天"内，2026-09-22 起必挂。
- 修复：日期改为相对今天生成。

### ✅ P0-1 mock webhook 可免费把任意订单标记为已支付
- 原因：网关把 `/api/webhooks` 整体列入白名单；`POST /webhooks/mock` 无签名、无环境开关。
- 修复：mock 接口拆到 `MockWebhookController`，仅 `stickybeak.payment.mock-webhook-enabled=true` 时注册（默认 false，prod profile 强制 false）；网关白名单收窄到 `/api/webhooks/stripe`，mock 需要登录。
- 测试：`InternalApiGuardTest`（默认不注册）、`AuthGlobalFilterTest`（mock 未登录 401）。

### ✅ P0-2 Stripe webhook 不校验事件类型 / 金额
- 原因：任何带 `client_reference_id` 的事件（如 `checkout.session.expired`）都会推进为已支付。
- 修复：只认 `checkout.session.completed` / `async_payment_succeeded` 且 `payment_status=paid`；实付金额、币种必须与 `t_payment_record` 一致，否则拒绝（HTTP 200 + processed=2，供人工核查）。
- 测试：`WebhookCoverageTest`（expired / unpaid / 金额不符 / 币种不符 / 无支付记录 / 异步支付成功）。

### ✅ P0-3 内部库存接口经网关暴露给所有登录用户
- 原因：`/api/products/**` 路由会转发到 `/products/stock/deduct|rollback|confirm`，无任何校验。
- 修复：新增 `@InternalApi` + `InternalApiInterceptor`，内部接口必须带 `X-Internal-Call`（网关剥离外部请求的该头，与 `X-User-Id` 同一信任模型）；网关先解码、规范化路径再拦截，`%73tock`、`../`、矩阵参数都无法绕过。
- 与原计划的差异：没有做"按过期时间清理 HELD 预占"的定时任务——商品服务无法判断订单是否已支付，按时间释放可能误放已付款订单的库存。改为：结账在扣减之后任一步失败都归还库存；订单服务新增 `OrderTimeoutSweeper`，兜底关闭超时消息丢失的待支付订单并释放库存。
- 测试：`InventoryControllerGuardTest`、`AuthGlobalFilterTest`、`OrderServiceImplTest`（结账失败补偿）、`OrderTimeoutSweeperTest`。

### ✅ P0-4 支付成功消息失败会被直接丢弃
- 原因：`handleOrderPaid` 在事务内同步调用 product / cart，任一失败整体回滚；监听器 `basicNack(requeue=false)`，但队列没配 DLX → 消息丢失，用户已付款却被超时关单。
- 修复：拆成 `markPaid`（事务内条件更新为 paid）+ `completePaidOrder`（确认库存、清购物车，幂等、失败重试）；新增 `MessageRetrySupport`：失败消息转发到 TTL 重试队列（`order.status.retry.queue` / `order.close.retry.queue`，到期回到原队列），超过次数进 `order.status.dlq` / `order.close.dlq`；已取消订单收到付款直接进 DLQ 等待退款（Sprint 11.3 自动退款）。全部是新队列，不改动已有队列参数。
- 测试：`OrderListenersRetryTest`。

### ✅ P0-5 订单状态并发竞态
- 原因：取消 / 超时 / 支付都是"查询 → 内存校验 → `updateById`"；库存回滚的状态检查在锁外。
- 修复：订单状态统一走 `OrderMapper.updateIfStatus`（`WHERE order_no=? AND status=?`），影响 0 行即抛冲突、事务回滚；`t_stock_hold` 用 `transitStatus` 条件流转，并发回滚只归还一次；取消后的资源释放放到事务提交之后（`AfterCommit`）；用户不能再自行取消已付款订单。
- 测试：`OrderConcurrencyTest`（支付与超时并发只有一个生效，重复 20 次）、`InventoryConcurrencyTest`（同一订单并发回滚只归还一次）。

### ✅ P0-6 Stripe Checkout Session 有效期（24h）远长于订单（30min）
- 修复：创建会话时设置 `expires_at`（默认 31 分钟，`stripe.session-expiry-minutes`）；订单取消 / 超时后调用新的内部接口 `/payments/{orderNo}/expire-session` 让会话失效。
- 剩余风险：关单与会话失效之间的极短窗口内完成的付款会进入 DLQ，自动退款在 Sprint 11.3 实现。
- 测试：`PaymentSessionExpiryTest`。

### ✅ P0-7（修复过程中新发现）任何登录用户都能为自己的订单创建任意金额的支付会话
- 原因：`POST /api/payments/checkout-session` 经网关对外开放，请求体里的 `amount` 会覆盖支付记录；配合 P0-2 的金额校验也会被绕过（校验的就是被改过的记录）。`GET /api/payments/records/{orderNo}` 也没有归属校验。
- 修复：两个接口都改为 `@InternalApi`，网关同时直接拦截。
- 测试：`InternalApiGuardTest`、`AuthGlobalFilterTest`。

---

## ✅ P1 — 生产部署配置

- prod compose 改用 `MYSQL_*`（与 `application.yml` 和 ECS task definition 一致）
- prod compose 的 gateway 补齐 `JWT_SECRET`、`REDIS_*`、`NACOS_*`
- 所有服务接入 `spring.data.redis.password`（Redisson 同样读取）与 Nacos 用户名密码
- prod profile 下 auth / gateway 不再有默认 JWT 密钥；`JwtSecrets` 对缺失、非 Base64、短于 256 bit 的密钥启动即失败并提示生成方式；`.env.prod.example` 不再附带不可用的示例值
- prod profile：cookie 默认 `Secure`、不种开发管理员、强制关闭 mock 回调
- 网关不再暴露 `actuator/gateway` 端点
- 额外：本地全栈 `docker-compose.yml` 之前同样回落到 `localhost`，已改为按容器名连接中间件

## ✅ P2 — 逻辑小问题

- Lua 返回 `-1`（商品不存在）视为扣减失败
- 管理员调库存改为单条 SQL（直接设置 / 按增量调整），提交后删除 Redis key，下次扣减从 MySQL 预热
- Redis 补偿改为"key 存在才加"的 Lua 脚本，避免 key 过期后凭空写出错误库存
- 回滚时的 Redis 回补放到事务提交之后
- 订单号改为 `SO + yyyyMMdd + 雪花 ID`（29 位）

## P3 — 文档与代码不一致（面试/答辩风险，Sprint 11 处理）

- README 宣称 Seata 分布式事务：代码无依赖、无 `@GlobalTransactional`
- README 宣称 Elasticsearch 全文检索：无任何 Java 代码使用
- README 宣称 JaCoCo "强制覆盖率门禁"：`pom.xml` 只有 `prepare-agent` + `report`，无 `check` 规则
- "三层防超卖"中 Redisson 锁每次只持有一把，"排序防 AB-BA 死锁"不成立；真正兜底的是 MySQL 条件更新
- 徽章：Spring Boot 写 3.3.1（实际 3.3.4）、Release 写 v1.0.0（实际 v1.1.0）；README 链接 `LICENSE` 但仓库无此文件；`HANDOFF.md` 停在 Sprint 5
