// 从 classified/商品 真实数据源生成前端 mock 目录：
//   - 图片复制到 stickybeak-frontend/public/mock-products/<slug>/
//   - 目录数据写到 stickybeak-frontend/src/mocks/catalog.json
// 数据结构对齐 Sprint 2 的 t_product / t_category / t_tag（docs/DATABASE_ER.md §2、§8）
// 用法: node scripts/generate-mock-catalog.cjs
const fs = require('fs');
const path = require('path');

const SRC = 'C:\\Users\\lzz28\\gdcup-fridge-magnets\\classified\\商品';
const REPO = path.resolve(__dirname, '..');
const IMG_OUT = path.join(REPO, 'stickybeak-frontend', 'public', 'mock-products');
const JSON_OUT = path.join(REPO, 'stickybeak-frontend', 'src', 'mocks', 'catalog.json');

const CATEGORIES = [
  { slug: 'bus-sign', name: '大学公交路牌', sortOrder: 1 },
  { slug: 'supermarket', name: '超市系列', sortOrder: 2 },
  { slug: 'train', name: '火车电车', sortOrder: 3 },
  { slug: 'bird', name: '小鸟路牌', sortOrder: 4 },
  { slug: 'booze', name: '酒鬼系列', sortOrder: 5 },
  { slug: 'phonecase', name: '手机壳周边', sortOrder: 6 },
];

// 精选 16 个商品（手工命名/定价/排序权重），dir = 数据源文件夹前缀
const SELECTIONS = [
  { dir: '053_', slug: 'aussie-uni-bus-stop-set', name: '土澳大学公交站牌冰箱贴套装', category: 'bus-sign', priceCents: 2990, stock: 42, featured: true, tags: ['UNSW', 'USYD', 'UQ', 'ANU', '莫那什大学'] },
  { dir: '060_', slug: 'group-of-eight-bus-signs', name: '八大名校公交路牌全套', category: 'bus-sign', priceCents: 3990, stock: 15, featured: true, tags: ['UNSW', '墨尔本大学', '莫那什大学', 'ANU'] },
  { dir: '039_', slug: 'uni-bus-sign-collection', name: '澳洲大学公交冰箱贴合集', category: 'bus-sign', priceCents: 1290, stock: 60, tags: ['UNSW', 'USYD'] },
  { dir: '085_', slug: 'campus-transit-signs', name: '校园路牌冰箱贴合集', category: 'bus-sign', priceCents: 1490, stock: 33, tags: ['UNSW', 'USYD', 'ANU'] },
  { dir: '050_', slug: 'unsw-bus-stop-sign', name: 'UNSW 校车站牌冰箱贴', category: 'bus-sign', priceCents: 990, stock: 80, tags: ['UNSW'] },
  { dir: '025_', slug: 'realistic-bus-sign', name: '仿真公交路牌冰箱贴', category: 'bus-sign', priceCents: 1090, stock: 55, tags: ['路牌'] },
  { dir: '009_', slug: 'sydney-t9-train-sign', name: '悉尼火车 T9 线路牌冰箱贴', category: 'train', priceCents: 1090, stock: 66, featured: true, tags: ['T9', '悉尼火车'] },
  { dir: '042_', slug: 'sydney-train-never-late', name: '悉尼火车冰箱贴 · 永不晚点版', category: 'train', priceCents: 990, stock: 71, tags: ['悉尼火车'] },
  { dir: '058_', slug: 'sydney-train-two-way-sign', name: '悉尼火车双向路牌冰箱贴', category: 'train', priceCents: 1190, stock: 48, tags: ['悉尼火车', '路牌'] },
  { dir: '020_', slug: 'aussie-supermarket-collection', name: '澳洲超市冰箱贴合集', category: 'supermarket', priceCents: 1390, stock: 52, featured: true, tags: ['Coles', 'WWS'] },
  { dir: '017_', slug: 'wws-supermarket-magnets', name: '窝窝屎超市周边冰箱贴', category: 'supermarket', priceCents: 890, stock: 90, tags: ['WWS'] },
  { dir: '056_', slug: 'greedy-birds', name: '贪吃小鸟冰箱贴', category: 'bird', priceCents: 990, stock: 77, tags: ['鹦鹉', '海鸥'] },
  { dir: '046_', slug: 'bird-snatchers', name: '抢食小鸟冰箱贴', category: 'bird', priceCents: 990, stock: 63, tags: ['鹦鹉'] },
  { dir: '016_', slug: 'chip-thief-seagull', name: '薯条刺客海鸥冰箱贴', category: 'bird', priceCents: 890, stock: 84, tags: ['海鸥'] },
  { dir: '089_', slug: 'booze-lovers', name: '酒鬼系列冰箱贴', category: 'booze', priceCents: 1090, stock: 39, tags: ['酒鬼'] },
  { dir: '212_', slug: 'aussie-phonecase', name: '土澳特色手机壳', category: 'phonecase', priceCents: 1690, stock: 25, tags: ['手机壳'] },
];

