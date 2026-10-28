// Seata AT 回滚验收（Sprint 11.1）：让「写订单」这一步失败，确认商品库的 MySQL 库存与 Redis 预扣都被归还。
//
// 故障注入方式：给 stickybeak_order.t_order 临时加一个 CHECK 约束，备注等于 MARK 的订单插入会失败。
// 下单顺序是「扣库存（product 库，远程 AT 分支）→ 写订单（order 库）」，所以扣库存已经执行、写订单失败，
// 全局事务回滚必须把商品库存经 undo_log 还原，Redis 预扣由 TransactionHook#afterRollback 归还。
// 脚本结束时（包括失败时）一定删除约束。
// 用法: node scripts/test-seata-rollback.cjs   （需要 docker-compose.yml 或 rebuild-services 起的整套服务）
const http = require('http');
const { execSync } = require('child_process');

const GATEWAY = 'http://localhost:8080';
const PRODUCT_ID = 1000001;
const QTY = 2;
const MARK = 'SEATA_ROLLBACK_TEST';
let cookie = '';

function request(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : '';
    const req = http.request(
      new URL(path, GATEWAY),
      {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
          ...(cookie ? { Cookie: cookie } : {}),
        },
      },
      (res) => {
        if (res.headers['set-cookie']) {
          const fresh = res.headers['set-cookie'].map((c) => c.split(';')[0]).join('; ');
          cookie = cookie ? `${cookie}; ${fresh}` : fresh;
        }
        let raw = '';
        res.on('data', (c) => (raw += c));
        res.on('end', () => {
          let json = null;
          try {
            json = JSON.parse(raw);
          } catch {
            // 非 JSON 响应保留原文
          }
          resolve({ status: res.statusCode, json, raw });
        });
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
const redis = (args) => execSync(`docker exec sb-redis redis-cli ${args}`, { encoding: 'utf-8' }).trim();
const lines = (text) => text.split('\n').map((l) => l.trim()).filter(Boolean);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function snapshot() {
  const [stock, sales] = mysql(`SELECT stock, sales FROM stickybeak_product.t_product WHERE id = ${PRODUCT_ID}`)
    .split(/\s+/)
    .map(Number);
  return {
    stock,
    sales,
    redisStock: redis(`get product:stock:${PRODUCT_ID}`) || null,
    holds: Number(mysql(`SELECT COUNT(*) FROM stickybeak_product.t_stock_hold WHERE product_id = ${PRODUCT_ID}`)),
    orders: Number(mysql('SELECT COUNT(*) FROM stickybeak_order.t_order')),
    undoProduct: Number(mysql('SELECT COUNT(*) FROM stickybeak_product.undo_log')),
    undoOrder: Number(mysql('SELECT COUNT(*) FROM stickybeak_order.undo_log')),
    // 其他待支付订单的账本本来就在，所以比较前后差集
    reservations: lines(redis('--scan --pattern stock:reservation:*')),
  };
}

const summary = (s) => JSON.stringify({ ...s, reservations: s.reservations.length });

let failures = 0;
function check(ok, label) {
  console.log(`  ${ok ? '✔' : '✖'} ${label}`);
  if (!ok) failures += 1;
}

async function main() {
  console.log('==> 准备买家、地址与购物车');
  const reg = await request('POST', '/api/auth/register', {
    email: `seata_${Date.now()}@example.com`,
    password: 'Password123!',
    nickname: 'SeataRollback',
  });
  if (reg.json?.code !== 0) throw new Error(`register failed: ${reg.raw}`);
  const addr = await request('POST', '/api/users/me/addresses', {
    receiver: 'Seata Tester',
    phone: '0412345678',
    state: 'NSW',
    city: 'Sydney',
    postcode: '2000',
    detail: '1 Martin Place',
    isDefault: true,
  });
  const addressId = addr.json.data.id;
  await request('DELETE', '/api/cart');
  await request('POST', '/api/cart/items', { productId: PRODUCT_ID, qty: QTY });

  // 先让 Redis 预扣缓存处于已预热状态：才能验证"扣了又还"，而不是"从没扣过"
  if (redis(`get product:stock:${PRODUCT_ID}`) === '') {
    redis(`set product:stock:${PRODUCT_ID} ${mysql(`SELECT stock FROM stickybeak_product.t_product WHERE id = ${PRODUCT_ID}`)}`);
  }

  const before = snapshot();
  console.log('==> 回滚前', summary(before));

  console.log(`==> 注入故障：t_order 拒绝 remark='${MARK}'`);
  mysql(
    `ALTER TABLE stickybeak_order.t_order ADD CONSTRAINT chk_seata_rollback_test CHECK (remark IS NULL OR remark <> '${MARK}')`,
  );
  try {
    const started = Date.now();
    const res = await request('POST', '/api/orders/checkout', {
      addressId,
      currency: 'AUD',
      paymentMethod: 'card',
      remark: MARK,
    });
    console.log(
      `==> 下单返回 HTTP ${res.status} code=${res.json?.code} (${Date.now() - started} ms): ${res.json?.message ?? res.raw.slice(0, 120)}`,
    );
    check(res.json?.code !== 0, '下单失败（写订单被约束拒绝）');

    // 分支回滚由 TC 驱动，给它一点时间
    let after = snapshot();
    for (let i = 0; i < 20 && (after.stock !== before.stock || after.redisStock !== before.redisStock); i += 1) {
      await sleep(500);
      after = snapshot();
    }
    console.log('==> 回滚后', summary(after));

    check(after.stock === before.stock, `MySQL 库存还原 (${before.stock} → ${after.stock})`);
    check(after.sales === before.sales, `销量还原 (${before.sales} → ${after.sales})`);
    check(after.redisStock === before.redisStock, `Redis 预扣归还 (${before.redisStock} → ${after.redisStock})`);
    check(after.holds === before.holds, `库存预占流水没有残留 (${before.holds} → ${after.holds})`);
    check(after.orders === before.orders, `没有写入订单 (${before.orders} → ${after.orders})`);
    check(after.undoProduct === 0 && after.undoOrder === 0, `undo_log 已清理 (product=${after.undoProduct}, order=${after.undoOrder})`);
    const leaked = after.reservations.filter((k) => !before.reservations.includes(k));
    check(leaked.length === 0, `Redis 订单账本 stock:reservation:* 没有新增残留${leaked.length ? ': ' + leaked.join(',') : ''}`);
  } finally {
    mysql('ALTER TABLE stickybeak_order.t_order DROP CHECK chk_seata_rollback_test');
    console.log('==> 已删除注入的约束');
  }

  // 对照：去掉约束后同样的购物车能正常下单，说明失败确实来自注入的故障；随后取消订单归还库存
  const ok = await request('POST', '/api/orders/checkout', {
    addressId,
    currency: 'AUD',
    paymentMethod: 'card',
    remark: 'Seata rollback control order',
  });
  check(ok.json?.code === 0, '对照：去掉故障后正常下单成功');
  if (ok.json?.code === 0) {
    await request('POST', `/api/orders/${ok.json.data.orderNo}/cancel`);
  }

  console.log(failures === 0 ? '\n🎉 Seata AT 回滚验收通过' : `\n⚠ ${failures} 项未通过`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('❌', e.message);
  process.exit(1);
});
