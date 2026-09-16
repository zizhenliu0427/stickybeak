# Stickybeak Enterprise Cloud Deployment Guide (AWS / Production)

> **Version**: v1.0.0  
> **Last Updated**: 2026-09-16  
> **Target Audience**: DevOps Engineers, SREs, Solution Architects, Technical Leads

---

## 1. Cloud Architecture Topology

Stickybeak is built for resilient, distributed multi-tenant microservices deployment. Below is the recommended enterprise architecture topology on Amazon Web Services (AWS):

```
                        [ Internet Users / Buyers / Admins ]
                                         │
                                         ▼
                            [ AWS Route 53 (DNS) ]
                                         │
                                         ▼
                       [ AWS CloudFront CDN / ACM SSL ]
                                         │
                                         ▼
                   [ AWS Application Load Balancer (ALB) ]
                                         │
             ┌───────────────────────────┴───────────────────────────┐
             │ Public Subnet (AZ-A)                                  │ Public Subnet (AZ-B)
             │   ┌─────────────────────┐                             │   ┌─────────────────────┐
             │   │  Nginx Web Gateway  │                             │   │  Nginx Web Gateway  │
             │   │  (SSL, Gzip, SPA)   │                             │   │  (SSL, Gzip, SPA)   │
             │   └──────────┬──────────┘                             │   └──────────┬──────────┘
             └──────────────┼────────────────────────────────────────┼──────────────┼─────────┘
                            │                                                       │
             ┌──────────────┴───────────────────────────────────────────────────────┴─────────┐
             │ Private Subnet (VPC Internal Network - No Public IP)                           │
             │                                                                                │
             │   ┌────────────────────────────────────────────────────────────────────────┐   │
             │   │                  Spring Cloud Gateway Cluster (:8080)                  │   │
             │   └───────────────────────────────────┬────────────────────────────────────┘   │
             │                                       │ (RPC / Nacos Service Discovery)        │
             │      ┌──────────────┬─────────────────┼────────────────┬──────────────┐        │
             │      ▼              ▼                 ▼                ▼              ▼        │
             │ ┌─────────┐   ┌───────────┐     ┌───────────┐    ┌───────────┐  ┌────────────┐ │
             │ │Auth Svc │   │Product Svc│     │ Cart Svc  │    │ Order Svc │  │Payment Svc │ │
             │ └────┬────┘   └─────┬─────┘     └─────┬─────┘    └─────┬─────┘  └─────┬──────┘ │
             │      │              │                 │                │              │        │
             │      └──────────────┼─────────────────┼────────────────┼──────────────┘        │
             │                     ▼                 ▼                ▼                       │
             │          [ AWS RDS MySQL 8.0 ]   [ ElastiCache ]   [ Amazon MQ ]               │
             │          (Multi-AZ Cluster)      (Redis 7.x)       (RabbitMQ)                  │
             │                     │                                  │                       │
             │                     ▼                                  ▼                       │
             │             [ Seata Server ]               [ Notification Service ]            │
             │            (Distributed Tx)                        (:8086)                     │
             │                                                        │                       │
             │                                                        ▼                       │
             │                                              [ Amazon SES / Mailhog ]          │
             └────────────────────────────────────────────────────────────────────────────────┘
                                   │                                 │
                                   ▼                                 ▼
                     [ AWS S3 Bucket (Images/Media) ]    [ Stripe API / Webhooks ]
```

---

## 2. Infrastructure Requirements & Sizing

### 2.1 Recommended Production Sizing (Base Baseline: ~1,000 Concurrent Users)

