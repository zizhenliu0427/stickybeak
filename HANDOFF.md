# HANDOFF — StickyBeak 交接文档

> 给下一个 AI 会话/协作者：读完本文 + `docs/` 三份文档即可无缝接手。
> 最近更新：2026-09-16 · Sprint 2 完成（tag v0.2.0）

## 项目是什么

**StickyBeak** — 澳洲主题冰箱贴电商平台。作品集项目优先，架构预留真实上线余地。
命名：StickyBeak = 澳俚"爱凑热闹的人"，暗合 sticky（磁贴）+ beak（鸟喙，招牌大葵/蒜苗鸡小鸟系列）。

## 当前状态

- **v0.0.1**（main）：Sprint 0 骨架，已用 Docker Maven 验证编译 + 7 服务注册进 Nacos + 网关路由全通
- **v0.1.0**（main）：Sprint 1 认证与 RBAC 完成
- **v0.2.0**（feature/sprint-2-product-catalog）：Sprint 2 商品目录 + 真实数据导入完成，全栈 E2E 联通
  - **真实的 265 件商品数据**：从小红书爬取，已入库 `stickybeak_product`，603 张图片复制至 `stickybeak-frontend/public/products/`
  - **后端 REST API 全通**：分类/标签/商品分页/筛选/排序/详情/推荐/精选，Redis 60s 缓存
  - **前端无缝对接真实接口**：`src/lib/catalog.ts` 替换 mock 实现，直接调网关 `/api`，TypeScript 0 错误
- 开发分支：`develop`；功能分支：`feature/sprint-N-xxx`（Git Flow）

## Sprint 1 交付明细（均已 E2E 验证）

- **auth 库**：`docker/mysql/init/02-auth-schema.sql`（t_user/t_role/t_user_role/t_refresh_token/t_address + 角色种子）。注意：MySQL 数据卷非空时 init 脚本不会重跑，需 `docker cp` + `docker exec sb-mysql mysql ... < file` 手动应用
- **auth 服务**：注册/登录/刷新/登出；bcrypt；JWT HS256 access 15min（claims: sub/email/roles/jti）；refresh 7 天不透明随机串、DB 只存 SHA-256 哈希、轮换 + 复用检测（复用→吊销全部会话）；登出 access jti 进 Redis 黑名单（`auth:blacklist:{jti}`，TTL=剩余有效期）
- **token 载体**：HttpOnly Cookie `sb_access` / `sb_refresh`（网关也接受 `Authorization: Bearer`）
- **网关**：`AuthGlobalFilter` 剥离外部伪造的 X-User-Id/X-User-Roles → 白名单放行 → 验 JWT + Redis 黑名单 → 注入身份头。白名单：auth 四个端点、GET 商品/分类/标签、webhooks、`*/health`、actuator
- **服务内兜底**：auth 的 `HeaderAuthFilter` 从头建 SecurityContext + `@PreAuthorize`（示例：`GET /api/users` 仅 ADMIN/SYSADMIN）
- **种子**：`DataSeeder` 幂等种角色 + 开发管理员 `admin@stickybeak.au` / `Admin123!`（生产 `SEED_DEV_ADMIN=false`）
- **前端**：登录/注册/个人中心（资料编辑 + 地址 CRUD，AntD 表单）；启动 `fetchMe` 探测 → 刷新不丢登录；守卫未初始化显示 Spin；401→自动刷新→失败清本地态（`setSessionExpiredHandler`），匿名浏览不再被强跳登录
- **测试**：后端 13 单测（register/login/refresh/logout + 复用检测）；前端 5 测（含 RequireAuth 三态）

## Sprint 2 交付明细（均已 E2E 验证）

- **product 库表**：`docker/mysql/init/03-product-schema.sql`
  - `t_category`：6 个种子分类（大学公交路牌/超市系列/火车电车/小鸟路牌/酒鬼系列/手机壳周边）
  - `t_product`：雪花 ID、slug(UK)、分类 ID、价格、库存、销量、笔记溯源 ID、featured、status、逻辑删除
  - `t_product_image`：多图与封面标记
  - `t_tag` + `t_product_tag_rel`：标签多对多关联
  - `t_stock_hold`：库存预占表预留（Sprint 5 用）
  - 显式声明 `SET NAMES utf8mb4;` 保证字符集原生支持中文与 Emoji
- **数据导入**：`scripts/import-products.py`
  - 遍历 265 篇小红书商品数据（`classified/商品/*/info.json`）
  - 自动关键词归类 + 标签提取 + 自动生成 URL-safe slug + 随机生成价格与库存
  - 提取 603 张图片并复制至 `stickybeak-frontend/public/products/{note_id}/`
  - 产生 `scripts/generated-product-data.sql` 一键灌入数据库