// 无信息量的通用标签不落到商品上
const GENERIC_TAGS = new Set([
  '澳洲生活', '澳洲好物', '冰箱贴', '万物皆可冰箱贴', '毕业礼物', '澳洲', '悉尼', '澳洲留学',
  '留学', '澳洲旅游', '悉尼生活', '好物推荐', '澳洲伴手礼', '悉尼纪念品', '悉尼伴手礼', '澳洲纪念品',
]);

function findDir(prefix) {
  const hit = fs.readdirSync(SRC).find((d) => d.startsWith(prefix));
  if (!hit) throw new Error(`dir not found: ${prefix}`);
  return hit;
}

function cleanDesc(desc) {
  if (!desc) return '';
  return desc
    .replace(/#[^#\[\]]+?\[话题\]#/g, '') // 去掉 #xx[话题]#
    .replace(/[\t\r]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
    .slice(0, 500);
}

fs.rmSync(IMG_OUT, { recursive: true, force: true });
fs.mkdirSync(IMG_OUT, { recursive: true });
fs.mkdirSync(path.dirname(JSON_OUT), { recursive: true });

const products = [];
for (const [i, sel] of SELECTIONS.entries()) {
  const dirName = findDir(sel.dir);
  const dirPath = path.join(SRC, dirName);
  const info = JSON.parse(fs.readFileSync(path.join(dirPath, 'info.json'), 'utf8'));

  // 图集：编号图最多 4 张；无编号图则退化用封面
  let files = fs.readdirSync(dirPath)
    .filter((f) => /^\d+\.(jpg|jpeg|png|webp)$/i.test(f))
    .sort()
    .slice(0, 4);
  if (files.length === 0 && fs.existsSync(path.join(dirPath, '封面.webp'))) {
    files = ['封面.webp'];
  }

  const outDir = path.join(IMG_OUT, sel.slug);
  fs.mkdirSync(outDir, { recursive: true });
  const images = files.map((f, idx) => {
    const ext = path.extname(f).toLowerCase();
    const outName = `${String(idx + 1).padStart(2, '0')}${ext}`;
    fs.copyFileSync(path.join(dirPath, f), path.join(outDir, outName));
    return `/mock-products/${sel.slug}/${outName}`;
  });

  // 只用人工精选标签：笔记原始标签噪声太多（大小写重复、营销词）
  const tags = [...new Set(sel.tags)];

  products.push({
    id: i + 1,
    slug: sel.slug,
    name: sel.name,
    description: cleanDesc(info.desc),
    category: sel.category,
    priceCents: sel.priceCents,
    stock: sel.stock,
    sales: ((info.index || i + 1) * 37) % 180 + 20,
    tags,
    images,
    sourceNoteId: info.id,
    featured: Boolean(sel.featured),
  });
  console.log(`✓ ${sel.slug}  images=${images.length}  tags=${tags.join(',')}`);
}

const catalog = { categories: CATEGORIES, products };
fs.writeFileSync(JSON_OUT, JSON.stringify(catalog, null, 2), 'utf8');
console.log(`\nproducts=${products.length}  categories=${CATEGORIES.length}`);
console.log(`images -> ${IMG_OUT}`);
console.log(`json   -> ${JSON_OUT}`);
