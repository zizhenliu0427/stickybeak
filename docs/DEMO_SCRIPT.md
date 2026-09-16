# Stickybeak v1.0.0 Enterprise Product & Architecture Demo Script
# 3-5 分钟商业级中英演示讲解剧本

> **Version**: v1.0.0 Production Release  
> **Target Audience**: Technical Interviewers, Architects, Investors, Stakeholders  
> **Duration**: 3 - 5 Minutes  
> **Presenter Roles**: Product Engineer & System Architect  

---

## 1. 演示准备工作 (Pre-Demo Preparation)

在演示开始前，请在浏览器中预先打开以下 5 个标签页：

1. **Tab 1 - 买家前台**: `http://localhost/` (Stickybeak Storefront)
2. **Tab 2 - 管理员后台**: `http://localhost/admin/dashboard` (Stickybeak Admin Console)
3. **Tab 3 - 接口文档**: `http://localhost:8080/doc.html` (Knife4j OpenAPI 3.0 文档大盘)
4. **Tab 4 - 消息中台**: `http://localhost:15672/` (RabbitMQ Management, `guest/guest`)
5. **Tab 5 - 邮件测试中台**: `http://localhost:8025/` (Mailhog Web UI)

---

## 2. 演示分步讲解时序表 (Demo Timeline & Walkthrough)

### 阶段一：开篇概览与架构基石 (00:00 - 00:45)
- **讲解要点**:
  > “各位评委/面试官大家好，今天我为大家演示的是 **Stickybeak** —— 一个基于云原生分布式架构的澳大利亚特色跨境电商系统。系统采用 **Spring Cloud 2023 + Spring Boot 3.3 + Java 17** 作为后端微服务底座，前端采用 **Vue 3 + Pinia + TypeScript + TailwindCSS** 现代化技术栈。
  >
  > 我们针对高并发、资金流安全和跨境场景做了深度工程优化，全链路实现了 **Seata 分布式事务**、**RabbitMQ 延迟队列与可靠事件发布**、**Stripe 国际化支付网关** 以及基于有限状态机（FSM）的严密订单履约闭环。”

---

### 阶段二：买家端购物闭环与 Stripe 支付流转 (00:45 - 01:45)
- **屏幕操作**: 切换到 Tab 1 (前台页面)。
- **操作动作**:
  1. 浏览澳大利亚土特产（袋鼠皮手袋、澳洲坚果、蜂巢蜂蜜等）；
  2. 点击商品详情，加入购物车，右上角购物车徽标实时响应更新；
  3. 点击结算按钮进入 Checkout 页面，提交订单；
  4. 触发订单创建后，展示跳转进入 Stripe 支付结算收银台（或触发 Mock 支付通道）。
- **讲解要点**:
  > “在用户下单瞬间，系统底层通过 Redis 预扣减商品库存，防止超卖并发穿透；订单服务创建待支付订单（状态码 `1: PENDING_PAYMENT`），并向 RabbitMQ 投递 30 分钟延迟死信消息。若买家超时未付，系统将自动触发超时关单并原子回滚库存。”

---

### 阶段三：核心技术深水区：可靠消息投递与幂等性保障 (01:45 - 02:45)
- **屏幕操作**: 切换到 Tab 4 (RabbitMQ) 与 Tab 5 (Mailhog)。
- **操作动作**:
  1. 展示支付成功后，Payment 服务向 `order.topic` 发送的 `order.paid` 事件已被即时消费；
  2. 打开 Mailhog 收件箱，展示买家邮箱已秒级收到 HTML 富文本订单确认邮件；
  3. （可选）触发相同的 Stripe Webhook payload 二次重试。
- **讲解要点**:
  > “在支付履约链路中，最容易出问题的是资金一致性与网络抖动重复推送。我们设计了**双重防线**：
  > 
  > 1. **Webhook 幂等性**: 数据库利用 `UK_stripe_event_id` 唯一索引对第三方流水做原子防重。面对 Stripe 的并发重试，第二次请求直接被拦截并返回 200，保证不会多次履约；
  > 2. **有限状态机拦截**: 订单引擎只允许 `PENDING_PAYMENT -> PAID`。若有恶意用户尝试通过伪造请求将已支付订单反向流转，状态机直接抛出 `BusinessException` 予以硬阻断。”

