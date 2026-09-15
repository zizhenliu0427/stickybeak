# HANDOFF — StickyBeak 交接文档

> 给下一个 AI 会话/协作者：读完本文 + `docs/` 三份文档即可无缝接手。

## 项目是什么

**StickyBeak** — 澳洲主题冰箱贴电商平台。作品集项目优先，架构预留真实上线余地。
命名：StickyBeak = 澳俚"爱凑热闹的人"，暗合 sticky（磁贴）+ beak（鸟喙，招牌大葵/蒜苗鸡小鸟系列）。

## 已完成（Sprint 0，tag 待打 v0.0.1）

- [x] monorepo 骨架：父 POM + 8 模块（gateway/auth/product/cart/order/payment/notification/common）
- [x] `stickybeak-common`：`Result<T>` / `ResultCode` / `BusinessException` / `GlobalExceptionHandler`
- [x] 每个服务：`Application` 主类 + `/health` 端点 + Nacos discovery/config 接入配置
- [x] Gateway：5 条路由（auth/product/cart/order/payment），StripPrefix=1，CORS
- [x] 前端：React 18 + TS + Vite + Redux Toolkit（auth/cart/currency 三个 slice）+ Router v6（含 RequireAuth 守卫）+ Tailwind + AntD + Axios 封装（401 自动刷新）+ Vitest（2 个示例测试通过，typecheck/build 通过）
- [x] Docker：`docker-compose-infra.yml`（MySQL/Redis/RabbitMQ/ES/Nacos/Seata/MinIO/MailHog）+ 全栈 `docker-compose.yml` + 前后端 Dockerfile + Nginx SPA 配置 + MySQL 建库 SQL
- [x] CI：GitHub Actions（后端 mvn verify / 前端 typecheck+test+build / compose 校验）
- [x] 设计文档：`docs/ARCHITECTURE.md`、`docs/DATABASE_ER.md`、`docs/SPRINT_ISSUES.md`

## 还没做 / 注意事项

- **后端未本地编译验证过**（本机无 Maven）。第一次跑先 `mvn -q -DskipTests package` 确认依赖解析没问题，预期可能要对齐 Spring Cloud / Alibaba 版本号。
- 前端页面除 Home 外都是 TODO 占位。
- Nacos 配置中心目前用 `optional:nacos:` 导入，配置不存在也能启动。
- 前端 `npm install` 用的是 install 不是 ci（lock 文件已生成，可切 ci）。

## 数据源（真实商品数据，差异化亮点）

已爬取并分类好的小红书 GDCUP 商品内容在 **`C:\Users\lzz28\gdcup-fridge-magnets\classified\商品\`**：
- 265 个子文件夹，每个含 `info.json`（标题/正文/标签/图片URL列表/源笔记ID）+ 封面 + 已下载图集（80 篇有完整图集）
- Sprint 2 Issue 2.2 要写导入脚本灌进 `t_product` / `t_product_image` / `t_tag`
- 映射关系见 `docs/DATABASE_ER.md` §8

## 下一步（按顺序）

1. 验证后端编译：`mvn -q -DskipTests package`，修版本问题
2. 起 infra：`docker compose -f docker/docker-compose-infra.yml up -d`，验证各服务注册进 Nacos
3. 打 tag `v0.0.1`
4. 进入 **Sprint 1（认证与 RBAC）**：Issue 1.1→1.5，见 `docs/SPRINT_ISSUES.md`

## 硬性规范（不要违反）

- 分层：`controller → service(接口+impl) → mapper(MyBatis Plus) → entity`，DTO 入 VO 出
- API 统一返回 `Result{code,message,data}`；异常抛 `BusinessException` 由全局处理器兜底
- DB：`t_` 前缀、snake_case、`is_deleted` 逻辑删、`DECIMAL(10,2)` 金额、`idx_表_列` 索引
- 金额在前端一律用 cents(number) 传输，展示只走 `src/lib/format.ts` 的 `formatPrice`
- 币种：AUD 为基准，CNY 展示换算；汇率快照落订单
- Commit：`feat:` `fix:` `docs:` `refactor:` `chore:`；分支 Git Flow
- 每个 Sprint 结束打 tag；验收标准以 `docs/SPRINT_ISSUES.md` 为准
