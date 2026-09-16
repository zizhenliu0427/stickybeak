# Stickybeak 🧲🦜 企业级微服务电商中台

<div align="center">

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.1-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.2-blue.svg)](https://spring.io/projects/spring-cloud)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg?logo=openjdk)](https://openjdk.org/)
[![React](https://img.shields.io/badge/React-18.3-61DAFB.svg?logo=react)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v1.0.0-success.svg)](https://github.com/zizhenliu0427/stickybeak/releases)

**基于 Spring Cloud Alibaba + React 18 的云原生澳大利亚风情文创跨境电商平台。**

[English](README.md) | [中文说明文档](README_CN.md) | [生产部署手册](docs/DEPLOYMENT_GUIDE.md) | [演示剧本与答辩指南](docs/DEMO_SCRIPT.md)

</div>

---

## 🌟 项目简介

> **StickyBeak** 源自澳大利亚俚语，原意为“爱凑热闹/好奇的人”，同时巧妙融合了 **Sticky**（磁贴/冰箱贴）与 **Beak**（鹦鹉鸟喙，平台特色吉祥物），主打澳大利亚特色文创磁贴与本土手作好物的跨境直邮。

**Stickybeak** 是一个按工业级标准打造的云原生分布式微服务系统。针对跨境出海业务中典型的高并发库存冲突、国际支付掉单与重复扣款、长链路履约状态不一致等痛点，进行了全链路深度架构设计与防线构筑：
- **云原生微服务底座**: 基于 Spring Cloud Alibaba（Nacos 服务发现/配置中心、Sentinel 流量防护、Seata 分布式事务）构建高可用微服务治理体系；
- **严格有限状态机（FSM）**: 订单全生命周期严格流转约束，杜绝逆向篡改、跨状态跳跃与并发竞争脏数据；
- **Stripe 国际化支付与幂等闭环**: 深度集成 Stripe 支付收银台，具备 HMAC SHA-256 签名验签、防重放时间窗口与底层 `UK_stripe_event_id` 唯一防重约束；
- **事件驱动最终一致性**: 基于 RabbitMQ 主题交换机与死信延迟队列，实现 30 分钟超时自动关单补库存，以及秒级异步订单确认邮件推送（MailHog / AWS SES）；
- **现代化双模前端应用**: React 18 + TypeScript + Vite + Redux Toolkit，商城端采用 TailwindCSS 流畅自适应，管理端采用 Ant Design 5 打造高效率运营中台；
- **工程级测试防线**: 集成 JaCoCo 单元测试覆盖率审计（核心模块覆盖率 > 90%），配备跨桌面端与移动端的 Playwright E2E 全链路回归套件。

---

## 🏗️ 整体架构图

```
                                [ 客户端浏览器 / 移动端 H5 ]
                                               │
                                               ▼
                              [ Nginx 生产反向代理网关 (:80 / :443) ]
                                (SSL/TLS, Gzip 压缩, SPA 路由兜底, 静态缓存)
                                               │
                                               ▼
                         [ Spring Cloud Gateway 集群 (:8080) ]
                           ├── JWT 令牌鉴权与全局用户上下文解析
                           ├── Redis Token 黑名单即时吊销过滤器
                           ├── Knife4j OpenAPI 3.0 全局接口聚合
                           └── Sentinel 限流、熔断降级防御
                                               │
            ┌──────────────────┬───────────────┴───────────────┬──────────────────┐
            ▼                  ▼                               ▼                  ▼
     ┌──────────────┐   ┌──────────────┐                ┌──────────────┐   ┌──────────────┐
     │  Auth Svc    │   │ Product Svc  │                │  Cart Svc    │   │  Order Svc   │
     │   (:8081)    │   │   (:8082)    │                │   (:8083)    │   │   (:8084)    │
     └──────┬───────┘   └──────┬───────┘                └──────┬───────┘   └──────┬───────┘
            │                  │                               │                  │
            │                  ├──────────────────────┐        │                  │
            │                  ▼                      ▼        ▼                  │
            │            [ Elasticsearch 8 ]    [ Redis 7 集群 ]                  │
            │            (海量商品全文检索)     (热点缓存 & 分布式购物车)         │
            │                                                                     │
            └───────────────────────────┬─────────────────────────────────────────┘
                                        │
                                        ▼
                                 [ MySQL 8.0 ]
                           (各微服务独立分库设计)
                                        ▲
                                        │ (Saga / AT 分布式事务协调器)
                               [ Seata Server (:8091) ]
                                        ▲
                                        │
            ┌───────────────────────────┴───────────────────────────┐
            ▼                                                       ▼
     ┌──────────────┐                                        ┌──────────────┐
     │ Payment Svc  │                                        │Notification  │
     │   (:8085)    │                                        │   (:8086)    │
     └──────┬───────┘                                        └──────▲───────┘
            │                                                       │
            ├──────────────► [ RabbitMQ 消息总线 ] ─────────────────┘
            │                (order.paid / order.cancel / 死信延迟队列)
            ▼
     [ Stripe API & Webhook ]
```

---

## 💎 核心架构与技术亮点

### 1. 国际化跨境支付与强幂等性保障
- 严格校验 Stripe HMAC SHA-256 Webhook 签名，设置 300 秒时间戳重放攻击防御；
- 数据库底层建立 `UK_stripe_event_id` 唯一索引硬隔离，即使在高并发重试下也能毫秒级拦截重复通知，返回 200 幂等响应；
- 解耦异步履约链路，支付成功后通过 RabbitMQ 广播 `order.paid` 事件，下游服务并行执行履约与通知。

### 2. 严密的有限状态机（FSM）
- 订单状态全周期闭环：`待支付 (1) -> 已支付 (2) -> 已发货 (3) -> 已完成 (4) / 已取消 (5) / 已退款 (6)`；
- 状态机严格阻断逆向流转与非法跨态跃迁，非法操作抛出受检 `BusinessException`，保护资金安全与数据一致性。

### 3. 分布式事务与防超卖机制
- 结算时利用 Redis Lua 脚本原子预扣减库存，杜绝高并发穿透与秒杀超卖；
- 接入 Seata 分布式事务协调器，超时关单或异常退单时自动发起跨库库存补偿。

### 4. 工业级可观测性与文档中台
- **Knife4j / OpenAPI 3.0**: 微服务聚合式接口文档大盘，网关统一路由 `/doc.html`；
- **JaCoCo 质量把关**: Maven 构建链条强制集成覆盖率门禁（状态机与支付 Webhook 覆盖率超 90%）；
- **全链路 Playwright E2E**: 覆盖正向买家购买闭环、Stripe 拒付负向流转及管理端审核履约。

---

## 📦 微服务项目模块划分

```
stickybeak/
├── stickybeak-gateway/        # Spring Cloud Gateway 微服务网关、JWT 过滤器、Knife4j 聚合
├── stickybeak-auth/           # 用户认证中心、RBAC 角色权限、地址簿管理
├── stickybeak-product/        # 商品中心、品类分类、库存流转、ES 搜索
├── stickybeak-cart/           # 分布式购物车（游客状态 + 登录合并）、心愿单
├── stickybeak-order/          # 订单状态机引擎、Seata 事务、库存预扣与延迟关单
├── stickybeak-payment/        # 跨境支付抽象、Stripe 收银台对接、Webhook 强幂等防重
├── stickybeak-notification/   # 邮件通知中心（基于 RabbitMQ 消费与 MailHog 发送）
├── stickybeak-common/         # 全局通用返回体 Result、统一错误码、OpenAPI 全局元数据
├── stickybeak-frontend/       # React 18 + Vite + Redux Toolkit + Tailwind + Ant Design
├── docker/                    # Docker Compose 编排（本地开发 & 生产就绪）、Nginx、MySQL 初始化
├── docs/                      # 架构全景文档、数据库 ER 图、云上部署手册、演示剧本
└── scripts/                   # 自动化 E2E 验证脚本、数据初始化辅助脚本
```

---

## 🚀 快速启动指南

### 环境依赖
- **Java**: Eclipse Temurin JDK 17+
- **Node.js**: 20+ (包含 npm)
- **Docker**: Docker Engine 24+ & Docker Compose v2+

### 方式一：本地开发调试模式

```bash
# 1. 启动基础设施中间件（MySQL、Redis、RabbitMQ、Nacos、Seata、MinIO、MailHog）
docker compose -f docker/docker-compose-infra.yml up -d

# 2. 编译并启动后端微服务
# 在 IDE 中或终端启动 stickybeak-gateway, stickybeak-auth 等各个服务：
mvn spring-boot:run -pl stickybeak-gateway
mvn spring-boot:run -pl stickybeak-auth
# 或编译全部模块包：
mvn clean package -DskipTests

# 3. 启动前端商城与后台
cd stickybeak-frontend
npm install
npm run dev
# 前端本地访问地址: http://localhost:5173
```

### 方式二：全栈一键容器化部署

```bash
# 构建并启动全部微服务、前端及 Nginx 反向代理
docker compose -f docker/docker-compose.yml up -d --build
```
打开浏览器访问 **http://localhost** 即可畅享完整商城。

---

## 🎛️ 控制台与端口全景表

| 服务名称 | 端口 | 访问地址 | 默认账号 / 备注 |
| :--- | :--- | :--- | :--- |
| **前端商城 & 管理端** | 80 | `http://localhost/` | 前端 SPA |
| **微服务网关 (Gateway)** | 8080 | `http://localhost:8080` | 微服务统一入口 |
| **Knife4j API 聚合文档** | 8080 | `http://localhost:8080/doc.html` | 公开接口调试 |
| **Nacos 注册与配置中心** | 8848 | `http://localhost:8848/nacos` | `nacos / nacos` |
| **RabbitMQ 消息中台** | 15672 | `http://localhost:15672` | `guest / guest` |
| **MailHog 邮件测试控制台** | 8025 | `http://localhost:8025` | 邮件实时查看 |
| **MinIO 对象存储中台** | 9001 | `http://localhost:9001` | `minioadmin / minioadmin` |
| **Elasticsearch 搜索引擎** | 9200 | `http://localhost:9200` | 单节点检索 |

---

## 🧪 自动化测试与覆盖率把关

### 1. 后端单元测试与 JaCoCo 覆盖率
```bash
# 在 Docker 环境中执行测试并生成 JaCoCo 报告
docker run --rm \
  -v "$(pwd):/workspace" \
  -v "stickybeak-m2:/root/.m2" \
  -w /workspace \
  maven:3.9-eclipse-temurin-17 mvn test
```
测试报告生成于各模块路径：`stickybeak-<module>/target/site/jacoco/index.html`。

### 2. 前端单元测试与类型安全校验
```bash
cd stickybeak-frontend
npm run typecheck    # TypeScript 类型全面静态检查
npm run test         # Vitest 单元测试套件
npm run build        # 生产包编译验证
```

### 3. 全链路 E2E 自动化回归验证
```bash
# 运行全链路端到端自动化业务与安全拦截测试
node scripts/test-sprint8-e2e.js
```

---

## 🌐 生产环境云上线指南

有关在 **AWS (ECS Fargate + RDS Multi-AZ + ElastiCache + ALB)** 上的企业级生产上线方案：  
👉 **[点击阅读完整云部署手册 (docs/DEPLOYMENT_GUIDE.md)](docs/DEPLOYMENT_GUIDE.md)**

```bash
# 生产环境 Docker Compose 快速上线
docker compose -f docker/docker-compose.prod.yml --env-file docker/.env.prod up -d
```

---

## 🎬 产品路演与技术答辩剧本

需要向技术面试官、导师评审组或投资人展示 Stickybeak？  
👉 **[点击查看 3-5 分钟演示剧本与答辩攻略 (docs/DEMO_SCRIPT.md)](docs/DEMO_SCRIPT.md)**

---

## 📄 开源许可 (License)

本项目采用 **Apache License 2.0** 许可证。
由 Stickybeak 研发团队匠心设计与构建。
