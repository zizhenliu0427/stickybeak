# StickyBeak 🧲🦜

> Aussie-themed fridge magnet e-commerce platform — Spring Cloud microservices + React.
> 名字来源：StickyBeak 是澳俚"爱凑热闹的人"，同时暗合 sticky（磁贴）+ beak（鸟喙，招牌大葵小鸟系列）。

[![CI](.github/workflows/ci.yml/badge.svg)](.github/workflows/ci.yml)

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Cloud 2023, Spring Cloud Alibaba (Nacos / Sentinel / Seata) |
| Data | MySQL 8 (per-service DBs), MyBatis Plus, Redis 7, RabbitMQ, Elasticsearch 8 |
| Frontend | React 18, TypeScript, Vite, Redux Toolkit, React Router v6, Tailwind (storefront), Ant Design 5 (admin) |
| Payment | Stripe sandbox — card (AU), Alipay, WeChat Pay; AUD/CNY currency switch |
| Ops | Docker Compose, Nginx, GitHub Actions, Knife4j (Swagger) |

## Repository Layout

```
stickybeak/
├── stickybeak-gateway/        # API gateway (routing, auth filter, Sentinel)
├── stickybeak-auth/           # JWT auth, RBAC (customer/admin/sysadmin), addresses
├── stickybeak-product/        # catalogue, stock, ES search
├── stickybeak-cart/           # cart (guest + merge-on-login), wishlist
├── stickybeak-order/          # order state machine, Seata saga, stock holds
├── stickybeak-payment/        # payment provider abstraction, Stripe, webhook idempotency
├── stickybeak-notification/   # email notifications via RabbitMQ
├── stickybeak-common/         # Result wrapper, error codes, global exception handler
├── stickybeak-frontend/       # React SPA
├── docker/                    # compose files, Dockerfiles, nginx, MySQL init
├── scripts/                   # data import & dev helpers
└── docs/                      # ARCHITECTURE.md / DATABASE_ER.md / SPRINT_ISSUES.md
```

## Quick Start

Prereqs: JDK 17+, Maven 3.8+, Node 20+, Docker.

```bash
# 1. Infrastructure (MySQL, Redis, RabbitMQ, ES, Nacos, Seata, MinIO, MailHog)
docker compose -f docker/docker-compose-infra.yml up -d

# 2. Backend services (each in its own terminal)
mvn -q -pl stickybeak-gateway,stickybeak-auth -am spring-boot:run
# ... or run each module's *Application from your IDE

# 3. Frontend
cd stickybeak-frontend
npm install
npm run dev        # http://localhost:5173
```

Full stack in one shot:

```bash
docker compose -f docker/docker-compose.yml up -d --build   # frontend on http://localhost
```

### Consoles

| Service | URL |
|---|---|
| Gateway | http://localhost:8080 |
| Nacos | http://localhost:8848/nacos (nacos/nacos) |
| RabbitMQ | http://localhost:15672 (guest/guest) |
| MinIO | http://localhost:9001 (minioadmin/minioadmin) |
| MailHog | http://localhost:8025 |
| Elasticsearch | http://localhost:9200 |

## Conventions

- Code style: Alibaba Java Coding Guidelines; layers `controller → service → mapper → entity`
- API: RESTful, unified body `{ code, message, data }` (see `stickybeak-common`)
- DB: `t_` prefix, snake_case, `is_deleted` logical delete, `DECIMAL(10,2)` money
- Branches: Git Flow (`main` / `develop` / `feature-xxx`); commits `feat:` `fix:` `docs:` `refactor:` `chore:`
- Sprint plan & issue backlog: [docs/SPRINT_ISSUES.md](docs/SPRINT_ISSUES.md)

## Docs

- [Architecture](docs/ARCHITECTURE.md)
- [Database ER](docs/DATABASE_ER.md)
- [Sprint plan](docs/SPRINT_ISSUES.md)
