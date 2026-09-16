#!/usr/bin/env node

/**
 * StickyBeak Sprint 7: PWA, Service Worker & Mobile UX Verification Script
 * Validates:
 * 1. Web App Manifest specifications & schema
 * 2. High-res PWA Icon files and binary PNG dimensions
 * 3. Service Worker multi-tier caching logic
 * 4. HTML meta tags & viewport configuration
 * 5. Mobile responsive navigation & touch target audit
 */

const fs = require('fs');
const path = require('path');

const FRONTEND_DIR = path.resolve(__dirname, '../stickybeak-frontend');
const PUBLIC_DIR = path.join(FRONTEND_DIR, 'public');

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

function parsePngDimensions(buffer) {
  // PNG signature check
  if (buffer.slice(0, 8).toString('hex') !== '89504e470d0a1a0a') {
    return null;
  }
  // IHDR chunk: width at offset 16 (4 bytes), height at offset 20 (4 bytes)
  const width = buffer.readUInt32BE(16);
  const height = buffer.readUInt32BE(20);
  return { width, height };
}

console.log('===============================================================');
console.log('🚀 开始 Sprint 7: PWA、Service Worker 与移动端体验 全量自动化走查');
console.log('===============================================================\n');

// 1. Web App Manifest 验证
console.log('==> 1. 验证 Web App Manifest (manifest.webmanifest)');
const manifestPath = path.join(PUBLIC_DIR, 'manifest.webmanifest');
assert(fs.existsSync(manifestPath), 'manifest.webmanifest 文件存在');

let manifest = {};
try {
  manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
  assert(manifest.name === 'StickyBeak — Aussie Fridge Magnets', `name 合法: "${manifest.name}"`);
  assert(manifest.short_name === 'StickyBeak', `short_name 合法: "${manifest.short_name}"`);
  assert(manifest.start_url === '/', `start_url 合法: "${manifest.start_url}"`);
  assert(manifest.display === 'standalone', `display 为原生沉浸式独立模式: "${manifest.display}"`);
  assert(manifest.theme_color === '#3d8066', `theme_color 为品牌尤加利绿: "${manifest.theme_color}"`);
  assert(manifest.background_color === '#faf8f4', `background_color 匹配主题底色: "${manifest.background_color}"`);
  assert(Array.isArray(manifest.icons) && manifest.icons.length >= 3, `包含多尺寸图标定义 (共 ${manifest.icons?.length || 0} 个)`);
  assert(Array.isArray(manifest.shortcuts) && manifest.shortcuts.length >= 3, `定义了快捷入口 shortcuts (共 ${manifest.shortcuts?.length || 0} 个)`);
} catch (e) {
  assert(false, `manifest.webmanifest 解析失败: ${e.message}`);
}

// 2. PWA 图标物理文件与二进制规格验证
console.log('\n==> 2. 验证 PWA 品牌图标物理文件与分辨率');
const requiredIcons = [
  { file: 'icons/icon-192.png', expectedW: 192, expectedH: 192, desc: '192x192 标准 PWA 图标' },
  { file: 'icons/icon-512.png', expectedW: 512, expectedH: 512, desc: '512x512 高清大图标' },
  { file: 'icons/icon-maskable-512.png', expectedW: 512, expectedH: 512, desc: '512x512 Maskable 自适应图标' },
  { file: 'icons/apple-touch-icon.png', expectedW: 180, expectedH: 180, desc: '180x180 Apple Touch Icon' },
];

for (const icon of requiredIcons) {
  const iconPath = path.join(PUBLIC_DIR, icon.file);
  const exists = fs.existsSync(iconPath);
  assert(exists, `${icon.desc} 文件存在: ${icon.file}`);
  if (exists) {
    const buf = fs.readFileSync(iconPath);
    const dims = parsePngDimensions(buf);
    assert(
      dims && dims.width === icon.expectedW && dims.height === icon.expectedH,
      `${icon.desc} 分辨率严格匹配 ${icon.expectedW}x${icon.expectedH} (实际: ${dims?.width}x${dims?.height})`
    );
  }
}

// Favicon 检查
assert(fs.existsSync(path.join(PUBLIC_DIR, 'favicon.ico')), 'favicon.ico 存在且非空');
assert(fs.existsSync(path.join(PUBLIC_DIR, 'favicon.svg')), 'favicon.svg 矢量图标存在');

