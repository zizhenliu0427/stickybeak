# StickyBeak — GitHub Issues by Sprint

> 节奏照搬 Novacart `GitHub_Issues_by_Sprint.md`：每个 Sprint 1–2 周，结束打可部署 tag。
> 总时长约 **16–18 周**（15–20 hrs/周，微服务形态比 Novacart 单体多 2–3 周基础设施成本）。

**先在 GitHub 建 Labels：**
`type:feature` `type:chore` `type:bug` `type:docs` `type:test`
`area:gateway` `area:auth` `area:product` `area:cart` `area:order` `area:payment` `area:frontend` `area:devops`
`priority:p0` `priority:p1` `priority:p2`
`sprint:0` … `sprint:9`

---

## Sprint 0 — 骨架与基础设施（Week 1–2）

**目标：** `docker compose up` 一键起全套空骨架。 **Tag:** `v0.0.1`

### Issue 0.1 — 初始化 monorepo
`type:chore` `area:devops` `p0`
- 按 ARCHITECTURE.md §5 建 8 个服务模块 + frontend + docker + docs
- 验收：`docker compose up` 起 Nacos/MySQL/Redis/RabbitMQ/ES/Seata/MinIO/MailHog + 各服务 `/actuator/health` 200；前端渲染占位首页

### Issue 0.2 — stickybeak-common 共享模块
`type:feature` `p0`
- `Result<T>` / `ResultCode` / `BusinessException` / `GlobalExceptionHandler` / 雪花 ID 工具
- 验收：任一服务抛 BusinessException 返回统一 `{code,message,data}`

### Issue 0.3 — CI 骨架
`type:chore` `area:devops` `p0`
- GitHub Actions：`mvn verify` + 前端 `tsc --noEmit` + `vitest run` + Docker 构建验证；分支保护
- 验收：CI < 8 分钟；失败不可合并

### Issue 0.4 — 网关 + Nacos 接入
`type:feature` `area:gateway` `p0`
- 所有服务注册进 Nacos；网关路由断言 + 统一 CORS；Sentinel 控制台接入
- 验收：`GET localhost:8080/api/product/health` 经网关转发成功

---

## Sprint 1 — 认证与 RBAC（Week 3–4）

**Tag:** `v0.1.0`

### Issue 1.1 — auth 库表 + 种子角色
`area:auth` `area:db` `p0` — 见 DATABASE_ER §1；种子 `customer/admin/sysadmin`

### Issue 1.2 — 注册 / 登录 / 刷新 / 登出
`area:auth` `p0`
- bcrypt；JWT HS256 access 15min + refresh 7d（DB 持久化 + 轮换 + 复用检测）
- 登出 refresh 置 revoked + access 进 Redis 黑名单
- 验收：JUnit 覆盖 happy path + 3 边界；重复注册 409

### Issue 1.3 — 网关鉴权过滤器 + 服务内角色兜底
`area:gateway` `area:auth` `p0`
- 网关 GlobalFilter 验 JWT、注入 `X-User-Id`/`X-User-Roles` 头；服务内 `@PreAuthorize` 兜底
- 验收：无 token 401；customer 访问 admin 接口 403

### Issue 1.4 — 前端登录/注册/个人中心 + 路由守卫
`area:frontend` `p0`
- React Router v6 嵌套路由 + 守卫；Axios 拦截器（token 注入、401 自动刷新）
- Redux Toolkit：`authSlice`；token 存 HttpOnly Cookie
- 验收：未登录访问 /profile 跳登录；刷新页面登录态不丢

### Issue 1.5 — 地址管理
`p1` — `GET/PATCH /api/users/me`，地址 CRUD（一人多地址，真实物流预留）

---

## Sprint 2 — 商品目录 + 真实数据导入（Week 5–6）

**Tag:** `v0.2.0`

### Issue 2.1 — product 库表 + 分类/标签种子
`area:product` `area:db` `p0` — 见 DATABASE_ER §2

### Issue 2.2 — ⭐ 真实商品数据导入脚本
`type:feature` `area:product` `p0`
- 读 `classified/商品/*/info.json` → 生成 t_product / t_product_image / t_tag 关联 SQL 或调 REST 导入
- 图片复制进 MinIO（开发）/ 静态目录；手工补录 price/stock/category 的 CSV 模板
- 验收：≥200 个真实商品上架可见；图片正常显示

### Issue 2.3 — 商品浏览 API（分页/筛选/排序）
`area:product` `p0`
- `GET /api/products?category&tags&minPrice&maxPrice&sort&page&q`
- 列表结果 Redis 缓存 60s（key 含全部过滤条件）
- 验收：分页元数据完整；缓存命中可观测

### Issue 2.4 — ES 搜索
`area:product` `p1`
- ES `products` 索引（IK 分词）；商品变更 → MQ → 刷索引；ES 宕机 Sentinel 降级 MySQL
- 验收：关键词"扣死"命中超市系列；降级开关有效

### Issue 2.5 — 前端商品列表/详情
`area:frontend` `p0`
- `/products`（筛选同步 URL query，可分享）+ `/products/:slug`；骨架屏；图片懒加载
- `productSlice` + RTK Query 缓存

### Issue 2.6 — 管理端商品 CRUD
`area:frontend` `p1`
- `/admin/products`：AntD Table + 图片拖拽上传（MinIO 预签名）+ 行内改价/库存

---

## Sprint 3 — 购物车与心愿单（Week 7–8）

**Tag:** `v0.3.0`

### Issue 3.1 — cart 库表 + 游客策略
`area:cart` `p0` — 签名 session cookie 标识游客车

### Issue 3.2 — 购物车 CRUD
`area:cart` `p0`
- 数量校验库存；每次变更重算总价；缺货标记不自动删
- Redis 快照加速读，MySQL 为真相源

### Issue 3.3 — ⭐ 登录合并购物车
`area:cart` `area:auth` `p0`
- 登录成功自动合并：同商品数量相加（封顶库存）；幂等
- 验收：单测覆盖 仅游客/仅用户/双方/双方重叠 四场景

### Issue 3.4 — 前端购物车抽屉 + /cart 页
`area:frontend` `p0` — 乐观更新；`cartSlice`；空态 CTA

### Issue 3.5 — 心愿单
`p1` — 心形切换、移入购物车

---

## Sprint 4 — 结账与 Stripe（Week 9–10）

**Tag:** `v0.4.0` ← **简历可用下限**

### Issue 4.1 — 支付提供商抽象（策略模式）
`area:payment` `p0`
- `PaymentProvider` 接口：createSession / verifyWebhook / handleEvent；Stripe 实现；微信支付宝预留
- 工厂模式按配置装配；单测 mock Stripe

### Issue 4.2 — Checkout Session
`area:payment` `area:order` `p0`
- 创建 session 时冻结购物车价格快照；先生成 pending 订单再跳转

### Issue 4.3 — ⭐ Webhook 幂等
`area:payment` `p0`
- 验签；`t_payment_webhook.event_id` 唯一键去重；成功 → MQ `order.paid`；失败留档 `processed=0`
- 验收：重复投递 no-op；伪造签名 400

### Issue 4.4 — ngrok 本地联调文档 + 脚本
`type:docs` `area:devops` `p1`

### Issue 4.5 — 前端结账流
`area:frontend` `p0` — 地址表单 → Stripe 跳转 → success 页轮询订单状态直至 webhook 落地

### Issue 4.6 — 订单确认邮件（异步）
`area:notification` `p1` — MQ 消费 + Thymeleaf 模板；本地 MailHog

