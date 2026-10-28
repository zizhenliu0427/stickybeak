// 故事线 9.2（规模版）：10 万行合成数据下 MySQL LIKE vs ES 倒排索引对比
// 自建临时库 stickybeak_bench + 临时 ES 索引 products_bench，跑完自动清理。
// 用法: node scripts/benchmark-search-scale.cjs [rows=100000]
const { execSync, execFileSync } = require('child_process');
const fs = require('fs');

const ROWS = Number(process.argv[2] || 100_000);
const TMP = process.env.TEMP;
const KW = '扣死';

function sh(cmd) {
  return execSync(cmd, { maxBuffer: 64 * 1024 * 1024 }).toString('utf8');
}
function mysql(sql) {
  const f = `${TMP}\\sb-bench.sql`;
  fs.writeFileSync(f, sql, 'utf8');
  execSync(`docker cp "${f}" sb-mysql:/tmp/sb-bench.sql`);
  return sh(`docker exec sb-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 < /tmp/sb-bench.sql"`);
}
function es(path, body) {
  const f = `${TMP}\\sb-es-bench.json`;
  fs.writeFileSync(f, body, 'utf8');
  return sh(`curl.exe -s "http://localhost:19200${path}" -H "Content-Type: application/json" --data-binary "@${f}"`);
}

console.log(`=== 规模基准：${ROWS} 行 ===`);

// 1. 建库建表（无二级索引，模拟优化前）
mysql(`DROP DATABASE IF EXISTS stickybeak_bench;
CREATE DATABASE stickybeak_bench DEFAULT CHARACTER SET utf8mb4;
CREATE TABLE stickybeak_bench.t_product_bench (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  status TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;`);
console.log('bench 表已建');

// 2. 生成合成数据（真实词根 + 序号），批量插入
const roots = ['悉尼火车', '大学公交路牌', '超市特价', '海鸥薯条', '电车路牌', '袋鼠路牌', '可颂大葵', '窝窝屎', '莫那什', '酒鬼'];
console.log('生成并插入合成数据...');
const t0 = Date.now();
const batchSize = 5000;
for (let start = 0; start < ROWS; start += batchSize) {
  const values = [];
  for (let i = start; i < Math.min(start + batchSize, ROWS); i++) {
    const r = roots[i % roots.length];
    // 每 37 行掺一个含关键词的商品
    const name = i % 37 === 0 ? `${r} 扣死 冰箱贴 ${i}` : `${r} 冰箱贴 ${i}`;
    const desc = i % 37 === 0 ? `澳洲 扣死 超市系列 ${i}` : `澳洲主题商品描述 ${i}`;
    values.push(`('${name}','${desc}',0)`);
  }
  mysql(`INSERT INTO stickybeak_bench.t_product_bench (name, description, status) VALUES ${values.join(',')};`);
}
console.log(`插入完成，耗时 ${((Date.now() - t0) / 1000).toFixed(1)}s`);

// 3. MySQL：EXPLAIN + 实测（LIKE 前后通配 = 全表扫描）
const query = `SELECT id, name FROM stickybeak_bench.t_product_bench WHERE name LIKE '%${KW}%' OR description LIKE '%${KW}%' LIMIT 12;`;
const explain = mysql(`EXPLAIN ${query}`).trim();
console.log('\n[MySQL EXPLAIN]');
console.log(explain);
let t = Date.now();
const res = sh(`docker exec sb-mysql mysql -uroot -proot --default-character-set=utf8mb4 -e "SELECT COUNT(*) FROM (${query.replace(/;$/, '')}) t" `);
const mysqlMs = Date.now() - t;
console.log(`[MySQL] 命中 ${res.split(/\s+/).pop()} 行，耗时 ${mysqlMs}ms`);

// 4. ES：建索引 + bulk 灌入 + 实测
sh(`curl.exe -s -X DELETE "http://localhost:19200/products_bench" -o NUL`);
es('/products_bench', JSON.stringify({
  mappings: { properties: {
    name: { type: 'text', analyzer: 'ik_max_word', searchAnalyzer: 'ik_smart' },
    description: { type: 'text', analyzer: 'ik_max_word', searchAnalyzer: 'ik_smart' },
    status: { type: 'integer' },
  } },
}));
console.log('\nES bench 索引已建，bulk 灌入...');
const t1 = Date.now();
for (let start = 0; start < ROWS; start += batchSize) {
  const lines = [];
  for (let i = start; i < Math.min(start + batchSize, ROWS); i++) {
    const r = roots[i % roots.length];
    const name = i % 37 === 0 ? `${r} 扣死 冰箱贴 ${i}` : `${r} 冰箱贴 ${i}`;
    const desc = i % 37 === 0 ? `澳洲 扣死 超市系列 ${i}` : `澳洲主题商品描述 ${i}`;
    lines.push(JSON.stringify({ index: { _id: i + 1 } }));
    lines.push(JSON.stringify({ name, description: desc, status: 0 }));
  }
  const f = `${TMP}\\sb-es-bulk.ndjson`;
  fs.writeFileSync(f, lines.join('\n') + '\n', 'utf8');
  sh(`curl.exe -s "http://localhost:19200/products_bench/_bulk" -H "Content-Type: application/x-ndjson" --data-binary "@${f}" -o NUL`);
}
sh(`curl.exe -s -X POST "http://localhost:19200/products_bench/_refresh" -o NUL`);
console.log(`ES 灌入完成，耗时 ${((Date.now() - t1) / 1000).toFixed(1)}s`);

t = Date.now();
const esRes = es('/products_bench/_search', JSON.stringify({
  query: { multi_match: { query: KW, fields: ['name^3', 'description'], analyzer: 'ik_smart' } },
  size: 12,
  track_total_hits: true,
}));
const esMs = Date.now() - t;
const esTotal = JSON.parse(esRes).hits.total.value;
console.log(`[ES] 命中 ${esTotal} 行，耗时 ${esMs}ms`);

// 5. 汇总 + 清理
console.log('\n=== 结论 ===');
console.log(`MySQL LIKE（全表扫描）: ${mysqlMs}ms`);
console.log(`ES 倒排索引:            ${esMs}ms`);
console.log(`倍数: ${(mysqlMs / esMs).toFixed(1)}x`);
mysql('DROP DATABASE IF EXISTS stickybeak_bench;');
sh(`curl.exe -s -X DELETE "http://localhost:19200/products_bench" -o NUL`);
console.log('清理完成（bench 库与索引已删）');
