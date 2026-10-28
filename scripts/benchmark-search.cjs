// 故事线 9.2：MySQL LIKE vs ES 同一关键词检索基准对比
// 用法: node scripts/benchmark-search.cjs [iterations]
// 说明：engine=es/mysql 显式参数会跳过结果缓存（压测专用），测的是真实检索路径。
const { execSync } = require('child_process');

const ITERATIONS = Number(process.argv[2] || 30);
const KEYWORDS = ['扣死', '火车', '海鸥', 'UNSW'];

function timed(url) {
  const start = process.hrtime.bigint();
  execSync(`curl.exe -s "${url}" -o NUL`);
  return Number(process.hrtime.bigint() - start) / 1e6; // ms
}

function percentile(sorted, p) {
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[idx];
}

const rows = [];
for (const kw of KEYWORDS) {
  const q = encodeURIComponent(kw);
  const esTimes = [];
  const mysqlTimes = [];
  for (let i = 0; i < ITERATIONS; i++) {
    // 交替执行，消除环境漂移
    esTimes.push(timed(`http://localhost:8080/api/products?engine=es&q=${q}`));
    mysqlTimes.push(timed(`http://localhost:8080/api/products?engine=mysql&q=${q}`));
  }
  esTimes.sort((a, b) => a - b);
  mysqlTimes.sort((a, b) => a - b);
  const avg = (a) => (a.reduce((s, v) => s + v, 0) / a.length).toFixed(1);
  rows.push({
    keyword: kw,
    esAvg: avg(esTimes),
    esP95: percentile(esTimes, 95).toFixed(1),
    mysqlAvg: avg(mysqlTimes),
    mysqlP95: percentile(mysqlTimes, 95).toFixed(1),
  });
}

console.log(`\n迭代次数/关键词: ${ITERATIONS}（含网关与 JSON 开销，两种引擎一致）\n`);
console.log('| 关键词 | ES avg(ms) | ES p95(ms) | MySQL avg(ms) | MySQL p95(ms) | 倍数(avg) |');
console.log('|---|---|---|---|---|---|');
for (const r of rows) {
  const ratio = (r.mysqlAvg / r.esAvg).toFixed(2);
  console.log(`| ${r.keyword} | ${r.esAvg} | ${r.esP95} | ${r.mysqlAvg} | ${r.mysqlP95} | ${ratio}x |`);
}