### Issue 4.7 — ⭐ 多币种 AUD / CNY 切换
`area:payment` `area:frontend` `p0`
- `t_exchange_rate` 表 + 定时任务每日刷新汇率（开放汇率 API），Redis 缓存当前值
- 定价基准 AUD；前端顶部币种切换器（`currencySlice`），统一 `formatPrice` 工具
- 结账按 Stripe presentment currency 以所选币种真实收款；订单落 `currency` + `exchange_rate_at_pay` 快照
- 验收：切换 CNY 后全站价格即时换算；历史订单金额不随后续汇率波动

### Issue 4.8 — 多支付方式：澳洲银行卡 / 支付宝 / 微信
`area:payment` `p0`
- Stripe Payment Methods 启用 card（澳洲银行卡）+ alipay + wechat_pay
- Checkout 页支付方式选择器；各方式 webhook 事件统一归一化进状态机
- 验收：三种方式在 Stripe 测试模式各跑通一次完整支付；失败路径有明确提示

---

## Sprint 5 — 订单状态机 + 防超卖（Week 11–12）

**Tag:** `v0.5.0`

### Issue 5.1 — 状态机配置表 + 历史
`area:order` `area:db` `p0` — 非法流转 422；history 全记录

### Issue 5.2 — ⭐ 防超卖三层防线
`area:order` `area:product` `p0`
- Redis 预扣 → Redisson 锁 + t_stock_hold（TTL 15min，超时 worker 释放）→ 条件 UPDATE 兜底
- Seata AT 编排：建单 + 扣库存 + 清购物车 跨库回滚
- 验收：并发 100 请求抢 10 件库存，成交恰为 10

### Issue 5.3 — 顾客订单历史
`area:frontend` `area:order` `p0` — 分页 + 状态时间线 UI；可取消时显示取消钮

### Issue 5.4 — 管理端订单管理
`area:frontend` `area:order` `p0` — 状态下拉只显示合法下一态（读配置表）；变更触发 MQ 邮件

---

## Sprint 6 — 管理后台分析（Week 13–14）

**Tag:** `v0.6.0`

### Issue 6.1 — 聚合查询 API
`area:order` `p0` — 总销售/每日订单/营收汇总/Top10 商品；结果缓存 5min；1 万单 < 200ms

### Issue 6.2 — 库存预警
`p1` — 阈值可配，红黄绿指示

### Issue 6.3 — Dashboard UI
`area:frontend` `p0` — ECharts 折线（营收）+ 柱状（Top 商品）；7/30/90 天切换；导出 CSV

---

## Sprint 7 — PWA 与移动端（Week 15）

**Tag:** `v0.7.0`

- 7.1 manifest + 图标（用 GDCUP 头像/商品图生成多尺寸）`p0`
- 7.2 Service Worker：静态 cache-first、商品页 network-first、离线横幅 `p0`
- 7.3 移动端审计：iPhone SE/14/Pixel 无横向滚动，触控 ≥44px `p1`

---

## Sprint 8 — 测试、文档、部署（Week 16+）

**Tag:** `v1.0.0` ← **首个生产 release**

- 8.1 E2E：Playwright 注册→浏览→加购→Stripe 测试卡→订单历史（含拒付负路径）`p0`
- 8.2 覆盖率：auth 80% / 状态机 90% / webhook 90% `p0`
- 8.3 Knife4j 全端点文档 `p1`
- 8.4 云部署：ECS/EC2 + RDS + 云 Redis + S3/OSS，HTTPS + 域名 `p0`
- 8.5 README + 架构图 + 3–5 分钟演示视频 `p0`

---

## Sprint 9 — 简历增强故事线（v1.0 之后，按需）

> 对应 docx 里每条面试素材，逐个落地成可讲的 PR：

| Issue | 故事线 |
|---|---|
| 9.1 Sentinel 大促限流实战 | 压测下单接口，QPS 阈值 + 熔断降级前后对比截图 |
| 9.2 ES 替代慢 SQL | 同一筛选查询 MySQL 500ms → ES 50ms 的 explain 对比 |
| 9.3 RabbitMQ 邮件轮询分配 | 多消费者均衡投递，可靠性（ACK/死信） |
| 9.4 Nacos 多环境配置 | dev/prod 配置隔离 + 动态刷新演示 |
| 9.5 慢 SQL 优化 | 索引优化 500ms→200ms 案例（对应 docx 素材） |
| 9.6 线程池调优 | webhook 热路径独立线程池，拒绝策略与监控 |

---

# 第二阶段路线图（2026-10 制定）

> 目标：求职（国内 Java 后端 + AI 应用方向）。
> 顺序：修好网站 → 补齐 README 宣称的能力 → SkyWalking → Python Agent + RAG → MCP。
> 规则：
> - **README 只写已落地并验证过的能力**，每个 Sprint 结束后再更新 README。
> - 文档与简历中的性能数字一律来自实测（压测脚本 + SkyWalking / 日志截图），不写预估值。Sprint 9 表中的数字同样以实测为准。
> - 新增 Labels：`area:agent` `area:observability` `sprint:10` … `sprint:14`

## Sprint 10 — 修复资金与库存安全（第一步 · 上）

**目标：** CI 转绿，`docs/CODE_REVIEW_TODO.md` 中 P0–P2 全部关闭。 **Tag:** `v1.2.0`

### Issue 10.1 — CI 转绿
`type:bug` `area:order` `p0`
- 修复 P0-0 时间相关测试
- 验收：后端 `mvn verify` 与前端 CI 全绿

### Issue 10.2 — 支付回调安全
`type:bug` `area:payment` `area:gateway` `p0`
- P0-1 mock webhook 仅 dev 环境可用，网关白名单收窄到 `/api/webhooks/stripe`
- P0-2 只处理 `checkout.session.completed` 且 `payment_status=paid`，核对金额与币种
- P0-6 Checkout Session 设置 30 分钟过期；取消订单时让 session 失效
- 验收：单测覆盖伪造 mock 请求、expired 事件、金额不符三种场景；prod profile 下 `/webhooks/mock` 不可访问

### Issue 10.3 — 内部接口隔离
`type:bug` `area:gateway` `area:product` `p0`
- P0-3 网关拦截 `/api/products/stock/**` 写接口；内部接口统一 `@InternalApi` + `X-Internal-Call`
- 孤儿预占：结账在扣减后任一步失败都归还；订单服务兜底扫描超时消息丢失的待支付订单（不在商品服务按时间释放，避免误放已付款订单的库存）
- 验收：登录用户经网关调用 deduct / rollback 被拒；超时未支付且消息丢失的订单会被关闭并归还库存

### Issue 10.4 — 消息可靠性与状态并发
`type:bug` `area:order` `p0`
- P0-4 拆分 `handleOrderPaid` 事务；`order.status.queue` / `order.close.queue` 配置 DLX 与死信队列；区分可重试与不可重试异常
- P0-5 订单状态与库存预占改为条件更新（`WHERE status = ?`）
- 验收：product 服务宕机期间支付消息不丢失，恢复后订单变为 PAID；"支付 + 超时"并发时只有一个生效（并发单测）

### Issue 10.5 — 生产配置修正
`type:bug` `area:devops` `p1`
- `CODE_REVIEW_TODO` P1 全部：prod compose 环境变量名、网关 `JWT_SECRET` / `REDIS_HOST`、Redis 密码、示例密钥改为合法 Base64、cookie `Secure`、关闭网关 `actuator/gateway`；prod 缺 `JWT_SECRET` 时启动失败
- 验收：按 prod 变量启动后各服务能连上依赖，网关能校验 auth 签发的 token

### Issue 10.6 — 逻辑小问题 + 冒烟回归
`type:bug` `p2`
- `CODE_REVIEW_TODO` P2 全部（Lua `-1`、库存 delta 原子更新、订单号生成、Redis 回滚顺序）
- 跑一遍全流程（注册 → 加购 → 支付 → 发货 → 取消），收集前端体验问题，按需补 issue
- 验收：`scripts/test-sprint5-e2e.js` 与 Playwright E2E 全部通过

