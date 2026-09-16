#!/usr/bin/env node

/**
 * StickyBeak Sprint 8: End-to-End Automated Test Runner (v1.0.0 Production Release)
 * Covers:
 * 1. Buyer Positive Flow: Register -> Browse -> Add to Cart -> Checkout -> Webhook Paid -> Order Verification
 * 2. Buyer Negative Flow: State Machine Invariant Check -> Webhook Idempotency
 * 3. Admin Operations Flow: Dashboard Analytics -> State Machine Advancement (processing -> shipped -> completed) -> Low Stock Alerts
 * 4. Gateway & RBAC Security: 401 Unauthorized, 403 Forbidden
 * 5. Production Artifacts & Configuration Verification
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const GATEWAY_HOST = 'localhost';
const GATEWAY_PORT = 8080;

let totalTests = 0;
let passedTests = 0;

function assert(condition, message) {
  totalTests++;
  if (condition) {
    console.log(`  ✔ [PASS] ${message}`);
    passedTests++;
  } else {
    console.error(`  ✖ [FAIL] ${message}`);
  }
}

function request(options, postData = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        host: GATEWAY_HOST,
        port: GATEWAY_PORT,
        ...options,
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => (data += chunk));
        res.on('end', () => {
          let json = null;
          try {
            json = JSON.parse(data);
          } catch {
            // Not JSON
          }
          resolve({
            statusCode: res.statusCode,
            headers: res.headers,
            body: data,
            json,
          });
        });
      }
    );
    req.on('error', reject);
    if (postData) {
      if (typeof postData === 'object') {
        req.write(JSON.stringify(postData));
      } else {
        req.write(postData);
      }
    }
    req.end();
  });
}

function extractCookie(headers, cookieName) {
  const cookies = headers['set-cookie'] || [];
  for (const c of cookies) {
    const match = c.match(new RegExp(`^${cookieName}=([^;]+)`));
    if (match) return match[1];
  }
  return null;
}

async function runE2ESuite() {
  console.log('===============================================================');
  console.log('🚀 开始 Sprint 8: 全链路 E2E 自动化测试走查 (v1.0.0 生产级)');
  console.log('===============================================================\n');

  const timestamp = Date.now();
  const buyerEmail = `s8_buyer_${timestamp}@example.com`;
  const buyerPassword = 'Password123!';
  const buyerNickname = `AussieBuyer_${timestamp}`;

  let buyerCookie = null;
  let adminCookie = null;

  // 1. 认证与用户初始化
  console.log('==> 1. 注册新买家并登录获取会话凭证');
  try {
    const regRes = await request(
      {
        path: '/api/auth/register',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      },
      {
        email: buyerEmail,
        password: buyerPassword,
        nickname: buyerNickname,
      }
    );
    assert(regRes.statusCode === 200 && regRes.json?.code === 0, `买家注册成功: ${buyerEmail}`);
    buyerCookie = extractCookie(regRes.headers, 'sb_access');
  } catch (err) {
    assert(false, `买家注册异常: ${err.message}`);
  }

  // 管理员登录
  try {
    const adminLoginRes = await request(
      {
        path: '/api/auth/login',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      },
      {
        email: 'admin@stickybeak.au',
        password: 'Admin123!',
      }
    );
    assert(adminLoginRes.statusCode === 200 && adminLoginRes.json?.code === 0, '管理员登录成功');
    adminCookie = extractCookie(adminLoginRes.headers, 'sb_access');
  } catch (err) {
    assert(false, `管理员登录异常: ${err.message}`);
  }

  // 2. 买家正向链路 (Positive Flow)
  console.log('\n==> 2. [测试用例 1] 买家正向闭环: 浏览 -> 加购 -> 结算 -> 支付确认 -> 订单履约');
  let selectedProduct = null;
  let createdOrderNo = null;

  // 2.1 目录检索
  try {
    const prodRes = await request({
      path: '/api/products?page=1&size=10',
      method: 'GET',
    });
    assert(prodRes.statusCode === 200 && prodRes.json?.data?.items?.length > 0, '商品目录检索成功');
    selectedProduct = prodRes.json.data.items[0];
    console.log(`     选择商品: [${selectedProduct.id}] ${selectedProduct.name}, 库存: ${selectedProduct.stock}`);
  } catch (err) {
    assert(false, `商品检索异常: ${err.message}`);
  }

  // 2.2 加购商品
  if (selectedProduct && buyerCookie) {
    try {
      const addCartRes = await request(
        {
          path: '/api/cart/items',
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Cookie: `sb_access=${buyerCookie}`,
          },
        },
        {
          productId: selectedProduct.id,
          qty: 2,
        }
      );
      assert(addCartRes.statusCode === 200 && addCartRes.json?.code === 0, `成功加购 2 件商品到购物车`);
    } catch (err) {
      assert(false, `加购异常: ${err.message}`);
    }

    // 2.3 查看购物车
    try {
      const cartRes = await request({
        path: '/api/cart',
        method: 'GET',
        headers: { Cookie: `sb_access=${buyerCookie}` },
      });
      assert(cartRes.statusCode === 200 && cartRes.json?.data?.items?.length > 0, '成功获取购物车明细');
    } catch (err) {
      assert(false, `查询购物车异常: ${err.message}`);
    }

    // 2.4 下单并扣减库存
    try {
      const orderRes = await request(
        {
          path: '/api/orders/checkout',
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Cookie: `sb_access=${buyerCookie}`,
          },
        },
        {
          currency: 'AUD',
          paymentMethod: 'card',
          address: {
            receiverName: 'Aussie Buyer',
            phone: '0412345678',
            country: 'Australia',
            state: 'NSW',
            city: 'Sydney',
            postcode: '2000',
            detailAddress: '100 George St',
          },
        }
      );
      assert(
        orderRes.statusCode === 200 && orderRes.json?.data?.orderNo,
        `订单创建成功, 单号: ${orderRes.json?.data?.orderNo}`
      );
      createdOrderNo = orderRes.json?.data?.orderNo;
    } catch (err) {
      assert(false, `下单异常: ${err.message}`);
    }

    // 2.5 模拟 Webhook 支付成功通知
    if (createdOrderNo) {
      try {
        const webhookRes = await request(
          {
            path: '/api/webhooks/mock',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
          },
          {
            eventId: `evt_s8_${timestamp}`,
            orderNo: createdOrderNo,
            amount: 50.0,
            currency: 'AUD',
            transactionId: `tx_s8_${timestamp}`,
          }
        );
        assert(webhookRes.statusCode === 200 && webhookRes.body.includes('SUCCESS'), '支付成功 Webhook 投递成功');

        // 等待异步状态机流转
        await new Promise((r) => setTimeout(r, 1200));

        // 2.6 买家订单中心校验
        const myOrdersRes = await request({
          path: '/api/orders/my',
          method: 'GET',
          headers: { Cookie: `sb_access=${buyerCookie}` },
        });
        const targetOrder = myOrdersRes.json?.data?.find((o) => o.orderNo === createdOrderNo);
        assert(
          targetOrder && targetOrder.status === 'paid',
          `订单中心验证通过: 状态已跃迁为 PAID (实付: ${targetOrder?.payAmount})`
        );
      } catch (err) {
        assert(false, `Webhook 验证异常: ${err.message}`);
      }
    }
  }

  // 3. 买家负向链路 (Negative Flow)
  console.log('\n==> 3. [测试用例 2] 买家负向闭环: 拒付/取消 -> 状态机非法流转拦截 -> 幂等性保障');
  if (buyerCookie && createdOrderNo) {
    try {
      // 3.1 尝试对已支付的订单直接流转到 COMPLETED (状态机非法跃迁拦截)
      const invalidTransitionRes = await request(
        {
          path: `/api/orders/admin/${createdOrderNo}/status`,
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Cookie: `sb_access=${adminCookie}`,
          },
        },
        {
          status: 'completed', // 从 paid 直接跳到 completed 违反状态机 (必须 paid -> processing -> shipped -> completed)
          remark: 'Illegal jump transition',
        }
      );
      assert(
        invalidTransitionRes.statusCode === 200 && invalidTransitionRes.json?.code === 1002,
        `状态机成功拦截非法跳跃流转 (PAID -> COMPLETED 直接跃迁阻断, code: 1002)`
      );
    } catch (err) {
      assert(false, `非法状态机拦截走查异常: ${err.message}`);
    }

    try {
      // 3.2 重复 Webhook 幂等性拦截
      const dupWebhookRes = await request(
        {
          path: '/api/webhooks/mock',
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        },
        {
          eventId: `evt_s8_${timestamp}`, // 相同 eventId
          orderNo: createdOrderNo,
        }
      );
      assert(
        dupWebhookRes.body.includes('ALREADY_PROCESSED'),
        '重复 Webhook 成功触发幂等拦截 (返回 ALREADY_PROCESSED)'
      );
    } catch (err) {
      assert(false, `Webhook 幂等性检查异常: ${err.message}`);
    }
  }

  // 4. 管理端运营管控流 (Admin Operations Flow)
  console.log('\n==> 4. [测试用例 3] 管理后台运营流: 数据看板 -> 订单履约流转 (配货/发货/完成) -> 低库存预警');
  if (adminCookie && createdOrderNo) {
    try {
      // 4.1 数据看板大盘指标
      const summaryRes = await request({
        path: '/api/orders/admin/analytics/summary',
        method: 'GET',
        headers: { Cookie: `sb_access=${adminCookie}` },
      });
      assert(
        summaryRes.statusCode === 200 && summaryRes.json?.data?.totalOrders >= 1,
        '管理后台成功聚合大盘指标 (累计订单数/GMV/待发货)'
      );

      // 4.2 销售走势
      const trendRes = await request({
        path: '/api/orders/admin/analytics/trend?days=7',
        method: 'GET',
        headers: { Cookie: `sb_access=${adminCookie}` },
      });
      assert(
        trendRes.statusCode === 200 && trendRes.json?.data?.length === 7,
        '成功聚合近 7 天销售走势数据 (完整补齐日期序列)'
      );

      // 4.3 热销商品 Top 10
      const topRes = await request({
        path: '/api/orders/admin/analytics/top-products?limit=10',
        method: 'GET',
        headers: { Cookie: `sb_access=${adminCookie}` },
      });
      assert(
        topRes.statusCode === 200 && Array.isArray(topRes.json?.data),
        '成功获取销量 Top 10 商品排行'
      );

      // 4.4 推进订单履约状态: PAID -> PROCESSING (配货拣货)
      const procRes = await request(
        {
          path: `/api/orders/admin/${createdOrderNo}/status`,
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Cookie: `sb_access=${adminCookie}`,
          },
        },
        {
          status: 'processing',
          remark: 'Warehouse started picking',
        }
      );
      assert(procRes.statusCode === 200 && procRes.json?.code === 0, '订单履约推进: PAID -> PROCESSING');

      // 4.5 录入快递单号发货: PROCESSING -> SHIPPED
      const shipRes = await request(
        {
          path: `/api/orders/admin/${createdOrderNo}/status`,
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Cookie: `sb_access=${adminCookie}`,
          },
        },
        {
          status: 'shipped',
          trackingNo: 'AUSPOST-V1-PROD-9988',
          remark: 'Dispatched via Express Post',
        }
      );
      assert(
        shipRes.statusCode === 200 && shipRes.json?.code === 0,
        '订单发货确认并录入快递单号: AUSPOST-V1-PROD-9988'
      );

      // 4.6 签收完成: SHIPPED -> COMPLETED
      const compRes = await request(
        {
          path: `/api/orders/admin/${createdOrderNo}/status`,
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Cookie: `sb_access=${adminCookie}`,
          },
        },
        {
          status: 'completed',
          remark: 'Delivered and signed by buyer',
        }
      );
      assert(compRes.statusCode === 200 && compRes.json?.code === 0, '订单签收完成: SHIPPED -> COMPLETED');

      // 4.7 验证低库存三级预警
      const alertRes = await request({
        path: '/api/products/admin/stock-alerts?threshold=10',
        method: 'GET',
        headers: { Cookie: `sb_access=${adminCookie}` },
      });
      assert(
        alertRes.statusCode === 200 && alertRes.json?.data?.outOfStockCount !== undefined,
        '成功获取商品低库存红橙黄三级预警'
      );
    } catch (err) {
      assert(false, `管理运营流走查异常: ${err.message}`);
    }
  }

  // 5. 网关层安全与权限隔离防御 (Security & RBAC)
  console.log('\n==> 5. [测试用例 4] 网关层安全隔离与权限防御');
  try {
    // 未携带 Token 访问受保护买家接口 -> 401
    const unauthRes = await request({
      path: '/api/orders/my',
      method: 'GET',
    });
    assert(unauthRes.statusCode === 401, '未认证请求严格拦截并返回 401 Unauthorized');

    // 普通买家越权访问后台运营统计 -> 403
    const forbiddenRes = await request({
      path: '/api/orders/admin/analytics/summary',
      method: 'GET',
      headers: { Cookie: `sb_access=${buyerCookie}` },
    });
    assert(forbiddenRes.statusCode === 403, '普通用户访问 /admin 接口严格阻断并返回 403 Forbidden');
  } catch (err) {
    assert(false, `权限防御检查异常: ${err.message}`);
  }

  // 6. 生产交付物完整性检查
  console.log('\n==> 6. [测试用例 5] 生产级交付产出物规格检查');
  const rootDir = path.resolve(__dirname, '..');
  assert(
    fs.existsSync(path.join(rootDir, 'docker/docker-compose.prod.yml')),
    '生产 Docker Compose 编排文件存在: docker-compose.prod.yml'
  );
  assert(
    fs.existsSync(path.join(rootDir, 'docker/nginx.prod.conf')),
    '生产 Nginx SSL/Gzip 配置存在: nginx.prod.conf'
  );
  assert(
    fs.existsSync(path.join(rootDir, 'docs/DEPLOYMENT_GUIDE.md')),
    '云上线运维手册存在: docs/DEPLOYMENT_GUIDE.md'
  );
  assert(
    fs.existsSync(path.join(rootDir, 'docs/DEMO_SCRIPT.md')),
    '3-5 分钟演示视频答辩剧本存在: docs/DEMO_SCRIPT.md'
  );
  assert(
    fs.existsSync(path.join(rootDir, 'stickybeak-frontend/playwright.config.ts')),
    'Playwright 测试套件配置存在'
  );
  assert(
    fs.existsSync(path.join(rootDir, 'stickybeak-frontend/e2e/shopping-flow.spec.ts')),
    'Playwright E2E 规范测试用例存在'
  );

  console.log('\n===============================================================');
  if (passedTests === totalTests) {
    console.log(`🎉 恭喜！Sprint 8 全链路 E2E 自动化走查 全部 ${totalTests} 项用例 100% 通过！`);
  } else {
    console.error(`⚠ 走查完成：${passedTests}/${totalTests} 通过，有 ${totalTests - passedTests} 项未满足预期。`);
    process.exit(1);
  }
  console.log('===============================================================\n');
}

runE2ESuite().catch((err) => {
  console.error('Fatal error running E2E suite:', err);
  process.exit(1);
});
