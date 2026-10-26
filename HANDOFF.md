# HANDOFF — StickyBeak 交接文档

> 给下一个 AI 会话/协作者：读完本文 + `docs/` 三份文档即可无缝接手。
> 最近更新：2026-10-26 · main = v1.1.0；develop 已在本地合入 Sprint 10–14（PR #1–#5）以及 ES/MinIO/SKU 合并/前台改版，并在完整 Docker 环境验收，尚未推送
> ⚠️ 2026-10-24 代码审查发现的待修问题见 [`docs/CODE_REVIEW_TODO.md`](docs/CODE_REVIEW_TODO.md)；需要 Docker / 模型 key / 桌面客户端的剩余验收见 [`HANDOFF_NEXT.md`](HANDOFF_NEXT.md)

## 项目是什么

**StickyBeak** — 澳洲主题冰箱贴电商平台。作品集项目优先，架构预留真实上线余地。
命名：StickyBeak = 澳俚"爱凑热闹的人"，暗合 sticky（磁贴）+ beak（鸟喙，招牌大葵/蒜苗鸡小鸟系列）。

## 当前状态

- **v0.0.1**（main）：Sprint 0 骨架，已用 Docker Maven 验证编译 + 7 服务注册进 Nacos + 网关路由全通
- **v0.1.0**（main）：Sprint 1 认证与 RBAC 完成
- **v0.2.0**（main）：Sprint 2 商品目录 + 真实数据导入完成，全栈 E2E 联通
- **v0.3.0**（main）：Sprint 3 购物车与心愿单全栈完成，E2E 验证全通
- **v0.4.0**（main）：Sprint 4 结账与 Stripe、Webhook 幂等、多币种、多支付方式、邮件通知（达成简历可用下限！）
- **v0.5.0**（main）：Sprint 5 订单状态机、防超卖三层防线（Redis Lua 预扣 → Redisson 排序互斥锁 → MySQL 条件更新）、RabbitMQ 原生 DLX 30m 延迟关单与库存原子回滚、前端订单管理中心
- **v0.6.0**（main）：Sprint 6 管理后台（RBAC 隔离 + 销售分析 + 订单/商品管理）
- **v0.7.0**（main）：Sprint 7 PWA（manifest + Service Worker + 移动端底部导航）
- **v1.0.0**（main）：Sprint 8 生产发布（Playwright E2E + 覆盖率补测 + Knife4j + 部署文档）
- **v1.1.0**（main / 当前发布）：Sprint 9 部分——Sentinel 网关限流、RabbitMQ 手动 ACK、AWS 就绪套件（CloudFormation/ECS/ECR）
- **develop 本地追加（2026-09-17 ~ 10-25）**：故事线 9.2/9.4/9.5/9.6、类目对齐、MinIO 建品 + 视频、SKU 合并（44 个在售）、前台编辑风改版
- **Sprint 10**（`feature/sprint-10-security-fixes`，待合并后打 `v1.2.0`）：修复 `docs/CODE_REVIEW_TODO.md` 中 P0–P2（支付回调、内部接口隔离、消息重试与死信、状态条件更新、生产配置）
- **Sprint 11**（`feature/sprint-11-readme-capabilities`，基于 Sprint 10 分支，待合并后打 `v1.3.0`）：把 README 宣称的能力全部落地 —— Seata AT 结账事务、ES 商品搜索（IK + 高亮 + MySQL 回退）、退款全流程、JaCoCo 覆盖率门禁、网关 Knife4j 文档聚合、Playwright 桌面 + 移动端用户旅程、SES 邮件配置、`LICENSE`；顺带修复了"网关无法启动"（Sprint 9 引入）、"管理后台刷新一直转圈"、"商品列表缓存从不命中"
- **Sprint 12**（`feature/sprint-12-skywalking`，基于 Sprint 11 分支，待合并后打 `v1.4.0`）：SkyWalking 全链路追踪 —— OAP 10.4 + BanyanDB + UI（:8088），7 个服务挂 agent 9.7，日志带 traceId 并上报、响应头 `X-Trace-Id`，告警规则（含死信队列积压自定义指标），网关端点分组；用链路定位并修复商品列表 N+1（`docs/PERF_PRODUCT_LIST.md`）
- **Sprint 13**（`feature/sprint-13-ai-agent`，基于 Sprint 12 分支，待合并后打 `v2.0.0`）：Python AI 购物助手 `stickybeak-agent` —— FastAPI + LangGraph，导购 / 订单 / 售后 RAG，写操作 interrupt 确认，MySQL checkpoint，SSE 流式，Redis 限流与额度，SkyWalking 跨语言链路，前端聊天抽屉，50 条评测集（详见 `stickybeak-agent/README.md`）
- **Sprint 14**（`feature/sprint-14-mcp`，基于 Sprint 13 分支，待合并后打 `v2.1.0`）：MCP Server `stickybeak-mcp`（5 个只读工具，Streamable HTTP，网关 `/api/mcp`），个人中心签发 30 天 `scope=mcp` 令牌（网关限制只能访问 MCP、可撤销），agent 改为通过 langchain-mcp-adapters 加载只读工具，Cursor / Cherry Studio / Claude Code 接入说明与命令行演示
- 2026-10-25：PR #1–#5 已在本地合入 develop（与上面的本地追加一起解冲突；ES 搜索保留 Sprint 11.2 的实现，见 9.2 一节），待推送后关闭 PR、补打 tag
- 2026-10-25 ~ 26：完整 Docker 环境验收（全栈 compose 构建 + Sprint 5/6/8 全链路脚本 + Seata 回滚 / 热点压测 + ES 对比与新鲜度 + SkyWalking 链路与告警 + MCP 令牌流程），顺带修复 Seata TC 锁泄漏等问题（见坑 64–72）
- 2026-10-26：AI 助手接入多家模型（通义 / DeepSeek / OpenAI / Gemini / 本地 Ollama），本地 Ollama 跑通真实模型评测（51/52）与首字延迟实测，修了对话存档库排序规则、知识库补向量等问题（见坑 73–76）与 HANDOFF_NEXT 的 P2 待办；结果写在 `docs/SPRINT_ISSUES.md` 各 Issue 的实现说明里
- 开发分支：`develop`；功能分支：`feature/sprint-N-xxx`（Git Flow）

## Sprint 1 交付明细（均已 E2E 验证）