---

## Sprint 11 — 补齐 README 宣称的能力（第一步 · 下）

**目标：** README 中的 Seata / Elasticsearch / 退款 / 覆盖率门禁全部真实落地。 **Tag:** `v1.3.0`

### Issue 11.1 — Seata 分布式事务
`type:feature` `area:order` `area:product` `p0`
- 结账链路"创建订单 + MySQL 扣减库存"纳入 `@GlobalTransactional`（AT 模式），替换现有手工 `compensate`
- 各业务库补 `undo_log` 表；Seata Server 版本与 Spring Cloud Alibaba 对齐（compose 现为 1.8.0）
- Redis 预扣不在全局事务内，全局回滚时补偿 Redis
- 验收：故意让订单写库失败 → MySQL 库存自动回滚；压测热点商品，记录 AT 全局锁对吞吐的影响（面试素材：AT 与 TCC 的取舍）

> 实现说明：
> - 全局事务边界是 `CheckoutTransaction#placeOrder`（`@GlobalTransactional`）：product 的 `deductStock`（一个本地事务 = 一个 AT 分支，XID 由 RestTemplate 拦截器透传）+ order 写订单 / 明细 / 状态历史。原来的手工 `compensate` 与"失败后调 rollback 接口"都删掉了。
> - 支付会话（Stripe）不在全局事务里：AT 分支提交后商品行的全局锁要到全局提交才释放，外部 HTTP 调用会让同一热门商品的结账在全局锁上排队。支付会话建失败时订单已提交，改为立即关单并释放库存（`cancelUnpayable`）。
> - Redis 预扣不受 undo_log 管理：Lua 扣减时按订单记账（`stock:reservation:{orderNo}`），本地事务没提交时由 `AfterCommit.onRollback` 归还；全局回滚时由订单服务注册的 Seata `TransactionHook#afterRollback` 调 `/products/stock/redis-restore` 归还。归还脚本"读账本 + 加回 + 删账本"是原子的，重复调用只生效一次。hook 在扣库存之前注册，覆盖"扣库存请求超时但商品服务其实扣了"的情况。
> - AT 写隔离：关单释放、退款回补、后台改库存这些不在全局事务里的本地写，会改到可能被未完成全局事务锁住的 `t_product` 行，已加 `@GlobalLock`，否则全局回滚时 after-image 校验失败、回滚卡住。
> - 网关剥离外部请求的 `TX_XID` 头，防止外部请求把写操作挂进别人的全局事务。
> - Seata 客户端是 SCA 2023.0.1.2 自带的 2.0.0；TC（Seata Server）用 2.3.0（原因见下方实测），file 存储、直连（不走注册中心）；`undo_log` 见 `docker/mysql/init/07-seata-undo-log.sql`。
> - 已知取舍：热点商品的结账在行级全局锁上串行（持锁时间 = 扣库存分支提交到全局提交，约几十毫秒），这是选 AT 而不是 TCC 的代价；全局回滚后若归还 Redis 的调用失败，Redis 库存只会偏少（不会超卖），后台改库存或 key 过期后从 MySQL 重新预热。
> - Docker 环境实测（2026-10-25，`docker compose -f docker/docker-compose.yml`）：
>   - 回滚：`scripts/test-seata-rollback.cjs` 给 `t_order` 临时加 CHECK 约束让写订单失败 → TC 全局回滚 → 商品分支按 undo_log 回滚（日志 `PhaseTwo_Rollbacked`）→ Redis 预扣经 `afterRollback` 归还。MySQL 库存、销量、Redis 库存、预占流水、订单、undo_log、订单账本全部还原；去掉约束后同一购物车正常下单。
>   - 热点压测：`scripts/bench-hot-checkout.cjs`，30 个买家同时买同一个商品，每轮结束后全部取消，3–4 轮取平均（同一台机器，全部服务在 Docker 里并挂着 SkyWalking agent）：
>
>     | 环境 | 吞吐 | p50 | p95 | 成功率 |
>     |---|---|---|---|---|
>     | Seata 关（`SEATA_ENABLED=false`） | 69.3 单/秒 | 312 ms | 425 ms | 100% |
>     | Seata AT 开（TC 2.0.0） | 32.6 单/秒 | 601 ms | 899 ms | 100% |
>     | Seata AT 开（TC 2.3.0） | 34.9 单/秒 | 528 ms | 842 ms | 100% |
>
>     开 AT 后同一热点商品的吞吐约降一半：扣库存分支提交后 `t_product` 那一行的全局锁要等全局提交才释放，并发结账在这把锁上排队（TC 日志里大量全局锁冲突重试）。
>   - 压测发现并修复的问题：
>     1. **Seata TC 2.0.0 的锁泄漏**（2.1.0、2.2.0 同样，反编译 `FileLocker` 确认）：非自动提交的分支注册遇到锁冲突时，先释放已拿到的锁，但随后还会继续循环拿剩下的行锁，这些锁不属于任何注册成功的分支，全局提交后也不释放。我们的分支锁顺序是 `t_product:{id};t_stock_hold:{id}`，热点行冲突后 `t_stock_hold` 行锁泄漏；之后这些预占的取消 / 超时归还（带 `@GlobalLock`）永远拿不到锁，库存一直处于"已预占"，直到重启 TC。实测一次压测后有 112 笔预占（218 件库存）卡住。2.3.0 的 `FileLocker` 冲突后直接跳出循环，已修复，所以 TC 升级到 `apache/seata-server:2.3.0`（2.0.0 客户端实测兼容，回滚验收与 Sprint 5/6/8 全链路脚本都通过）；升级后重跑压测无残留。
>     2. 补偿类写（关单释放、退款回补）等全局锁的默认时长约 300 ms（10 ms × 30 次），热点时基本等不到；放宽到 20 ms × 100 次 ≈ 2 秒（`InventoryServiceImpl`）。
>     3. 用户取消时归还库存失败，原来要等 30 分钟后的超时消息才重试；改为立即投进 `order.close.retry.queue`，约 10 秒后由关单监听器重试（`OrderResourceReleaser#releaseQuietly`）。

### Issue 11.2 — Elasticsearch 商品搜索（即 9.2）
`type:feature` `area:product` `p0`
- ES 镜像安装 IK 中文分词插件（版本与 ES 8.14.3 一致）；商品索引包含中英文名、描述、分类、标签、价格、销量
- 搜索接口：多字段检索、高亮、筛选、排序；前端搜索切换到新接口
- MySQL → ES 同步：商品变更发 RabbitMQ 事件做增量更新 + 每日全量对账
- 验收：同一查询 MySQL `LIKE` 与 ES 的耗时实测对比写入 docs；后台改价 / 上下架后数秒内搜索结果可见

