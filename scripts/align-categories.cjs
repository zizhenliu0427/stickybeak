// 品类对齐 dry-run / 执行工具：分类索引.json × t_product.source_note_id
// 用法:
//   node scripts/align-categories.cjs          # dry-run，只出报告和 SQL 文件
//   node scripts/align-categories.cjs --apply  # 生成 SQL 并应用到本地 MySQL 容器
const { execSync } = require('child_process');
const fs = require('fs');

const INDEX_JSON = 'C:\\Users\\lzz28\\gdcup-fridge-magnets\\classified\\分类索引.json';
const TMP = process.env.TEMP;

// ---- 新类目体系（slug 复用现有可对应的，前端硬编码链接不断）----
const NEW_CATEGORIES = [
  { slug: 'bus-sign', name: '校园公交路牌', sort: 1 },
  { slug: 'supermarket', name: '超市冰箱贴', sort: 2 },
  { slug: 'train', name: '火车地铁路牌', sort: 3 },
  { slug: 'tram', name: '电车路牌', sort: 4 },
  { slug: 'bird', name: '小鸟冰箱贴', sort: 5 },
  { slug: 'road-sign', name: '交通指示牌', sort: 6 },
  { slug: 'collections', name: '商品合集', sort: 7 },
  { slug: 'achievement', name: '留子成就', sort: 8 },
  { slug: 'slogan', name: '生活标语', sort: 9 },
  { slug: 'phonecase', name: '手机壳', sort: 10 },
];
// 旧类目归宿：booze 并入 road-sign（其余 5 个保留 slug 改名）
const RETIRE_SLUGS = ['booze'];

// ---- 读取分类索引（商品 bucket）----
const index = JSON.parse(fs.readFileSync(INDEX_JSON, 'utf8'));
const productBuckets = index.filter((b) => b.bucket === '商品');
const noteToCategory = new Map(); // noteId -> 子品类名（分类索引里的中文名）
for (const b of productBuckets) {
  for (const n of b.notes) {
    noteToCategory.set(n.id, b.category);
  }
}
// 分类索引中文名 → 新 slug
const CN_TO_SLUG = {
  '校园公交路牌': 'bus-sign',
  '超市冰箱贴': 'supermarket',
  '火车地铁路牌': 'train',
  '电车路牌': 'tram',
  '小鸟冰箱贴': 'bird',
  '交通指示牌': 'road-sign',
  '商品合集': 'collections',
  '留子成就': 'achievement',
  '生活标语': 'slogan',
  '手机壳': 'phonecase',
};
for (const cn of Object.keys(CN_TO_SLUG)) {
  if (![...noteToCategory.values()].includes(cn)) {
    console.log(`⚠️ 分类索引里没有用到品类「${cn}」`);
  }
}

// ---- 读店铺现状 ----
function mysql(sql) {
  const f = `${TMP}\\sb-align.sql`;
  fs.writeFileSync(f, 'SET NAMES utf8mb4;\n' + sql, 'utf8');
  execSync(`docker cp "${f}" sb-mysql:/tmp/sb-align.sql`);
  return execSync(`docker exec sb-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 -N -B < /tmp/sb-align.sql"`, {
    maxBuffer: 32 * 1024 * 1024,
  }).toString('utf8');
}

const prodRows = mysql(`SELECT id, source_note_id, category_id FROM stickybeak_product.t_product WHERE is_deleted=0;`)
  .trim().split('\n').map((l) => {
    const [id, noteId, catId] = l.split('\t');
    return { id, noteId, catId };
  });
const catRows = mysql(`SELECT id, slug, name FROM stickybeak_product.t_category;`)
  .trim().split('\n').map((l) => {
    const [id, slug, name] = l.split('\t');
    return { id: Number(id), slug, name };
  });
const catIdBySlug = new Map(catRows.map((c) => [c.slug, c.id]));

console.log(`店铺商品: ${prodRows.length} | 分类索引商品笔记: ${noteToCategory.size}`);