- **auth 库**：`docker/mysql/init/02-auth-schema.sql`（t_user/t_role/t_user_role/t_refresh_token/t_address + 角色种子）。
- **auth 服务**：注册/登录/刷新/登出；bcrypt；JWT HS256 access 15min（claims: sub/email/roles/jti）；refresh 7 天不透明随机串、DB 只存 SHA-256 哈希、轮换 + 复用检测（复用→吊销全部会话）；登出 access jti 进 Redis 黑名单（`auth:blacklist:{jti}`，TTL=剩余有效期）
- **token 载体**：HttpOnly Cookie `sb_access` / `sb_refresh`（网关也接受 `Authorization: Bearer`）
- **网关**：`AuthGlobalFilter` 剥离外部伪造的 X-User-Id/X-User-Roles → 白名单放行 → 验 JWT + Redis 黑名单 → 注入身份头。
- **服务内兜底**：auth 的 `HeaderAuthFilter` 从头建 SecurityContext + `@PreAuthorize`
- **种子**：`DataSeeder` 幂等种角色 + 开发管理员 `admin@stickybeak.au` / `Admin123!`（生产 `SEED_DEV_ADMIN=false`）
- **前端**：登录/注册/个人中心（资料编辑 + 地址 CRUD，AntD 表单）；启动 `fetchMe` 探测 → 刷新不丢登录；401 自动刷新
- **测试**：后端 13 单测；前端 5 测（含 RequireAuth 三态）

## Sprint 2 交付明细（均已 E2E 验证）

- **product 库表**：`docker/mysql/init/03-product-schema.sql`（t_category/t_product/t_product_image/t_tag/t_product_tag_rel/t_stock_hold）
- **数据导入**：`scripts/import-products.py`（导入 265 件真实小红书商品 + 603 张图片到前端 public）
- **后端服务**：`stickybeak-product`
  - REST API：分类、标签、商品分页/多维筛选/排序、详情、推荐、精选，Redis 60s 缓存
  - Sprint 3 增强：扩展 `GET /products/id/{id}` 与 `POST /products/batch` 供下游服务高效查询
- **前端体验**：`catalog.ts` 对接真实接口；🇦🇺 澳式英语/🇨🇳 中文双语切换；深浅色模式（含系统自动跟随）；Dark Reader 浏览器插件兼容（`<meta name="darkreader-lock">`）

## Sprint 3 交付明细（均已 E2E 验证）

- **cart 库表**：`docker/mysql/init/04-cart-schema.sql`
  - `t_cart`：雪花 ID、user_id（NULL 兼容游客）、session_id（NULL 兼容登录用户）、UK 防重
  - `t_cart_item`：自增 ID、cart_id、product_id、qty、price_at_add 快照、逻辑删除
  - `t_wishlist`：自增 ID、user_id、product_id、UK 防重
- **网关优化**：`AuthGlobalFilter` 增加 `OPTIONAL_AUTH_PREFIXES`（`/api/cart`、`/api/wishlist`）
  - 携带合法 JWT 时注入 `X-User-Id` 与 `X-User-Roles`
  - 未携带 token 时剥离伪造身份后直接放行，由下游 Cart 服务按游客模式处理
- **后端服务**：`stickybeak-cart`
  - `CartController`：
    - `GET /cart`：获取当前购物车（未带身份自动签发 30 天 HttpOnly Cookie `sb_guest`）
    - `POST /cart/items`：加购（数量校验库存，超限抛 1001 业务异常）
    - `PUT /cart/items/{itemId}`：修改数量（库存校验）
    - `DELETE /cart/items/{itemId}`：删除单项
    - `DELETE /cart`：清空购物车
    - `POST /cart/merge`：登录合并（将浏览器 localStorage 游客项与服务端已有购物车合并）
  - `WishlistController`：
    - `GET /wishlist`：获取用户心愿单（未登录 401）
    - `POST /wishlist/toggle/{productId}`：收藏/取消心愿单
    - `POST /wishlist/move-to-cart/{productId}`：移入购物车并从心愿单删除
  - `ProductClient`：基于 `@LoadBalanced RestTemplate` 跨服务批量拉取 Product 详情与最新库存
  - Redis 缓存：`cart:user:{userId}` 与 `cart:session:{sessionId}` 读加速，写操作即时删缓存
- **前端集成**：
  - `src/lib/cart.ts`：封装完整 Cart & Wishlist API 请求客户端
  - `src/features/cart/cartSlice.ts`：双模式升级（游客保留 localStorage，登录用户使用 async thunks 调用后端），App 启动/登录时自动触发 `mergeCartOnLogin`，登出时清空购物车态
  - `CartPage.tsx`：全面改用 async thunk，增加清空购物车二次确认弹窗
  - `ProductDetailPage.tsx`：加购走 async thunk，新增心愿单心形收藏按钮（实时检测并高亮已收藏态）
  - `i18n.ts`：补充心愿单与清空购物车英澳双语字典
- **测试**：
  - 后端：`CartServiceImplTest` 覆盖全部 CRUD + ⭐ 登录合并 4 场景（仅游客/仅用户/无交集双方/交集双方数量累加封顶库存）；`WishlistServiceImplTest` 覆盖心愿单
  - 前端：Vitest 17/17 单元测试通过，TypeScript 0 错误

## Sprint 4 交付明细（均已 E2E 验证 · 达成简历可用下限）

- **库表与基础设施**：
  - `docker/mysql/init/05-order-schema.sql`（`t_order`, `t_order_item`, `t_order_status_history`）
  - `docker/mysql/init/06-payment-schema.sql`（`t_payment_record`, `t_exchange_rate`, `t_payment_webhook`）
- **支付微服务（`stickybeak-payment`）**：
  - 策略模式收单（`PaymentProvider` 接口，`StripePaymentProvider` 与 `MockPaymentProvider`）
  - 多支付方式（Card 澳洲卡 / Alipay 支付宝 / WeChat Pay 微信支付）
  - ⭐ **Webhook 幂等验签**：`t_payment_webhook.event_id` UK 防重，成功落流水并投递 `order.paid` 至 RabbitMQ
  - 多币种实时汇率：`t_exchange_rate` 存储基准汇率（AUD→CNY），Redis 24h 缓存，定时任务同步
- **订单微服务（`stickybeak-order`）**：
  - 下单瞬间冻结商品单价/图文快照与收货地址 JSON 快照，记录汇率快照
  - 监听 RabbitMQ `order.paid` 事件推进订单为 `paid`，记录审计日志，触发 Cart 服务清空买家购物车
- **通知微服务（`stickybeak-notification`）**：
  - 消费 `order.paid` 事件，Thymeleaf 渲染澳洲主题 HTML 确认信，SMTP 发送至 MailHog（8025 查看）
- **前端全栈（`stickybeak-frontend`）**：
  - `/checkout` 结账页、`/checkout/success` 轮询庆贺页、`/checkout/cancel` 取消页，双语字典