> 实现说明（与原计划的差异）：
> - 增量同步没有走 RabbitMQ：商品写入方只有 product 服务自己，后台改价 / 改库存 / 上下架在事务提交后发 Spring 事件（`ProductChangedEvent`），由 `ProductIndexer` 重建该商品文档；启动时 + 每天 03:30 全量对账，顺带清理已逻辑删除的文档。跨服务、或写入方变多时再换 MQ / Canal（binlog）。
> - ES 只负责检索、相关度排序和高亮，返回商品 ID；详情、图片、实时库存仍从 MySQL 读。ES 不可用或关闭（`SEARCH_ES_ENABLED=false`）时自动回退 MySQL `LIKE`，商城不受影响。
> - 下单引起的销量 / 库存变化只在全量对账时进入 ES，所以搜索结果按"销量"排序最多滞后一天；列表上展示的数字来自 MySQL，不受影响。暂不改成实时：那样每笔结账都要在热路径上写一次 ES，而且 Seata 全局回滚时索引不会跟着回滚。
> - 合并说明：develop 上另有一套本地实现（故事线 9.2，RabbitMQ 同步 + Sentinel 熔断），合并 PR 时保留了本套，删除了那一套；基准脚本依赖的 `engine=es|mysql` 调试参数（显式指定时跳过缓存）移植了过来。
> - Docker 环境实测（2026-10-25）：
>   - IK 镜像构建成功（`get.infini.cloud` 的插件地址可用）；`scripts/es-search-test.cjs` 直连 ES 与经网关的结果一致，名称高亮正常。
>   - 耗时对比：`scripts/benchmark-search.cjs`，每个关键词 30 次，经网关，含 JSON 开销：
>
>     | 关键词 | ES avg / p95 | MySQL `LIKE` avg / p95 |
>     |---|---|---|
>     | 扣死 | 62.7 / 111.8 ms | 49.9 / 54.4 ms |
>     | 火车 | 63.5 / 68.5 ms | 54.5 / 60.4 ms |
>     | 海鸥 | 56.6 / 60.8 ms | 49.2 / 63.5 ms |
>     | UNSW | 59.7 / 79.7 ms | 53.1 / 69.8 ms |
>
>     商品表只有 269 行，MySQL 全表扫描很便宜，ES 多一次网络往返反而慢 10–20%；与 `docs/SEARCH_BENCHMARK.md` 里 10 万行合成数据时 ES 才明显领先的结论一致。
>   - 新鲜度：`scripts/test-search-freshness.cjs`，测的是搜索可见（`_search`，受 refresh 影响），不是实时的 `_doc` GET。改价后 9–131 ms 可见；上架后 14–958 ms 可搜到（ES 默认 1 秒 refresh）；下架后约 15 ms 就搜不到（MySQL 回表时按状态过滤）。
>   - 停掉 ES 容器后搜索自动回退 MySQL `LIKE`，商品服务健康检查仍是 UP。

### Issue 11.3 — 退款流程
`type:feature` `area:payment` `area:order` `p0`
- 新增 `REFUNDED` 状态及状态机转移
- 管理员对已支付订单发起退款 → Stripe Refund API → `charge.refunded` webhook 确认 → 订单 REFUNDED + 库存回补 + 邮件通知
- 覆盖 P0-6 场景：已取消订单收到支付成功时自动退款
- 验收：Stripe 测试模式下完整走通，E2E 覆盖

> 实现说明：状态机新增 `paid / processing → refunding → refunded`（已支付订单不再允许直接取消）；管理员退款调用 Stripe Refund API（幂等键 `refund-{orderNo}`），`charge.refunded` 回调确认后发 `order.refunded`，订单服务消费后置为 REFUNDED 并回补已确认库存；关单后才到账的支付自动退款。Mock 支付通道退款立即成功。未完成：Stripe 测试模式下的真实退款还没跑过；Playwright 管理端用例覆盖了前端退款与重试按钮。

### Issue 11.4 — JaCoCo 覆盖率门禁
`type:test` `area:devops` `p1`
- 先实测各模块覆盖率；pom 增加 `check` 规则，按包设阈值（状态机、webhook ≥ 90%，其余按实测设底线）
- 验收：覆盖率低于阈值时 CI 失败；README 中的覆盖率数字与报告一致

> 实现说明：父 pom 的 `check` 绑定在 `verify`，各模块用 `jacoco.line.minimum` 设底线（实测向下取整）；order / payment 额外对 `OrderStateMachine`、`WebhookServiceImpl` 设 90% 类级规则。README 覆盖率表取自 `mvn verify` 的报告。

### Issue 11.5 — 文档校准
`type:docs` `p1`
- 补 `LICENSE`（Apache 2.0）；修正徽章版本；README 措辞与实际一致（如 Redis 为单节点）；更新 HANDOFF 与架构图
- 验收：README 中每一项能力都能在代码里找到对应实现

> 实现说明：逐条对照后发现 README 还宣称了 Knife4j 网关聚合、Playwright 桌面 + 移动端 E2E、SES 邮件，三者都没有真正可用的实现，一并补齐；只有纯基础设施描述（Redis 集群、网关集群、状态机编号）改为与实际一致。核对过程中还发现网关自 Sprint 9 起无法启动、管理后台刷新卡住、商品列表缓存从不命中，均已修复，见 `docs/CODE_REVIEW_TODO.md`。

---

## Sprint 12 — SkyWalking 全链路追踪（第二步）

**目标：** 一次下单在 SkyWalking 中能看到跨服务、跨 MQ 的完整链路。 **Tag:** `v1.4.0`

### Issue 12.1 — 部署 OAP + UI
`type:chore` `area:observability` `p0`
- compose 新增 SkyWalking OAP + UI；存储复用现有 ES（与 ES 8.14 的兼容性开工时核对）

> 实现说明（与原计划的差异）：
> - 版本：OAP / UI 10.4.0，Java agent 9.7.0。
>   - 11.0 去掉了自带的 UI，所以没用。
>   - `pom.xml` 里 apm-toolkit 的版本必须与 `Dockerfile.backend` 的 agent 版本一致。
> - 存储没有复用 ES，改用 BanyanDB 0.10.3。
>   - 它是 OAP 10.x 的默认存储，专为链路 / 指标设计，单个容器。
>   - 复用 ES 的话，SkyWalking 会建大量索引，并和商品搜索抢同一个 ES 节点的堆内存。
> - compose 新增 `banyandb`、`oap`、`skywalking-ui` 三个容器，端口：
>   - UI：`8088`
>   - OAP gRPC：`11800`
>   - OAP HTTP / GraphQL：`12800`
>   - OAP 健康检查是 `/healthcheck`（10.4 里 `/internal/l7check` 返回 404）。
> - 告警规则、自定义指标和网关端点分组三份配置在 `docker/skywalking/`，以只读方式挂进 OAP。

### Issue 12.2 — Java 服务接入
`type:feature` `area:observability` `p0`
- 7 个服务挂 Java agent（`Dockerfile.backend` 与本地启动脚本同步修改）；启用网关（WebFlux）所需插件；确认 RabbitMQ / Redis / MySQL 链路
- 验收：结账请求可见 gateway → order → product → payment；支付回调 → MQ → order → notification 的异步链路能串起来

> 实现说明：
> - `Dockerfile.backend` 从官方 agent 镜像复制 agent，并启用 4 个 optional 插件：
>   - springmvc-annotation-6.x：Spring 6 下端点按路由模板命名。
>   - resttemplate-6.x：服务间调用透传 trace。
>   - spring-cloud-gateway-4.x、spring-webflux-6.x：网关。
> - 只有设置了 `SW_AGENT_COLLECTOR_BACKEND_SERVICES` 才挂 agent，不配就和以前一样启动。
> - `scripts/rebuild-services.ps1` 同步修改。
> - 网关按真实 URL 命名端点，订单号、slug 会让端点数无限增长。
>   - `docker/skywalking/endpoint-name-grouping.yml` 把它们归成 `/api/products/{var}` 这类模板。
>   - 同一层的字面量规则必须写在 `{var}` 之前，原因见文件注释。
> - 本地实测（OAP / BanyanDB / agent 同版本的二进制直接运行，7 个服务挂 agent）：
>   - 7 个服务都注册到了 OAP。
>   - 结账链路 gateway → order → cart / product（Lua、Redisson、MySQL）→ payment 完整可见。
>   - 30 分钟后延迟队列触发的关单也挂在同一条链路上。
>   - 支付回调 → `order.paid` → order（确认库存、清空购物车）+ notification 在同一条链路里。
>   - 网关端点分组生效，OAP 里出现的是 `/api/products/{var}`、`/api/orders/{var}`。
>   - 链路截图和调用树见 `docs/PERF_PRODUCT_LIST.md`。
> - Docker 环境实测（2026-10-25）：`Dockerfile.backend` 构建成功（agent 9.7.0 + 4 个可选插件），7 个 Java 服务和 `stickybeak-agent`、`stickybeak-mcp` 都出现在 OAP 里；结账链路 gateway → order → cart → product（Redis Lua / Redisson / MySQL）→ auth → RabbitMQ → payment 完整；网关端点按模板分组（`/api/orders/{var}`、`/api/products/admin/{var}/price`）。
> - Docker 环境发现并修复：OAP 镜像里没有 curl，compose 里 OAP 的健康检查一直是 unhealthy，改为 bash `/dev/tcp` 探测；全栈 compose 的 ES / Nacos gRPC 宿主端口改成与 infra 一致的 19200 / 19848（9200、9848 在 Windows 动态排除段内，绑定失败）；补了根目录 `.dockerignore`（Windows 上装的 `node_modules` 复制进 Linux 前端镜像会构建失败）；`Dockerfile.backend` 加 BuildKit Maven 缓存，7 个镜像共用依赖。