- **后端服务**：`stickybeak-product`
  - 四层架构：`ProductController/CategoryController/TagController → Service → Mapper → Entity`
  - API 端点：
    - `GET /api/categories`：全部分类（排序权重）
    - `GET /api/categories/{slug}`：单分类详情
    - `GET /api/tags`：全部标签列表（154 个）
    - `GET /api/products`：分页（page/size）、分类筛选、标签筛选（逗号分割）、价格区间（minPrice/maxPrice）、关键词搜索（name+description LIKE）、排序（sales/price-asc/price-desc/new）
    - `GET /api/products/featured`：首页推荐商品
    - `GET /api/products/{slug}`：商品详情（含关联图片列表、标签列表、所属分类）
    - `GET /api/products/{slug}/related`：同品类关联推荐
  - Redis 缓存：`product:list:{queryHash}` 缓存 60s，提升高频读性能，Redis 挂掉优雅降级直接查 DB
- **前端对接**：
  - `src/lib/catalog.ts` 从 mock 本地 JSON 切换为通过统一 `api.ts` 请求后端真实网关接口，函数签名完全对齐
  - 配合 Vite proxy `/api` 转发网关 8080，支持 SSR / SPA 浏览
  - 前端 9/9 单元测试 + TypeScript typecheck 零报错
- **测试**：
  - 后端新增 `CategoryServiceImplTest` 和 `ProductServiceImplTest`，各服务全量单测在 Docker 中通过（`mvn -q test` 退出码 0）
  - 其余骨架服务 `@SpringBootTest` 已改造为轻量级单元测试，彻底解耦 Docker 构建时的中间件依赖

## 环境差异（本机实测，新机器必读）

- **Redis 宿主端口 16379**（6379/6380 被 Windows Hyper-V 保留段 6346-6445 占用）；容器网络内仍是 6379。IDE 本地跑服务设 `REDIS_PORT=16379`
- **MinIO** 官方已停发社区版镜像 → 用 `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`
- 本机无 Maven/JDK：构建测试全部走 Docker 镜像 `maven:3.9-eclipse-temurin-17`（m2 缓存在 named volume `stickybeak-m2`）
- 宿主机 80/8000/8081/9092 被其他项目容器占用——全栈 compose 的 frontend:80 会冲突，届时改端口或停掉对方
- PowerShell 调 curl.exe 传 JSON 会被剥引号：**请求体写临时文件用 `-d @file`**
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
cd stickybeak-frontend; npm run typecheck; npm run test; npm run dev
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
10. `GlobalExceptionHandler` 已对齐真实 HTTP 状态码（401/403/404…），1000+ 业务码仍 HTTP 200——前端 401 自动刷新依赖这一点，别改回统一 200
11. **网关 `StripPrefix=1` 会截去前缀 `/api`**：下游微服务的 Controller `@RequestMapping` 不能重复写 `/api`（例如写 `@RequestMapping("/products")` 而非 `@RequestMapping("/api/products")`），否则路由映射 404
12. **服务重启脚本 `rebuild-services.ps1` 需注入环境变量**：任何接入 MySQL 或 Redis 的微服务必须在 `$envMap` 显式配置 `MYSQL_HOST=sb-mysql` 和 `REDIS_HOST=sb-redis`，否则在容器内部网络会 fallback 至 `localhost` 触发 Connection Refused
13. **MySQL 导入数据中文与 Emoji 乱码**：
    - SQL 文件首部必须包含 `SET NAMES utf8mb4;`
    - 切勿使用 PowerShell pipeline `Get-Content ... | docker exec -i`（Windows 控制台编码会强制将 Unicode 字符转为问号 `?`），应使用 `docker cp` 将文件拷入容器并在容器内 `mysql ... -e "source ..."`
14. **容器内构建环境与中间件解耦**：微服务的 `@SpringBootTest` 冒烟测试在构建环境独立运行（如 Maven 容器）无 Nacos/Redis 时会报错卡死，统一改造为纯单元测试，保证 Docker build 与 CI 稳定无外部强依赖

## 下一步（Sprint 3 — 购物车与心愿单，tag v0.3.0）

按 `docs/SPRINT_ISSUES.md` Issue 3.1→3.5：
1. **3.1** cart 库表（`t_cart` / `t_cart_item` / `t_wishlist`）+ 游客签名 session cookie
2. **3.2** 购物车 CRUD（加购/修改数量/删除/清空；库存校验；Redis 快照 + MySQL 真相源）
3. **3.3** ⭐ **登录合并购物车**（游客车与登录用户车合并算法，同商品数量相加限额库存，幂等保护）
4. **3.4** 前端购物车抽屉 + `/cart` 结算前预览页对接真实后端 API
5. **3.5** 心愿单增删查

## 硬性规范（不要违反）

- 分层：`controller → service(接口+impl) → mapper(MyBatis Plus) → entity`，DTO 入 VO 出
- API 统一返回 `Result{code,message,data}`；异常抛 `BusinessException` 由全局处理器兜底
- DB：`t_` 前缀、snake_case、`is_deleted` 逻辑删、`DECIMAL(10,2)` 金额、`idx_表_列` 索引
- 金额在前端一律用 cents(number) 传输，展示只走 `src/lib/format.ts` 的 `formatPrice`
- 币种：AUD 为基准，CNY 展示换算；汇率快照落订单
- Commit：`feat:` `fix:` `docs:` `refactor:` `chore:`；分支 Git Flow
- 每个 Sprint 结束打 tag；验收标准以 `docs/SPRINT_ISSUES.md` 为准