## Sprint 5 交付明细（均已 E2E 验证 · 高并发防超卖与订单状态机）

- **商品微服务 — 防超卖三层防线（`stickybeak-product`）**：
  - 第 1 道（内存削峰）：Redis Lua 原子预扣减，拦截 99% 无效流量
  - 第 2 道（分布式防死锁）：Redisson 升序排序加锁，彻底规避 AB-BA 交叉锁死锁
  - 第 3 道（DB 乐观更新与流水审计）：MySQL 条件更新 `UPDATE t_product SET stock = stock - ? WHERE id = ? AND stock >= ?`，落地预占流水表 `t_stock_hold`（0-HELD, 1-CONFIRMED, 2-RELEASED），支持逆向自动补偿
- **订单微服务 — 状态机与延迟关单（`stickybeak-order`）**：
  - `OrderStateMachine`：严格白名单转移规则校验，拦截非法逆向跃迁（抛 1002），写审计日志
  - RabbitMQ 原生 DLX 延迟队列：`order.delay.queue` (TTL 30m) $\to$ `order.dlx.exchange` $\to$ `order.close.queue`
  - 关单监听器：`OrderTimeoutCloseListener` 幂等消费超时消息并原子释放库存
- **购物车微服务（`stickybeak-cart`）**：
  - 彻底解决 `t_cart_item.uk_cart_product` 软删除与唯一键冲突，采用物理清理与历史行复用
- **前端订单中心（`stickybeak-frontend`）**：
  - 新增 `/orders` 页面（状态筛选 Tabs、订单卡片、商品快照、30m 实时倒计时组件、取消订单 Popconfirm、继续支付入口）
- **真实环境 E2E 验证（`scripts/test-sprint5-e2e.js`）**：
  - 覆盖下单预占、主动取消回滚、非法流转拦截、Webhook 确认、20 并发争抢 5 件库存压测（严格 0 超卖）、DLX 超时关单回滚全部 6 大用例通过

## Sprint 6 交付明细（v0.6.0 · 管理后台）

- **权限隔离**：`RequireAdmin` 前端路由守卫 + 网关 `AuthGlobalFilter` 管理路径拦截 + 服务内 `@PreAuthorize` 三层
- **后台布局**：`AdminLayout`（AntD Pro 风格侧边栏）+ `lib/admin.ts` API 客户端
- **三个页面**：`AdminDashboardPage`（销售分析图表）、`AdminOrdersPage`（订单分页/多维检索/状态推进）、`AdminProductsPage`（商品上下架/库存/预警）
- **后端**：`AdminOrderController` + `AdminOrderServiceImpl`（管理端订单聚合查询与状态流转）
- **测试**：`scripts/test-sprint6-e2e.js`

## Sprint 7 交付明细（v0.7.0 · PWA）

- `manifest.webmanifest` + 多尺寸图标（`scripts/generate-pwa-icons.py` 用 GDCUP 素材生成 192/512/maskable/apple-touch）
- `sw.js`：静态资源 cache-first、商品页 network-first、离线兜底
- 移动端：`MobileBottomNav` 底部导航（2026-09-28 编辑风改版已移除，见下文）、`OfflineBanner` + `useOnlineStatus` 离线检测、触控目标 ≥44px
- **测试**：`scripts/test-sprint7-pwa.js`

## Sprint 8 交付明细（v1.0.0 · 生产发布）

- **E2E**：Playwright `stickybeak-frontend/e2e/shopping-flow.spec.ts`（注册→浏览→加购→支付→订单历史）+ `scripts/test-sprint8-e2e.js`
- **覆盖率补测**：`AuthCoverageTest` / `OrderStateMachineCoverageTest` / `WebhookCoverageTest`
- **API 文档**：`OpenApiConfig`（Knife4j，common 模块统一接入）
- **部署**：`docker/docker-compose.prod.yml` + `nginx.prod.conf` + `.env.prod.example`；`docs/DEPLOYMENT_GUIDE.md` + `docs/DEMO_SCRIPT.md`；中英双 README
- 注意：云资源为「配置就绪」，未实际部署上线（无 HTTPS + 域名证据）

## Sprint 9 交付明细（v1.1.0 + 2026-09-17 补齐 · 简历故事线）

- **9.1 Sentinel 限流**：`SentinelGatewayConfig`（网关层规则 + 单测）
- **9.3 RabbitMQ 手动 ACK**：`OrderPaidListener` / `OrderTimeoutCloseListener` 改手动确认
- **AWS 就绪（计划外）**：`docker/aws/cloudformation-vpc-ecs.yml`、ECS task 定义 ×2、`scripts/aws-ecr-push.sh`、`scripts/verify-aws-readiness.js`、CI 增强
- **9.2 ES 全文搜索 + 基准**（2026-09-17 补；2026-10-25 合并 PR 时实现换成 Sprint 11.2 的版本）：
  - 本地与 PR #2 各写了一套 ES 搜索，合并时保留 PR #2 的 `product/search/*`（事务提交后重建文档 + 启动/每日全量对账 + 名称高亮，ES 异常时回退 MySQL LIKE）。原来的 MQ `product.changed` 同步和 Sentinel 熔断随之删除
  - 保留下来的本地能力：`engine=es|mysql` 调试参数（显式指定时跳过缓存，基准脚本依赖它）、`POST /api/products/admin/reindex`（现在调用 `ProductIndexer.reindexAll()`）
  - ES 容器装 IK 插件，持久化靠 `docker/elasticsearch/Dockerfile`（compose 已改 build 方式）
  - 基准：`scripts/benchmark-search.cjs`（真实数据）+ `benchmark-search-scale.cjs`（10 万行合成）→ `docs/SEARCH_BENCHMARK.md`（诚实结论：小数据量持平，10 万行 MySQL 全扫 118ms vs ES 52ms）
- **9.4 Nacos 多环境**（2026-09-17 补）：`ConfigDemoController` + `scripts/demo-nacos-multi-env.cjs` → `docs/NACOS_MULTI_ENV.md`（热刷新实测免重启；prod 命名空间隔离实测——prod 实例对 public 网关直接不可见）
- **9.5 慢 SQL 案例**（2026-09-17 补）：`scripts/demo-sql-optimization.cjs` → `docs/SQL_OPTIMIZATION.md`。
  - 主案例：趋势查询覆盖索引 152ms→72ms（50 万行），生产迁移 `07-order-analytics-index.sql` 已应用
  - 番外反例：Top-N 聚合加索引反而 607→732ms（优化器翻转 join 顺序），正解是预聚合汇总表
