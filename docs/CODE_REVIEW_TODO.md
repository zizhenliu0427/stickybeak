# 代码审查待修清单（2026-10-24）

> 来源：对 `main`（v1.1.0）的一次全量代码审查。尚未修复，按优先级排列。
> 修复时每项单独 commit，修完在本文件对应条目前打 ✅。
> 排期：P0–P2 → `docs/SPRINT_ISSUES.md` Sprint 10；P3 决定**补齐实现而不是删除**，见 Sprint 11。

## 审查时的构建状态

- 后端 `mvn verify`：8 模块全部编译通过，**1 个测试失败**（见 P0-0）
- 前端 `typecheck` / `vitest`（19/19）/ `build`：全部通过

---

## P0 — 资金与库存安全（优先修）

### P0-0 时间炸弹测试导致 CI 红
- `stickybeak-order/src/test/java/au/com/stickybeak/order/service/AdminOrderServiceTest.java:73`
- 写死 `"2026-09-15"` 却断言它落在"最近 7 天"内，2026-09-21 之后必挂。
- 修法：用 `LocalDate.now().format(...)` 生成日期。

### P0-1 mock webhook 可免费把任意订单标记为已支付
- `AuthGlobalFilter.java:50` 把 `/api/webhooks` 整体列入白名单（无需登录）
- `WebhookController.java:31` 的 `POST /webhooks/mock` 无签名、无环境开关，生产也开着
- 复现：`curl -X POST /api/webhooks/mock -d '{"orderNo":"SO...","eventId":"x"}'` → 订单变 PAID
- 修法：mock 接口加 `@Profile("dev")` 或配置开关（默认关）；网关白名单只放行 `/api/webhooks/stripe`。

### P0-2 Stripe webhook 不校验事件类型 / 金额
- `WebhookServiceImpl.java:116` 解析了 `eventType` 但没用；`:165` 只要有 `client_reference_id` 就发 `OrderPaidEvent`
- `checkout.session.expired` 等事件也会被当成支付成功
- 修法：只处理 `checkout.session.completed` 且 `payment_status == "paid"`；核对 `amount_total` / `currency` 与 `t_payment_record` 一致。

### P0-3 内部库存接口经网关暴露给所有登录用户
- 网关 `product` 路由 `/api/products/**`（`stickybeak-gateway/src/main/resources/application.yml:35`）会转发到 `InventoryController`（`/products/stock/deduct|rollback|confirm`），无角色校验
- 风险：用伪造 orderNo 永久预占全部库存（无过期预占清理任务，`StockHold.expireTime` 没人读）；对自己的待支付订单提前 rollback 再付款 → 超卖
- 修法：网关拦截 `/api/products/stock/**`（只允许 GET 查询或全拦）；服务内部调用改走不经网关的内部路径并校验内部调用标识；补一个清理过期 HELD 预占的定时任务。

### P0-4 支付成功消息失败会被直接丢弃
- `OrderServiceImpl.handleOrderPaid`（`:311`、`:314`）在本地事务内同步调用 product / cart 服务，任一失败则整个事务回滚
- `OrderPaidListener.java:41` `basicNack(requeue=false)`，注释说进 DLQ，但 `order.status.queue`（`order/config/RabbitMQConfig.java:45`）没配 DLX → 消息丢失
- 支付侧 webhook 已标记 processed=1，Stripe 不会重发 → 用户已付款，30 分钟后订单被超时关单、库存释放
- `spring.rabbitmq.listener.simple.retry` 因 listener 自己 catch 了异常而无效
- 修法：先在事务内把订单置 PAID 并提交，再异步/可重试地做 confirmStock、clearCart；给 `order.status.queue` 配 DLX + 死信队列；区分可重试异常（requeue 或延迟重试）与不可恢复异常。

### P0-5 订单状态并发竞态
- `OrderServiceImpl` 取消（`:277-278`）、超时（`:338-339`）、支付（`:304-308`）都是"查询 → 内存校验 → `updateById`"，`Order` 无 `@Version`
- 支付与超时同时到达可能都成功；库存 rollback（`InventoryServiceImpl.java:179-202`）状态检查在锁外、`updateById` 无条件 → 可能重复归还库存
- 修法：状态更新改为 `UPDATE t_order SET status=? WHERE order_no=? AND status=?`，影响行数为 0 视为已被其他流程处理；`t_stock_hold` 同理按 `status=HELD` 条件更新。

### P0-6 Stripe Checkout Session 有效期（24h）远长于订单（30min）
- `StripePaymentProvider.java:44` 未设置 `expires_at`
- 订单已超时取消后用户仍能付款 → 扣款但无订单、无退款
- 修法：创建 session 时 `setExpiresAt(now + 30min)`（Stripe 最小值 30 分钟）；取消订单时调用 `Session.expire()`；对"已取消订单收到支付成功"走自动退款或人工告警。

---

## P1 — 生产部署配置

- `docker/docker-compose.prod.yml:21-23` 传 `DB_HOST/DB_USER/DB_PASSWORD`，各服务 `application.yml` 读 `MYSQL_*` → 会连 `localhost` / `root:root`（ECS task definition 用的是 `MYSQL_*`，没问题）
- prod compose 的 `gateway` 服务未传 `JWT_SECRET`、`REDIS_HOST` → 回落到仓库里公开的开发密钥，与 auth 签名不一致，且配置遗漏时可被伪造 token。建议 prod profile 下缺 `JWT_SECRET` 直接启动失败
- 所有服务 yml 都没有 `spring.data.redis.password`，`REDIS_PASSWORD` 无人读取
- `docker/.env.prod.example:39` 的 `JWT_SECRET` 不是合法 Base64（含 `_`），而 `JwtTokenProvider` / `JwtValidator` 用 `Decoders.BASE64` 解码 → 照抄示例启动失败
- `app.cookie.secure` 默认 `false`，prod compose 未覆盖 → 生产 cookie 不带 `Secure`
- 网关暴露了 `actuator/gateway` 端点（`gateway application.yml:72`），dev compose 映射了 8080；生产经 nginx 只代理 `/api/`，影响较小，但建议关闭

## P2 — 逻辑小问题

- `InventoryServiceImpl.java:78` Lua 返回 `-1`（商品不存在）未被当作失败，会继续走锁与 MySQL，并在 Redis 写入一个错误的库存 key
- `AdminProductServiceImpl.updateStock` 用 delta 时是读-改-写，与并发扣减存在丢失更新；应改为 `stock = stock + delta` 原子更新
- 订单号 `Math.abs(UUID.hashCode() % 1e8)` 每日空间 1 亿，量大时有碰撞概率，碰撞会导致两单共用库存预占记录
- `rollbackStock` 在 `@Transactional` 内先改 Redis，后续 DB 失败回滚时 Redis 不会回滚

## P3 — 文档与代码不一致（面试/答辩风险）

- README 宣称 Seata 分布式事务：代码无依赖、无 `@GlobalTransactional`
- README 宣称 Elasticsearch 全文检索：无任何 Java 代码使用
- README 宣称 JaCoCo "强制覆盖率门禁"：`pom.xml` 只有 `prepare-agent` + `report`，无 `check` 规则
- "三层防超卖"中 Redisson 锁每次只持有一把，"排序防 AB-BA 死锁"不成立；真正兜底的是 MySQL 条件更新
- 徽章：Spring Boot 写 3.3.1（实际 3.3.4）、Release 写 v1.0.0（实际 v1.1.0）；README 链接 `LICENSE` 但仓库无此文件；`HANDOFF.md` 停在 Sprint 5
