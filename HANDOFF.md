# HANDOFF — StickyBeak 交接文档

> 给下一个 AI 会话/协作者：读完本文 + `docs/` 三份文档即可无缝接手。
> 最近更新：2026-09-17 · 全部 Sprint 完成，main = v1.1.0（含 Sprint 9 部分故事线）

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
- 开发分支：`develop`（当前唯一工作分支，不再开 feature 分支）
- ~~计划偏差备忘~~ ES 全文搜索已补齐（2026-09-17，Issue 2.4 + 故事线 9.2/9.4/9.5/9.6 全部落地）

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
- 移动端：`MobileBottomNav` 底部导航、`OfflineBanner` + `useOnlineStatus` 离线检测、触控目标 ≥44px
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
- **9.2 ES 全文搜索 + 基准**（2026-09-17 补）：
  - product 服务接入 `spring-boot-starter-data-elasticsearch`，`ProductDocument`（IK 分词：name^3/description，价格存 cents）
  - 检索链路：`GET /api/products?q=` → ES multi_match → 命中 id 回 MySQL 取实体；`engine=es|mysql` 调试参数（显式指定跳过缓存）
  - 降级：`@SentinelResource` + 异常比例熔断（>50%/60s → 断 30s）自动回退 MySQL LIKE——已实测停 ES 容器后搜索正常
  - 一致性：管理端变更 → MQ `product.changed` → `ProductIndexListener` 刷索引（无双写）；全量兜底 `POST /api/products/admin/reindex`
  - ES 容器装 IK 插件，持久化靠 `docker/elasticsearch/Dockerfile`（compose 已改 build 方式）
  - 基准：`scripts/benchmark-search.cjs`（真实数据）+ `benchmark-search-scale.cjs`（10 万行合成）→ `docs/SEARCH_BENCHMARK.md`（诚实结论：小数据量持平，10 万行 MySQL 全扫 118ms vs ES 52ms）
- **9.4 Nacos 多环境**（2026-09-17 补）：`ConfigDemoController` + `scripts/demo-nacos-multi-env.cjs` → `docs/NACOS_MULTI_ENV.md`（热刷新实测免重启；prod 命名空间隔离实测——prod 实例对 public 网关直接不可见）
- **9.5 慢 SQL 案例**（2026-09-17 补）：`scripts/demo-sql-optimization.cjs` → `docs/SQL_OPTIMIZATION.md`。
  - 主案例：趋势查询覆盖索引 152ms→72ms（50 万行），生产迁移 `07-order-analytics-index.sql` 已应用
  - 番外反例：Top-N 聚合加索引反而 607→732ms（优化器翻转 join 顺序），正解是预聚合汇总表
- **9.6 webhook 线程池**（2026-09-17 补）：`WebhookExecutorConfig`（core4/max8/queue200/CallerRuns 背压）+ 接收端「验签+幂等落档 → 异步处理」+ `GET /api/webhooks/pool-stats` 监控 → `docs/THREADPOOL_WEBHOOK.md`；60 并发实测 0 丢失 0 拒绝
  - ⚠️ 行为变更：webhook 受理返回 `ACCEPTED`（原为处理完返回 `SUCCESS`），e2e 断言已同步更新

## 数据源（真实商品数据，差异化亮点 · 2026-09-17 双 AI 对账核实）

小红书 GDCUP 爬取项目在 **`C:\Users\lzz28\gdcup-fridge-magnets\`**：
- **规范数据源**：`data/note_details.json`（294 篇唯一笔记全量：正文/标签/图集URL）+ `media/notes/`（294 夹 / 1122 张图）
- `classified/` 为分类拷贝（最新一次重跑后）：商品 **268** 篇全部有正文+图集，其他内容 26 篇（25 完整）
- 唯一缺口：`6a806381000000000801111c`（其他内容类，非商品）正文/图集空、封面在；需要时用爬取项目 `scripts/save_one_note.py` 单补
- 主页 296 条 = 294 唯一 + 2 篇置顶重复；封面 296 张 + 头像 1 张
- 爬取项目审计脚本：`gdcup-fridge-magnets\scripts\audit-crawl.cjs`（`node` 直接跑）
- **差值提醒**：店铺库导入的是旧口径 265 件；最新分类 268 篇 → 有 3 篇可增量导入
- Sprint 2 导入用的是 `scripts/import-products.py` → `generated-product-data.sql`

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

## 剩余项（全部非阻塞，按需）

1. **3 篇商品增量导入**（分类重跑后 265 → 268；且用户已按 10 个子品类重新分类，店铺 6 个旧种子类目与新体系未对齐——`分类索引.json` 含 note id 可机械重映射）
2. **真实云部署**：AWS 配置已就绪（`docker/aws/`），尚未实际上线
3. **可选**：补爬缺口笔记 `6a806381`（非商品，观鸟内容）

## 硬性规范（不要违反）

- 分层：`controller → service(接口+impl) → mapper(MyBatis Plus) → entity`，DTO 入 VO 出
- API 统一返回 `Result{code,message,data}`；异常抛 `BusinessException` 由全局处理器兜底
- DB：`t_` 前缀、snake_case、`is_deleted` 逻辑删、`DECIMAL(10,2)` 金额、`idx_表_列` 索引
- 金额在前端一律用 cents(number) 传输，展示只走 `src/lib/format.ts` 的 `formatPrice`
- 币种：AUD 为基准，CNY 展示换算；汇率快照落订单
- Commit：`feat:` `fix:` `docs:` `refactor:` `chore:`；分支 Git Flow
- 每个 Sprint 结束打 tag；验收标准以 `docs/SPRINT_ISSUES.md` 为准
