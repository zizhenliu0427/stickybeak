// ES 索引新鲜度（Sprint 11.2 验收）：后台改价 / 上下架后，多久能在搜索结果里看到变化。
// 同步链路：AdminProductService 改库 → 事务提交后 ProductIndexer 重建该商品文档 → ES refresh（默认 1s）后可搜到。
// 只操作 E2E 脚本建出来的测试商品，结束时保持下架，不影响店里的在售 SKU。
// 用法: node scripts/test-search-freshness.cjs
const http = require('http');
const { execSync } = require('child_process');

const GATEWAY = 'http://localhost:8080';
const ES = 'http://localhost:19200/stickybeak-products';
let cookie = '';

function call(method, url, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : '';
    const req = http.request(
      new URL(url),
      {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
          ...(cookie ? { Cookie: cookie } : {}),
        },
      },
      (res) => {
        if (res.headers['set-cookie']) cookie = res.headers['set-cookie'].map((c) => c.split(';')[0]).join('; ');
        let raw = '';
        res.on('data', (c) => (raw += c));
        res.on('end', () => resolve(JSON.parse(raw || 'null')));
      },
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

const mysql = (sql) =>
  execSync(`docker exec sb-mysql mysql -uroot -proot -N -e "${sql}"`, {
    encoding: 'utf-8',
    stdio: ['ignore', 'pipe', 'ignore'],
  }).trim();

/** 轮询直到 predicate 成立，返回耗时（ms）；超时返回 null */
async function waitFor(predicate, timeoutMs = 10000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await predicate()) return Date.now() - started;
    await new Promise((r) => setTimeout(r, 50));
  }
  return null;
}

/** 走搜索（受 refresh 影响），不是实时的 _doc GET */
async function searchedPrice(id) {
  const res = await call('POST', `${ES}/_search`, { query: { ids: { values: [String(id)] } }, _source: ['price'] });
  return res.hits.hits[0]?._source.price;
}

async function searchHits(id) {
  const res = await call('GET', `${GATEWAY}/api/products?engine=es&q=E2E&size=50`);
  return res.data.items.some((p) => String(p.id) === String(id));
}

async function main() {
  const id = mysql("SELECT id FROM stickybeak_product.t_product WHERE name LIKE 'E2E%' ORDER BY id DESC LIMIT 1");
  if (!id) {
    console.log('没有 E2E 测试商品，先运行 test-product-create-e2e.cjs');
    return;
  }
  await call('POST', `${GATEWAY}/api/auth/login`, { email: 'admin@stickybeak.au', password: 'Admin123!' });
  const timings = {};

  // 1. 改价：ES 里的价格（价格区间筛选、按价格排序用的是它）
  const oldPrice = Number(mysql(`SELECT price FROM stickybeak_product.t_product WHERE id = ${id}`));
  const newPrice = Math.round((oldPrice + 1) * 100) / 100;
  await call('PUT', `${GATEWAY}/api/products/admin/${id}/price`, { price: newPrice });
  timings.price = await waitFor(async () => (await searchedPrice(id)) === newPrice);

  // 2. 上架：经网关搜索多久能搜到
  await call('PUT', `${GATEWAY}/api/products/admin/${id}/status`, { status: 0 });
  timings.list = await waitFor(() => searchHits(id));

  // 3. 下架：多久搜不到（结束状态保持下架）
  await call('PUT', `${GATEWAY}/api/products/admin/${id}/status`, { status: 1 });
  timings.unlist = await waitFor(async () => !(await searchHits(id)));

  await call('PUT', `${GATEWAY}/api/products/admin/${id}/price`, { price: oldPrice });

  const fmt = (ms) => (ms === null ? '10 秒内未生效' : `${ms} ms`);
  console.log(`商品 ${id}`);
  console.log(`  改价 ${oldPrice} → ${newPrice} 后搜索可见：${fmt(timings.price)}`);
  console.log(`  上架后可搜到：${fmt(timings.list)}`);
  console.log(`  下架后搜不到：${fmt(timings.unlist)}`);
  const ok = Object.values(timings).every((ms) => ms !== null);
  console.log(ok ? '\n✅ 索引新鲜度验收通过' : '\n⚠ 未通过');
  process.exit(ok ? 0 : 1);
}

main().catch((e) => {
  console.error('❌', e.message);
  process.exit(1);
});
