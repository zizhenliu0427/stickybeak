// 故事线 9.4 演示：Nacos 多环境配置隔离 + 动态刷新（免重启）
// 流程：读当前值 → 发布 dev 配置 → 观察热更新 → 建 prod 命名空间并发布 prod 配置（隔离证据）
// 用法: node scripts/demo-nacos-multi-env.cjs
const { execSync } = require('child_process');
const fs = require('fs');

const TMP = process.env.TEMP;
const NACOS = 'http://localhost:8848';
const API = 'http://localhost:8080/api/products/config-demo';

function sh(cmd) {
  return execSync(cmd, { maxBuffer: 16 * 1024 * 1024 }).toString('utf8');
}

function getDemo() {
  const out = sh(`curl.exe -s "${API}"`);
  return JSON.parse(out).data;
}

function publishConfig(dataId, content, namespace) {
  // 注意：Windows 上 curl --data-urlencode content@file 会用控制台代码页(GBK)读文件导致中文乱码；
  // 这里在 node 里完成 URL 编码（纯 ASCII 传输），彻底规避。
  const body = `content=${encodeURIComponent(content)}`;
  const nsArg = namespace ? `&tenant=${namespace}` : '';
  const cmd = `curl.exe -s -X POST "${NACOS}/nacos/v1/cs/configs?dataId=${dataId}&group=DEFAULT_GROUP&type=yml${nsArg}" -H "Content-Type: application/x-www-form-urlencoded;charset=UTF-8" --data-raw "${body}"`;
  return sh(cmd).trim();
}

console.log('=== 1. 当前生效值（本地默认或 Nacos 已有配置）===');
console.log(JSON.stringify(getDemo()));

// 2. 发布 dev 配置（v1）
const devV1 = `app:\n  demo:\n    store-banner: "🦘 DEV 环境横幅 v1 —— 来自 Nacos 公共命名空间"\n    list-cache-ttl-seconds: 60\n`;
console.log('\n=== 2. 发布 stickybeak-product-dev.yml（v1）===', publishConfig('stickybeak-product-dev.yml', devV1));

// 3. 轮询等待热更新
let demo = null;
for (let i = 0; i < 15; i++) {
  execSync('powershell -Command "Start-Sleep -Milliseconds 1500"');
  demo = getDemo();
  if (String(demo.storeBanner).includes('v1')) break;
}
console.log('=== 3. 热更新后（未重启服务）===');
console.log(JSON.stringify(demo));
if (!String(demo.storeBanner).includes('v1')) {
  console.log('!! 热更新未生效，检查 @RefreshScope / Nacos dataId');
  process.exit(1);
}

// 4. 再改一版（v2），证明可反复动态刷新
const devV2 = devV1.replace('v1', 'v2').replace('60', '120');
console.log('\n=== 4. 发布 v2 ===', publishConfig('stickybeak-product-dev.yml', devV2));
for (let i = 0; i < 15; i++) {
  execSync('powershell -Command "Start-Sleep -Milliseconds 1500"');
  demo = getDemo();
  if (String(demo.storeBanner).includes('v2')) break;
}
console.log('热更新 v2:', JSON.stringify(demo));

// 5. 建 prod 命名空间并发布 prod 配置（同 dataId 不同值 = 环境隔离）
console.log('\n=== 5. 创建 prod 命名空间 ===');
let nsId = '';
try {
  sh(`curl.exe -s -X POST "${NACOS}/nacos/v1/console/namespaces?customNamespaceId=prod&namespaceName=prod&namespaceDesc=prod env"`);
} catch { /* 已存在则忽略 */ }
const nsList = JSON.parse(sh(`curl.exe -s "${NACOS}/nacos/v1/console/namespaces"`));
const prod = nsList.data.find((n) => n.namespaceShowName === 'prod' || n.namespace === 'prod');
nsId = prod ? prod.namespace : 'prod';
console.log('prod namespace id:', nsId);

const prodCfg = `app:\n  demo:\n    store-banner: "🚀 PROD 环境横幅 —— 来自 prod 命名空间"\n    list-cache-ttl-seconds: 300\n`;
console.log('发布 prod 配置:', publishConfig('stickybeak-product-prod.yml', prodCfg, nsId));

console.log('\n=== 完成 ===');
console.log('dev 环境当前值:', JSON.stringify(getDemo()));
console.log(`环境隔离验证方式：重启 product 服务并设 NACOS_NAMESPACE=${nsId} SPRING_PROFILES_ACTIVE=prod，`);
console.log('该端点将显示 prod 命名空间 + prod profile 的值（300s TTL）。');
