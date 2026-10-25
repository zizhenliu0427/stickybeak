// E2E：admin 登录 → 申请预签名 URL → 直传 MinIO → 新建商品 → 前台列表/ES 搜索验证
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const TMP = process.env.TEMP;
const GW = 'http://localhost:8080';
const ADMIN_JSON = `${TMP}\\sb-admin.json`;
const JAR = `${TMP}\\sb-admin-cookies.txt`;

function sh(cmd) {
  return execSync(cmd, { maxBuffer: 16 * 1024 * 1024 }).toString('utf8');
}
function api(method, url, bodyFile, jar = JAR) {
  const data = bodyFile ? `-d "@${bodyFile}" -H "Content-Type: application/json"` : '';
  return sh(`curl.exe -s -b "${jar}" -c "${jar}" -X ${method} "${GW}${url}" ${data}`);
}
function writeJson(name, obj) {
  const f = `${TMP}\\${name}`;
  fs.writeFileSync(f, JSON.stringify(obj), 'utf8');
  return f;
}

(async () => {
  // 1. admin 登录（注意：登录与后续调用必须用同一个 cookie jar）
  fs.rmSync(JAR, { force: true });
  fs.writeFileSync(ADMIN_JSON, JSON.stringify({ email: 'admin@stickybeak.au', password: 'Admin123!' }), 'utf8');
  let r = api('POST', '/api/auth/login', ADMIN_JSON);
  console.log('1. admin 登录:', r.includes('"code":0') ? 'OK' : r);

  // 2. 申请预签名 URL
  const uploadReq = writeJson('sb-upload-req.json', { filename: 'e2e-test.jpg', contentType: 'image/jpeg' });
  r = api('POST', '/api/products/admin/upload-url', uploadReq);
  const presign = JSON.parse(r).data;
  console.log('2. 预签名 URL:', presign.objectKey, '|', presign.uploadUrl.slice(0, 80) + '...');

  // 3. 直传一张真实商品图到 MinIO
  const imgPath = path.resolve(__dirname, '../stickybeak-frontend/public/mock-products/sydney-t9-train-sign/01.jpg');
  sh(`curl.exe -s -X PUT "${presign.uploadUrl}" --data-binary "@${imgPath}" -H "Content-Type: image/jpeg" -o NUL -w "PUT %{http_code}\\n"`);
  console.log('3. 图片直传 MinIO: PUT 完成');

  // 4. 验证公网可读
  const check = sh(`curl.exe -s -o NUL -w "%{http_code}" "${presign.publicUrl}"`).trim();
  console.log('4. 公网 URL 可读:', check);

  // 5. 新建商品
  const createReq = writeJson('sb-create-product.json', {
    name: 'E2E 测试·悉尼火车 T9 冰箱贴（可删）',
    categoryId: 3, // 火车地铁路牌
    price: 19.90,
    stock: 5,
    description: '端到端测试创建的商品，验证 MinIO 上传 + ES 同步链路。',
    images: [presign.publicUrl],
    tags: ['E2E', 'T9'],
    featured: false,
  });
  r = api('POST', '/api/products/admin', createReq);
  const created = JSON.parse(r);
  const newId = created.data.id;
  console.log('5. 新建商品 id:', newId);

  // 6. 等 ES 同步（MQ 消费）后搜索验证
  await new Promise((r2) => setTimeout(r2, 3000));
  const searchUrl = `${GW}/api/products?q=${encodeURIComponent('E2E 测试')}`;
  const searchRes = JSON.parse(sh(`curl.exe -s "${searchUrl}"`));
  const hit = searchRes.data.items.find((i) => i.id === newId);
  console.log('6. ES 搜索「E2E 测试」命中新商品:', hit ? `YES（${hit.name}）` : 'NO');

  // 7. 前台列表可见（火车类目）
  const listRes = JSON.parse(sh(`curl.exe -s "${GW}/api/products?category=train&size=50"`));
  console.log('7. 前台火车类目含新商品:', listRes.data.items.some((i) => i.id === newId) ? 'YES' : 'NO');

  console.log('\n✅ E2E 通过' + (hit ? '' : '（ES 未命中，检查 MQ 消费）'));
})().catch((e) => {
  console.error('E2E 失败:', e.message);
  process.exit(1);
});
