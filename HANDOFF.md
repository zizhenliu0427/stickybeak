# HANDOFF — StickyBeak 交接文档

> 给下一个 AI 会话/协作者：读完本文 + `docs/` 三份文档即可无缝接手。
> 最近更新：2026-09-16 · Sprint 5 完成（tag v0.5.0 · 订单状态机与防超卖三层防线）
> ⚠️ 2026-10-24 代码审查发现的待修问题见 [`docs/CODE_REVIEW_TODO.md`](docs/CODE_REVIEW_TODO.md)，开工前先看。

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
- **v0.6.0 – v1.1.0**（main / 当前发布）：Sprint 6–9 管理后台、PWA、E2E 与云部署配置、Sentinel 限流与手动 ACK
- **Sprint 10**（`feature/sprint-10-security-fixes`，待合并后打 `v1.2.0`）：修复 `docs/CODE_REVIEW_TODO.md` 中 P0–P2（支付回调、内部接口隔离、消息重试与死信、状态条件更新、生产配置）
- **Sprint 11**（`feature/sprint-11-readme-capabilities`，基于 Sprint 10 分支，待合并后打 `v1.3.0`）：把 README 宣称的能力全部落地 —— Seata AT 结账事务、ES 商品搜索（IK + 高亮 + MySQL 回退）、退款全流程、JaCoCo 覆盖率门禁、网关 Knife4j 文档聚合、Playwright 桌面 + 移动端用户旅程、SES 邮件配置、`LICENSE`；顺带修复了"网关无法启动"（Sprint 9 引入）、"管理后台刷新一直转圈"、"商品列表缓存从不命中"
- **Sprint 12**（`feature/sprint-12-skywalking`，基于 Sprint 11 分支，待合并后打 `v1.4.0`）：SkyWalking 全链路追踪 —— OAP 10.4 + BanyanDB + UI（:8088），7 个服务挂 agent 9.7，日志带 traceId 并上报、响应头 `X-Trace-Id`，告警规则（含死信队列积压自定义指标），网关端点分组；用链路定位并修复商品列表 N+1（`docs/PERF_PRODUCT_LIST.md`）
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
22. **内部接口（Sprint 10）**：只供服务间调用的接口标 `@InternalApi`，调用方 RestTemplate 必须挂 `InternalCallRequestInterceptor`（订单服务已挂），否则返回 403。网关会剥离外部请求的 `X-Internal-Call`，并对 `/api/products/stock/**` 写接口、`/api/payments/checkout-session|records`、`*/expire-session` 直接拒绝。网关的路径判断都基于**解码 + 规范化后**的路径。
23. **mock 支付回调默认关闭**：本地 E2E 需 `PAYMENT_MOCK_WEBHOOK_ENABLED=true`（`rebuild-services.ps1` 与 `docker-compose.yml` 已设置）；mock 回调需要登录 cookie，且 `amount` 必须等于订单 `payAmount`，否则返回 `REJECTED`。
24. **RabbitMQ 已声明队列的 arguments 不能改**（再次声明会 `PRECONDITION_FAILED`）：Sprint 10 的重试 / 死信全部用新队列（`order.*.retry.queue`、`order.*.dlq`），没有动 `order.status.queue` / `order.close.queue`。以后要改已有队列参数，需先在管理台删除旧队列或改名。
25. **远程调用与 Redis 写入放到事务提交后**：用 `common` 的 `AfterCommit.run(...)`；没有事务时立即执行（单测里也是）。
26. **`OrderMapper.updateIfStatus` 是 default 方法**：Mockito mock 默认返回 0（视为并发冲突并抛异常），单测里要显式 `when(...).thenReturn(1)`。
27. **prod profile 没有默认 JWT 密钥**：`JWT_SECRET` 缺失 / 非 Base64 / 短于 256 bit 都会启动失败，生成：`openssl rand -base64 48`。
28. **网关能否启动要靠 `GatewayContextTest` 守住**：Sprint 9 起网关因为重复定义 `sentinelGatewayFilter`（自动配置已注册同名 Bean）一直启动失败，而单测从不加载上下文，CI 是绿的。Sentinel 的 Filter / ExceptionHandler 交给 `SentinelSCGAutoConfiguration`，自定义 429 响应以 `BlockRequestHandler` Bean 提供。
29. **Seata AT（Sprint 11）**：全局事务边界是 `CheckoutTransaction#placeOrder`，支付会话在全局提交之后创建（外部调用不要放进全局事务）。product / order 依赖 Seata Server（`SEATA_ADDR`，compose 里是 seata-server 2.0.0）；本地临时调试可 `SEATA_ENABLED=false`，但那样结账失败时商品库不会回滚。业务库要有 `undo_log` 表（`docker/mysql/init/07-seata-undo-log.sql`，老数据卷需手动执行一次）。
30. **会改 `t_product` 库存行的本地写必须加 `@GlobalLock`**（关单释放、退款回补、后台改库存都已加）：否则与未完成的结账全局事务交错后，全局回滚做 after-image 校验失败，回滚会卡住需要人工处理。
31. **Redis 预扣不受 undo_log 管理**：扣减 Lua 同时写订单账本 `stock:reservation:{orderNo}`；本地事务没提交由 `AfterCommit.onRollback` 归还，全局回滚由订单服务的 Seata `TransactionHook#afterRollback` 调 `/products/stock/redis-restore` 归还。归还脚本是原子"读 + 加回 + 删账本"，重复调用安全。Lua 里拼 key，只支持单节点 Redis。
32. **网关剥离外部请求的 `TX_XID`**，否则外部请求可以把写操作挂进别人的全局事务。
33. **商品搜索走 ES 失败会自动回退 MySQL**：`SEARCH_ES_ENABLED=false` 可整体关闭；索引需要 IK 插件（`docker/elasticsearch/Dockerfile` 构建镜像，插件下载地址在网络受限环境可能需要换成离线 zip）。ES 健康检查已关闭，ES 挂了商品服务仍是 UP。
34. **Knife4j 文档只在网关 `/doc.html` 看**：网关路由只转发 `/api-docs/{svc}/v3/api-docs/**`，千万别改成 `/api-docs/{svc}/**`——这个前缀是公开的，会变成绕过鉴权的旁路。生产 `API_DOCS_ENABLED=false`。
35. **Playwright 用例在浏览器里模拟接口**（`stickybeak-frontend/e2e/support/mockApi.ts`）：页面调用了没模拟的接口会让用例失败；PWA 的 Service Worker 会绕过 `page.route`，配置里已 `serviceWorkers: 'block'`。`@playwright/test` 版本固定，升级时 CI 会自动装对应的 Chromium。
36. **SkyWalking agent 只在设置了 `SW_AGENT_COLLECTOR_BACKEND_SERVICES` 时挂载**（`Dockerfile.backend` 的 ENTRYPOINT）。agent 版本（Dockerfile）必须与 `pom.xml` 的 `skywalking.version`（apm-toolkit）一致，否则 traceId 打不出来。
37. **Spring Boot 3 / 网关的 agent 插件在 `optional-plugins` 里**，必须复制到 `plugins/`：springmvc-annotation-6.x、resttemplate-6.x、spring-cloud-gateway-4.x、spring-webflux-6.x。少了 resttemplate 插件，订单调其他服务的链路会断开；少了 springmvc 插件，端点按真实 URL 命名。
38. **网关端点分组规则（`docker/skywalking/endpoint-name-grouping.yml`）**：同一层按书写顺序取第一个命中的节点、不回溯，所以 `/api/orders/my` 这类字面量规则必须写在 `/api/orders/{var}` 之前。新增带路径变量的网关路由时记得补规则。
39. **OAP 10.4 的健康检查是 `/healthcheck`**（`/internal/l7check` 返回 404）；告警规则用 MQE 表达式（`expression:`），旧版 `metrics-name` / `op` / `threshold` 写法不再生效。
40. **本机直接跑 BanyanDB 要去掉代理环境变量**（`HTTPS_PROXY` 等）：Go 的 gRPC 客户端会把内部连接也走代理，报 `failed to re-connect to grpc server`。
41. **支付回调里 `order.paid` 是在事务内发送的**，这是有意的：发送失败 → 事务回滚 → Stripe 重试；提交失败 → 下次回调再发一次，由消费方幂等兜住。改成提交后发送会在进程崩溃时丢消息。
42. **商品列表 / 搜索 / 精选 / 相关商品都走 `enrichProducts` 批量补全**（一页固定 4 条补全 SQL，单测守住）。不要再在循环里按商品查分类、图片、标签。

