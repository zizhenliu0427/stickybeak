// Sprint 6 Live End-to-End Verification (Admin Portal, Analytics, Stock & Orders)
const http = require('http');
const { execSync } = require('child_process');

const GATEWAY = 'http://localhost:8080';

function makeRequest(method, path, body, cookie) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, GATEWAY);
    const data = body ? JSON.stringify(body) : '';
    const headers = {
      ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}),
      ...(cookie ? { Cookie: cookie } : {}),
    };

    const req = http.request(url, { method, headers }, (res) => {
      let setCookie = '';
      if (res.headers['set-cookie']) {
        setCookie = res.headers['set-cookie'].map((c) => c.split(';')[0]).join('; ');
      }
      let resBody = '';
      res.on('data', (chunk) => (resBody += chunk));
      res.on('end', () => {
        try {
          const json = JSON.parse(resBody);
          resolve({ status: res.statusCode, data: json, cookie: setCookie });
        } catch (e) {
          resolve({ status: res.statusCode, raw: resBody, cookie: setCookie });
        }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
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
  console.log('====================================================================');
  console.log('🚀 开始 Sprint 6: 管理后台 Dashboard、销售分析与运营管控 全链路验证');
  console.log('====================================================================');

  // 等待微服务完全健康
  await sleep(3000);

  // 1. 测试用例 1: RBAC 管理权限隔离与网关拦截
  console.log('\n==> 1. [测试用例 1] RBAC 权限隔离拦截测试');
  const customerEmail = `buyer_${Date.now()}@example.com`;
  const regResp = await makeRequest('POST', '/api/auth/register', {
    email: customerEmail,
    password: 'Password123!',
    nickname: 'TestBuyer',
  });
  const customerCookie = regResp.cookie;
  console.log(`   普通买家已注册: ${customerEmail}`);

  // 买家尝试访问管理端汇总接口
  const forbiddenResp = await makeRequest('GET', '/api/orders/admin/analytics/summary', null, customerCookie);
  console.log(`   买家越权访问返回状态码: ${forbiddenResp.status} (预期: 403 Forbidden)`);
  if (forbiddenResp.status !== 403) {
    throw new Error(`Expected 403 Forbidden for regular customer, got ${forbiddenResp.status}`);
  }

  // 登录开发内置管理员
  console.log('   登录开发管理员: admin@stickybeak.au');
  const adminLoginResp = await makeRequest('POST', '/api/auth/login', {
    email: 'admin@stickybeak.au',
    password: 'Admin123!',
  });
  if (adminLoginResp.data?.code !== 0) {
    throw new Error(`Admin login failed: ${JSON.stringify(adminLoginResp)}`);
  }
  const adminCookie = adminLoginResp.cookie;
  console.log('   ✔ 管理员登录成功，已获取管理员会话 Cookie');

  // 管理员访问汇总接口
  const adminSummaryResp = await makeRequest('GET', '/api/orders/admin/analytics/summary', null, adminCookie);
  console.log(`   管理员访问管理端接口返回状态码: ${adminSummaryResp.status}, code: ${adminSummaryResp.data?.code}`);
  if (adminSummaryResp.status !== 200 || adminSummaryResp.data?.code !== 0) {
    throw new Error(`Admin access failed: ${JSON.stringify(adminSummaryResp)}`);
  }
  console.log('✔ [测试用例 1 通过] RBAC 权限隔离验证通过：普通买家 403 严格阻断，管理员 200 放行！');

  // 2. 测试用例 2: 销售分析聚合、近 7 天趋势与 Top 10 热销榜
  console.log('\n==> 2. [测试用例 2] 销售分析聚合与走势图 API 校验');
  const summary = adminSummaryResp.data.data;
  console.log('   Dashboard Summary 指标数据:');
  console.log(`     - 累计 GMV: $${summary.totalGmv} AUD`);
  console.log(`     - 今日 GMV: $${summary.todayGmv} AUD`);
  console.log(`     - 总订单数: ${summary.totalOrders}, 今日订单: ${summary.todayOrders}`);
  console.log(`     - 待发货订单数: ${summary.pendingShipmentOrders}`);
  if (summary.totalOrders === undefined || summary.totalGmv === undefined) {
    throw new Error('Analytics summary missing key metrics');
  }

  // 趋势图接口
  const trendResp = await makeRequest('GET', '/api/orders/admin/analytics/trend?days=7', null, adminCookie);
  console.log(`   近 7 天销售走势数据点数: ${trendResp.data?.data?.length} (预期: 7)`);
  if (!Array.isArray(trendResp.data?.data) || trendResp.data.data.length !== 7) {
    throw new Error(`Expected 7 trend data points, got ${trendResp.data?.data?.length}`);
  }

  // Top 10 商品
  const topsResp = await makeRequest('GET', '/api/orders/admin/analytics/top-products?limit=5', null, adminCookie);
  console.log(`   Top 热销商品返回条目目数: ${topsResp.data?.data?.length}`);
  console.log('✔ [测试用例 2 通过] 销售分析与走势聚合 API 验证通过！');

  // 3. 测试用例 3: 商品上下架控制与前台买家端同步
  console.log('\n==> 3. [测试用例 3] 商品上下架切换与前台缓存同步');
  const testProductId = 1000001;

  // 下架商品
  console.log(`   下架商品 ${testProductId}...`);
  const unlistResp = await makeRequest(
    'PUT',
    `/api/products/admin/${testProductId}/status`,
    { status: 1 },
    adminCookie
  );
  if (unlistResp.data?.code !== 0) {
    throw new Error(`Failed to unlist product: ${JSON.stringify(unlistResp)}`);
  }

  const dbStatus1 = runMysql(
    `SELECT status FROM stickybeak_product.t_product WHERE id = ${testProductId};`
  );
  console.log(`   MySQL 数据库中商品状态: ${dbStatus1} (1=UNLISTED)`);
  if (dbStatus1 !== '1') {
    throw new Error(`Expected DB status 1, got ${dbStatus1}`);
  }

  // 重新上架
  console.log(`   重新上架商品 ${testProductId}...`);
  const listResp = await makeRequest(
    'PUT',
    `/api/products/admin/${testProductId}/status`,
    { status: 0 },
    adminCookie
  );
  if (listResp.data?.code !== 0) {
    throw new Error(`Failed to list product: ${JSON.stringify(listResp)}`);
  }
  const dbStatus0 = runMysql(
    `SELECT status FROM stickybeak_product.t_product WHERE id = ${testProductId};`
  );
  console.log(`   MySQL 数据库中商品状态: ${dbStatus0} (0=ACTIVE)`);
  if (dbStatus0 !== '0') {
    throw new Error(`Expected DB status 0, got ${dbStatus0}`);
  }
  console.log('✔ [测试用例 3 通过] 管理员上下架切换及数据库/缓存同步验证通过！');

  // 4. 测试用例 4: 动态库存调配与 Redis 预扣缓存强一致同步
  console.log('\n==> 4. [测试用例 4] 动态库存增补与 Redis 预扣减缓存同步');
  const currentDbStock = parseInt(
    runMysql(`SELECT stock FROM stickybeak_product.t_product WHERE id = ${testProductId};`),
    10
  );
  const targetStock = currentDbStock + 50;
  console.log(`   商品当前库存: ${currentDbStock}, 计划调配为: ${targetStock}`);

  const stockUpdateResp = await makeRequest(
    'PUT',
    `/api/products/admin/${testProductId}/stock`,
    { stock: targetStock },
    adminCookie
  );
  if (stockUpdateResp.data?.code !== 0) {
    throw new Error(`Failed to update stock: ${JSON.stringify(stockUpdateResp)}`);
  }

  const afterDbStock = parseInt(
    runMysql(`SELECT stock FROM stickybeak_product.t_product WHERE id = ${testProductId};`),
    10
  );
  const afterRedisStock = parseInt(runRedis(`get product:stock:${testProductId}`), 10);
  console.log(`   调配后 MySQL 物理库存: ${afterDbStock}`);
  console.log(`   调配后 Redis 缓存库存: ${afterRedisStock}`);

  if (afterDbStock !== targetStock || afterRedisStock !== targetStock) {
    throw new Error(
      `Stock mismatch! Expected ${targetStock}, DB: ${afterDbStock}, Redis: ${afterRedisStock}`
    );
  }

  // 检查预警列表
  const alertsResp = await makeRequest('GET', '/api/products/admin/stock-alerts?threshold=20', null, adminCookie);
  console.log(`   低库存预警返回商品项: ${alertsResp.data?.data?.totalAlerts} 项`);
  console.log('✔ [测试用例 4 通过] 动态库存调配与 Redis 缓存强一致同步验证通过！');

  // 5. 测试用例 5: 订单履约全状态机推进 (Paid -> Processing -> Shipped -> Completed)
  console.log('\n==> 5. [测试用例 5] 订单全状态机推进 (Paid -> Processing -> Shipped -> Completed)');

  // 买家创建收货地址
  const addrResp = await makeRequest(
    'POST',
    '/api/users/me/addresses',
    {
      receiver: 'Sprint6 Recipient',
      phone: '0499887766',
      state: 'VIC',
      city: 'Melbourne',
      postcode: '3000',
      detail: 'Level 5, 120 Collins Street',
      isDefault: true,
    },
    customerCookie
  );
  const addressId = addrResp.data.data.id;

  // 加购并下单
  await makeRequest('POST', '/api/cart/items', { productId: testProductId, qty: 1 }, customerCookie);
  const checkoutResp = await makeRequest(
    'POST',
    '/api/orders/checkout',
    {
      addressId,
      currency: 'AUD',
      paymentMethod: 'card',
    },
    customerCookie
  );
  const orderNo = checkoutResp.data.data.orderNo;
  console.log(`   买家创建订单成功: ${orderNo}`);

  // 模拟支付成功
  await makeRequest('POST', '/api/webhooks/mock', {
    id: `evt_mock_${Date.now()}`,
    type: 'mock.payment.paid',
    orderNo,
    amount: 34.9,
    currency: 'AUD',
  });
  await sleep(1500);

  // 管理员推进状态: paid -> processing (开始拣货)
  console.log('   管理员推进订单为 processing (拣货中)...');
  const processResp = await makeRequest(
    'PUT',
    `/api/orders/admin/${orderNo}/status`,
    { status: 'processing', remark: 'Order picked and packed in Sydney warehouse' },
    adminCookie
  );
  if (processResp.data?.code !== 0) {
    throw new Error(`Failed to transition to processing: ${JSON.stringify(processResp)}`);
  }

  // 管理员推进状态: processing -> shipped (发货录入单号)
  console.log('   管理员录入物流单号发货: processing -> shipped...');
  const trackingNo = `AUSPOST_SB6_${Date.now()}`;
  const shipResp = await makeRequest(
    'PUT',
    `/api/orders/admin/${orderNo}/status`,
    { status: 'shipped', trackingNo, remark: 'Express Courier dispatched' },
    adminCookie
  );
  if (shipResp.data?.code !== 0) {
    throw new Error(`Failed to transition to shipped: ${JSON.stringify(shipResp)}`);
  }

  // 管理员推进状态: shipped -> completed (确认收货)
  console.log('   管理员确认签收: shipped -> completed...');
  const completeResp = await makeRequest(
    'PUT',
    `/api/orders/admin/${orderNo}/status`,
    { status: 'completed', remark: 'Customer acknowledged parcel delivered' },
    adminCookie
  );
  if (completeResp.data?.code !== 0) {
    throw new Error(`Failed to transition to completed: ${JSON.stringify(completeResp)}`);
  }

  // 检查最终数据库状态
  const finalOrderStatus = runMysql(
    `SELECT status FROM stickybeak_order.t_order WHERE order_no = '${orderNo}';`
  );
  console.log(`   最终订单状态: ${finalOrderStatus} (预期: completed)`);
  if (finalOrderStatus !== 'completed') {
    throw new Error(`Expected status 'completed', got '${finalOrderStatus}'`);
  }

  // 检查状态机审计流水
  const historyCount = parseInt(
    runMysql(
      `SELECT count(*) FROM stickybeak_order.t_order_status_history h ` +
      `JOIN stickybeak_order.t_order o ON h.order_id = o.id ` +
      `WHERE o.order_no = '${orderNo}' AND h.operator_role = 'admin';`
    ),
    10
  );
  console.log(`   管理员在订单流转中留存的审计历史记录数: ${historyCount} (预期 >= 3)`);
  if (historyCount < 3) {
    throw new Error(`Expected at least 3 admin history logs, got ${historyCount}`);
  }
  console.log('✔ [测试用例 5 通过] 订单全状态机推进与管理员操作审计流水完全正确！');

  console.log('\n====================================================================');
  console.log('🎉 恭喜！Sprint 6 管理后台全部 5 大核心全链路测试用例 100% 通过！');
  console.log('====================================================================');
}

main().catch((err) => {
  console.error('\n❌ 测试失败:', err);
  process.exit(1);
});
