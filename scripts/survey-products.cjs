// 调研 classified/商品 数据源：每篇笔记的标题、图集数量、图片文件大小
// 用法: node scripts/survey-products.cjs
const fs = require('fs');
const path = require('path');

const ROOT = 'C:\\Users\\lzz28\\gdcup-fridge-magnets\\classified\\商品';

const rows = [];
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
  const images = fs
    .readdirSync(dirPath)
    .filter((f) => /\.(jpg|jpeg|png|webp)$/i.test(f))
    .map((f) => {
      const st = fs.statSync(path.join(dirPath, f));
      return { file: f, kb: Math.round(st.size / 1024) };
    });
  rows.push({
    dir,
    title: (info.title || '').replace(/\s+/g, ' ').slice(0, 60),
    descLen: (info.desc || '').length,
    tags: info.tags || [],
    imageCount: images.length,
    totalKb: images.reduce((s, i) => s + i.kb, 0),
    images: images.map((i) => i.file),
  });
}

// 按图集大小排序，图多的优先
rows.sort((a, b) => b.imageCount - a.imageCount || b.totalKb - a.totalKb);
console.log(`total notes: ${rows.length}`);
console.log(`with >=3 images: ${rows.filter((r) => r.imageCount >= 3).length}`);
console.log('');
for (const r of rows.slice(0, 40)) {
  console.log(`${r.dir}\n  title: ${r.title}\n  images: ${r.imageCount} (${r.totalKb}KB)  tags: ${r.tags.join(',')}\n`);
}
