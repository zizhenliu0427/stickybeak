// 按 6 个种子品类对 265 篇笔记分类，输出每类图集最丰富的候选
// 用法: node scripts/categorize-products.cjs
const fs = require('fs');
const path = require('path');

const ROOT = 'C:\\Users\\lzz28\\gdcup-fridge-magnets\\classified\\商品';

const CATEGORIES = {
  'bus-sign': ['UNSW', 'USYD', 'UQ', 'ANU', 'UTS', '莫那什', '墨尔本', '麦考瑞', '公交', '巴士', '路牌', '车站', '校园'],
  'supermarket': ['Coles', 'coles', 'wws', 'Woolworths', '超市', '扣死', '窝窝屎'],
  'train': ['火车', 'T9', '电车', 'tram', 'metro', '地铁', '🚇', '🚃', '铁路'],
  'bird': ['鸟', '鹦鹉', '大葵', '海鸥', '鹈鹕', '葵花', '蒜苗鸡'],
  'booze': ['酒', '啤', 'whisky', '威士忌', '红酒', 'bar', 'Bar'],
  'phonecase': ['手机壳'],
};

const notes = [];
for (const dir of fs.readdirSync(ROOT)) {
  const dirPath = path.join(ROOT, dir);
  if (!fs.statSync(dirPath).isDirectory()) continue;
  const infoPath = path.join(dirPath, 'info.json');
  if (!fs.existsSync(infoPath)) continue;
  let info;
  try {
    info = JSON.parse(fs.readFileSync(infoPath, 'utf8'));
  } catch {
    continue;
  }
  const images = fs.readdirSync(dirPath).filter((f) => /^\d+\.(jpg|jpeg|png|webp)$/i.test(f));
  const text = `${info.title || ''} ${(info.tags || []).join(' ')} ${(info.desc || '').slice(0, 200)}`;
  notes.push({ dir, index: info.index, title: info.title || '', tags: info.tags || [], imageCount: images.length, images: images.sort(), text });
}

const buckets = {};
for (const [cat, keywords] of Object.entries(CATEGORIES)) {
  buckets[cat] = notes
    .filter((n) => keywords.some((k) => n.text.includes(k)))
    .sort((a, b) => b.imageCount - a.imageCount)
    .slice(0, 8);
}

for (const [cat, list] of Object.entries(buckets)) {
  console.log(`\n===== ${cat} (${list.length}) =====`);
  for (const n of list) {
    console.log(`  ${n.dir}`);
    console.log(`    title: ${n.title.replace(/\s+/g, ' ').slice(0, 50)}  images=${n.imageCount}`);
  }
}