- **9.6 webhook 线程池**（2026-09-17 补）：`WebhookExecutorConfig`（core4/max8/queue200/CallerRuns 背压）+ 接收端「验签+幂等落档 → 异步处理」+ `GET /api/webhooks/pool-stats` 监控 → `docs/THREADPOOL_WEBHOOK.md`；60 并发实测 0 丢失 0 拒绝
  - 2026-10-25 合并 PR #1 后改为**可选**：默认同步处理（PR #1 的语义：失败回滚、返回 5xx，由 Stripe 重试），`WEBHOOK_ASYNC_ENABLED=true` 时才走线程池并返回 `ACCEPTED`。两种模式跑的是同一段校验逻辑（事件类型、金额、币种、退款回调），见 `WebhookServiceImpl`；`pool-stats` 只在异步模式注册，且需要管理员

## 数据源（真实商品数据，差异化亮点 · 2026-09-17 双 AI 对账核实）

小红书 GDCUP 爬取项目在 **`C:\Users\lzz28\gdcup-fridge-magnets\`**：
- **规范数据源**：`data/note_details.json`（294 篇唯一笔记全量：正文/标签/图集URL）+ `media/notes/`（294 夹 / 1122 张图）
- `classified/` 为分类拷贝（最新一次重跑后）：商品 **268** 篇全部有正文+图集，其他内容 26 篇（25 完整）
- 唯一缺口：`6a806381000000000801111c`（其他内容类，非商品）正文/图集空、封面在；需要时用爬取项目 `scripts/save_one_note.py` 单补
- 主页 296 条 = 294 唯一 + 2 篇置顶重复；封面 296 张 + 头像 1 张
- 爬取项目审计脚本：`gdcup-fridge-magnets\scripts\audit-crawl.cjs`（`node` 直接跑）
- **差值提醒**：店铺库导入的是旧口径 265 件；最新分类 268 篇 → 有 3 篇可增量导入
- Sprint 2 导入用的是 `scripts/import-products.py` → `generated-product-data.sql`

## 2026-09-17 追加：管理端新建商品 + 图片/视频上传（MinIO 预签名直传）

- **链路**：admin 前端 `ProductCreateDrawer` → `POST /products/admin/upload-url` 拿预签名 PUT URL → 浏览器直传 MinIO → `POST /products/admin` 建商品（`media[{url,mediaType}]` + 标签自动 upsert + slug 缺省自动生成 `p-{id}`）→ 事务提交后刷 ES 文档
- **媒体**：`t_product_image.media_type` = `image` | `video`（存量默认 image）。封面只能是图片；详情页画廊可播 mp4/mov/webm。卡片封面取第一张图，没有图时退化播视频首帧。
- **MinIO 接线**：product 服务 `MinioConfig` 双 client（internal 管理 / presign 签发），`StorageServiceImpl` 启动时建 bucket `stickybeak` + 匿名读策略；允许 jpeg/png/webp/gif + mp4/mov/webm。容器 env：`MINIO_ENDPOINT=http://sb-minio:9000`、`MINIO_PUBLIC_ENDPOINT=http://localhost:9000`
- **雪花 ID 精度**：`JacksonLongConfig` 全局 Long→String；前端实体 ID 一律 `string`。MP Page / 列表 `total` 等计数 Long 在 API 边界 `Number()` 归一化。存量小 id 与新建雪花 id 都安全。
- **E2E**：`scripts/test-product-create-e2e.cjs`（图片建品）+ `scripts/test-product-video-e2e.cjs`（视频+图片建品，校验 media_type 与 id 字符串）
- ⚠️ **已知坑（新）**：
  28. **MinIO java SDK 预签名前会联网查 bucket region**——必须 `region("us-east-1")` 显式设置，否则容器内用 public endpoint 签发时连接失败
  29. **~~雪花 ID 撞 JS Number 精度上限~~（已修）**：Jackson 全局 Long→String + 前端 ID 改 string。计数类 Long（Page.total 等）记得 `Number()`，不要当 ID 用字符串做加减。
  30. **存量库要跑增量 SQL**：`docker/mysql/init/08-product-media-type.sql`（幂等 ADD COLUMN media_type）。全新安装已写进 `03-product-schema.sql`

## 2026-09-17 追加：SKU 视觉合并（店面 = 44 个在售 SKU，已落库）

- **背景**：导入的 265 件是「一篇笔记 = 一件商品」，同一印刷款在多篇笔记里重复出现。按封面图聚类后与店主逐类确认，合并成 44 个规范 SKU。
- **工具链**：`scripts/cluster-sku-covers.py`（封面 dHash 近重聚类，不改库 → `scripts/sku-review/listings.json` + 每类 `sheet-*.jpg` 联系表）→ `sku-review/build-proposal.py`（`sku-proposal.json`）→ `sku-review/apply-sku-merge.py`（SKU 表写在脚本里，生成 `apply-sku-merge.sql` + `canonical-skus.json`）→ `sku-review/fix-cover-sort.sql`
- **合并规则**：全部先下架（status=1），再把 44 个保留 id 改名/改 slug/改类目并上架；**不删行**（历史订单/购物车仍引用旧 id）。天崩开局（1000266）从成就合集克隆。手机壳、DIY 教程、合集/lookbook、E2E 测试商品、T2 Parramatta/T4 未上架
- **排序修复**：导入后 create_time 全相同 → 分页 LIMIT/OFFSET 重复/漏行。`fix-cover-sort.sql` 把在售商品 create_time 按 id 错开；`ProductServiceImpl` 列表加 `id DESC` 兜底排序，图集按 `is_cover DESC, sort_order` 取（前台用 `images[0]` 当封面）
- 当前上架分布：bus-sign 11 / supermarket 8 / train 7 / road-sign 6 / bird 4 / achievement 5 / tram 2 / slogan 1；**collections、phonecase 为 0**（类目 tab 点进去是空的）

## 2026-09-28 追加：前台编辑风改版（2026-10-25 收尾）

