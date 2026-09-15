import catalogJson from '../mocks/catalog.json';

/**
 * 商品目录服务层。
 * 当前实现：本地 mock（真实 GDCUP 数据，scripts/generate-mock-catalog.cjs 生成）。
 * Sprint 2 后端就绪后，把函数体换成 lib/api 的 get() 调用即可，签名不变：
 *   listProducts  → GET /api/products?category&tags&minPrice&maxPrice&sort&page&q
 *   getProductBySlug → GET /api/products/:slug
 *   listCategories → GET /api/categories
 */

export interface Category {
  slug: string;
  name: string;
  sortOrder: number;
}

export interface Product {
  id: number;
  slug: string;
  name: string;
  description: string;
  category: string;
  /** 价格（AUD  cents）——硬性规范：金额一律 cents 传输 */
  priceCents: number;
  stock: number;
  sales: number;
  tags: string[];
  images: string[];
  sourceNoteId: string;
  featured: boolean;
}

export interface ProductQuery {
  category?: string;
  /** 逗号分隔，任一命中 */
  tags?: string;
  minPrice?: number;
  maxPrice?: number;
  /** new | sales | price-asc | price-desc */
  sort?: string;
  q?: string;
  page?: number;
  size?: number;
}

export interface ProductListResult {
  items: Product[];
  total: number;
  page: number;
  size: number;
}

const catalog = catalogJson as unknown as { categories: Category[]; products: Product[] };

/** 模拟网络延迟，让骨架屏可见 */
function latency(): Promise<void> {
  return new Promise((r) => setTimeout(r, 120 + Math.random() * 180));
}

export async function listProducts(query: ProductQuery = {}): Promise<ProductListResult> {
  await latency();
  const { category, tags, minPrice, maxPrice, sort = 'new', q, page = 1, size = 12 } = query;

  let items = catalog.products.slice();

  if (category) {
    items = items.filter((p) => p.category === category);
  }
  if (tags) {
    const wanted = tags.split(',').map((t) => t.trim().toLowerCase());
    items = items.filter((p) => p.tags.some((t) => wanted.includes(t.toLowerCase())));
  }
  if (minPrice != null) {
    items = items.filter((p) => p.priceCents >= minPrice);
  }
  if (maxPrice != null) {
    items = items.filter((p) => p.priceCents <= maxPrice);
  }
  if (q) {
    const needle = q.toLowerCase();
    items = items.filter(
      (p) =>
        p.name.toLowerCase().includes(needle) ||
        p.description.toLowerCase().includes(needle) ||
        p.tags.some((t) => t.toLowerCase().includes(needle)),
    );
  }

  switch (sort) {
    case 'sales':
      items.sort((a, b) => b.sales - a.sales);
      break;
    case 'price-asc':
      items.sort((a, b) => a.priceCents - b.priceCents);
      break;
    case 'price-desc':
      items.sort((a, b) => b.priceCents - a.priceCents);
      break;
    default: // new —— mock 用 id 倒序近似
      items.sort((a, b) => b.id - a.id);
  }

  const total = items.length;
  const start = (page - 1) * size;
  return { items: items.slice(start, start + size), total, page, size };
}

export async function getProductBySlug(slug: string): Promise<Product | null> {
  await latency();
  return catalog.products.find((p) => p.slug === slug) ?? null;
}

export async function listCategories(): Promise<Category[]> {
  await latency();
  return catalog.categories.slice().sort((a, b) => a.sortOrder - b.sortOrder);
}

/** 首页精选 */
export async function listFeatured(): Promise<Product[]> {
  await latency();
  return catalog.products.filter((p) => p.featured);
}

/** 同类推荐（详情页） */
export async function listRelated(category: string, excludeSlug: string, limit = 4): Promise<Product[]> {
  await latency();
  return catalog.products
    .filter((p) => p.category === category && p.slug !== excludeSlug)
    .slice(0, limit);
}
