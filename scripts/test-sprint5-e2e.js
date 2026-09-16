// Sprint 5 Live End-to-End & Concurrency Verification (Node.js runner)
const http = require('http');
const { execSync } = require('child_process');

const GATEWAY = 'http://localhost:8080';
let cookieHeader = '';

function post(path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, GATEWAY);
    const data = JSON.stringify(body || {});
    const req = http.request(
      url,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
          ...(cookieHeader ? { Cookie: cookieHeader } : {}),
        },
      },
      (res) => {
        if (res.headers['set-cookie']) {
          const newCookies = res.headers['set-cookie'].map((c) => c.split(';')[0]).join('; ');
          cookieHeader = cookieHeader ? `${cookieHeader}; ${newCookies}` : newCookies;
        }
        let resBody = '';
        res.on('data', (chunk) => (resBody += chunk));
        res.on('end', () => {
          try {
            const json = JSON.parse(resBody);
            resolve({ status: res.statusCode, data: json });
          } catch (e) {
            resolve({ status: res.statusCode, raw: resBody });
          }
        });
      }
    );
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

function get(path) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, GATEWAY);
    const req = http.request(
      url,
      {
        method: 'GET',
        headers: {
          ...(cookieHeader ? { Cookie: cookieHeader } : {}),
        },
      },
      (res) => {
        let resBody = '';
        res.on('data', (chunk) => (resBody += chunk));
        res.on('end', () => {
          try {
            const json = JSON.parse(resBody);
            resolve({ status: res.statusCode, data: json });
          } catch (e) {
            resolve({ status: res.statusCode, raw: resBody });
          }
        });
      }
    );
    req.on('error', reject);
    req.end();
  });
}

function del(path) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, GATEWAY);
    const req = http.request(
      url,
      {
        method: 'DELETE',
        headers: {
          ...(cookieHeader ? { Cookie: cookieHeader } : {}),
        },
      },
      (res) => {
        let resBody = '';
        res.on('data', (chunk) => (resBody += chunk));
        res.on('end', () => resolve({ status: res.statusCode, body: resBody }));
      }
    );
    req.on('error', reject);
    req.end();
  });
}

function runMysql(sql) {
  const cmd = `docker exec sb-mysql mysql -uroot -proot -N -e "${sql}"`;
  return execSync(cmd, { encoding: 'utf-8' }).trim();
}

function runRedis(cmdArgs) {
  const cmd = `docker exec sb-redis redis-cli ${cmdArgs}`;
  return execSync(cmd, { encoding: 'utf-8' }).trim();
}

