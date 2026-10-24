# Stickybeak 🧲🦜 Enterprise E-Commerce Platform

<div align="center">

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-blue.svg)](https://spring.io/projects/spring-cloud)
[![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2023.0.1.2-orange.svg)](https://github.com/alibaba/spring-cloud-alibaba)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg?logo=openjdk)](https://openjdk.org/)
[![React](https://img.shields.io/badge/React-18.3-61DAFB.svg?logo=react)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-v1.4.0-success.svg)](docs/SPRINT_ISSUES.md)

**A Cloud-Native, High-Performance Microservices E-Commerce Platform for Australian Themed Souvenirs & Regional Specialities.**

[English](README.md) | [中文说明文档](README_CN.md) | [Production Deployment](docs/DEPLOYMENT_GUIDE.md) | [Demo Script](docs/DEMO_SCRIPT.md)

</div>

---

## 🌟 Overview

**Stickybeak** is a distributed e-commerce system built on a modern cloud-native stack. It targets the hard parts of cross-border commerce — overselling under load, duplicate or lost payment notifications, and orders drifting out of sync across services:
- **Distributed Architecture**: Spring Cloud Alibaba — Nacos service discovery & config, Sentinel gateway rate limiting, and Seata AT distributed transactions for checkout.
- **Strict State Machine (FSM)**: Every order transition is validated and persisted with a conditional update, so concurrent payment, timeout and cancellation events cannot corrupt an order.
- **Stripe Cross-Border Payments**: Hosted Checkout with cards, Alipay and WeChat Pay; webhook signature verification, amount/currency checks, database-backed idempotency, and full refunds through the Stripe Refund API.
- **Event-Driven Fulfilment**: RabbitMQ topic exchange with manual ACK, retry queues and dead-letter queues; 30-minute auto-cancellation via a delay queue plus a sweeper; order confirmation emails over SMTP (MailHog locally, Amazon SES in production).
- **Full-Text Search**: Elasticsearch 8 with the IK Chinese analyzer — weighted multi-field matching, highlighting and relevance sorting, with automatic fallback to MySQL.
- **Dual-Front React 18 Application**: TailwindCSS-powered consumer storefront (PWA) paired with an Ant Design 5 admin console.
- **Engineering Rigor**: A JaCoCo coverage gate in `mvn verify` (≥ 90% line coverage enforced for the order state machine and Stripe webhook handling), Playwright journeys on desktop and mobile in CI, and full-stack API E2E scripts.

---

## 🏗️ System Architecture

```
                                [ Web Browser / Mobile Client ]
                                               │
                                               ▼
                              [ Nginx Edge Reverse Proxy (:80 / :443) ]
                                (SSL, Gzip, SPA Routing, Static Assets)
                                               │
                                               ▼
                              [ Spring Cloud Gateway (:8080) ]
                           ├── JWT Auth & Claims → X-User-* headers
                           ├── Redis Token Blacklist Revocation
                           ├── Internal-API & TX_XID header stripping
                           ├── Knife4j OpenAPI 3.0 Aggregation (/doc.html)
                           └── Sentinel Rate Limiting (HTTP 429 JSON)
                                               │
            ┌──────────────────┬───────────────┴───────────────┬──────────────────┐
            ▼                  ▼                               ▼                  ▼
     ┌──────────────┐   ┌──────────────┐                ┌──────────────┐   ┌──────────────┐
     │  Auth Svc    │   │ Product Svc  │                │  Cart Svc    │   │  Order Svc   │
     │   (:8081)    │   │   (:8082)    │                │   (:8083)    │   │   (:8084)    │
     └──────┬───────┘   └──┬────────┬──┘                └──────┬───────┘   └──┬───────┬───┘
            │              │        │                          │              │       │
            │              │        ▼                          │              │       │
            │              │  [ Elasticsearch 8 + IK ]         │              │       │
            │              │  (search; MySQL fallback)         │              │       │
            │              │                                   │              │       │
            │              └────── Seata AT (checkout) ────────┼──────────────┘       │
            │                 [ Seata Server 2.0 (:8091) ]     │                      │
            ▼                       ▼                          ▼                      │
     [ MySQL 8.0 — one schema per service ]     [ Redis 7 (single node): stock,       │
                                                  carts, caches, token blacklist ]    │
                                                                                      │
     ┌──────────────┐   order.paid / order.refunded   ┌─────────────────────────────┐ │
     │ Payment Svc  │────────────────────────────────►│ RabbitMQ (topic exchange,   │ │
     │   (:8085)    │                                 │ delay queue, retry, DLQ)    │ │
     └──────┬───────┘                                 └──────┬───────────────┬──────┘ │
            │                     order.paid (email)         │               │        │
            ▼                  ┌─────────────────────────────┘               └────────┘
     [ Stripe API & Webhooks ] ▼                       order.paid / timeout close / refund
                        ┌──────────────┐
                        │ Notification │
                        │   (:8086)    │
                        └──────────────┘
```

---

## 💎 Key Architectural Highlights

### 1. Cross-Border Stripe Payment with Robust Idempotency
- Verifies Stripe webhook signatures (HMAC SHA-256 with the SDK's 300-second replay window) and checks the paid amount and currency against the payment record before fulfilling.
- A unique key on the Stripe event ID (`uk_event_id`) turns concurrent retries of the same notification into no-ops.
- Checkout sessions can only be created by the order service; the amount always comes from the order, never from the browser.
- Full refunds: admin-initiated refunds call the Stripe Refund API with an idempotency key, the `charge.refunded` webhook confirms them, and a payment that arrives after the order was cancelled is refunded automatically.

### 2. Strict Finite State Machine (FSM)
- Order lifecycle: `pending → paid → processing → shipped → completed`, `pending → cancelled`, and `paid / processing → refunding → refunded`.
- Illegal or skipped transitions raise a `BusinessException`; every transition is written with `UPDATE … WHERE status = <expected>`, so a racing update fails instead of overwriting.

### 3. Distributed Transactions & Inventory Locks
- Three layers against overselling: Redis Lua atomic pre-deduction → Redisson lock per product → MySQL conditional update (`stock >= qty`).
- Checkout runs in a **Seata AT global transaction** (`CheckoutTransaction`): the stock deduction in the product database and the order write in the order database commit or roll back together.
- Redis pre-deductions are recorded in a per-order ledger and restored atomically when the transaction does not commit, including through a Seata rollback hook.
- Local writes that touch the same stock rows (cancellations, refunds, admin stock edits) use `@GlobalLock`; the Stripe call happens after the global commit, so hot products are not locked during external I/O.

### 4. Product Search
- Elasticsearch 8 with the IK analyzer: weighted multi-field matching (name, category, tags, description), highlighted names and relevance or list sorting.
- Admin changes are re-indexed after commit, and a full reconciliation runs at startup and daily. If Elasticsearch is down or disabled, search falls back to MySQL.

### 5. Observability, API Docs & Quality Gates
- **SkyWalking Tracing**: all 7 services run the SkyWalking Java agent, so one trace covers gateway → order → cart / product / payment and the asynchronous `order.paid` hand-off to order and notification. Responses carry an `X-Trace-Id` header, and logs carry the same trace ID and are shipped to OAP.
- **Alerts**: slow and failing services or endpoints, slow SQL, and dead-letter queue backlog (a custom meter reported by the order service).
- **Trace-driven tuning**: traces exposed an N+1 query on the product list; batching it raised throughput from 64 to 191 req/s and cut p99 from 1.57 s to 392 ms ([details](docs/PERF_PRODUCT_LIST.md)).
- **Knife4j / OpenAPI 3.0**: each service publishes springdoc specs, aggregated at the gateway's `/doc.html`; service-to-service endpoints are labelled, and docs are switched off in production.
- **JaCoCo Gate**: `mvn verify` fails when a module drops below its line-coverage floor.
- **Playwright Journeys**: search, guest-to-checkout, order history and admin refunds run on Desktop Chrome and Pixel 7 in CI.

---

## 📦 Repository Structure

```
stickybeak/
├── stickybeak-gateway/        # Spring Cloud Gateway, JWT filter, Sentinel, Knife4j aggregation
├── stickybeak-auth/           # User authentication, RBAC, address book
├── stickybeak-product/        # Product catalog, categories, inventory, ES search
├── stickybeak-cart/           # Redis cart (guest + login merge), wishlist
├── stickybeak-order/          # Order state machine, Seata AT checkout, timeouts & refunds
├── stickybeak-payment/        # Stripe sandbox, payment provider abstraction, webhooks & refunds
├── stickybeak-notification/   # Email notifications via RabbitMQ & SMTP (MailHog / SES)
├── stickybeak-common/         # Response wrapper, error codes, internal-API guard, OpenAPI config
├── stickybeak-frontend/       # React 18 + Vite + Redux Toolkit + Tailwind + AntD, Playwright e2e
├── docker/                    # Docker Compose files (local & prod), Nginx configs, MySQL DDL
├── docs/                      # Deployment guide, demo script, architecture, ER diagrams
└── scripts/                   # Full-stack E2E verification & seed scripts
```

---

## 🚀 Quick Start Guide

### Prerequisites
- **Java**: Eclipse Temurin JDK 17+
- **Node.js**: 20+ (with npm)
- **Docker**: Docker Engine 24+ & Docker Compose v2+

### Option A: Local Development Environment

```bash
# 1. Start core infrastructure (MySQL, Redis, RabbitMQ, Nacos, Seata, Elasticsearch, MinIO, MailHog)
docker compose -f docker/docker-compose-infra.yml up -d

# 2. Run backend microservices
# Start stickybeak-gateway, stickybeak-auth, stickybeak-product, etc. from your IDE or CLI:
mvn spring-boot:run -pl stickybeak-gateway
mvn spring-boot:run -pl stickybeak-auth
# ... or build all jars:
mvn clean package -DskipTests

# 3. Start frontend development server
cd stickybeak-frontend
npm install
npm run dev
# Front-end available at: http://localhost:5173
```

### Option B: One-Command Full Stack (Docker Compose)

```bash
# Builds and launches all microservices, middleware, and Nginx reverse proxy
docker compose -f docker/docker-compose.yml up -d --build
```
Open **http://localhost** in your browser.

> Existing MySQL volumes do not re-run the init scripts. After upgrading, apply `docker/mysql/init/07-seata-undo-log.sql` once (Seata `undo_log` tables).

---

## 🎛️ Management Consoles & Port Matrix

| Service | Port | Default URL | Credentials |
| :--- | :--- | :--- | :--- |
| **Frontend Storefront & Admin** | 80 | `http://localhost/` | N/A |
| **API Gateway** | 8080 | `http://localhost:8080` | N/A |
| **Knife4j OpenAPI 3.0 Docs** | 8080 | `http://localhost:8080/doc.html` | Public (disabled in prod) |
| **Nacos Discovery & Config** | 8848 | `http://localhost:8848/nacos` | `nacos / nacos` |
| **Seata Console** | 7091 | `http://localhost:7091` | `seata / seata` |
| **RabbitMQ Management** | 15672 | `http://localhost:15672` | `guest / guest` |
| **MailHog Inbox** | 8025 | `http://localhost:8025` | Public |
| **MinIO Storage Console** | 9001 | `http://localhost:9001` | `minioadmin / minioadmin` |
| **Elasticsearch** | 9200 (full stack) / 19200 (infra compose) | `http://localhost:9200` | Security disabled |
| **SkyWalking UI** | 8088 | `http://localhost:8088` | Public |
| **SkyWalking OAP** | 11800 (agent gRPC) / 12800 (HTTP, GraphQL) | `http://localhost:12800/healthcheck` | Storage: BanyanDB |

---

## 🧪 Testing & Quality Assurance

### 1. Backend Unit Tests & Coverage Gate
```bash
# Runs all tests, writes JaCoCo reports and enforces the coverage floors
docker run --rm \
  -v "$(pwd):/workspace" \
  -v "stickybeak-m2:/root/.m2" \
  -w /workspace \
  maven:3.9-eclipse-temurin-17 mvn verify
```
Reports: `stickybeak-<module>/target/site/jacoco/index.html`. Current line coverage (floor enforced by the gate):

| Module | Line coverage | Floor |
| :--- | :--- | :--- |
| gateway | 73.0% | 65% |
| product | 61.7% | 55% |
| notification | 54.0% | 50% |
| cart | 53.9% | 50% |
| order | 49.3% | 45% (`OrderStateMachine` 100%, floor 90%) |
| payment | 45.2% | 35% (`WebhookServiceImpl` 97.2%, floor 90%) |
| auth | 30.5% | 30% |

### 2. Frontend Unit, Type Safety & Journey Tests
```bash
cd stickybeak-frontend
npm run typecheck    # TypeScript (app + e2e)
npm run test         # Vitest unit test suite
npm run build        # Production bundle compilation
npm run e2e          # Playwright journeys, Desktop Chrome + Pixel 7 (API mocked in the browser)
```

### 3. Full-Stack End-to-End (E2E) Regression Testing
```bash
# Against a running Docker Compose stack: buyer flow, webhook idempotency, admin operations, RBAC
node scripts/test-sprint8-e2e.js
```

---

## 🌐 Cloud Production Deployment

For enterprise production deployments on **Amazon Web Services (AWS)** using ECS Fargate, RDS Multi-AZ, ElastiCache, and ALB:
👉 **[Read the Complete Cloud Deployment Guide (docs/DEPLOYMENT_GUIDE.md)](docs/DEPLOYMENT_GUIDE.md)**

```bash
# Production Docker Compose launcher
docker compose -f docker/docker-compose.prod.yml --env-file docker/.env.prod up -d
```

---

## 🎬 Product Demo & Interview Script

Need to present Stickybeak for a technical interview, team review, or stakeholder walk-through?  
👉 **[Check out the 3-5 Minute Demo Script & Defense Guide (docs/DEMO_SCRIPT.md)](docs/DEMO_SCRIPT.md)**

---

## 📄 License & Attribution

This project is licensed under the **[Apache License 2.0](LICENSE)**.
Created with passion by the Stickybeak Engineering Team.