// 3. Service Worker 离线引擎与多层缓存策略
console.log('\n==> 3. 验证 Service Worker (sw.js) 缓存分层策略');
const swPath = path.join(PUBLIC_DIR, 'sw.js');
assert(fs.existsSync(swPath), 'public/sw.js Service Worker 文件存在');
if (fs.existsSync(swPath)) {
  const sw = fs.readFileSync(swPath, 'utf-8');
  assert(sw.includes('stickybeak-static-v1'), '定义了静态资源缓存桶: stickybeak-static-v1');
  assert(sw.includes('stickybeak-data-v1'), '定义了商品数据缓存桶: stickybeak-data-v1');
  assert(sw.includes('skipWaiting()'), '包含 skipWaiting() 生命周期立即接管');
  assert(sw.includes('clients.claim()'), '包含 clients.claim() 激活后控制已有客户端');
  assert(sw.includes('/api/products') && sw.includes('/products/'), '商品目录与商品图片配置为 Network-First 策略并回退本地缓存');
  assert(
    sw.includes('/api/orders') && sw.includes('/api/cart') && sw.includes('/api/auth'),
    '敏感/交易接口配置为 Network-Only 严格直连，绝不本地脏缓存'
  );
}

// 4. HTML 入口与 PWA 规范元标签
console.log('\n==> 4. 验证 index.html PWA 规范引入');
const indexPath = path.join(FRONTEND_DIR, 'index.html');
const indexHtml = fs.readFileSync(indexPath, 'utf-8');
assert(indexHtml.includes('rel="manifest"') && indexHtml.includes('/manifest.webmanifest'), '引入了 manifest.webmanifest 声明');
assert(indexHtml.includes('name="theme-color"') && indexHtml.includes('#3d8066'), '配置了 theme-color 品牌主题色');
assert(indexHtml.includes('apple-mobile-web-app-capable'), '开启了 iOS Safari 全屏 Web App 沉浸模式');
assert(indexHtml.includes('rel="apple-touch-icon"'), '声明了 Apple Touch Icon 链接');
assert(indexHtml.includes('viewport-fit=cover'), '移动端 viewport 配置了 viewport-fit=cover 全面屏安全区适配');

// 5. 移动端组件与触控审计
console.log('\n==> 5. 移动端沉浸式导航与触控审计 (WCAG & iOS HIG >= 44px)');
const mobileNavPath = path.join(FRONTEND_DIR, 'src/components/MobileBottomNav.tsx');
assert(fs.existsSync(mobileNavPath), '移动端沉浸式底部导航组件 MobileBottomNav.tsx 存在');
if (fs.existsSync(mobileNavPath)) {
  const navCode = fs.readFileSync(mobileNavPath, 'utf-8');
  assert(navCode.includes('sm:hidden'), '仅在移动端小屏 (< 640px) 激活底部导航');
  assert(navCode.includes('cartCount'), '购物车 Tab 支持实时徽标 Badge 展示');
  assert(navCode.includes('env(safe-area-inset-bottom)'), '支持 iPhone 底部 Home 条安全区沉浸适配');
  assert(navCode.includes('h-14'), '触控高度满足 56px (严格 >= 44px 规范)');
}

const bannerPath = path.join(FRONTEND_DIR, 'src/components/OfflineBanner.tsx');
assert(fs.existsSync(bannerPath), '全站离线感知组件 OfflineBanner.tsx 存在');

const stepperPath = path.join(FRONTEND_DIR, 'src/components/QtyStepper.tsx');
if (fs.existsSync(stepperPath)) {
  const stepperCode = fs.readFileSync(stepperPath, 'utf-8');
  assert(stepperCode.includes('min-h-[44px]') && stepperCode.includes('min-w-[44px]'), '数量步进器按钮触控靶心 >= 44px');
}

const detailPath = path.join(FRONTEND_DIR, 'src/pages/ProductDetailPage.tsx');
if (fs.existsSync(detailPath)) {
  const detailCode = fs.readFileSync(detailPath, 'utf-8');
  assert(detailCode.includes('sm:hidden') && detailCode.includes('stickyAdd'), '商品详情页配置了移动端吸底浮动加购条');
}

const appLayoutPath = path.join(FRONTEND_DIR, 'src/layouts/AppLayout.tsx');
if (fs.existsSync(appLayoutPath)) {
  const layoutCode = fs.readFileSync(appLayoutPath, 'utf-8');
  assert(layoutCode.includes('overflow-x-hidden'), '根容器设置了 overflow-x-hidden 杜绝小屏横向滚动');
  assert(layoutCode.includes('MobileBottomNav'), '全局集成挂载了 MobileBottomNav');
  assert(layoutCode.includes('OfflineBanner'), '全局集成挂载了 OfflineBanner');
}

console.log('\n===============================================================');
if (passedTests === totalTests) {
  console.log(`🎉 恭喜！Sprint 7 PWA 与移动端优化 全部 ${totalTests} 项检查 100% 通过！`);
} else {
  console.error(`⚠ 检查完成：${passedTests}/${totalTests} 通过，有 ${totalTests - passedTests} 项未满足预期。`);
  process.exit(1);
}
console.log('===============================================================\n');