---

### 阶段四：管理员中台大盘与订单履约流转 (02:45 - 03:45)
- **屏幕操作**: 切换到 Tab 2 (管理端后台)。
- **操作动作**:
  1. 使用管理员账号进入系统；
  2. 展示首页仪表盘（实时订单成交量、销售总额、热销类目分析、近 7 日趋势折线图）；
  3. 进入订单管理列表，找到刚才支付完成的订单（状态显示 `PAID`）；
  4. 输入物流单号（例如 `AU-POST-987654`），点击「发货」按钮，订单状态平滑流转为 `SHIPPED`。
- **讲解要点**:
  > “在管理端，我们实施了严格的 RBAC 角色鉴权和 JWT 黑名单机制。管理员可以一站式洞察全站经营指标，并对订单执行正向状态流转与退款审核。所有敏感操作均在网关层做过权限拦截与审计追踪。”

---

### 阶段五：工程交付质量与高可用部署 (03:45 - 04:30)
- **屏幕操作**: 切换到 Tab 3 (Knife4j 文档) 与终端展示。
- **操作动作**:
  1. 展示 Knife4j 聚合的微服务 API 文档大盘，在线发起一次健康检查接口调试；
  2. 简短展示单元测试与覆盖率（JaCoCo 报告：状态机 100%、Webhook 100%、Auth 90%+）；
  3. 展示 `docs/DEPLOYMENT_GUIDE.md` 与 `docker-compose.prod.yml` 生产就绪方案。
- **讲解要点**:
  > “最后在工程交付层面：
  >
  > - **标准化文档体系**: 我们通过 Knife4j OpenAPI 3.0 聚合了全系统各微服务的 Swagger 接口定义；
  > - **质量防线**: 后端采用 JaCoCo 进行覆盖率把关，核心业务模块覆盖率超 90%，并配套了 Playwright E2E 端到端回归套件；
  > - **生产就绪**: 提供了一键式 Docker Compose 生产编排及 AWS ECS Fargate + RDS + ElastiCache 架构指南，具备企业级的高可用与横向扩容能力。
  > 
  > 以上就是 Stickybeak 系统的完整演示，欢迎各位评委和老师提问！”

---

## 3. 常见答辩问题与应对策略 (Q&A Defense Cheat Sheet)

| 典型问题 | 回答要点与核心关键词 |
| :--- | :--- |
| **Q1: 为什么选择 RabbitMQ 而不是纯 RPC 驱动支付后流程？** | **答**: 异步解耦与削峰填谷。支付成功后发邮件、扣减真实库存、通知商家属于高 I/O 操作，若采用同步 RPC 会延长 Stripe Webhook 响应时延（容易被 Stripe 判定为超时并重复投递）。通过 RabbitMQ 最终一致性，Webhook 可在 20ms 内极速返回 200。 |
| **Q2: 如何解决下单与库存扣减的并发超卖？** | **答**: 采用 Redis Lua 脚本预扣库存实现毫秒级原子校验；结合数据库乐观锁 `version` 或分布式锁防并发穿透；在订单状态机中严格遵循原子转移。 |
| **Q3: 生产环境下如何保证 JWT 的登出安全性？** | **答**: 用户点击登出后，网关/认证服务会将该 Token 解析出剩余 TTL，并将 `jwt:blacklist:{token}` 写入 Redis 缓存。网关全局过滤器校验请求时先查 Redis 黑名单，杜绝 Token 重放攻击。 |
| **Q4: 为什么微服务之间不用普通 HTTP，而采用 Nacos 注册发现？** | **答**: 生产环境下服务会动态弹性扩缩容。Nacos 提供了心跳保活、健康检测与负载均衡（LoadBalancer）。当某个微服务实例下线时，网关与消费方能够毫秒级平滑摘除流量。 |