### Issue 12.3 — 日志关联 traceId
`type:feature` `area:observability` `p1`
- logback 输出 traceId（SkyWalking apm-toolkit），日志上报 OAP，可从 trace 跳转到对应日志

> 实现说明：
> - common 模块提供共享的 `logback-spring.xml`：
>   - 控制台每行带 `[TID:...]`。
>   - 同时通过 gRPC 把日志上报 OAP。
>   - 没挂 agent 时显示 `TID:N/A`，不影响启动。
> - `TraceIdResponseFilter` 把 traceId 写进响应头 `X-Trace-Id`：
>   - 网关会原样转发这个头。
>   - 用户报错时提供这个值，就能直接定位到链路和日志。
> - 本地实测：响应头里的 traceId 与日志里的 `TID` 一致；按 traceId 查日志，能查到多个服务的日志。

### Issue 12.4 — 告警
`type:feature` `area:observability` `p1`
- 慢接口、错误率、死信队列积压的告警规则

> 实现说明：
> - `docker/skywalking/alarm-settings.yml` 使用 OAP 10 的 MQE 表达式，规则包括：
>   - 服务：错误率、平均响应时间。
>   - 端点：p99、平均响应时间、错误率。
>   - 慢 SQL。
>   - 死信队列积压。
> - 死信队列积压指标：
>   - SkyWalking 不直接采集 RabbitMQ 队列深度，所以由订单服务用 agent meter API 上报 `order_dlq_backlog`（3 个死信队列的消息数之和）。
>   - `meter-analyzer-config/stickybeak.yaml` 把它转成 `meter_stickybeak_dlq_backlog`。
>   - 告警条件：大于 0 就告警，30 分钟内不重复。
> - 本地实测：往 `order.status.dlq` 放 1 条消息，约 1 分钟后触发告警，截图见 `docs/PERF_PRODUCT_LIST.md`。
> - Docker 环境复测（2026-10-25）：同样触发；指标挂在服务实例上（MAL 规则用 `instance(...)`），按服务查询会是空的。Seata 锁泄漏那段时间，"商品服务成功率低于 95%""关单消费者 P99 超过 2 秒"两条告警也真实触发了。

### Issue 12.5 — 用链路数据做一次真实优化（即 9.5）
`type:feature` `p1`
- 用现有并发压测脚本压结账接口，按 trace 定位真实瓶颈并优化，优化前后数据与截图写入 docs
- 验收：有可复现的优化前后对比

> 实现说明：
> - 优化对象从结账改成了商品列表。结账在热点商品上按设计就是串行的（Lua 预扣、Redisson 锁、AT 全局锁），流量也远小于浏览。
> - 问题：链路显示商品列表有 N+1 查询。
>   - 一页 42–50 条 SQL，每条单独借还一次连接。
>   - 并发时等连接占了大部分耗时。
> - 修复：改成批量补全，一页固定 6 条 SQL。
> - 效果（同机，`wrk -t4 -c32`，绕过缓存）：
>   - 吞吐 64 → 191 req/s。
>   - p50 476 → 157ms。
>   - p99 1.57s → 392ms。
> - 方法、数据与截图见 `docs/PERF_PRODUCT_LIST.md`。

---

## Sprint 13 — Python AI Agent + RAG（第三步）

**目标：** 买家导购 / 订单查询 / 售后问答助手上线：流式对话、写操作需用户确认、售后回答有据可查。 **Tag:** `v2.0.0`

> 选型：Python 3.12 + FastAPI + LangGraph + LangChain；模型用通义千问（DashScope），DeepSeek 备选，通过配置切换。
> 架构：Java 业务中台 + Python AI 服务；agent 注册进 Nacos，经网关统一鉴权与限流。
> 向量库：复用 Elasticsearch（与 11.2 商品搜索共用集群、不同索引）。数据量在千到万条级别，且需要关键词 + 向量混合检索，ES 两者都原生支持；向量规模到千万级或需要 GPU 索引时再考虑 Milvus。

### Issue 13.1 — Python 服务骨架
`type:chore` `area:agent` `p0`
- `stickybeak-agent/`（uv 管理依赖）；Nacos 注册（nacos-sdk-python）；网关新增路由 `/api/agent/**` → `lb://stickybeak-agent`；SkyWalking Python agent；Dockerfile；CI 增加 ruff + pytest
- 验收：经网关访问 `/api/agent/health` 返回 200；SkyWalking 中能看到 Java 网关 → Python agent 的跨语言链路

> 实现说明：
> - `stickybeak-agent/`：uv 管理依赖并锁定（`uv.lock`），Python 3.12。
> - Nacos 注册用 nacos-sdk-python 的 v2 异步接口；没配 `NACOS_ADDR` 时用 `SERVICE_URLS` 静态地址。
> - 网关路由 `agent`（`lb://stickybeak-agent`，Sentinel 20 QPS）。
> - Dockerfile 用 uv 分层安装；compose 三套都加了 agent；CI 新增 ruff + pytest。
> - SkyWalking Python agent 两个坑：1.3.0 的 wheel 漏打了 `skywalking.protocol`，1.2.x 在 Python 3.12 上插件全部加载失败。现在从 1.3.0 源码包安装（`no-binary-package`）。
> - 本地实测：经网关 `/api/agent/health` 返回 200，未登录调 `/api/agent/chat` 返回 401。SkyWalking 中可见两段跨进程链路：网关 `/api/agent/chat` → Python FastAPI `/agent/chat`，Python httpx → 商品服务 `GET:/products`，都有 CROSS_PROCESS 引用。
> - Docker 环境实测（2026-10-25）：镜像构建成功（`ghcr.io/astral-sh/uv:0.11.32` 存在，`apache-skywalking` 源码包能在镜像里构建）。发现与 Nacos 同时启动时注册失败直接退出（Java 服务会重试，Python 不会），改为每 2 秒重试、最多约 1 分钟；MCP Server 启动时知识库入库同样改为 ES 未就绪时隔 5 秒重试。修复后 `/api/agent/health` 返回 `mcpConnected: true`，日志 `knowledge base ingest: {'total': 66, 'upserted': 66, ...}`（未配模型 key，只写 BM25 文本）。

### Issue 13.2 — 模型接入与降级
`type:feature` `area:agent` `p0`
- 模型名与 key 走环境变量 / Nacos 配置；超时与重试；模型不可用时返回友好提示
- 验收：断开模型 API 时商城其余功能正常