- **视觉**：纸色 `#f6f1e8` / 墨色 `#1c1916`，Cormorant Garamond + Noto Serif SC 标题、Outfit 正文（Google Fonts，`index.html` 引入）；AntD 主题同步（圆角 0）。通用类 `.eyebrow/.muted/.tone-paper/.btn-ink/.btn-ghost/.btn-cream` 在 `index.css`
- **首页**：不再拉接口，改为编辑长页 hero → series（3 个品类入口）→ craft → story → visit（Paddy's Markets 线下店），图在 `public/editorial/` 与 `public/mock-products/`
- **导航**：去掉移动端底部 Tab 栏 `MobileBottomNav`；顶栏 ≥1280px 居中导航，更窄时顶栏下方横向滚动栏（1024–1279px 居中导航会压到右侧按钮组）。语言/币种/主题收进「EN · AUD」下拉
- **品牌外链**：`src/lib/brand.ts`（GDCUP 与 Sydney Souvenirs 小红书主页用 profile ID，不用会过期的 xhslink/xsec_token；Paddy's Markets 460 号摊位地图）
- **英文商品文案**：库里商品资料只有中文 → `src/lib/productCopy.ts` 按 slug 提供 en-AU 名称/描述，覆盖全部 44 个在售 SKU。**新上架/改 slug 要补一条**，否则英文界面会露中文名
- **离线兜底目录**：后端不可达时，商品列表、详情、同类推荐退回 `src/lib/localCatalog.ts`（读 `src/mocks/catalog.json`）。404/业务错误仍按“商品不存在”处理，只有无响应/5xx 才兜底。`catalog.json` 由 `node scripts/export-shop-catalog.cjs` 从 sb-mysql 导出在售 SKU，**上下架/改价/改 slug 后重跑**（旧的 `generate-mock-catalog.cjs` 会把它退回 Sprint 1 的 16 件 mock）
- **验证**（2026-10-25）：tsc 0 错、vitest 19/19、`vite build` 通过、`test-sprint7-pwa.js` 40/40（断言已随改版更新）、product 模块单测 19/19；浏览器实测英文列表/详情、两页分页 24+20 无重复、停 product 服务后列表与详情走本地目录、375px 无横向滚动

## 环境差异（本机实测，新机器必读）

- **Redis 宿主端口 16379**（6379/6380 被 Windows Hyper-V 保留段 6346-6445 占用）；容器网络内仍是 6379
- **Nacos 宿主 gRPC 端口 19848**；**Elasticsearch 宿主端口 19200**（9200 和 9848 落在 Windows 动态排除段）
- **MinIO** 官方已停发社区版镜像 → 用 `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`
- 本机无 Maven/JDK：构建测试全部走 Docker 镜像 `maven:3.9-eclipse-temurin-17`（m2 缓存在 named volume `stickybeak-m2`）
- PowerShell 调 curl.exe 传 JSON 会被剥引号：**请求体写临时文件用 `-d '@file'`（注意加单引号避免 PS splatting 冲突）**
- 无 admin 权限，不要尝试 `net stop winnat` 等系统级操作

## 常用命令（本机）

```powershell
# 起基础设施
docker compose -f docker/docker-compose-infra.yml up -d

# 重建并重启全部服务（Windows jar 文件锁：必须停全部→构建→起全部）
powershell -File scripts/rebuild-services.ps1

# 后端全量测试（Docker 容器运行）
docker run --rm -v "${PWD}:/workspace" -v stickybeak-m2:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-17 mvn -q test

# 前端类型检查与测试
cd stickybeak-frontend; npx tsc --noEmit; npx vitest run; npm run dev
```

## 踩过的坑（别再踩）