async function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  console.log('===============================================================');
  console.log('🚀 开始 Sprint 5: 订单状态机与防超卖三层防线 全链路真实环境走查');
  console.log('===============================================================');

  // 1. 注册测试用户
  const email = `sprint5_${Date.now()}@example.com`;
  console.log(`\n==> 1. 注册并登录测试用户: ${email}`);
  const regResp = await post('/api/auth/register', {
    email,
    password: 'Password123!',
    nickname: 'Sprint5Runner',
  });
  if (!regResp.data || regResp.data.code !== 0) {
    throw new Error(`Registration failed: ${JSON.stringify(regResp)}`);
  }
  console.log(`✔ 注册成功，已建立 Cookie 会话: ${cookieHeader.substring(0, 35)}...`);

  // 2. 创建收货地址
  console.log('\n==> 2. 创建收货地址 (Sydney, NSW)');
  const addrResp = await post('/api/users/me/addresses', {
    receiver: 'Sprint5 Buyer',
    phone: '0412345678',
    state: 'NSW',
    city: 'Sydney',
    postcode: '2000',
    detail: 'Suite 101, 200 George Street',
    isDefault: true,
  });
  if (!addrResp.data || addrResp.data.code !== 0) {
    throw new Error(`Address creation failed: ${JSON.stringify(addrResp)}`);
  }
  const addressId = addrResp.data.data.id;
  console.log(`✔ 收货地址创建成功, addressId: ${addressId}`);

  // 3. 检查商品库存
  const productId = 1000001;
  const initialStockParts = runMysql(
    `SELECT stock, sales FROM stickybeak_product.t_product WHERE id = ${productId};`
  ).split(/\s+/);
  const initStock = parseInt(initialStockParts[0], 10);
  const initSales = parseInt(initialStockParts[1], 10);
  console.log(`\n==> 3. 商品 ${productId} 初始物理库存: Stock=${initStock}, Sales=${initSales}`);

  // 4. 测试用例 1: 正常加购与下单（三层防线扣减库存 + 记录预占流水）
  console.log('\n==> 4. [测试用例 1] 正常加购与下单 (购买 2 件)');
  await del('/api/cart');
  await post('/api/cart/items', { productId, qty: 2 });
  const checkoutResp = await post('/api/orders/checkout', {
    addressId,
    currency: 'AUD',
    paymentMethod: 'card',
    remark: 'Sprint 5 Node Test Order',
  });
  if (!checkoutResp.data || checkoutResp.data.code !== 0) {
    throw new Error(`Checkout failed: ${JSON.stringify(checkoutResp)}`);
  }
  const orderNo = checkoutResp.data.data.orderNo;
  console.log(`✔ 订单创建成功, orderNo: ${orderNo}`);

  // 验证 MySQL 物理库存扣减 -2
  const afterOrderStockParts = runMysql(
    `SELECT stock, sales FROM stickybeak_product.t_product WHERE id = ${productId};`
  ).split(/\s+/);
  const afterStock = parseInt(afterOrderStockParts[0], 10);
  console.log(`   MySQL 物理库存变动: ${initStock} -> ${afterStock} (预期扣减 2)`);
  if (afterStock !== initStock - 2) {
    throw new Error(`MySQL stock deduction mismatch! Expected ${initStock - 2}, got ${afterStock}`);
  }

  // 验证 Redis 缓存库存
  const redisStock = parseInt(runRedis(`get product:stock:${productId}`), 10);
  console.log(`   Redis 缓存库存: ${redisStock} (与 DB 保持一致)`);
  if (redisStock !== afterStock) {
    throw new Error(`Redis cache mismatch! Expected ${afterStock}, got ${redisStock}`);
  }

  // 验证 t_stock_hold 状态为 0 (HELD)
  const holdStatus = runMysql(
    `SELECT status FROM stickybeak_product.t_stock_hold WHERE order_no = '${orderNo}' AND product_id = ${productId};`
  );
  console.log(`   t_stock_hold 预占流水状态: ${holdStatus} (0=HELD)`);
  if (holdStatus !== '0') {
    throw new Error(`Stock hold status expected 0, got ${holdStatus}`);
  }
  console.log('✔ [测试用例 1 通过] 三层防线库存扣减与预占流水验证完全正确!');

  // 5. 测试用例 2: 买家主动取消订单与原子回滚
  console.log('\n==> 5. [测试用例 2] 买家主动取消订单并验证原子库存回滚');
  const cancelResp = await post(`/api/orders/${orderNo}/cancel`, {});
  if (cancelResp.data.code !== 0) {
    throw new Error(`Cancel order failed: ${JSON.stringify(cancelResp)}`);
  }
  console.log(`✔ 触发取消订单接口成功`);

  const orderDetailResp = await get(`/api/orders/${orderNo}`);
  const currentStatus = orderDetailResp.data.data.status;
  console.log(`   订单当前状态: ${currentStatus} (预期: cancelled)`);
  if (currentStatus !== 'cancelled') {
    throw new Error(`Expected status 'cancelled', got '${currentStatus}'`);
  }

  // 校验 MySQL 物理库存回滚 (+2)
  const rolledStockParts = runMysql(
    `SELECT stock, sales FROM stickybeak_product.t_product WHERE id = ${productId};`
  ).split(/\s+/);
  const rolledStock = parseInt(rolledStockParts[0], 10);
  console.log(`   MySQL 物理库存恢复: ${afterStock} -> ${rolledStock} (恢复至初始: ${initStock})`);
  if (rolledStock !== initStock) {
    throw new Error(`Rolled stock expected ${initStock}, got ${rolledStock}`);
  }

  // 校验 Redis 缓存库存回滚 (+2)
  const rolledRedisStock = parseInt(runRedis(`get product:stock:${productId}`), 10);
  console.log(`   Redis 缓存库存恢复: ${rolledRedisStock}`);
  if (rolledRedisStock !== initStock) {
    throw new Error(`Rolled Redis stock expected ${initStock}, got ${rolledRedisStock}`);
  }

  // 校验 t_stock_hold 状态变为 2 (RELEASED)
  const releasedStatus = runMysql(
    `SELECT status FROM stickybeak_product.t_stock_hold WHERE order_no = '${orderNo}' AND product_id = ${productId};`
  );
  console.log(`   t_stock_hold 预占流水状态: ${releasedStatus} (2=RELEASED)`);
  if (releasedStatus !== '2') {
    throw new Error(`Stock hold status expected 2, got ${releasedStatus}`);
  }
  console.log('✔ [测试用例 2 通过] 订单取消、状态机推进与库存原子回滚完全正确!');

  // 6. 测试用例 3: 状态机非法逆向流转拦截
  console.log('\n==> 6. [测试用例 3] 状态机非法流转拦截 (重复取消已取消的订单)');
  const repeatCancel = await post(`/api/orders/${orderNo}/cancel`, {});
  console.log(`   拦截响应: HTTP ${repeatCancel.status}, code = ${repeatCancel.data?.code}`);
  if (repeatCancel.data && repeatCancel.data.code === 0) {
    throw new Error('Illegal state transition was not rejected!');
  }
  console.log('✔ [测试用例 3 通过] 成功拦截非法状态机流转!');

  // 7. 测试用例 4: 支付完成确认预占库存 (status: HELD -> CONFIRMED)
  console.log('\n==> 7. [测试用例 4] 支付完成确认库存 (t_stock_hold 0 -> 1)');
  const delRes = await del('/api/cart');
  console.log('   del /api/cart:', delRes.status, delRes.body);
  const addRes = await post('/api/cart/items', { productId, qty: 1 });
  console.log('   add to cart:', JSON.stringify(addRes));
  const checkout2 = await post('/api/orders/checkout', {
    addressId,
    currency: 'AUD',
    paymentMethod: 'card',
  });
  console.log('   checkout2 response:', JSON.stringify(checkout2));
  const orderNo2 = checkout2.data.data.orderNo;

  // 触发 Mock Webhook
  await post('/api/webhooks/mock', {
    id: `evt_mock_${Date.now()}`,
    type: 'mock.payment.paid',
    orderNo: orderNo2,
    amount: 34.9,
    currency: 'AUD',
    transactionId: 'tx_sprint5_mock',
  });

  await sleep(2000); // 等待 MQ 消费
  const orderDetail2 = await get(`/api/orders/${orderNo2}`);
  console.log(`   订单状态: ${orderDetail2.data.data.status} (预期: paid)`);
  if (orderDetail2.data.data.status !== 'paid') {
    throw new Error(`Order failed to transition to 'paid', got '${orderDetail2.data.data.status}'`);
  }

  const confirmedStatus = runMysql(
    `SELECT status FROM stickybeak_product.t_stock_hold WHERE order_no = '${orderNo2}' AND product_id = ${productId};`
  );
  console.log(`   t_stock_hold 状态: ${confirmedStatus} (1=CONFIRMED)`);
  if (confirmedStatus !== '1') {
    throw new Error(`Stock hold status expected 1, got ${confirmedStatus}`);
  }
  console.log('✔ [测试用例 4 通过] 支付成功确认库存流水验证通过!');

  // 8. 测试用例 5: 防超卖高并发压测 (20 并发争抢 5 件库存)
  console.log('\n==> 8. [测试用例 5] 防超卖高并发压测 (20 并发请求抢购 5 件库存)');
  const stressProduct = 1000002;
  // 精确重置库存为 5
  runMysql(
    `UPDATE stickybeak_product.t_product SET stock = 5, sales = 0 WHERE id = ${stressProduct};`
  );
  runRedis(`del product:stock:${stressProduct}`);
  // 预热
  await get(`/api/products/id/${stressProduct}`);
  console.log(`   商品 ${stressProduct} 初始库存已重置为: 5`);

  // 并发 20 个请求调用扣减库存
  let successCount = 0;
  let failCount = 0;
  const requests = Array.from({ length: 20 }, (_, i) => {
    return post('/api/products/stock/deduct', {
      orderNo: `SO_STRESS_${Date.now()}_${i}`,
      items: [{ productId: stressProduct, qty: 1 }],
    }).then((res) => {
      if (res.data && res.data.code === 0) {
        successCount++;
      } else {
        failCount++;
      }
    });
  });

  await Promise.all(requests);
  console.log(`   并发执行完成: 成功 = ${successCount} 次, 拦截/失败 = ${failCount} 次`);

  const finalDbStock = parseInt(
    runMysql(`SELECT stock FROM stickybeak_product.t_product WHERE id = ${stressProduct};`),
    10
  );
  const finalRedisStock = parseInt(runRedis(`get product:stock:${stressProduct}`), 10);
  console.log(`   压测后 MySQL 物理库存: ${finalDbStock} (严格为 0)`);
  console.log(`   压测后 Redis 缓存库存: ${finalRedisStock} (严格为 0)`);

  if (finalDbStock !== 0 || finalRedisStock !== 0) {
    throw new Error(`Overselling detected! DB: ${finalDbStock}, Redis: ${finalRedisStock}`);
  }
  if (successCount !== 5 || failCount !== 15) {
    throw new Error(`Expected 5 successes and 15 failures, got ${successCount} and ${failCount}`);
  }
  console.log('✔ [测试用例 5 通过] 高并发防超卖验证通过！20 并发请求严格扣减 5 件，剩余 15 次全部被前置阻断，0 超卖！');

  // 9. 测试用例 6: RabbitMQ 延迟死信队列超时自动关单与库存回滚
  console.log('\n==> 9. [测试用例 6] RabbitMQ 延迟死信队列超时自动关单与库存回滚');
  await del('/api/cart');
  await post('/api/cart/items', { productId, qty: 1 });
  const checkout3 = await post('/api/orders/checkout', {
    addressId,
    currency: 'AUD',
    paymentMethod: 'card',
  });
  const orderNo3 = checkout3.data.data.orderNo;
  const stockBeforeTimeout = parseInt(
    runMysql(`SELECT stock FROM stickybeak_product.t_product WHERE id = ${productId};`),
    10
  );
  console.log(`   待超时订单: ${orderNo3}, 下单后当前物理库存: ${stockBeforeTimeout}`);

  // 模拟 DLX 死信触发：直接投递至 order.dlx.exchange
  const escapedPayload = JSON.stringify(orderNo3).replace(/"/g, '\\"');
  execSync(
    `docker exec sb-rabbitmq rabbitmqadmin publish exchange="order.dlx.exchange" routing_key="order.close.routing" payload="\\"${orderNo3}\\""`
  );

  await sleep(2500); // 等待 MQ 消费

  const orderDetail3 = await get(`/api/orders/${orderNo3}`);
  console.log(`   超时后订单状态: ${orderDetail3.data.data.status} (预期: cancelled)`);
  if (orderDetail3.data.data.status !== 'cancelled') {
    throw new Error(`Order failed to timeout-cancel, status is '${orderDetail3.data.data.status}'`);
  }

  const stockAfterTimeout = parseInt(
    runMysql(`SELECT stock FROM stickybeak_product.t_product WHERE id = ${productId};`),
    10
  );
  console.log(`   超时关单后物理库存: ${stockAfterTimeout} (预期回滚 +1: ${stockBeforeTimeout + 1})`);
  if (stockAfterTimeout !== stockBeforeTimeout + 1) {
    throw new Error(`Stock did not roll back on timeout! Expected ${stockBeforeTimeout + 1}, got ${stockAfterTimeout}`);
  }
  console.log('✔ [测试用例 6 通过] RabbitMQ 延迟死信队列自动关单与库存回滚验证完全正确!');

  console.log('\n===============================================================');
  console.log('🎉 恭喜！Sprint 5 订单状态机与防超卖三层防线 全部 6 大测试用例 100% 通过！');
  console.log('===============================================================');
}

main().catch((err) => {
  console.error('\n❌ 测试失败:', err);
  process.exit(1);
});
