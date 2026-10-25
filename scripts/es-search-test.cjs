// ES 搜索链路测试：直连 ES vs 走网关 API（UTF-8 写临时文件，绕开 PowerShell 编码坑）
// 用法: node scripts/es-search-test.cjs [关键词]   默认「鹦鹉」
// 索引与字段对应 Sprint 11.2 的实现（product/search/ElasticsearchProductSearchService）
const fs = require('fs');
const { execSync } = require('child_process');

const tmp = process.env.TEMP;
const kw = process.argv[2] || '鹦鹉';
const INDEX = 'stickybeak-products';

// 1. 直连 ES：ik_smart 分析关键词看分词
fs.writeFileSync(`${tmp}\\sb-q1.json`, JSON.stringify({ analyzer: 'ik_smart', text: kw }), 'utf8');
console.log(`=== 直连 ES: ik_smart 分词「${kw}」 ===`);
const tokens = JSON.parse(
  execSync(`curl.exe -s "http://localhost:19200/${INDEX}/_analyze" -H "Content-Type: application/json" --data-binary "@${tmp}\\sb-q1.json"`).toString('utf8'),
);
console.log((tokens.tokens || []).map((t) => t.token).join(' / '));

// 2. 直连 ES：与 Java 侧同构的 bool + multi_match（只查在售）
fs.writeFileSync(`${tmp}\\sb-q2.json`, JSON.stringify({
  query: {
    bool: {
      must: [{ multi_match: { query: kw, fields: ['name^3', 'categoryName^2', 'tags.text^2', 'description'] } }],
      filter: [{ term: { status: 0 } }],
    },
  },
  _source: ['name'],
  size: 5,
}), 'utf8');
console.log('\n=== 直连 ES: bool multi_match（Java 同构）===');
const direct = JSON.parse(
  execSync(`curl.exe -s "http://localhost:19200/${INDEX}/_search" -H "Content-Type: application/json" --data-binary "@${tmp}\\sb-q2.json"`).toString('utf8'),
);
console.log('hits:', direct.hits.total.value);
direct.hits.hits.forEach((h) => console.log(' -', h._source.name));

// 3. 走网关 API（engine=es 强制 ES、跳过缓存）
console.log(`\n=== 网关 API: /api/products?q=${kw}&engine=es ===`);
const url = 'http://localhost:8080/api/products?engine=es&q=' + encodeURIComponent(kw);
const j = JSON.parse(execSync(`curl.exe -s "${url}"`).toString('utf8'));
console.log('total:', j.data.total);
j.data.items.slice(0, 5).forEach((i) => console.log(' -', i.highlightedName || i.name));