1. **MP 3.5.7 用 `selectBatchIds`**，没有 `selectByIds`（3.5.9+ 才有）
2. `LambdaUpdateWrapper.set()` 即时解析 lambda 列，纯 Mockito 单测无 MP lambda 缓存会炸 → 用字符串列 `UpdateWrapper`
3. Security 6.x `@PreAuthorize` 抛 `AuthorizationDeniedException`（继承 `AccessDeniedException`），两个都要接
4. 跨 @RestControllerAdvice 是「@Order 序先匹配」而非最具体优先 → `SecurityExceptionHandler` 必须 `@Order(HIGHEST_PRECEDENCE)`，否则被通用 Exception 兜底吞成 500
5. common 的 MVC advice 会被 WebFlux 网关/无 security 的服务扫描到 → `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass` 防 NoClassDefFoundError
6. MySQL JDBC URL 的 characterEncoding 要 `UTF-8`（Java 字符集名），写 utf8mb4 会启动失败
7. 不用 spring-boot-starter-parent 时 `-parameters` 编译参数默认关闭 → 父 POM 已补（pluginManagement）
8. `spring-boot-maven-plugin` 不在 starter-parent 下不会自动 repackage → 父 POM 已绑定
9. 前端测试用经典 `MemoryRouter`：`createMemoryRouter` 数据路由导航撞 jsdom/undici AbortSignal 不兼容
10. `GlobalExceptionHandler` 已对齐真实 HTTP 状态码（401/403/404…），1000+ 业务码仍 HTTP 200
11. **网关 `StripPrefix=1` 会截去前缀 `/api`**：下游微服务的 Controller `@RequestMapping` 不能重复写 `/api`
12. **服务重启脚本 `rebuild-services.ps1` 需注入环境变量**：任何接入 MySQL 或 Redis 的微服务必须在 `$envMap` 显式配置 `MYSQL_HOST=sb-mysql` 和 `REDIS_HOST=sb-redis`
13. **MySQL 导入数据中文与 Emoji 乱码**：SQL 文件首部必须包含 `SET NAMES utf8mb4;`；切勿使用 PowerShell pipeline，使用 `docker cp` + `docker exec`
14. **容器内构建环境与中间件解耦**：微服务的 `@SpringBootTest` 冒烟测试在构建环境独立运行无中间件会报错，统一改造为纯单元测试
15. **Dark Reader 浏览器插件双重反色**：启用深色模式时动态向 `<head>` 注入 `<meta name="darkreader-lock">` 并设置 `color-scheme: dark`
16. **Mockito 验证 BaseMapper 歧义**：BaseMapper 拥有 `insert(T)` 和 `insert(Collection<T>)` 重载，测试中 `verify(mapper).insert(any())` 会因歧义编译报错，必须显式声明 `any(CartItem.class)`
17. **网关可选认证（Optional Auth）路由**：类似购物车这类游客与登录用户皆可访问的接口，不能简单挂入纯白名单或强制拦截，需通过 `OPTIONAL_AUTH_PREFIXES`：有 token 则验签注入身份，无 token 剥离伪造头后放行
18. **PowerShell 中 curl 传参 `@file`**：在 PowerShell 下 `@` 是 splatting 操作符，不加引号的 `@file.json` 会导致语法解析错误，必须使用单引号 `'@file.json'`
19. **Windows 火绒杀毒拦截本地 `.ps1` 并发/自动化脚本**：严禁创建复杂的新 `.ps1` 脚本进行自动化测试或压测；应统一使用 Node.js 脚本驱动（如 `scripts/test-sprint5-e2e.js`）或 Docker 容器内执行。
20. **MySQL 逻辑删除与唯一键冲突**：当数据表有 `UNIQUE KEY (a, b)` 同时开启 MyBatis-Plus `@TableLogic` 软删除时，删除行仍占用物理唯一键索引，再次添加相同键会报 `Duplicate entry`。购物车等临时表应采用物理删除 `DELETE FROM ...`。
21. **RabbitMQ 原生 DLX 延迟队列配置**：队列设置 `x-message-ttl` + `x-dead-letter-exchange` + `x-dead-letter-routing-key` 即可实现原生延迟，无需安装第三方延迟插件；消费者只监听 DLX 死信目标队列，确保消息到期后精确触发。
22. **Sentinel 网关适配器自动注册**：`SentinelSCGAutoConfiguration` 已提供 `sentinelGatewayFilter`/`SentinelGatewayBlockExceptionHandler`，自定义 `@Configuration` 不得重复声明同名 @Bean（否则 BeanDefinitionOverrideException 启动失败），只做规则加载与 BlockHandler 定制
23. **SCA 2023 config import 不会自动加载 `-{profile}` 变体**：需显式 `optional:nacos:xxx-${spring.profiles.active:dev}.yml`；且 dataId 必须在启动时已存在（哪怕为空）才会被订阅热更新
24. **Windows curl `--data-urlencode xx@file` 按控制台代码页读文件**：中文必乱码；Node `encodeURIComponent` 后 `--data-raw` 纯 ASCII 发送
25. **`query.hashCode()` 当缓存 key 是身份哈希**：ModelAttribute 绑定的 POJO 每次请求都是新对象 → 缓存永不命中。已修为字段拼接 key（listCacheKey）
26. **`mvn test` 不会重新打 jar**：容器重启前必须 `package`，否则跑的是旧代码
27. **MySQL 加索引不等于优化**：优化器可能因新索引翻转 join 顺序反而更慢（Top-N 案例实测 607→732ms），必须 EXPLAIN ANALYZE 复查；聚合类查询的终极解是预聚合汇总表
31. **分页排序必须有唯一键兜底**：批量导入后 create_time 全相同，只按 create_time 排序时 MySQL 每页顺序不稳定，LIMIT/OFFSET 会重复/漏行 → 末尾追加 `ORDER BY id DESC`
32. **前台封面取 `images[0]`，不看 `is_cover`**：改封面时要同时把封面行 sort_order 排到最前（`fix-cover-sort.sql`），后端图集查询也按 `is_cover DESC` 先排
33. **内部接口（Sprint 10）**：只供服务间调用的接口标 `@InternalApi`，调用方 RestTemplate 必须挂 `InternalCallRequestInterceptor`（订单服务已挂），否则返回 403。网关会剥离外部请求的 `X-Internal-Call`，并对 `/api/products/stock/**` 写接口、`/api/payments/checkout-session|records`、`*/expire-session` 直接拒绝。网关的路径判断都基于**解码 + 规范化后**的路径。
34. **mock 支付回调默认关闭**：本地 E2E 需 `PAYMENT_MOCK_WEBHOOK_ENABLED=true`（`rebuild-services.ps1` 与 `docker-compose.yml` 已设置）；mock 回调需要登录 cookie，且 `amount` 必须等于订单 `payAmount`，否则返回 `REJECTED`。
35. **RabbitMQ 已声明队列的 arguments 不能改**（再次声明会 `PRECONDITION_FAILED`）：Sprint 10 的重试 / 死信全部用新队列（`order.*.retry.queue`、`order.*.dlq`），没有动 `order.status.queue` / `order.close.queue`。以后要改已有队列参数，需先在管理台删除旧队列或改名。
36. **远程调用与 Redis 写入放到事务提交后**：用 `common` 的 `AfterCommit.run(...)`；没有事务时立即执行（单测里也是）。
37. **`OrderMapper.updateIfStatus` 是 default 方法**：Mockito mock 默认返回 0（视为并发冲突并抛异常），单测里要显式 `when(...).thenReturn(1)`。
38. **prod profile 没有默认 JWT 密钥**：`JWT_SECRET` 缺失 / 非 Base64 / 短于 256 bit 都会启动失败，生成：`openssl rand -base64 48`。
39. **网关能否启动要靠 `GatewayContextTest` 守住**：Sprint 9 起网关因为重复定义 `sentinelGatewayFilter`（自动配置已注册同名 Bean）一直启动失败，而单测从不加载上下文，CI 是绿的。Sentinel 的 Filter / ExceptionHandler 交给 `SentinelSCGAutoConfiguration`，自定义 429 响应以 `BlockRequestHandler` Bean 提供。
40. **Seata AT（Sprint 11）**：全局事务边界是 `CheckoutTransaction#placeOrder`，支付会话在全局提交之后创建（外部调用不要放进全局事务）。product / order 依赖 Seata Server（`SEATA_ADDR`，compose 里是 seata-server 2.0.0）；本地临时调试可 `SEATA_ENABLED=false`，但那样结账失败时商品库不会回滚。业务库要有 `undo_log` 表（`docker/mysql/init/07-seata-undo-log.sql`，老数据卷需手动执行一次）。
41. **会改 `t_product` 库存行的本地写必须加 `@GlobalLock`**（关单释放、退款回补、后台改库存都已加）：否则与未完成的结账全局事务交错后，全局回滚做 after-image 校验失败，回滚会卡住需要人工处理。
42. **Redis 预扣不受 undo_log 管理**：扣减 Lua 同时写订单账本 `stock:reservation:{orderNo}`；本地事务没提交由 `AfterCommit.onRollback` 归还，全局回滚由订单服务的 Seata `TransactionHook#afterRollback` 调 `/products/stock/redis-restore` 归还。归还脚本是原子"读 + 加回 + 删账本"，重复调用安全。Lua 里拼 key，只支持单节点 Redis。
43. **网关剥离外部请求的 `TX_XID`**，否则外部请求可以把写操作挂进别人的全局事务。
44. **商品搜索走 ES 失败会自动回退 MySQL**：`SEARCH_ES_ENABLED=false` 可整体关闭；索引需要 IK 插件（`docker/elasticsearch/Dockerfile` 构建镜像，插件下载地址在网络受限环境可能需要换成离线 zip）。ES 健康检查已关闭，ES 挂了商品服务仍是 UP。
45. **Knife4j 文档只在网关 `/doc.html` 看**：网关路由只转发 `/api-docs/{svc}/v3/api-docs/**`，千万别改成 `/api-docs/{svc}/**`——这个前缀是公开的，会变成绕过鉴权的旁路。生产 `API_DOCS_ENABLED=false`。
46. **Playwright 用例在浏览器里模拟接口**（`stickybeak-frontend/e2e/support/mockApi.ts`）：页面调用了没模拟的接口会让用例失败；PWA 的 Service Worker 会绕过 `page.route`，配置里已 `serviceWorkers: 'block'`。`@playwright/test` 版本固定，升级时 CI 会自动装对应的 Chromium。
47. **SkyWalking agent 只在设置了 `SW_AGENT_COLLECTOR_BACKEND_SERVICES` 时挂载**（`Dockerfile.backend` 的 ENTRYPOINT）。agent 版本（Dockerfile）必须与 `pom.xml` 的 `skywalking.version`（apm-toolkit）一致，否则 traceId 打不出来。
48. **Spring Boot 3 / 网关的 agent 插件在 `optional-plugins` 里**，必须复制到 `plugins/`：springmvc-annotation-6.x、resttemplate-6.x、spring-cloud-gateway-4.x、spring-webflux-6.x。少了 resttemplate 插件，订单调其他服务的链路会断开；少了 springmvc 插件，端点按真实 URL 命名。
49. **网关端点分组规则（`docker/skywalking/endpoint-name-grouping.yml`）**：同一层按书写顺序取第一个命中的节点、不回溯，所以 `/api/orders/my` 这类字面量规则必须写在 `/api/orders/{var}` 之前。新增带路径变量的网关路由时记得补规则。
50. **OAP 10.4 的健康检查是 `/healthcheck`**（`/internal/l7check` 返回 404）；告警规则用 MQE 表达式（`expression:`），旧版 `metrics-name` / `op` / `threshold` 写法不再生效。
51. **本机直接跑 BanyanDB 要去掉代理环境变量**（`HTTPS_PROXY` 等）：Go 的 gRPC 客户端会把内部连接也走代理，报 `failed to re-connect to grpc server`。
52. **支付回调里 `order.paid` 是在事务内发送的**，这是有意的：发送失败 → 事务回滚 → Stripe 重试；提交失败 → 下次回调再发一次，由消费方幂等兜住。改成提交后发送会在进程崩溃时丢消息。
53. **商品列表 / 搜索 / 精选 / 相关商品都走 `enrichProducts` 批量补全**（一页固定 4 条补全 SQL，单测守住）。不要再在循环里按商品查分类、图片、标签。
54. **AI 助手的用户身份只能来自网关的 `X-User-Id`**：工具通过 `RunnableConfig` 读取，不要把 user_id 做成工具参数（模型 / 注入的提示词就能冒充别人）。新增写操作工具时加进 `WRITE_TOOLS`，它才会先经过 confirm 节点。
55. **SkyWalking Python agent**：1.3.0 的 wheel 漏打了 `skywalking.protocol`，1.2.x 在 Python 3.12 上插件全部加载失败（只创建模块不执行），所以 `pyproject.toml` 用 `no-binary-package` 从 1.3.0 源码包安装。升级前先确认 wheel 里有 `skywalking/protocol`。
56. **PyMySQL 固定在 1.1.x**：1.2 把 `escape_bytes_prefixed` 换成了占位字符串，aiomysql 0.3 仍在调用它，checkpoint 写入 bytes 时直接报错（单测用内存 checkpointer 发现不了）。
57. **DashScope 兼容模式**：结构化输出要用 `method="function_calling"`（不支持 json_schema）；`OpenAIEmbeddings` 要设 `check_embedding_ctx_length=False`（不接受 token id），text-embedding-v3 每批最多 10 条。
58. **ES Python 客户端必须是 8.x**（`elasticsearch<9`）：9.x 客户端发 v9 兼容头，ES 8.14 会拒绝。
59. **AI 助手的 thread_id 由服务端生成，格式 `{userId}-{32 位 hex}`**：接口按前缀校验归属，不要改成客户端随意传入。
60. **MCP Server 只放只读工具**：写操作只能在自家 agent 里经用户确认后执行，不要加到 MCP（外部 AI 客户端可能不经用户确认就调用）。新增 MCP 工具要标 `readOnlyHint`，需要身份的用 `require_user(ctx)`。
61. **MCP 令牌（`scope=mcp`）只能访问 `/api/mcp`**：网关按 scope 拦截，并比对 Redis `auth:mcp:{userId}` 里的当前 jti。新增给外部客户端的入口时，不要放宽这个限制。
62. **`mcp` 固定在 1.x**：langchain-mcp-adapters 0.3 只支持 mcp<2。升级 MCP SDK 前先确认 adapters 的版本支持。
63. **agent 依赖 MCP Server 才能工作**：启动时每隔几秒重试加载工具，期间 `/agent/health` 的 `mcpConnected=false`，聊天返回"暂不可用"。本地调试先起 `python -m stickybeak_agent.mcp_server`。
64. **Seata TC 2.0–2.2 的 file 锁存储会泄漏行锁 → TC 用 2.3.0**：分支注册遇到锁冲突时先释放已拿到的锁，却继续循环拿剩下的行锁，这些锁不属于任何注册成功的分支、全局提交后也不释放。热点商品并发结账后 `t_stock_hold` 行锁泄漏，取消 / 超时 / 退款回补（带 `@GlobalLock`）永远拿不到锁，库存卡在"已预占"直到重启 TC。症状：TC 日志反复出现 `Global lock on [t_stock_hold:N] is holding by <一个早已提交的 xid>`。客户端仍是 SCA 自带的 2.0.0，与 TC 2.3.0 实测兼容。
65. **补偿写的全局锁等待要放宽**：`@GlobalLock` 默认只等约 300 ms，热点商品那一行几乎一直被某个结账持有；关单释放、退款回补已改成 20 ms × 100 次。用户取消时归还失败会立即投进 `order.close.retry.queue`（约 10 秒后重试），不再等 30 分钟后的超时消息。
66. **`SEATA_ENABLED=false` 是真的关掉**：order / product 启动日志里不再有 TM / RM 注册，结账不经过 TC；热点压测里开 / 关 AT 的吞吐差约一倍（数据见 SPRINT_ISSUES 11.1）。
67. **全栈 compose 在 Windows 上**：ES / Nacos gRPC 宿主端口用 19200 / 19848（与 infra 一致）；根目录 `.dockerignore` 必须排除 `node_modules`（Windows 原生依赖进 Linux 前端镜像会构建失败）；OAP 镜像没有 curl，健康检查用 bash `/dev/tcp`。
68. **Python 服务与中间件同时启动**：Nacos 注册与知识库入库都会重试（`ServiceResolver.start`、`_ingest_in_background`），不要改回失败即退出——Java 服务自带重试，Python 不会。
69. **`JacksonLongConfig` 把所有 Long 序列化成字符串**（防 JS 精度丢失）：Python agent 拿到的 `total`、`id` 都是字符串，计数要转数字（`java_api.search_products`）；Playwright 的模拟后端也按字符串 ID 返回，否则和真实接口不一致。
70. **webhook 线程池是可选模式**：默认同步处理（失败回滚、Stripe 重试）；`WEBHOOK_ASYNC_ENABLED=true` 才走线程池并返回 `ACCEPTED`，两种模式跑同一段校验逻辑。E2E 脚本两种返回值都接受。
71. **测 ES 新鲜度要用 `_search`**：`_doc` GET 是实时的，搜索要等 refresh（默认 1 秒）。下架后基本立刻搜不到，是因为 MySQL 回表时按上架状态过滤了。
72. **压测脚本准备数据时会撞上网关 Sentinel 限流**（auth 60 QPS、order 100 QPS），返回 code 429；准备阶段要退避重试，别把 429 当成业务失败。SkyWalking 的死信积压指标挂在服务实例上（MAL `instance(...)`），按服务查是空的。
73. **AI 助手的模型配置在 `docker/.env`**（已 gitignore，模板 `docker/.env.example`）：`LLM_CHAIN` 决定降级顺序（qwen / deepseek / openai / gemini / ollama），agent 与 stickybeak-mcp 两个容器都通过 `env_file` 读取；改完 `docker compose -f docker/docker-compose.yml up -d --force-recreate agent mcp`。容器里访问宿主机 Ollama 用 `host.docker.internal`。
74. **Ollama 思考型模型要关思考**：`reasoning_effort: "none"` 才能完全关掉（`think: false` 关不干净），否则路由这类小调用也要等几十秒；`OLLAMA_REASONING_EFFORT` 默认就是 `none`。模型第一次加载进显存约 80 秒，`LLM_TIMEOUT_SECONDS` 本地可以调大。
75. **`stickybeak_agent` 库不能写死 `utf8mb4_general_ci`**：LangGraph MySQL checkpoint 的查询用了 `json_table`，MySQL 8 里它的输出列是 `utf8mb4_0900_ai_ci`，混用直接报 "Illegal mix of collations"，每次对话都失败（PR 当初在 MariaDB 上测的，没暴露）。老数据卷跑一次 `docker/mysql/upgrade/agent-collation-mysql8.sql`。
76. **知识库入库按"内容哈希 + 向量模型"增量**：换 `EMBEDDING_PROVIDER` / `EMBEDDING_MODEL` 后 MCP 启动时会自动重新向量化；维度必须等于 `EMBEDDING_DIMENSIONS`（1024），不一致时要 `--recreate`。没有重排（只有通义有）时用 `RAG_MIN_VECTOR_SCORE` 拒答，阈值随向量模型变化，bge-m3 是 0.79，换模型要重新校准（方法见 SPRINT_ISSUES 13.4）。