| Component | AWS Resource | Sizing / Specification | Redundancy |
| :--- | :--- | :--- | :--- |
| **Edge & CDN** | CloudFront + Route 53 | Global Edge Locations + SSL ACM | Multi-Edge |
| **Load Balancer**| AWS ALB | Internet-Facing Application Load Balancer | Cross-AZ |
| **Web & Services**| EC2 / ECS Fargate | 3 x `c6i.xlarge` (4 vCPU, 8 GB RAM) | Multi-AZ |
| **Relational DB** | AWS RDS for MySQL | `db.r6g.xlarge` (4 vCPU, 32 GB RAM, Multi-AZ) | Primary + Standby |
| **Cache Cluster** | AWS ElastiCache Redis | `cache.r6g.large` (2 vCPU, 13 GB RAM, Cluster Mode) | 2 Nodes (Primary/Replica) |
| **Message Broker**| Amazon MQ (RabbitMQ)| `mq.m5.large` (2 vCPU, 8 GB RAM) | Active/Standby Pair |
| **Media Storage** | AWS S3 Bucket | Standard Storage with Lifecycle Policies | Cross-Region Optional |

---

## 3. Database & Middleware Initialization

### 3.1 RDS MySQL Initialization
Connect to your RDS MySQL primary instance via VPC bastion or VPN:
```bash
mysql -h <rds-endpoint>.rds.amazonaws.com -u admin -p
```
Execute the initialization scripts in sequence:
1. `docker/mysql/init/01-init.sql`: Creates database instances `stickybeak_auth`, `stickybeak_product`, `stickybeak_cart`, `stickybeak_order`, `stickybeak_payment`, and `stickybeak_seata`.
2. Seed initial admin user, default categories, and products.
3. Verify timezone is configured to `Australia/Sydney` (or `UTC`).

### 3.2 ElastiCache Redis Configuration
- Enable Redis AUTH token;
- Set `maxmemory-policy: allkeys-lru`;
- Ensure security group allows ingress on port `6379` strictly from the ECS/EC2 microservice security group.

### 3.3 RabbitMQ Queues and DLX
Verify the following exchanges and queues are provisioned:
- `order.topic` (Topic Exchange)
- `order.paid.queue` (bound to `order.topic` with routing key `order.paid`)
- `order.ttl.queue` & `order.dlx.exchange` (30-minute auto-cancel delayed queue)

---

## 4. Production Deployment via Docker Compose

### 4.1 Step 1: Environment File Configuration
Copy the template and populate production secrets:
```bash
cd /opt/stickybeak
cp docker/.env.prod.example docker/.env.prod
chmod 600 docker/.env.prod
nano docker/.env.prod
```

Critical parameters to review:
- `NACOS_ADDR`: Internal address of your Nacos server/cluster.
- `DB_HOST`, `DB_USER`, `DB_PASSWORD`: RDS MySQL connection credentials.
- `REDIS_HOST`, `REDIS_PASSWORD`: ElastiCache credentials.
- `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`: Amazon MQ credentials.
- `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`: Live keys from the Stripe Dashboard.
- `JWT_SECRET`: High-entropy 256-bit secret key.

### 4.2 Step 2: SSL Certificate Installation
Mount official TLS certificates into `/etc/ssl/stickybeak/`:
- `server.crt`: Full certificate chain (cert + intermediate CA).
- `server.key`: RSA/ECDSA private key (permissions `chmod 600`).

Alternatively, use Certbot with the pre-configured ACME webroot challenge:
```bash
certbot certonly --webroot -w /var/www/certbot -d stickybeak.example.com
```

### 4.3 Step 3: Launch Services
```bash
cd /opt/stickybeak
docker compose -f docker/docker-compose.prod.yml --env-file docker/.env.prod up -d --build
```

### 4.4 Step 4: Verify Health Status
```bash
# Check running containers
docker compose -f docker/docker-compose.prod.yml ps

# Check Gateway actuator health
curl -f https://stickybeak.example.com/api/actuator/health

# Inspect real-time Nginx access logs
docker logs -f sb-prod-frontend --tail 100
```

---

## 5. Cloud Native AWS ECS Fargate Deployment

For enterprise high-availability without managing host servers, Stickybeak images can be deployed to AWS ECS Fargate:

