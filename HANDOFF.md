# HANDOFF — StickyBeak 交接文档

> 给下一个 AI 会话/协作者：读完本文 + `docs/` 三份文档即可无缝接手。
> 最近更新：2026-09-15 · Sprint 1 完成（tag v0.1.0）

## 项目是什么

**StickyBeak** — 澳洲主题冰箱贴电商平台。作品集项目优先，架构预留真实上线余地。
命名：StickyBeak = 澳俚"爱凑热闹的人"，暗合 sticky（磁贴）+ beak（鸟喙，招牌大葵/蒜苗鸡小鸟系列）。

## 当前状态

- **v0.0.1**（main）：Sprint 0 骨架，已用 Docker Maven 验证编译 + 7 服务注册进 Nacos + 网关路由全通
- **v0.1.0**（main）：Sprint 1 认证与 RBAC 完成
- **feature/storefront-ui**（未合并）：storefront 全套 UI 前置完成——首页/商品列表（URL 同步筛选）/详情（图集）/购物车页 + 迷你抽屉 + 游客车 localStorage 持久化 + AUD/CNY 切换（演示汇率 4.75 写死在 currencySlice，4.7 换真实汇率）
  - **mock 目录层**：`src/lib/catalog.ts` 接口形状 = Sprint 2 REST 契约，后端就绪后只换函数体
  - mock 数据由 `scripts/generate-mock-catalog.cjs` 从真实数据源生成（16 个商品 / 6 品类 / 57 张图，在 `public/mock-products/`）
  - 重新生成：`node scripts/generate-mock-catalog.cjs`
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
# 后端测试
docker run --rm -v "${PWD}:/workspace" -v stickybeak-m2:/root/.m2 -w /workspace maven:3.9-eclipse-temurin-17 mvn -q test
# 前端
cd stickybeak-frontend; npm run dev   # :5173，/api 代理到 :8080
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

## 数据源（真实商品数据，差异化亮点）

已爬取并分类好的小红书 GDCUP 商品内容在 **`C:\Users\lzz28\gdcup-fridge-magnets\classified\商品\`**：
- 265 个子文件夹，每个含 `info.json`（标题/正文/标签/图片URL列表/源笔记ID）+ 封面 + 已下载图集（80 篇有完整图集）
- Sprint 2 Issue 2.2 要写导入脚本灌进 `t_product` / `t_product_image` / `t_tag`
- 映射关系见 `docs/DATABASE_ER.md` §8

## 下一步（Sprint 2 — 商品目录 + 真实数据导入，tag v0.2.0）

按 `docs/SPRINT_ISSUES.md` Issue 2.1→2.6：
1. **2.1** product 库表（t_category/t_product/t_product_image/t_tag/t_product_tag_rel/t_stock_hold）+ 分类种子（大学公交路牌/超市/火车电车/小鸟路牌/酒鬼/手机壳）
2. **2.2** ⭐ `scripts/import-products.py`：读 `classified/商品/*/info.json` → SQL/REST 导入；图片进 MinIO；price/stock/category 手工补录 CSV 模板
3. **2.3** 商品浏览 API（分页/筛选/排序 + Redis 缓存 60s）
4. **2.4** ES 搜索（IK 分词；ES 宕机降级 MySQL）
5. **2.5** 前端商品列表/详情（筛选同步 URL query、骨架屏、懒加载、RTK Query）
6. **2.6** 管理端商品 CRUD（/admin/products，AntD Table + MinIO 预签名上传）

注意：其余 5 个服务的 `@SpringBootTest` 冒烟测试在各自接入 DB/Redis 后会挂（auth 已删并换成纯单测）——接入时同样处理，或引入 H2/Testcontainers。

## 硬性规范（不要违反）

- 分层：`controller → service(接口+impl) → mapper(MyBatis Plus) → entity`，DTO 入 VO 出
- API 统一返回 `Result{code,message,data}`；异常抛 `BusinessException` 由全局处理器兜底
- DB：`t_` 前缀、snake_case、`is_deleted` 逻辑删、`DECIMAL(10,2)` 金额、`idx_表_列` 索引
- 金额在前端一律用 cents(number) 传输，展示只走 `src/lib/format.ts` 的 `formatPrice`
- 币种：AUD 为基准，CNY 展示换算；汇率快照落订单
- Commit：`feat:` `fix:` `docs:` `refactor:` `chore:`；分支 Git Flow
- 每个 Sprint 结束打 tag；验收标准以 `docs/SPRINT_ISSUES.md` 为准
