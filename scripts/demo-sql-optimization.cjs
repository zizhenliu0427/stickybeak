// 故事线 9.5 主案例：慢 SQL 优化 —— 管理后台「每日销售趋势」查询
// 真实查询：OrderMapper.selectDailySalesTrend（WHERE create_time >= N 天前 GROUP BY 天）
// 优化前 t_order 无 (is_deleted, create_time) 索引 → 全表扫描；加索引后 → 索引范围扫描。
// 在临时 bench 表上实测（50 万订单 / 180 天跨度），跑完自动清理。
// 用法: node scripts/demo-sql-optimization.cjs [rows=500000] [windowDays=30]
const { execSync } = require('child_process');
const fs = require('fs');

const ROWS = Number(process.argv[2] || 500_000);
const WINDOW = Number(process.argv[3] || 30);
const TMP = process.env.TEMP;

function mysql(sql) {
  const f = `${TMP}\\sb-sqlopt.sql`;
  fs.writeFileSync(f, 'SET NAMES utf8mb4;\n' + sql, 'utf8');
  execSync(`docker cp "${f}" sb-mysql:/tmp/sb-sqlopt.sql`);
  return execSync(`docker exec sb-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 < /tmp/sb-sqlopt.sql"`, {
    maxBuffer: 32 * 1024 * 1024,
  }).toString('utf8');
}

// 与 OrderMapper.selectDailySalesTrend 同构（bench 表版）
const TREND_QUERY = `SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS order_date,
       COUNT(*) AS order_count,
       COALESCE(SUM(CASE WHEN status IN ('paid','processing','shipped','completed') THEN total_amount ELSE 0 END), 0) AS daily_gmv
FROM stickybeak_bench.t_order_bench
WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL ${WINDOW} DAY) AND is_deleted = 0
GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
ORDER BY order_date ASC;`;

console.log(`=== 慢 SQL 案例：每日销售趋势（${ROWS} 订单 / 180 天跨度 / 窗口 ${WINDOW} 天）===`);

// 建表：复刻 t_order 现有索引（不含 create_time 相关）
mysql(`
DROP DATABASE IF EXISTS stickybeak_bench;
CREATE DATABASE stickybeak_bench DEFAULT CHARACTER SET utf8mb4;
CREATE TABLE stickybeak_bench.t_order_bench (
  id BIGINT PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  UNIQUE KEY uk_bench_order_no (order_no),
  INDEX idx_bench_order_user (user_id),
  INDEX idx_bench_order_status (status)
) ENGINE=InnoDB;
`);

console.log('灌入合成订单（均匀分布 180 天）...');
const t0 = Date.now();
const statuses = ['paid', 'processing', 'shipped', 'completed', 'pending', 'cancelled'];
const B = 5000;
for (let s = 0; s < ROWS; s += B) {
  const vals = [];
  for (let i = s; i < Math.min(s + B, ROWS); i++) {
    const daysAgo = i % 180;
    const hh = String(i % 24).padStart(2, '0');
    const mm = String(i % 60).padStart(2, '0');
    vals.push(`(${i + 1},'NO${i + 1}',${1000 + (i % 500)},'${statuses[i % statuses.length]}',${(i % 200) + 10}.00,0,DATE_SUB(CURDATE(), INTERVAL ${daysAgo} DAY) + INTERVAL ${i % 86400} SECOND)`);
  }
  mysql(`INSERT INTO stickybeak_bench.t_order_bench (id, order_no, user_id, status, total_amount, is_deleted, create_time) VALUES ${vals.join(',')};`);
}
mysql('ANALYZE TABLE stickybeak_bench.t_order_bench;');
console.log(`灌入完成 ${((Date.now() - t0) / 1000).toFixed(1)}s`);

console.log('\n========== 优化前（无 create_time 索引）==========');
console.log(mysql(`EXPLAIN ANALYZE ${TREND_QUERY}`));
let t = Date.now();
mysql(TREND_QUERY);
const beforeMs = Date.now() - t;
console.log(`[实测] 优化前: ${beforeMs}ms`);

console.log('========== 加覆盖索引 idx_bench_cover (is_deleted, create_time, status, total_amount) ==========');
mysql('ALTER TABLE stickybeak_bench.t_order_bench ADD INDEX idx_bench_cover (is_deleted, create_time, status, total_amount);');

console.log('\n========== 优化后 ==========');
console.log(mysql(`EXPLAIN ANALYZE ${TREND_QUERY}`));
t = Date.now();
mysql(TREND_QUERY);
const afterMs = Date.now() - t;
console.log(`[实测] 优化后: ${afterMs}ms`);

console.log('\n=== 结论 ===');
console.log(`优化前 ${beforeMs}ms → 优化后 ${afterMs}ms（${(beforeMs / Math.max(afterMs, 1)).toFixed(1)}x）`);

mysql('DROP DATABASE IF EXISTS stickybeak_bench;');
console.log('清理完成');
