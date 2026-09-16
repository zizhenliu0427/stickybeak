# StickyBeak 🧲🦜

> 澳洲主题冰箱贴电商平台 — Spring Cloud 微服务 + React。
> 名字来源：StickyBeak 是澳洲俚语"爱凑热闹的人"，同时暗合 sticky（磁贴）+ beak（鸟喙，招牌大葵小鸟系列）。

[English](README.md)

[![CI](.github/workflows/ci.yml/badge.svg)](.github/workflows/ci.yml)

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 3.3, Spring Cloud 2023, Spring Cloud Alibaba (Nacos / Sentinel / Seata) |
| 数据 | MySQL 8（每服务独立库）, MyBatis Plus, Redis 7, RabbitMQ, Elasticsearch 8 |
| 前端 | React 18, TypeScript, Vite, Redux Toolkit, React Router v6, Tailwind（商城端）, Ant Design 5（管理端） |
| 支付 | Stripe sandbox — 银行卡（AU）、支付宝、微信支付；AUD / CNY 币种切换 |
| 运维 | Docker Compose, Nginx, GitHub Actions, Knife4j (Swagger) |

## 仓库结构

```
stickybeak/
├── stickybeak-gateway/        # API 网关（路由、鉴权过滤、Sentinel 限流）
├── stickybeak-auth/           # JWT 认证、RBAC（customer/admin/sysadmin）、地址管理
├── stickybeak-product/        # 商品目录、库存、ES 搜索
├── stickybeak-cart/           # 购物车（游客 + 登录合并）、心愿单
├── stickybeak-order/          # 订单状态机、Seata 事务、库存锁定
├── stickybeak-payment/        # 支付提供商抽象、Stripe、Webhook 幂等
├── stickybeak-notification/   # RabbitMQ 异步邮件通知
├── stickybeak-common/         # 统一返回体、错误码、全局异常处理
├── stickybeak-frontend/       # React 单页应用
├── docker/                    # Compose 文件、Dockerfile、Nginx、MySQL 初始化
├── scripts/                   # 数据导入 & 开发辅助脚本
└── docs/                      # ARCHITECTURE.md / DATABASE_ER.md / SPRINT_ISSUES.md
```

## 快速开始

环境要求：JDK 17+、Maven 3.8+、Node 20+、Docker。

```bash
# 1. 启动基础设施（MySQL、Redis、RabbitMQ、ES、Nacos、Seata、MinIO、MailHog）
docker compose -f docker/docker-compose-infra.yml up -d

# 2. 启动后端服务（各自开一个终端）
mvn -q -pl stickybeak-gateway,stickybeak-auth -am spring-boot:run
# ... 或在 IDE 中运行各模块的 *Application 启动类

# 3. 启动前端
cd stickybeak-frontend
npm install
npm run dev        # http://localhost:5173
```

全栈一键启动：

```bash
docker compose -f docker/docker-compose.yml up -d --build   # 前端访问 http://localhost
```

### 管理控制台

| 服务 | 地址 |
|---|---|
| 网关 | http://localhost:8080 |
| Nacos | http://localhost:8848/nacos (nacos/nacos) |
| RabbitMQ | http://localhost:15672 (guest/guest) |
| MinIO | http://localhost:9001 (minioadmin/minioadmin) |
| MailHog | http://localhost:8025 |
| Elasticsearch | http://localhost:9200 |

## 开发规范

- 代码风格：阿里巴巴 Java 开发规范；分层 `controller → service → mapper → entity`
- API：RESTful，统一返回体 `{ code, message, data }`（参见 `stickybeak-common`）
- 数据库：`t_` 前缀、snake_case、`is_deleted` 逻辑删除、`DECIMAL(10,2)` 金额
- 分支：Git Flow（`main` / `develop` / `feature-xxx`）；提交 `feat:` `fix:` `docs:` `refactor:` `chore:`
- Sprint 计划与 Issue 列表：[docs/SPRINT_ISSUES.md](docs/SPRINT_ISSUES.md)

## 文档

- [架构设计](docs/ARCHITECTURE.md)
- [数据库 ER 图](docs/DATABASE_ER.md)
- [Sprint 计划](docs/SPRINT_ISSUES.md)
