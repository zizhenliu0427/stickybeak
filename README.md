# Stickybeak 🧲🦜 Enterprise E-Commerce Platform

<div align="center">

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.1-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.2-blue.svg)](https://spring.io/projects/spring-cloud)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg?logo=openjdk)](https://openjdk.org/)
[![React](https://img.shields.io/badge/React-18.3-61DAFB.svg?logo=react)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v1.0.0-success.svg)](https://github.com/zizhenliu0427/stickybeak/releases)

**A Cloud-Native, High-Performance Microservices E-Commerce Platform for Australian Themed Souvenirs & Regional Specialities.**

[English](README.md) | [中文说明文档](README_CN.md) | [Production Deployment](docs/DEPLOYMENT_GUIDE.md) | [Demo Script](docs/DEMO_SCRIPT.md)

</div>

---

## 🌟 Overview

**Stickybeak** is an enterprise-grade distributed e-commerce system engineered with a modern cloud-native stack. Built from the ground up to address real-world cross-border commerce challenges, it integrates:
- **Resilient Distributed Architecture**: Spring Cloud Alibaba (Nacos, Sentinel, Seata) providing robust service discovery, dynamic traffic shaping, and distributed transaction guarantees.
- **Strict State Machine (FSM)**: Eliminates illegal order state transitions and race conditions during payment and fulfilment.
- **Stripe Cross-Border Payment Engine**: Full sandbox integration supporting international credit cards, Alipay, and WeChat Pay, reinforced with cryptographic HMAC signature verification and database-backed idempotency.
- **Event-Driven Asynchronous Fulfilment**: RabbitMQ topic exchanges and dead-letter delay queues (30-minute auto-cancellation, instant transactional email confirmations via Mailhog/SES).
- **Dual-Front React 18 Application**: TailwindCSS-powered modern consumer storefront paired with an Ant Design 5 administrative management console.
- **Enterprise Engineering Rigor**: JaCoCo code coverage auditing (>90% for state machine and webhooks) and automated Playwright E2E regression pipelines.

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
                         [ Spring Cloud Gateway Cluster (:8080) ]
                           ├── JWT Token Auth & Claims Resolution
                           ├── Redis Token Blacklist Revocation Filter
                           ├── Knife4j OpenAPI 3.0 Unified Aggregation
                           └── Sentinel Circuit Breaker & Rate Limiter
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
            │            [ Elasticsearch 8 ]    [ Redis 7 Cluster ]               │
            │            (Full-text Search)     (Hot Cache & Carts)               │
            │                                                                     │
            └───────────────────────────┬─────────────────────────────────────────┘
                                        │
                                        ▼
                                 [ MySQL 8.0 ]
                       (Isolated Per-Service Schemas)
                                        ▲
                                        │ (Saga / AT Branch Coordinator)
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
            ├──────────────► [ RabbitMQ Topic Exchange ] ───────────┘
            │                (order.paid / order.cancel / DLX)
            ▼
     [ Stripe API & Webhooks ]
```

---

## 💎 Key Architectural Highlights

### 1. Cross-Border Stripe Payment with Robust Idempotency
- Cryptographically validates Stripe HMAC SHA-256 signatures with replay attack time-window protection (< 300s).
- Backed by an atomic database constraint (`UK_stripe_event_id`), eliminating duplicate fulfilments caused by concurrent network retries.
- Decouples downstream asynchronous tasks via RabbitMQ `order.paid` events.

### 2. Strict Finite State Machine (FSM)
- Governs the complete order lifecycle: `PENDING_PAYMENT (1) -> PAID (2) -> SHIPPED (3) -> COMPLETED (4) / CANCELLED (5) / REFUNDED (6)`.
- Rejects any illicit reverse or skipped transitions via strongly-typed `BusinessException` with zero database corruption.

### 3. Distributed Transactions & Inventory Locks
- Employs Redis atomic pre-deduction during checkout to protect against flash-sale overselling.
- Backed by Seata for cross-service inventory reservation rollbacks and automatic compensation.

### 4. Enterprise Observability & Documentation
- **Knife4j / OpenAPI 3.0**: Centrally aggregated API catalog accessible directly via Gateway `/doc.html`.
- **JaCoCo Audit**: Automated single-source code coverage thresholds integrated into Maven build phases.
- **Dual-Platform E2E Pipelines**: Playwright headless automated journeys across desktop and mobile form-factors.

---

## 📦 Repository Structure

```
stickybeak/
├── stickybeak-gateway/        # Spring Cloud Gateway, JWT filter, Knife4j router
├── stickybeak-auth/           # User authentication, RBAC, address book
├── stickybeak-product/        # Product catalog, categories, inventory, ES search
├── stickybeak-cart/           # Redis cart (guest + login merge), wishlist
├── stickybeak-order/          # Order finite state machine, Seata saga, stock locks
├── stickybeak-payment/        # Stripe sandbox, payment provider abstraction, webhook idempotency
├── stickybeak-notification/   # Email notifications via RabbitMQ & MailHog
├── stickybeak-common/         # Universal response wrapper, error codes, OpenAPI metadata
├── stickybeak-frontend/       # React 18 + Vite + Redux Toolkit + Tailwind + AntD
├── docker/                    # Docker Compose files (local & prod), Nginx configs, MySQL DDL
├── docs/                      # Deployment guide, demo script, architecture, ER diagrams
└── scripts/                   # Automated E2E verification & seed scripts
```

---

## 🚀 Quick Start Guide

### Prerequisites
- **Java**: Eclipse Temurin JDK 17+
- **Node.js**: 20+ (with npm)
- **Docker**: Docker Engine 24+ & Docker Compose v2+

### Option A: Local Development Environment

```bash
# 1. Start core infrastructure (MySQL, Redis, RabbitMQ, Nacos, Seata, MinIO, MailHog)
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

---

## 🎛️ Management Consoles & Port Matrix

| Service | Port | Default URL | Credentials |
| :--- | :--- | :--- | :--- |
| **Frontend Storefront & Admin** | 80 | `http://localhost/` | N/A |
| **API Gateway** | 8080 | `http://localhost:8080` | N/A |
| **Knife4j OpenAPI 3.0 Docs** | 8080 | `http://localhost:8080/doc.html` | Public |
| **Nacos Discovery & Config** | 8848 | `http://localhost:8848/nacos` | `nacos / nacos` |
| **RabbitMQ Management** | 15672 | `http://localhost:15672` | `guest / guest` |
| **MailHog Inbox** | 8025 | `http://localhost:8025` | Public |
| **MinIO Storage Console** | 9001 | `http://localhost:9001` | `minioadmin / minioadmin` |
| **Elasticsearch** | 9200 | `http://localhost:9200` | Security disabled |

---

## 🧪 Testing & Quality Assurance

### 1. Backend Unit & Coverage Testing
```bash
# Run complete test suite and generate JaCoCo coverage reports
docker run --rm \
  -v "$(pwd):/workspace" \
  -v "stickybeak-m2:/root/.m2" \
  -w /workspace \
  maven:3.9-eclipse-temurin-17 mvn test
```
Inspect JaCoCo HTML reports at `stickybeak-<module>/target/site/jacoco/index.html`.

### 2. Frontend Unit & Type Safety Testing
```bash
cd stickybeak-frontend
npm run typecheck    # TypeScript verification
npm run test         # Vitest unit test suite
npm run build        # Production bundle compilation
```

### 3. End-to-End (E2E) Regression Testing
```bash
# Automated end-to-end integration and security barrier test suite
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

This project is licensed under the **Apache License 2.0**.
Created with passion by the Stickybeak Engineering Team.