// ---- 差异分析 ----
const shopNoteIds = new Set(prodRows.map((p) => p.noteId));
const missingInShop = [...noteToCategory.keys()].filter((id) => !shopNoteIds.has(id));
const inShopNotProduct = prodRows.filter((p) => !noteToCategory.has(p.noteId));
console.log(`\n[分类索引有 / 店铺没有]（漏导入）: ${missingInShop.length}`);
for (const id of missingInShop) {
  console.log(`  ${id} → ${noteToCategory.get(id)}`);
}
console.log(`[店铺有 / 分类索引非商品]（需关注）: ${inShopNotProduct.length}`);
for (const p of inShopNotProduct) {
  console.log(`  product ${p.id} note=${p.noteId} 当前类目id=${p.catId}`);
}

// ---- 生成迁移 SQL ----
const slugToNewCatId = new Map(); // slug → id（新分配或复用）
const lines = [];
lines.push('-- 品类对齐迁移：新 10 子品类 + 商品按 source_note_id 重映射');
lines.push('SET NAMES utf8mb4;');
lines.push('USE stickybeak_product;');
lines.push('');

// 类目 upsert：slug UK 存在则改名+改排序，不存在则插入
let nextId = Math.max(...catRows.map((c) => c.id)) + 1;
for (const c of NEW_CATEGORIES) {
  if (catIdBySlug.has(c.slug)) {
    slugToNewCatId.set(c.slug, catIdBySlug.get(c.slug));
    lines.push(`UPDATE t_category SET name='${c.name}', sort_order=${c.sort}, is_deleted=0 WHERE slug='${c.slug}';`);
  } else {
    slugToNewCatId.set(c.slug, nextId);
    lines.push(`INSERT INTO t_category (id, name, slug, parent_id, sort_order, is_deleted) VALUES (${nextId}, '${c.name}', '${c.slug}', NULL, ${c.sort}, 0);`);
    nextId++;
  }
}
lines.push('');
for (const slug of RETIRE_SLUGS) {
  lines.push(`UPDATE t_category SET is_deleted=1 WHERE slug='${slug}'; -- 并入交通指示牌`);
}
lines.push('');
lines.push('-- 商品重映射（source_note_id → 新类目）');
let remapCount = 0;
let skipped = 0;
for (const p of prodRows) {
  const cn = noteToCategory.get(p.noteId);
  if (!cn) {
    skipped++;
    continue;
  }
  const slug = CN_TO_SLUG[cn];
  const newCatId = slugToNewCatId.get(slug);
  if (String(newCatId) !== String(p.catId)) {
    lines.push(`UPDATE t_product SET category_id=${newCatId} WHERE id=${p.id}; -- ${cn}`);
    remapCount++;
  }
}

const sqlPath = `${TMP}\\sb-align-migration.sql`;
fs.writeFileSync(sqlPath, lines.join('\n'), 'utf8');

console.log(`\n迁移计划：类目更新/新增 ${NEW_CATEGORIES.length} 个，退役 ${RETIRE_SLUGS.length} 个（booze→交通指示牌）`);
console.log(`商品重映射 ${remapCount} 条（${skipped} 条无新类目跳过），${prodRows.length - remapCount - skipped} 条无需变动`);
console.log(`SQL 已写入 ${sqlPath}`);

// 新体系下的类目分布预览
const dist = {};
for (const p of prodRows) {
  const cn = noteToCategory.get(p.noteId);
  if (cn) dist[cn] = (dist[cn] || 0) + 1;
}
console.log('\n对齐后类目分布:');
for (const c of NEW_CATEGORIES) {
  const cn = Object.keys(CN_TO_SLUG).find((k) => CN_TO_SLUG[k] === c.slug);
  console.log(`  ${c.name}(${c.slug}): ${dist[cn] || 0}`);
}

if (process.argv.includes('--apply')) {
  console.log('\n=== 应用迁移 ===');
  execSync(`docker cp "${sqlPath}" sb-mysql:/tmp/sb-align-migration.sql`);
  execSync(`docker exec sb-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 < /tmp/sb-align-migration.sql"`);
  console.log('迁移已应用');
  console.log('\n=== 验证：新类目分布 ===');
  console.log(mysql(`SELECT c.name, c.slug, COUNT(p.id) AS cnt FROM stickybeak_product.t_category c LEFT JOIN stickybeak_product.t_product p ON p.category_id=c.id AND p.is_deleted=0 WHERE c.is_deleted=0 GROUP BY c.id ORDER BY c.sort_order;`));
}
