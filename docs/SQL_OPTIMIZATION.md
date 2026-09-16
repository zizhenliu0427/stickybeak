# 慢 SQL 优化案例（故事线 9.5）

> 复现：`node scripts/demo-sql-optimization.cjs [rows] [windowDays]`（临时 bench 库，跑完自动清理）
> 实测环境：本机 Docker MySQL 8.0，2026-09-17

## 主案例：管理后台「每日销售趋势」（真实查询的真实优化）

**目标查询**（`OrderMapper.selectDailySalesTrend`，仪表盘 7/30/90 天趋势图）：

```sql
SELECT DATE_FORMAT(create_time,'%Y-%m-%d') AS order_date, COUNT(*) AS order_count,
       SUM(CASE WHEN status IN ('paid','processing','shipped','completed') THEN total_amount ELSE 0 END) AS daily_gmv
FROM t_order
WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) AND is_deleted = 0
GROUP BY DATE_FORMAT(create_time,'%Y-%m-%d');
```

### 优化前（t_order 只有 uk_order_no / idx_order_user / idx_order_status）

50 万订单、180 天跨度、30 天窗口，EXPLAIN ANALYZE：

```
-> Aggregate using temporary table (actual time=142ms, rows=31)
   -> Filter: (is_deleted=0 and create_time >= ...) (rows=86118)
      -> Table scan on t_order_bench (rows=500000)   ← 全表扫描 50 万行
```

实测 **152ms**。

### 第一次尝试（失败）：普通二级索引 (is_deleted, create_time)

优化器估算窗口内行数 16.7 万（占 33%），**直接放弃索引继续全表扫描**——
二级索引范围扫描 + 回表 8.6 万次的随机读成本，在优化器看来不如顺序全扫。
教训：**索引不是加上就有用，回表成本会吃掉选择性一般的索引。**

### 第二次尝试（成功）：覆盖索引 (is_deleted, create_time, status, pay_amount, total_amount)

把查询涉及的**全部列**收进索引，消除回表：

```
-> Aggregate using temporary table (actual time=71.9ms, rows=31)
   -> Filter: (...)
      -> Covering index range scan on t_order_bench using idx_bench_cover
         over (is_deleted = 0 AND '2026-08-18' <= create_time)  (rows=86118)   ← 只扫索引
```

实测 **72ms**（2.1x），且差距随总量放大、随窗口收窄继续拉大（7 天窗口更优）。

**生产迁移**：`docker/mysql/init/07-order-analytics-index.sql`（已应用到本地库）。

## 番外（反直觉）：Top-N 热销聚合，索引反而变慢

`OrderItemMapper.selectTopSellingProducts`（t_order_item JOIN t_order GROUP BY product_id），50 万条目实测：

| 方案 | 耗时 | 计划 |
|---|---|---|
| 优化前 | 607ms | 订单侧状态过滤驱动 → 条目侧 order_id 索引查找 |
| 加双侧覆盖索引 | **732ms（更慢！）** | 优化器翻转为条目侧 50 万全扫 + 逐行 PK 回查订单 |
| 覆盖索引 + STRAIGHT_JOIN 固定驱动顺序 | 660ms | 仍无本质改善 |

**结论**：这条查询的成本在「33 万行进临时表聚合」本身，索引救不了。
数据量真到这一步，正解是**预聚合汇总表**（如 `t_product_sales_daily(product_id, sale_date, qty, gmv)` 定时物化，
Top-N 退化为几百行的 GROUP BY，<10ms）或走 ES/terms 聚合。当前真实单量下（百级订单）无需引入。

## 方法论清单（面试可讲）

1. 先 EXPLAIN ANALYZE 看真实执行器耗时，不靠猜
2. 加索引后必须复查执行计划——优化器可能弃用或翻转 join 顺序
3. 覆盖索引（查询列全收进索引）是消除回表的关键手段
4. 聚合类查询的终极优化是预聚合/物化，不是 B+ 树
