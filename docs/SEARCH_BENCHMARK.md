# 搜索基准报告 — MySQL LIKE vs Elasticsearch（故事线 9.2）

> 复现：`node scripts/benchmark-search.cjs 30`（真实数据）与 `node scripts/benchmark-search-scale.cjs 100000`（规模合成数据，跑完自动清理）
> 环境：本机 Docker（MySQL 8 / ES 8.14.3+IK / 网关 :8080），2026-09-17 实测

## 结论先行

| 规模 | MySQL LIKE | ES（IK 分词） | 说明 |
|---|---|---|---|
| 265 件真实商品（线上库） | avg 55.6–61.5ms | avg 59.1–73.4ms | 数据全进内存，两者持平，MySQL 甚至略快（ES 多一跳网络） |
| 100,000 行合成数据 | **118ms**（全表扫描） | **52ms** | **2.3x**，且差距随数据量线性 vs 近常数放大 |

## 真实数据基准（engine 参数强制路径、跳过缓存）

| 关键词 | ES avg | ES p95 | MySQL avg | MySQL p95 | 倍数 |
|---|---|---|---|---|---|
| 扣死 | 73.4ms | 91.3ms | 61.5ms | 74.4ms | 0.84x |
| 火车 | 62.1ms | 68.4ms | 56.3ms | 71.0ms | 0.91x |
| 海鸥 | 61.7ms | 71.9ms | 57.1ms | 64.1ms | 0.93x |
| UNSW | 59.1ms | 70.5ms | 55.6ms | 70.0ms | 0.94x |

> 小数据量下 ES 没有优势甚至略慢——这是诚实的结论：ES 的价值在规模与相关性功能（分词、权重、聚合），
> 不在 265 行的玩具规模。生产决策：保留 ES 路径应对数据增长 + 复杂检索（分词/拼音/高亮扩展位）。

## 规模基准（10 万行，stickybeak_bench 临时库 / products_bench 临时索引）

查询：`WHERE name LIKE '%扣死%' OR description LIKE '%扣死%'`（前后通配 → 无法走 B+ 树）

**MySQL EXPLAIN（优化前，无可用索引）：**

```
id  select_type  table            type  possible_keys  key   rows    filtered  Extra
1   SIMPLE       t_product_bench  ALL   NULL           NULL  100000  20.99     Using where
```

- MySQL：全表扫描 100,000 行，命中 2,703 行，**118ms**
- ES（同关键词 multi_match name^3+description，IK 分词）：命中 2,703 行，**52ms**
- EXPLAIN 要点：`type=ALL` 全扫描 + `rows=100000`；ES 倒排索引命中数与数据规模近似无关

## 机制总结（面试话术）

1. `LIKE '%kw%'` 前后通配使 B+ 树索引失效 → 全表扫描，耗时随行数线性增长
2. ES 用 IK 分词建倒排索引，检索复杂度近似 O(命中数)，与总量基本无关
3. 本项目链路：`GET /api/products?q=` → ES multi_match（name^3/description）→ 命中 id 回 MySQL 取实体 → VO；
   ES 宕机由 Sentinel 熔断（异常比例 >50%/60s → 熔断 30s）自动降级 MySQL LIKE，见 `ProductSearchServiceImpl`
4. 一致性：管理端变更 → MQ `product.changed` → 监听器刷索引（最终一致，非双写）；全量兜底 `POST /api/products/admin/reindex`