## 下一步（第二阶段路线图，2026-10 制定）

按 `docs/SPRINT_ISSUES.md` 第二阶段路线图依次推进：
1. **Sprint 10**（v1.2.0）修复 `docs/CODE_REVIEW_TODO.md` 中 P0–P2
2. **Sprint 11**（v1.3.0）补齐 README 宣称的能力：Seata、ES 商品搜索、退款、JaCoCo 门禁、Knife4j、Playwright —— 代码已完成，待在完整 Docker 环境验收 Seata 回滚与 ES 搜索
3. **Sprint 12**（v1.4.0）SkyWalking 全链路追踪 —— 代码已完成，链路 / 日志 / 告警 / 优化已在本机用同版本二进制验证，待在 Docker 环境构建镜像验收
4. **Sprint 13**（v2.0.0）Python AI Agent（FastAPI + LangGraph）+ RAG（向量库用 ES）
5. **Sprint 14**（v2.1.0）MCP Server 与客户端接入

## 硬性规范（不要违反）

- 分层：`controller → service(接口+impl) → mapper(MyBatis Plus) → entity`，DTO 入 VO 出
- API 统一返回 `Result{code,message,data}`；异常抛 `BusinessException` 由全局处理器兜底
- DB：`t_` 前缀、snake_case、`is_deleted` 逻辑删、`DECIMAL(10,2)` 金额、`idx_表_列` 索引
- 金额在前端一律用 cents(number) 传输，展示只走 `src/lib/format.ts` 的 `formatPrice`
- 币种：AUD 为基准，CNY 展示换算；汇率快照落订单
- Commit：`feat:` `fix:` `docs:` `refactor:` `chore:`；分支 Git Flow
- 每个 Sprint 结束打 tag；验收标准以 `docs/SPRINT_ISSUES.md` 为准