## 剩余项（全部非阻塞，按需）

1. **数据尾巴**：
   - 2 篇商品笔记未导入店铺（693d2a83→商品合集、69522416→校园公交路牌）。SKU 合并后店面以 `canonical-skus.json` 为准：导入后若是已有印刷款就不用上架；是新印刷款则在 `apply-sku-merge.py` 加 SKU，并补 `productCopy.ts` 英文文案、重跑 `export-shop-catalog.cjs`
   - ~~商品 1000007 是否下架~~（已解决）：SKU 合并时改为在售的「NO BIN CHICKEN 白鹮」SKU
   - 商品合集、手机壳两个类目当前 0 个在售 SKU：隐藏空类目还是补货，由业务决定
2. **真实云部署**：AWS 配置已就绪（`docker/aws/`），尚未实际上线
3. **可选**：补爬缺口笔记 `6a806381`（非商品，观鸟内容）
4. **可选**：主包 2.5 MB（gzip 814 KB），可按路由 `lazy()` 拆分 admin 与 AntD 重组件

### 品类体系（2026-09-17 已对齐用户分类）

店铺类目 = 分类索引.json 的 10 个商品子品类：校园公交路牌(bus-sign) / 超市冰箱贴(supermarket) /
火车地铁路牌(train) / 电车路牌(tram) / 小鸟冰箱贴(bird) / 交通指示牌(road-sign，含原酒鬼系列) /
商品合集(collections) / 留子成就(achievement) / 生活标语(slogan) / 手机壳(phonecase)。
- 对齐工具：`node scripts/align-categories.cjs`（dry-run）/ `--apply` 应用。**重跑导入后需再执行一次**
- 对齐后 ES 需重建索引：admin 登录后 `POST /api/products/admin/reindex`