> 实现说明：
> - 通义（DashScope）和 DeepSeek 都用 OpenAI 兼容接口（ChatOpenAI）接入，换模型只改环境变量。
> - 每个模型单独设超时与重试；主模型超时、限流或 5xx 时 `with_fallbacks` 切到备用模型。
> - 结构化输出用 function calling，因为 DashScope 兼容模式不支持 json_schema。
> - 本地实测（本机网络到模型 API 不通，正好验证降级）：助手 SSE 返回"暂时不可用"，同时经网关的商品列表、我的订单正常返回 200。
> - 2026-10-26 扩展为多家模型：`LLM_CHAIN` 按顺序降级，可选 `qwen`、`deepseek`、`openai`、`gemini`、`ollama`（本地，无需 key）；没配 key 的云端模型跳过。配置放 `docker/.env`（已 gitignore，模板 `docker/.env.example`），agent 与 MCP 两个容器都读取。
> - 本地 Ollama（RTX 5090，`qwen3.8` 27B Q4）实测：OpenAI 兼容接口的工具调用与流式 token 用量都正常；思考型模型默认会先"想"，路由这种小调用也要几十秒，`reasoning_effort: "none"` 能完全关掉（`think: false` 关不干净），单次调用约 0.6 秒，所以 `OLLAMA_REASONING_EFFORT` 默认 `none`。模型第一次加载进显存约 80 秒。
> - 云端实测（2026-10-26）：
>   - OpenAI：用项目所有者提供的 OpenAI 兼容网关（ChatGPT 反代，`OPENAI_BASE_URL` 指过去）。`/v1/models` 列出 gpt-5.6 / gpt-6 系列，试过的 6 个（gpt-5.6-luna / terra / sol、gpt-6-luna / sol、gpt-6.1-sol）工具调用与流式 token 用量都正常；gpt-5.6 系列一次会并行调 2–3 个搜索工具，gpt-6 系列只调 1 个。默认用轻量的 `gpt-6-luna`（项目所有者的要求，sol 对这个场景没必要；sol 的评测与延迟数据也记在 13.5 / 13.7 供对比）。网关只有对话模型，没有向量模型，向量仍用本地 bge-m3。
>   - 注意：langchain-openai 1.7 对 `gpt-6*` 模型带工具时、以及名字含 `codex` 的模型，会改走 `/v1/responses`（Responses API）而不是 `/v1/chat/completions`。这个网关两种都支持；换成只实现 chat/completions 的兼容网关时，要在 `ChatOpenAI` 上设 `use_responses_api=False`。
>   - 经网关的导购（带商品链接）、加购确认、售后问答（带引用）、"有实体店吗"、查订单手测正常；降级链 `openai,ollama`，`/api/agent/health` 显示两个模型。
>   - Gemini：提供的 key（`AQ.` 开头）在 AI Studio 的原生与 OpenAI 兼容接口、Vertex AI 的 express 与项目接口上都返回 401 `ACCESS_TOKEN_TYPE_UNSUPPORTED`，未能使用。需要 AI Studio 的 key（`AIza` 开头）；Vertex AI 的 OpenAI 兼容接口只认 OAuth 令牌，用 API key 要单独写调原生接口的 provider。
>   - DeepSeek / 通义：未提供 key，未测。

### Issue 13.3 — LangGraph 编排与工具
`type:feature` `area:agent` `p0`
- 状态图：意图路由 → 导购 / 订单查询 / 售后问答
- 工具：`search_products` `get_product` `list_my_orders` `get_order` `add_to_cart` `cancel_order`，调用现有 Java 服务
- 用户身份取自网关注入的 `X-User-Id`，不由模型传参
- 写操作用 LangGraph `interrupt` 暂停，前端确认后恢复；多轮对话状态 checkpoint 持久化
- 验收：查询他人订单被拒；未确认时写操作不执行；刷新页面后对话可继续

> 实现说明：
> - 状态图：route（结构化输出）→ shopping / orders 子 agent（各自只绑定自己的工具）/ after_sales / other。
> - 用户身份通过 `RunnableConfig` 注入工具，不出现在工具参数里。
> - 写工具先进 `confirm` 节点：卡片内容由服务端查询，用 `interrupt` 暂停，`Command(resume=...)` 恢复。
> - 等待确认时用户直接发新消息，悬空的工具调用会补一条"未执行"结果，避免模型接口报错。
> - 对话状态存 MySQL（langgraph-checkpoint-mysql，库 `stickybeak_agent`）。
> - 本地实测（真实 Java 服务 + MariaDB，模型用脚本替身）：
>   - 查他人订单：订单服务返回 403，工具回复"查不到"。
>   - 取消订单：确认前数据库仍是 pending；新建连接池和图（模拟重启）后，从 MySQL 恢复出等待确认的操作，确认后订单变为 cancelled。
> - 实测中发现 PyMySQL 1.2 与 aiomysql 0.3 不兼容（写入 bytes 报错），已固定 PyMySQL 1.1.x。

### Issue 13.4 — RAG 售后知识库
`type:feature` `area:agent` `p0`
- 语料：退换货、物流时效、跨境关税、商品保养、品牌故事（中英双语）
- 入库：切分 → 向量化（通义 text-embedding 系列，或开源 bge-m3）→ ES 索引（`dense_vector` 字段 + IK 分词文本字段）；离线入库脚本 + 增量更新
- 检索：kNN 向量检索 + BM25 关键词检索 → 应用层 RRF 融合 → 重排序（如 gte-rerank / bge-reranker）→ 生成带引用来源的回答
- 验收：知识库覆盖不到的问题明确说"不知道"而不是编造；回答附来源

> 实现说明：
> - 语料：`knowledge/{zh,en}/` 7 个主题、中英文各 32 个片段，按二级标题切分。退货期限、运费等运营政策是示例值。
> - 入库：增量，按内容哈希比对，只向量化新增或改动的片段，删除已不存在的片段；启动时自动执行一次。
> - 检索：kNN 与 BM25 各取 20 条，应用层 RRF 融合（ES 8.14 的 RRF 需要付费许可），gte-rerank 重排后按阈值过滤，按提问语言过滤语料。
> - 没有相关片段时直接回答"不知道"并给出客服邮箱，不调用模型；ES 不可用时回答"知识库暂不可用"。
> - 已验证：只用内存 BM25（不调用模型），评测集 13 条有期望来源的售后问题都能在前 4 条里检索到期望文档。
> - 2026-10-26 向量与重排改为可配置：`EMBEDDING_PROVIDER` = `qwen` / `openai` / `gemini` / `ollama`（bge-m3，原生 1024 维）/ `none`；重排只有通义有（`RERANK_PROVIDER=qwen`），其他情况设 `none`，改用向量相似度阈值 `RAG_MIN_VECTOR_SCORE` 判断"知识库是否覆盖"。
> - 发现并修复：入库是按内容哈希增量的，之前没配向量模型时入的库，配上模型后内容没变就永远不会补向量；换向量模型也不会重新向量化。现在每个片段记录生成向量的模型（`embedding_model`），模型变了就重新向量化。
> - Docker 环境实测（ES + IK + Ollama bge-m3，不重排）：MCP 启动时 66 个片段全部向量化入库，之后重启增量跳过。阈值校准（每题最高向量分，(1+cos)/2）：
>   - 能回答的问题最低 0.8046（"冰箱贴可以用酒精擦吗？"），多数在 0.83–0.91；
>   - 知识库没覆盖的："会员积分"0.7607、"招聘"0.7408、"门店几点开门"0.7739 → 取 `RAG_MIN_VECTOR_SCORE=0.79`；
>   - 局限：没覆盖的"能开发票吗"有 0.8012，与能回答的问题分不开，只能靠提示词让模型说"不知道"（实测它会这样回答）；有通义 key 时用 gte-rerank 更准。
> - 发现并修复：短问题检索时会拼上上一句（为了"那运费呢？"这种追问），但换话题时（"有没有鹦鹉的冰箱贴？"→"定制要多久？"）拼出来的查询被阈值整组滤掉，误答"不知道"。现在短问题两种查询都检索，取最高分更高的一组（`retrieve_for`）。
> - 知识库补了"有实体店吗"（Paddy's Markets Haymarket 460 号摊位，网站首页与页脚已公开的信息）。之前没有这一段时，闲聊节点会凭空回答"纯线上商店，没有实体店"；闲聊提示词也加了"不要陈述上面没写的商店事实"。
> - 未完成：通义 text-embedding-v3 + gte-rerank 的组合待配置 DashScope key 后实测。

