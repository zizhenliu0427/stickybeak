// 热点商品并发结账压测（Sprint 11.1 验收）：N 个买家同时对同一个商品下单，记录吞吐与延迟。
// 用来对比 Seata AT 开 / 关时全局锁（t_product 同一行）对吞吐的影响：
//   1) 默认环境跑一次（SEATA_ENABLED=true）
//   2) product、order 以 SEATA_ENABLED=false 重启后再跑一次
// 下完的订单会立即取消，库存归还。
// 用法: node scripts/bench-hot-checkout.cjs [并发数=30] [轮数=3] [标签]
const http = require('http');

const GATEWAY = 'http://localhost:8080';
const PRODUCT_ID = Number(process.env.PRODUCT_ID || 1000001);
const N = Number(process.argv[2] || 30);
const ROUNDS = Number(process.argv[3] || 3);
const LABEL = process.argv[4] || '';

function request(method, path, body, cookie) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : '';
    const req = http.request(
      new URL(path, GATEWAY),
      {
        method,
        agent: false,
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
          ...(cookie ? { Cookie: cookie } : {}),
        },
      },
      (res) => {
        const setCookie = res.headers['set-cookie'];
        let raw = '';
        res.on('data', (c) => (raw += c));
        res.on('end', () => {
          let json = null;
          try {
            json = JSON.parse(raw);
          } catch {
            // ignore
          }
          resolve({ status: res.statusCode, json, cookie: setCookie ? setCookie.map((c) => c.split(';')[0]).join('; ') : null });
        });
      },
    );
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

/** 准备阶段会撞上网关对 auth 路由的 Sentinel 限流（60 QPS），失败时退避重试 */
async function withRetry(send) {
  for (let attempt = 0; ; attempt += 1) {
    const res = await send();
    if (res.json?.code === 0) return res;
    if (attempt >= 20) throw new Error(`setup request failed: HTTP ${res.status} ${JSON.stringify(res.json)}`);
    await new Promise((r) => setTimeout(r, 250));
  }
}

async function buyer(i) {
  const reg = await withRetry(() => request('POST', '/api/auth/register', {
    email: `hot_${Date.now()}_${i}@example.com`,
    password: 'Password123!',
    nickname: `Hot${i}`,
  }));
  const cookie = reg.cookie;
  const addr = await withRetry(() => request('POST', '/api/users/me/addresses', {
    receiver: `Hot ${i}`, phone: '0412345678', state: 'NSW', city: 'Sydney', postcode: '2000', detail: '1 George St', isDefault: true,
  }, cookie));
  return { cookie, addressId: addr.json.data.id };
}

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];

async function round(buyers) {
  await Promise.all(buyers.map((b) => request('POST', '/api/cart/items', { productId: PRODUCT_ID, qty: 1 }, b.cookie)));
  const started = process.hrtime.bigint();
  const results = await Promise.all(
    buyers.map(async (b) => {
      const t0 = process.hrtime.bigint();
      const res = await request('POST', '/api/orders/checkout', { addressId: b.addressId, currency: 'AUD', paymentMethod: 'card' }, b.cookie);
      return { ms: Number(process.hrtime.bigint() - t0) / 1e6, ok: res.json?.code === 0, orderNo: res.json?.data?.orderNo, b, error: res.json?.code === 0 ? null : `${res.status}/${res.json?.code} ${res.json?.message ?? ''}`.trim() };
    }),
  );
  const wallMs = Number(process.hrtime.bigint() - started) / 1e6;
  // 归还库存
  await Promise.all(results.filter((r) => r.ok).map((r) => request('POST', `/api/orders/${r.orderNo}/cancel`, null, r.b.cookie)));
  const lat = results.map((r) => r.ms).sort((a, b) => a - b);
  const errors = {};
  results.filter((r) => !r.ok).forEach((r) => (errors[r.error] = (errors[r.error] || 0) + 1));
  return { errors, ok: results.filter((r) => r.ok).length, wallMs, p50: pct(lat, 50), p95: pct(lat, 95), max: lat[lat.length - 1] };
}

async function main() {
  console.log(`准备 ${N} 个买家 ...`);
  const buyers = [];
  for (let i = 0; i < N; i += 1) buyers.push(await buyer(i));
  // 预热一轮（JIT、连接池、Redis 预扣缓存），不计入结果
  await round(buyers.slice(0, Math.min(5, N)));

  const rows = [];
  for (let r = 1; r <= ROUNDS; r += 1) {
    const res = await round(buyers);
    rows.push(res);
    console.log(`第 ${r} 轮：成功 ${res.ok}/${N}，总耗时 ${res.wallMs.toFixed(0)} ms，吞吐 ${((res.ok * 1000) / res.wallMs).toFixed(1)} 单/秒，p50 ${res.p50.toFixed(0)} ms，p95 ${res.p95.toFixed(0)} ms，max ${res.max.toFixed(0)} ms${Object.keys(res.errors).length ? '，失败: ' + JSON.stringify(res.errors) : ''}`);
  }
  const avg = (k) => rows.reduce((s, x) => s + x[k], 0) / rows.length;
  const tput = rows.reduce((s, x) => s + (x.ok * 1000) / x.wallMs, 0) / rows.length;
  console.log(`\n| ${LABEL || '环境'} | 并发 ${N} | 吞吐 ${tput.toFixed(1)} 单/秒 | p50 ${avg('p50').toFixed(0)} ms | p95 ${avg('p95').toFixed(0)} ms | 成功率 ${((avg('ok') / N) * 100).toFixed(0)}% |`);
}

main().catch((e) => {
  console.error('❌', e.message);
  process.exit(1);
});
