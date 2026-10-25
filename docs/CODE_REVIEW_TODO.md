# 代码审查待修清单（2026-10-24）

> 来源：对 `main`（v1.1.0）的一次全量代码审查，按优先级排列。
> 修完在条目前打 ✅，并写明修法与对应测试。
> 排期：P0–P2 → `docs/SPRINT_ISSUES.md` Sprint 10（**已完成**，分支 `feature/sprint-10-security-fixes`）；
> P3 决定**补齐实现而不是删除**，见 Sprint 11（**已完成**，分支 `feature/sprint-11-readme-capabilities`）。

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

## ✅ P3 — 文档与代码不一致（Sprint 11 补齐实现，分支 `feature/sprint-11-readme-capabilities`）

- ✅ README 宣称 Seata 分布式事务 → 结账"扣库存 + 写订单"接入 Seata AT（`CheckoutTransaction`），Redis 预扣按订单账本在回滚时归还，库存行的本地写加 `@GlobalLock`
- ✅ README 宣称 Elasticsearch 全文检索 → IK 分词索引、多字段加权、高亮、排序、MySQL 回退、事务后增量 + 每日全量同步
- ✅ README 宣称 JaCoCo 强制门禁 → `mvn verify` 增加 `check`，各模块按实测设底线，`OrderStateMachine` / `WebhookServiceImpl` ≥ 90%
- ✅ "三层防超卖"排序防死锁：扣库存现在是一个本地事务（Seata 分支），各商品行锁持有到提交，按商品 ID 排序后两个结账不会互相等待对方的行锁，这条说法成立了
- ✅ 徽章（Spring Boot 3.3.4 / Spring Cloud 2023.0.3 / v1.3.0）、补 `LICENSE`（Apache 2.0）、README 中英文措辞与实现逐项对齐（Redis 单节点、网关单实例、状态机真实状态、覆盖率表）；`HANDOFF.md` 更新到 Sprint 11
- ✅ README 宣称 Knife4j 网关聚合 → 之前 knife4j 只以 `provided` 出现在 common，没有任何服务提供文档。现在 5 个业务服务接入 springdoc + Knife4j，网关 `/doc.html` 聚合（只转发 `/v3/api-docs`，不形成鉴权旁路），生产关闭
- ✅ README 宣称 Playwright 桌面 + 移动端 E2E → 之前 `@playwright/test` 未安装、用例从未跑过。现在 CI 新增 e2e job（Desktop Chrome + Pixel 7，浏览器内模拟接口）
- ✅ README 宣称 SES 邮件 → SMTP 认证、STARTTLS、发件地址改为可配置，生产 compose 按 SES SMTP 接口配置

## Sprint 11 中额外发现并修复

- ✅ **网关根本无法启动**（Sprint 9 引入）：`SentinelGatewayConfig` 重复声明了 `SentinelSCGAutoConfiguration` 已注册的同名 Bean，Spring Boot 默认禁止覆盖。单测从不加载 Spring 上下文，所以 CI 一直是绿的。修复后新增 `GatewayContextTest` 启动完整网关上下文
- ✅ **管理后台直接打开 / 刷新一直转圈**：登录态探测只在商城布局里发起，`/admin/*` 不经过它，`RequireAdmin` 永远等不到结果。改为应用启动时探测一次（Playwright 管理端用例发现）
- ✅ **商品列表缓存从不命中**：`ProductQuery` 没有重写 `hashCode`，缓存 key 用的是对象身份哈希，每个请求都写一个新的 Redis key。改为由全部查询参数生成摘要 key

## Sprint 12 中发现并修复

- ✅ **商品列表 N+1 查询**：每个商品单独查分类 / 图片 / 标签，一页 42–50 条 SQL、每条单独借还连接，并发时大部分时间在等连接。改为批量补全（一页 6 条 SQL），吞吐 64 → 191 req/s，p99 1.57s → 392ms，见 `docs/PERF_PRODUCT_LIST.md`
- ✅ **网关端点数无限增长**：网关按真实 URL 命名端点（订单号、商品 slug），OAP 指标被打散。加了端点分组规则

## 2026-10-25 合并 PR #1–#5 后在 Docker 环境中发现并修复

- ✅ 前端 `npm run lint` 是死脚本 → 补 ESLint 10 flat 配置（typescript-eslint、react-hooks、react-refresh），修掉 25 个错误（主要是 `catch (err: any)`），CI 前端 job 加 lint；剩 4 个 `exhaustive-deps` 警告是"筛选条件变化时重新拉取"的有意写法
- ✅ "立即支付"会重新下单 → 新增 `POST /orders/{orderNo}/pay` 为原订单重新取支付链接；支付服务给同一订单发新会话前先让旧会话失效（否则两个链接都能付款），旧会话关不掉时不发新链接；支付方式沿用下单时的选择
- ✅ Seata 与 ES 在完整 Docker 环境实测：回滚、热点压测、`LIKE` / ES 耗时、索引新鲜度、ES 宕机回退，结果见 `docs/SPRINT_ISSUES.md` 11.1、11.2
- ✅ **Seata TC 2.0.0 锁泄漏导致库存卡在"已预占"**（热点压测发现）：TC 升级到 2.3.0；补偿写的全局锁等待放宽到约 2 秒；取消时归还失败改为约 10 秒后重试，不再等 30 分钟。细节见 SPRINT_ISSUES 11.1
- ✅ agent / MCP 与 Nacos 同时启动时注册失败直接退出、知识库入库失败不重试 → 都改为重试
- ✅ OAP 健康检查用了镜像里没有的 curl；全栈 compose 的 ES / Nacos 端口与 Windows 保留段冲突；缺 `.dockerignore`（Windows 的 `node_modules` 进 Linux 镜像）
- ✅ 5xx 错误提示带上网关返回的 `X-Trace-Id`；个人中心文案走 i18n
- ✅ MCP `search_products` 的类目枚举还是 PR 分支上的旧 6 类（含已并入交通指示牌的 `booze`，缺 tram / road-sign / collections / achievement / slogan），模型按"墨尔本电车"等类目筛不出来；改为与商品服务一致的 10 类，提示词里的商品线描述同步更新，并加单测守住

## 待处理

- 搜索按"销量"排序最多滞后一天（下单引起的销量变化只在每日全量对账进入 ES）。暂不改成实时：每笔结账都要在热路径上写 ES，且 Seata 全局回滚时索引不会跟着回滚