```
                  ┌─────────────────────────────────────────┐
                  │          AWS ECR (Image Registry)       │
                  │   stickybeak/gateway:v1.0.0             │
                  │   stickybeak/order:v1.0.0               │
                  │   stickybeak/frontend:v1.0.0            │
                  └────────────────────┬────────────────────┘
                                       │ Pull Task Images
                                       ▼
                  ┌─────────────────────────────────────────┐
                  │           AWS ECS Cluster               │
                  │  ┌───────────────────────────────────┐  │
                  │  │ Fargate Service: stickybeak-web   │  │
                  │  │ (Auto-Scaling 2 -> 10 Tasks)      │  │
                  │  └───────────────────────────────────┘  │
                  │  ┌───────────────────────────────────┐  │
                  │  │ Fargate Service: stickybeak-order │  │
                  │  │ (Auto-Scaling 2 -> 8 Tasks)       │  │
                  │  └───────────────────────────────────┘  │
                  └─────────────────────────────────────────┘
```

### 5.1 Step 1: Provision Infrastructure via AWS CloudFormation
Deploy the standard VPC, subnets, NAT Gateway, ALB, and ECS Fargate cluster in one step:
```bash
aws cloudformation create-stack \
  --stack-name stickybeak-production-stack \
  --template-body file://docker/aws/cloudformation-vpc-ecs.yml \
  --capabilities CAPABILITY_IAM \
  --region ap-southeast-2
```

### 5.2 Step 2: Build & Push Images to Amazon ECR
Use the provided automated automation script to build and push all 8 microservices and frontend images:
```bash
chmod +x scripts/aws-ecr-push.sh
./scripts/aws-ecr-push.sh <AWS_ACCOUNT_ID> ap-southeast-2 v1.0.0
```

### 5.3 Step 3: Register ECS Task Definitions & Update Services
Register the Fargate task definition templates:
```bash
aws ecs register-task-definition --cli-input-json file://docker/aws/ecs-task-definition-gateway.json
aws ecs register-task-definition --cli-input-json file://docker/aws/ecs-task-definition-order.json

# Deploy or rolling update ECS Fargate service
aws ecs update-service \
  --cluster stickybeak-prod-cluster \
  --service stickybeak-gateway-service \
  --task-definition stickybeak-prod-gateway \
  --force-new-deployment
```

### 5.4 Step 4: Pre-flight Production Readiness Verification
Run the automated pre-flight audit script to ensure all configurations, credentials, and templates are compliant:
```bash
node scripts/verify-aws-readiness.js
```

---

## 6. Security Hardening Checklist

- [x] **Network Isolation**: All databases and backend services placed in private VPC subnets with NAT Gateway egress only.
- [x] **SSL / TLS Termination**: Strict TLS 1.2 and 1.3 only; disabled obsolete SSLv3 and TLS 1.0/1.1 protocols.
- [x] **Security Headers**: HSTS, `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, and robust CSP configured in Nginx.
- [x] **JWT Token Blacklisting**: Redis-backed token revocation on logout to eliminate replay windows.
- [x] **Stripe Webhook Verification**: Cryptographic HMAC SHA-256 signature validation with replay-attack timestamp tolerance (< 300s).
- [x] **DDoS & Rate Limiting**: Gateway Sentinel / Redis Token Bucket rate limiter on public auth endpoints (`/api/v1/auth/login`, `/api/v1/auth/register`).

---

## 7. Disaster Recovery & Backup Plan

1. **Database Recovery (PITR)**:
   - AWS RDS automated backups enabled with 30-day retention;
   - Point-In-Time Recovery (PITR) capable down to 5-minute intervals.
2. **Redis Disaster Strategy**:
   - Redis AOF (Append-Only File) persistence enabled every second;
   - Daily automated RDB snapshots sent to cross-region S3.
3. **Runbook for Webhook Failures**:
   - Webhook duplicate events automatically resolved via `UK_stripe_event_id` constraint (idempotent 200 return);
   - Stripe Dashboard offers manual replay of past event payloads if network interruption occurs.