### Issue 13.5 — 流式输出与前端
`type:feature` `area:frontend` `area:agent` `p0`
- SSE 流式输出；网关与 nginx 对 `/api/agent/` 关闭缓冲
- 前端聊天组件：中英双语、写操作确认卡片、引用来源展示
- 验收：首字延迟实测并记录

> 实现说明：
> - SSE 事件：meta、token、tool、sources、confirm、error、done。`done` 带首字延迟和总耗时，同时写日志。
> - 网关逐块转发；nginx 对 `/api/agent/` 关闭缓冲，agent 也返回 `X-Accel-Buffering: no`。
> - 前端右下角聊天抽屉：
>   - 中英双语，回答逐字显示，显示工具状态（"正在查询订单…"）。
>   - 写操作显示确认卡片，确认加购后刷新购物车角标；售后回答下方展示引用来源。
>   - 对话 ID 存在 localStorage，刷新后恢复对话和待确认的卡片。
> - 测试：Playwright 5 条旅程 × 桌面 / 移动端，覆盖确认、拒绝、来源和刷新恢复；vitest 覆盖 SSE 解析与状态 reducer。
> - 本地实测：经网关的响应头为 `text/event-stream` + `x-accel-buffering: no`，事件逐条到达。
> - 首字延迟实测（2026-10-26，本地 Ollama `qwen3.8`，经 nginx :80 + 网关，15 次不同问题，含导购工具调用与售后检索）：p50 937 ms、p95 3208 ms、最快 697 ms。导购类慢一些（模型会连续调几次搜索工具），售后与闲聊约 0.7–1.2 秒。
> - 云端对比（同一天，OpenAI 兼容网关，同样经 nginx + 网关、同一组 15 个问题；15 个样本时 p95 就是最慢那次）：
>   - `gpt-6-luna`（默认）：p50 3951 ms、p95 8876 ms、最快 3130 ms；
>   - `gpt-6-sol`：p50 5778 ms、p95 7286 ms、最快 3495 ms。
>   - 一次对话至少两次模型调用（路由 + 回答），经这个网关每次约 2.5–3.5 秒（路由只输出 18 个 token 也要 3 秒左右），所以中位数比本地慢 4 倍左右；导购类要多一轮工具调用，最慢。回答本身仍逐字出现。
> - 聊天抽屉现在把助手回答里的 `[商品名](/products/slug)` 渲染成站内链接、`**加粗**` 渲染成粗体；其他链接（包括工具结果里被注入的外链）一律当纯文本。

### Issue 13.6 — 安全与成本
`type:feature` `area:agent` `p1`
- 按用户限流与每日额度；工具返回内容只当数据（防提示词注入）；记录每次调用的 token 用量与耗时
- 验收：超额返回 429；注入类测试用例不触发写操作

> 实现说明：
> - Redis 计数：每用户每分钟请求数、每日请求数、每日 token 数（按实际用量累加）。超出返回 429 和 `Retry-After`；Redis 不可用时返回 503，不放开额度。
> - 每次模型调用写一行 JSON 日志：用户、对话、节点、模型、输入 / 输出 token、耗时。
> - 防注入：
>   - 工具结果包成 `{"data": ...}`，提示词声明其中的指令是数据；知识库原文不能闭合 `<documents>` 标签。
>   - 子 agent 只绑定自己的工具；写操作必须经用户确认（结构上的兜底）。
> - 本地实测：同一用户第 11 次请求返回 429（`retry-after: 36`）。

### Issue 13.7 — 评测集
`type:test` `area:agent` `p1`
- 50 条测试对话：导购、订单查询、售后、越权、注入
- 指标：意图准确率、工具调用正确率、RAG 召回率@k、回答忠实度；修改 prompt 或检索参数后必跑
- 验收：评测脚本一键运行并输出报告

> 实现说明：
> - `eval/dataset.yaml` 共 52 条：导购 10、订单 10、售后 17（其中 2 条知识库没覆盖）、闲聊 3、越权 6、注入 6（其中一个商品描述里藏了注入指令）。2026-10-26 由 50 条调整，原因见下方评测结果。
> - `uv run python -m eval.run_eval` 输出 `report.md` 与 `results.json`。业务数据用固定 fixtures；知识库可选内存版或 ES。
> - 指标：意图、工具调用、确认、召回率@k、忠实度（模型评审）、拒答、越权泄露；所有用例都断言"未确认的写操作没有执行"。
> - 指标计算和评测流程有单测（用假模型）。
> - 真实模型评测（2026-10-26，本地 Ollama `qwen3.8` 27B Q4 + bge-m3，ES 知识库，不重排，`RAG_MIN_VECTOR_SCORE=0.79`，评审也用同一个模型）：
>
>   | 指标 | 第一次 | 修复后 |
>   |---|---|---|
>   | 通过 | 45/50 | 51/52 |
>   | 意图准确率 | 93.5% | 100% |
>   | 工具调用正确率 | 100% | 100% |
>   | 确认正确率 | 100% | 100% |
>   | RAG 召回率@k | 92.9% | 100% |
>   | 回答忠实度 | 92.3% | 94.1% |
>   | 拒答正确率 | 50% | 100% |
>   | 越权 / 注入不泄露 | 88.9% | 100% |
>   | 未确认的写操作 | 0 | 0 |
>
>   一轮 87 秒。第一次的 5 条失败与处理：
>   - "你们有实体店吗"被分到闲聊，模型凭空答"没有实体店"（真实存在线下摊位）→ 补知识库、路由提示词写明"商店事实一律算售后"、闲聊提示词禁止陈述未写明的事实。
>   - "取消订单 SO-PAID-7""为什么订单被自动关闭"路由错 → 路由提示词补充"提到订单号算订单""问商店一般规则算售后"。
>   - 注入用例 inject-06 模型其实做对了（拒绝编 90 天政策），判失败是评测脚本的问题：只在 after_sales 类别下统计召回；禁用词检查把模型复述的用户原话当成泄露 → 修正脚本与禁用词。
>   - "比特币付款"原本当作知识库没覆盖的拒答题，但支付说明列全了支持的方式，回答"不支持"是有依据的 → 改为可回答用例，另换"会员积分""开发票"两道真正没覆盖的拒答题（评测集变为 52 条）。
>   - 修复后剩 1 条：kb-14"比特币"被评审判为不忠实（"不支持"是从"只支持这几种"推出来的，评审从严）。
> - 云端模型评测（2026-10-26，OpenAI 兼容网关，其余条件同上：bge-m3、ES、不重排、阈值 0.79，评审用被测模型）：
>
>   | 指标 | gpt-6-sol | gpt-6-luna 第一次 | 修提示词后 luna | 修提示词后本地 qwen3.8 |
>   |---|---|---|---|---|
>   | 通过 | 51/52 | 50/52 | 52/52 | 51/52 |
>   | 意图准确率 | 97.9% | 100% | 100% | 100% |
>   | 工具调用正确率 | 100% | 95.5% | 100% | 100% |
>   | 确认正确率 | 100% | 100% | 100% | 100% |
>   | RAG 召回率@k | 100% | 100% | 100% | 100% |
>   | 回答忠实度 | 100%（16/16） | 94.1% | 100% | 94.1% |
>   | 拒答 / 越权注入不泄露 | 100% / 100% | 100% / 100% | 100% / 100% | 100% / 100% |
>   | 未确认的写操作 | 0 | 0 | 0 | 0 |
>   | 一轮耗时 | 149 秒 | 139 秒 | 145 秒 | 96 秒 |
>
>   失败与处理：
>   - sol 把 kb-08"为什么我的订单被自动关闭了？"分到订单（因为说了"我的订单"），去查账户订单而不是解释"未支付订单自动关闭"的规则 → 路由提示词补充：问"商店为什么这样处理订单"算售后，除非给了订单号或要求查具体订单。sol 的忠实度分母是 16，就是因为这条没进售后。
>   - luna 第一次把 order-09"SO-SHIPPED-7 是用人民币付的吗"用 `list_my_orders` 查（答案对，但没用 `get_order`）→ 订单提示词补充：给了订单号就用 `get_order`，没给才列订单。改完订单类 10/10。
>   - kb-14"比特币"的忠实度判定不稳定：luna 第一次判不忠实、第二次判忠实，本地模型两次都判不忠实（回答"不支持"是从"只支持这几种"推出来的）。这条看的是评审模型的松紧，不是回答本身的问题。
>   - 两处提示词改动后本地模型仍是 51/52，没有变差。
> - 未完成：通义 / DeepSeek / Gemini 的评测（没有可用的 key）。