## 下一步（第二阶段路线图，2026-10 制定）

> 代码已全部完成；需要 Docker、模型 key 或桌面客户端才能做的剩余验收与待办见 **`HANDOFF_NEXT.md`**。

按 `docs/SPRINT_ISSUES.md` 第二阶段路线图依次推进：
1. **Sprint 10**（v1.2.0）修复 `docs/CODE_REVIEW_TODO.md` 中 P0–P2
2. **Sprint 11**（v1.3.0）补齐 README 宣称的能力：Seata、ES 商品搜索、退款、JaCoCo 门禁、Knife4j、Playwright —— 已在 Docker 环境验收 Seata 回滚、热点压测、ES 搜索；剩 Stripe 测试模式真实退款
3. **Sprint 12**（v1.4.0）SkyWalking 全链路追踪 —— 已在 Docker 环境验收（镜像构建、链路、端点分组、告警）
4. **Sprint 13**（v2.0.0）Python AI Agent（FastAPI + LangGraph）+ RAG（向量库用 ES）—— 代码已完成，已在本机连真实 Java 服务、MariaDB、Redis、SkyWalking 验证；真实模型评测与 ES 向量检索待配置 DashScope key 后实测
5. **Sprint 14**（v2.1.0）MCP Server 与客户端接入 —— Docker 环境下经网关的匿名 / 令牌 / 越权 403 / 撤销 401 已验证；外部客户端的演示 GIF 待在桌面端录制

## 硬性规范（不要违反）

- 分层：`controller → service(接口+impl) → mapper(MyBatis Plus) → entity`，DTO 入 VO 出
- API 统一返回 `Result{code,message,data}`；异常抛 `BusinessException` 由全局处理器兜底
- DB：`t_` 前缀、snake_case、`is_deleted` 逻辑删、`DECIMAL(10,2)` 金额、`idx_表_列` 索引
- 金额在前端一律用 cents(number) 传输，展示只走 `src/lib/format.ts` 的 `formatPrice`
- 币种：AUD 为基准，CNY 展示换算；汇率快照落订单
- Commit：`feat:` `fix:` `docs:` `refactor:` `chore:`；分支 Git Flow
- 每个 Sprint 结束打 tag；验收标准以 `docs/SPRINT_ISSUES.md` 为准
