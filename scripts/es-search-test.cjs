// ES 搜索链路测试：直连 ES vs 走网关 API（UTF-8 写临时文件，绕开 PowerShell 编码坑）
const fs = require('fs');
const { execSync } = require('child_process');

const tmp = process.env.TEMP;

// 1. 直连 ES：ik_smart 分析「扣死」看分词
fs.writeFileSync(`${tmp}\\sb-q1.json`, JSON.stringify({ query: { match: { name: { query: '扣死', analyzer: 'ik_smart' } } }, _source: ['name'] }), 'utf8');
console.log('=== 直连 ES: match name=扣死 (ik_smart) ===');
console.log(execSync(`curl.exe -s "http://localhost:19200/products/_search" -H "Content-Type: application/json" --data-binary "@${tmp}\\sb-q1.json"`).toString('utf8').slice(0, 600));

// 2. 直连 ES：multi_match name^3 + description（与 Java 侧同构）
fs.writeFileSync(`${tmp}\\sb-q2.json`, JSON.stringify({
  query: { bool: { must: [{ multi_match: { query: '扣死', fields: ['name^3', 'description'], analyzer: 'ik_smart' } }], filter: [{ term: { status: 0 } }] } },
  _source: ['name']
}), 'utf8');
console.log('\n=== 直连 ES: bool multi_match（Java 同构）===');
console.log(execSync(`curl.exe -s "http://localhost:19200/products/_search" -H "Content-Type: application/json" --data-binary "@${tmp}\\sb-q2.json"`).toString('utf8').slice(0, 600));

// 3. 走网关 API（engine=es 强制 ES）
console.log('\n=== 网关 API: /api/products?q=扣死&engine=es ===');
const url = 'http://localhost:8080/api/products?engine=es&q=' + encodeURIComponent('扣死');
const out = execSync(`curl.exe -s "${url}"`).toString('utf8');
const j = JSON.parse(out);
console.log('total:', j.data.total);
j.data.items.slice(0, 5).forEach((i) => console.log(' -', i.name));