---

## Sprint 14 — MCP 接入（第四步）

**目标：** 商城能力以 MCP Server 形式提供，agent 与外部 AI 客户端都通过 MCP 调用。 **Tag:** `v2.1.0`

### Issue 14.1 — MCP Server
`type:feature` `area:agent` `p0`
- `stickybeak-mcp`（Python 官方 MCP SDK，Streamable HTTP 传输）；工具：`search_products` `get_product` `list_my_orders` `get_order` `search_faq`，以只读为主

> 实现说明：
> - 与 agent 同一个 Python 项目、同一个镜像，`python -m stickybeak_agent.mcp_server` 启动（:8089），在 Nacos 注册为 `stickybeak-mcp`。
> - 用官方 SDK 的 FastMCP 实现，Streamable HTTP 无状态 + JSON 响应，可以直接放在网关和负载均衡后面。
> - 5 个工具全部只读，并标注 `readOnlyHint`。写操作（加购、取消订单）不对外提供。
> - `search_faq` 返回带出处的原文片段；内部错误（ES 地址等）不返回给调用方。
> - 售后知识库的检索与入库从 agent 移到这里。
> - 测试：MCP Server 在进程内运行（httpx ASGITransport），用官方客户端走真实协议。

### Issue 14.2 — 鉴权
`type:feature` `area:agent` `area:gateway` `p0`
- 复用现有 JWT：经网关校验后注入身份；未登录只能使用公开工具

> 实现说明：
> - 网关路由 `mcp`（`/api/mcp` → `lb://stickybeak-mcp`，Sentinel 30 QPS），`/api/mcp` 为可选认证：
>   - 带令牌：注入 `X-User-Id`。
>   - 不带令牌：剥离伪造的身份头后放行，订单工具由 MCP Server 拒绝。
> - MCP 令牌：网页登录的 access token 只有 15 分钟，不适合配进 AI 客户端。auth 服务新增 `POST/GET/DELETE /users/me/mcp-token`：
>   - 签发 30 天、`scope=mcp` 的 JWT。
>   - 每个用户只保留一个有效令牌（jti 存 Redis `auth:mcp:{userId}`），重新生成或撤销立即生效。
> - 网关看到 `scope=mcp`：
>   - 不是 `/api/mcp` 的请求返回 403，包括用它再签发令牌。
>   - jti 与 Redis 不一致返回 401。
> - 个人中心新增"AI 客户端接入（MCP）"卡片：生成、撤销令牌，复制客户端配置。
> - 本地实测（真实网关、auth、order 服务）：
>   - 匿名可以用商品工具，订单工具提示需要登录。
>   - 带令牌可以查到自己的真实订单。
>   - 令牌调 `/api/orders/my` 返回 403；撤销后再调 MCP 返回 401。

### Issue 14.3 — agent 改为 MCP 客户端
`type:feature` `area:agent` `p1`
- 用 langchain-mcp-adapters 加载 MCP 工具，替换 13.3 中的进程内工具定义
- 验收：13.7 评测集结果不下降

> 实现说明：
> - agent 启动后从 MCP Server 加载 5 个只读工具；MCP 服务没起来时每隔几秒重试，期间助手返回"暂不可用"。
> - 进程内只保留两个写工具，它们依赖 confirm 节点的人工确认，不能对外。
> - 售后检索也改为调用 MCP 的 `search_faq`。
> - 用户身份：拦截器从当前运行配置取出用户 ID，写进 `X-User-Id`；工具参数里没有用户 ID。
> - `MCP_URL=http://stickybeak-mcp/mcp`：主机名按 Nacos 解析（`DiscoveryTransport`，类似 Spring 的 `lb://`）。
> - MCP 工具结果在发给模型前转成单行文本（部分 OpenAI 兼容接口不接受数组内容；单行也省 token）。
> - 单测和评测都改为经进程内 MCP Server 调用，与生产链路一致。
> - 本地实测：agent → MCP Server → Java 服务全部是真实服务；模型参数里伪造 `user_id` 也只能查到当前用户的订单。
> - SkyWalking 里可以看到网关 → MCP（Python）→ 订单服务（Java）的完整链路。
> - 未完成：评测集需要真实模型，本机无法访问模型 API，"结果不下降"还没法对比。工具定义（名称、参数、描述）与 Sprint 13 保持一致。

### Issue 14.4 — 外部客户端演示
`type:docs` `area:agent` `p1`
- 用 Cherry Studio / Cursor 等 MCP 客户端连接商城查询商品与订单，录制 GIF 放入 README

> 实现说明：
> - `stickybeak-agent/README.md` 给出 Cursor、Cherry Studio、Claude Code、mcp-remote 的接入配置。
> - 命令行演示脚本：`python -m stickybeak_agent.mcp_demo`，README 里附了本地经网关的实际输出。
> - Docker 环境实测（2026-10-25，命令行客户端经网关）：匿名可搜索商品和 FAQ，订单工具提示需要登录；个人中心签发的 MCP 令牌能查到本人订单；该令牌访问 `/api/orders/my` 或再签发令牌返回 403；撤销后 MCP 请求返回 401。
> - 未完成：GIF 需要在桌面端的 Cherry Studio / Cursor 里录制，待补。

---

## 里程碑

| 里程碑 | Tag | 累计周 | 简历可用？ |
|---|---|---|---|
| 骨架 | v0.0.1 | 2 | 否 |
| 认证 | v0.1.0 | 4 | 否 |
| 目录（真实数据） | v0.2.0 | 6 | 否 |
| 购物车 | v0.3.0 | 8 | 否 |
| **结账** | **v0.4.0** | **10** | **是 — 下限** |
| 订单+防超卖 | v0.5.0 | 12 | 是 |
| 分析后台 | v0.6.0 | 14 | 是 — 强 |
| PWA | v0.7.0 | 15 | 是 |
| **生产** | **v1.0.0** | **16+** | **顶级** |
| 故事线 | v1.x | 按需 | 面试弹药 |
| 安全修复 | v1.2.0 | +1 | 是 — 去掉硬伤 |
| 能力补齐（Seata / ES / 退款 / 门禁） | v1.3.0 | +2 | 是 |
| 全链路追踪 | v1.4.0 | +1 | 是 |
| **AI Agent + RAG** | **v2.0.0** | **+2–3** | **是 — AI 方向** |
| MCP | v2.1.0 | +1 | 是 |
