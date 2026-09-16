# HANDOFF — StickyBeak 交接文档

> 给下一个 AI 会话/协作者：读完本文 + `docs/` 三份文档即可无缝接手。
> 最近更新：2026-09-16 · Sprint 3 完成（tag v0.3.0）

## 项目是什么

**StickyBeak** — 澳洲主题冰箱贴电商平台。作品集项目优先，架构预留真实上线余地。
命名：StickyBeak = 澳俚"爱凑热闹的人"，暗合 sticky（磁贴）+ beak（鸟喙，招牌大葵/蒜苗鸡小鸟系列）。

## 当前状态

- **v0.0.1**（main）：Sprint 0 骨架，已用 Docker Maven 验证编译 + 7 服务注册进 Nacos + 网关路由全通
- **v0.1.0**（main）：Sprint 1 认证与 RBAC 完成
- **v0.2.0**（main）：Sprint 2 商品目录 + 真实数据导入完成，全栈 E2E 联通
- **v0.3.0**（feature/sprint-3-cart-wishlist）：Sprint 3 购物车与心愿单全栈完成，E2E 验证全通
  - **cart 库表 3 张**：`t_cart` / `t_cart_item` / `t_wishlist` 已落地 MySQL
  - **网关可选认证模式**：`OPTIONAL_AUTH_PREFIXES` 支持游客（无 token 放行）与登录用户（验签注入 X-User-Id）
  - **购物车双模式 + 游客 Session Cookie**：首次访问自动签发 `sb_guest` Cookie（30天），后端支持游客与用户双轨
  - **微服务协同**：Cart 服务通过 `@LoadBalanced RestTemplate` 调用 Product 服务实时获取商品信息与库存，解耦库边界
  - **⭐ 登录合并购物车**：合并四场景（仅游客/仅用户/双方无交集/双方交集累加且封顶库存）算法完备，单测与 E2E 双验证
  - **心愿单（Wishlist）**：Toggle 收藏/取消、列表查询、一键移入购物车
  - **前端全栈联动**：Redux `cartSlice` 双模式改造（游客 localStorage / 登录全走后端 REST API），商品详情页与购物车页全部联通
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
cd stickybeak-frontend; npx tsc --noEmit; npm run test; npm run dev
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

## 下一步（Sprint 4 — 结账与 Stripe，tag v0.4.0 ← 简历可用下限！）

按 `docs/SPRINT_ISSUES.md` Issue 4.1→4.8：
1. **4.1** 支付提供商抽象（策略模式：`PaymentProvider` 接口，Stripe 实现）
2. **4.2** Checkout Session 创建（冻结购物车价格快照，先生成 pending 订单）
3. **4.3** ⭐ **Webhook 幂等性**（验签、`t_payment_webhook.event_id` 唯一去重、成功投递 MQ）
4. **4.4** 本地联调文档 + Stripe CLI / ngrok 脚本
5. **4.5** 前端结账流（地址确认 → Stripe 跳转 → 成功页轮询 webhook 落地）
6. **4.6** 异步订单确认邮件（RabbitMQ + Thymeleaf 模板 + MailHog）
7. **4.7** ⭐ **多币种 AUD / CNY 真实结账**（汇率定时刷新与快照落单）
8. **4.8** 多支付方式支持（Card 澳洲银行卡 / Alipay / WeChat Pay）

## 硬性规范（不要违反）

- 分层：`controller → service(接口+impl) → mapper(MyBatis Plus) → entity`，DTO 入 VO 出
- API 统一返回 `Result{code,message,data}`；异常抛 `BusinessException` 由全局处理器兜底
- DB：`t_` 前缀、snake_case、`is_deleted` 逻辑删、`DECIMAL(10,2)` 金额、`idx_表_列` 索引
- 金额在前端一律用 cents(number) 传输，展示只走 `src/lib/format.ts` 的 `formatPrice`
- 币种：AUD 为基准，CNY 展示换算；汇率快照落订单
- Commit：`feat:` `fix:` `docs:` `refactor:` `chore:`；分支 Git Flow
- 每个 Sprint 结束打 tag；验收标准以 `docs/SPRINT_ISSUES.md` 为准
