-- ============================================================================
-- 故事线 9.5 生产迁移：订单分析查询覆盖索引
-- 目标查询：OrderMapper.selectDailySalesTrend / sumTodayGmv（管理后台仪表盘）
-- 50 万行实测：全表扫描 152ms → 覆盖索引范围扫描 72ms（docs/SQL_OPTIMIZATION.md）
-- ============================================================================

SET NAMES utf8mb4;
USE stickybeak_order;

-- 趋势查询的覆盖索引：is_deleted + create_time 范围 + status/total_amount 覆盖
-- （注意：selectDailySalesTrend 汇总的是 total_amount，sumTodayGmv 用 pay_amount，
--   两种常用分析列都在索引里才能 index-only）
ALTER TABLE t_order
    ADD INDEX idx_order_analytics (is_deleted, create_time, status, pay_amount, total_amount);
