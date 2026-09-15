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
