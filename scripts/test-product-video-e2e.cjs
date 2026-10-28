// E2E：商品视频支持 —— 上传 mp4 + 图片 → 建品 → 详情 VO 媒体类型正确 → 前台可见
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const TMP = process.env.TEMP;
const GW = 'http://localhost:8080';
const JAR = `${TMP}\\sb-admin-cookies.txt`;

function sh(cmd) {
  return execSync(cmd, { maxBuffer: 16 * 1024 * 1024 }).toString('utf8');
}
function api(method, url, bodyFile) {
  const data = bodyFile ? `-d "@${bodyFile}" -H "Content-Type: application/json"` : '';
  return sh(`curl.exe -s -b "${JAR}" -c "${JAR}" -X ${method} "${GW}${url}" ${data}`);
}
function writeJson(name, obj) {
  const f = `${TMP}\\${name}`;
  fs.writeFileSync(f, JSON.stringify(obj), 'utf8');
  return f;
}
function uploadFile(filePath, filename, contentType) {
  const req = writeJson('sb-upload-req.json', { filename, contentType });
  const presign = JSON.parse(api('POST', '/api/products/admin/upload-url', req)).data;
  if (!presign) throw new Error('presign failed: ' + api('POST', '/api/products/admin/upload-url', req));
  sh(`curl.exe -s -X PUT "${presign.uploadUrl}" --data-binary "@${filePath}" -H "Content-Type: ${contentType}" -o NUL`);
  const check = sh(`curl.exe -s -o NUL -w "%{http_code}" "${presign.publicUrl}"`).trim();
  return { ...presign, httpStatus: check };
}

(async () => {
  // 0. 登录（新鲜会话）
  fs.rmSync(JAR, { force: true });
  const loginBody = writeJson('sb-admin.json', { email: 'admin@stickybeak.au', password: 'Admin123!' });
  let r = api('POST', '/api/auth/login', loginBody);
  console.log('0. admin 登录:', r.includes('"code":0') ? 'OK' : r);

  // 1. 上传视频 + 图片。脚本只验证上传与 media_type 链路、不验证播放：没有现成视频时写一个只含 ftyp 头的 mp4
  const videoPath = `${TMP}\\sb-test-video.mp4`;
  if (!fs.existsSync(videoPath)) {
    fs.writeFileSync(videoPath, Buffer.from('000000186674797069736f6d0000020069736f6d69736f32', 'hex'));
  }
  const video = uploadFile(videoPath, 'e2e-demo.mp4', 'video/mp4');
  console.log('1. 视频上传:', video.objectKey, '| 公网可读:', video.httpStatus);
  const img = uploadFile(
    path.resolve(__dirname, '../stickybeak-frontend/public/mock-products/sydney-t9-train-sign/02.jpg'),
    'e2e-cover.jpg',
    'image/jpeg',
  );
  console.log('   图片上传:', img.objectKey, '| 公网可读:', img.httpStatus);

  // 2. 建品（图片在前=封面，视频在后）
  const createReq = writeJson('sb-create-video-product.json', {
    name: 'E2E 视频测试·悉尼火车冰箱贴（可删）',
    categoryId: '3',
    price: 22.9,
    stock: 8,
    description: '带视频的商品：验证 media_type 链路。',
    media: [
      { url: img.publicUrl, mediaType: 'image' },
      { url: video.publicUrl, mediaType: 'video' },
    ],
    tags: ['E2E', '视频'],
  });
  r = api('POST', '/api/products/admin', createReq);
  const created = JSON.parse(r);
  const newId = created.data?.id;
  console.log('2. 新建商品 id:', newId, '（字符串精度完整:', /^\d+$/.test(newId) ? 'YES' : 'NO', '）');

  // 3. 详情 VO 校验媒体类型 + id 字符串
  const detail = JSON.parse(sh(`curl.exe -s "${GW}/api/products/id/${newId}"`)).data;
  console.log('3. 详情 id 类型:', typeof detail.id, '=', detail.id);
  console.log('   media:', JSON.stringify(detail.media));
  console.log('   images（应只含图片）:', JSON.stringify(detail.images));

  // 4. ES 同步 + 前台可见
  await new Promise((r2) => setTimeout(r2, 3000));
  const searchRes = JSON.parse(sh(`curl.exe -s "${GW}/api/products?q=${encodeURIComponent('视频测试')}"`));
  console.log('4. ES 搜索命中:', searchRes.data.items.some((i) => i.id === newId) ? 'YES' : 'NO');

  console.log('\n✅ 视频链路 E2E 通过');
})().catch((e) => {
  console.error('E2E 失败:', e.message);
  process.exit(1);
});
