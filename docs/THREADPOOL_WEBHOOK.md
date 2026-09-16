# Webhook 热路径线程池调优（故事线 9.6）

> 演示：`node scripts/demo-webhook-threadpool.cjs 60`（60 并发 mock webhook 压测 + 实时水位）
> 监控端点：`GET /api/webhooks/pool-stats`
> 实测日期：2026-09-17

## 设计

Stripe 官方最佳实践：webhook 接收端应尽快返回 2xx，重活异步做。
原实现把整个处理（解析 → 更新流水 → 发 MQ）压在 Tomcat 请求线程上，大促并发时支付回调会拖垮 Web 线程池。

**改造后（`WebhookServiceImpl` → `WebhookEventProcessor`）**：

```
HTTP 线程（Tomcat）                webhookTaskExecutor（core 4 / max 8 / queue 200）
─────────────────────             ────────────────────────────────────────────
验签（CPU 微秒级）
幂等落档 t_payment_webhook（快） ─→ executor.execute(processor.process(...))
立即 200 ACCEPTED                     解析 payload → 更新 t_payment_record → MQ order.paid
                                      失败 → processed=2 留档（人工/定时重试）
```

- **拒绝策略 CallerRunsPolicy**：队列满 → 回退调用方线程执行 = 天然背压，事件不丢（对比 AbortPolicy 直接抛错丢事件）
- **崩溃恢复**：`processed=0` 的记录允许重复投递重投（`enqueue` 对已存在未处理记录放行）
- **幂等不变式不变**：event_id UK + processed=1 快速去重，与同步版语义一致

## 实测（60 并发突发）

| 指标 | 值 |
|---|---|
| HTTP 全部受理 | 60/60 ACCEPTED |
| 线程池峰值 | active=4（core），largestPoolSize=4（未触及 max=8） |
| 队列峰值 | 33 / 200（突发被吸收，Tomcat 线程不被拖住） |
| 任务完成 | completedTaskCount=60，0 拒绝 0 丢失 |

## 监控口径

`pool-stats` 暴露：activeCount / poolSize / largestPoolSize / queueSize / queueRemainingCapacity / taskCount / completedTaskCount / rejectionPolicy。
生产建议：Micrometer 注册 `task.pool.*` 指标进 Prometheus，告警线 `queueSize/capacity > 80%` 持续 1 分钟。

## 测试

- `WebhookServiceImplTest`（7 测）：受理语义、幂等 no-op、UK 并发兜底、崩溃恢复重投、畸形 JSON 400
- `WebhookEventProcessorTest`（5 测）：mock/stripe 解析、流水更新、MQ 事件字段、失败置 2、并发 no-op
