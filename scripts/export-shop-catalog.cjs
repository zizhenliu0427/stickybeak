// 把店里当前在售的 SKU 导出成前端离线目录（后端不可用时 localCatalog 用它兜底）：
//   - 数据来自 sb-mysql 容器里的 stickybeak_product（status=0 的在售商品 + 全部有效类目）
//   - 写到 stickybeak-frontend/src/mocks/catalog.json，结构与 generate-mock-catalog.cjs 的产物一致
//   - 图片 URL 原样保留（/products/<noteId>/xx.jpg 已在前端 public 里）
// SKU 合并、上下架、改价之后重跑一次，保持兜底目录与线上一致。
// 用法: node scripts/export-shop-catalog.cjs
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const REPO = path.resolve(__dirname, '..');
const JSON_OUT = path.join(REPO, 'stickybeak-frontend', 'src', 'mocks', 'catalog.json');
const CONTAINER = process.env.MYSQL_CONTAINER || 'sb-mysql';
const PASSWORD = process.env.MYSQL_ROOT_PASSWORD || 'root';

/** 每行一个 JSON 对象（MySQL 8 JSON_OBJECT），避免 TSV 转义中文/换行的问题 */
function queryJson(sql) {
  const out = execFileSync(
    'docker',
    ['exec', CONTAINER, 'mysql', '-uroot', `-p${PASSWORD}`, '--default-character-set=utf8mb4',
      '-N', '-B', '--raw', 'stickybeak_product', '-e', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 32 * 1024 * 1024 },
  );
  return out.split('\n').filter(Boolean).map((line) => JSON.parse(line));
}

const categories = queryJson(`
  SELECT JSON_OBJECT('slug', slug, 'name', name, 'sortOrder', sort_order)
  FROM t_category WHERE is_deleted = 0 ORDER BY sort_order, id`);

const products = queryJson(`
  SELECT JSON_OBJECT(
    'id', p.id,
    'slug', p.slug,
    'name', p.name,
    'description', COALESCE(p.description, ''),
    'category', c.slug,
    'priceCents', CAST(ROUND(p.price * 100) AS UNSIGNED),
    'stock', p.stock,
    'sales', p.sales,
    'sourceNoteId', COALESCE(p.source_note_id, ''),
    'featured', p.featured = 1
  )
  FROM t_product p JOIN t_category c ON c.id = p.category_id
  WHERE p.status = 0 AND p.is_deleted = 0
  ORDER BY p.id`);

const images = queryJson(`
  SELECT JSON_OBJECT('productId', i.product_id, 'url', i.url)
  FROM t_product_image i JOIN t_product p ON p.id = i.product_id
  WHERE p.status = 0 AND p.is_deleted = 0 AND i.is_deleted = 0 AND i.media_type = 'image'
  ORDER BY i.product_id, i.is_cover DESC, i.sort_order, i.id`);

const tags = queryJson(`
  SELECT JSON_OBJECT('productId', r.product_id, 'name', t.name)
  FROM t_product_tag_rel r
  JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
  JOIN t_product p ON p.id = r.product_id
  WHERE p.status = 0 AND p.is_deleted = 0
  ORDER BY r.product_id, t.id`);

const byProduct = (rows, key) => {
  const map = new Map();
  for (const row of rows) {
    const list = map.get(String(row.productId)) ?? [];
    list.push(row[key]);
    map.set(String(row.productId), list);
  }
  return map;
};
const imageMap = byProduct(images, 'url');
const tagMap = byProduct(tags, 'name');

const catalog = {
  categories,
  products: products.map((p) => ({
    ...p,
    featured: Boolean(p.featured),
    tags: tagMap.get(String(p.id)) ?? [],
    images: imageMap.get(String(p.id)) ?? [],
  })),
};

const missing = catalog.products
  .flatMap((p) => p.images)
  .filter((url) => url.startsWith('/') && !fs.existsSync(path.join(REPO, 'stickybeak-frontend', 'public', url)));
if (missing.length > 0) {
  console.warn(`warning: ${missing.length} image(s) not found under public/, e.g. ${missing[0]}`);
}

fs.writeFileSync(JSON_OUT, `${JSON.stringify(catalog, null, 2)}\n`, 'utf8');
console.log(`wrote ${path.relative(REPO, JSON_OUT)}: ${catalog.categories.length} categories, ${catalog.products.length} products`);
