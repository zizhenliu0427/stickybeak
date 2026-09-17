/**
 * 商品目录服务层。
 * Sprint 2：从 mock 切换到真实后端 API。
 *   listProducts  → GET /api/products?category&tags&minPrice&maxPrice&sort&page&size&q
 *   getProductBySlug → GET /api/products/:slug
 *   listCategories → GET /api/categories
 *   listFeatured   → GET /api/products/featured
 *   listRelated    → GET /api/products/:slug/related
 */

import { get } from './api';

export interface Category {
  id: string;
  slug: string;
  name: string;
  sortOrder: number;
}

export interface Product {
  /** 雪花 ID，后端 Long 序列化为字符串（防 JS 精度丢失） */
  id: string;
  slug: string;
  name: string;
  description: string;
  category: string;
  /** 价格（AUD  cents）——硬性规范：金额一律 cents 传输 */
  priceCents: number;
  stock: number;
  sales: number;
  tags: string[];
  /** 仅图片 URL（卡片/封面安全） */
  images: string[];
  /** 全量媒体（含视频），详情页画廊用 */
  media: { url: string; mediaType: 'image' | 'video' }[];
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

export async function listProducts(query: ProductQuery = {}): Promise<ProductListResult> {
  const params: Record<string, unknown> = {};
  if (query.category) params.category = query.category;
  if (query.tags) params.tags = query.tags;
  if (query.minPrice != null) params.minPrice = query.minPrice;
  if (query.maxPrice != null) params.maxPrice = query.maxPrice;
  if (query.sort) params.sort = query.sort;
  if (query.q) params.q = query.q;
  params.page = query.page ?? 1;
  params.size = query.size ?? 12;

  const raw = await get<ProductListResult>('/products', params);
  // 后端 Long 全局序列化为字符串：total 是计数不是 ID，转回 number
  return { ...raw, total: Number(raw.total) };
}

export async function getProductBySlug(slug: string): Promise<Product | null> {
  try {
    return await get<Product>(`/products/${slug}`);
  } catch {
    return null;
  }
}

export async function listCategories(): Promise<Category[]> {
  return get<Category[]>('/categories');
}

/** 首页精选 */
export async function listFeatured(): Promise<Product[]> {
  return get<Product[]>('/products/featured');
}

/** 同类推荐（详情页） */
export async function listRelated(_category: string, excludeSlug: string, limit = 4): Promise<Product[]> {
  return get<Product[]>(`/products/${excludeSlug}/related`, { limit });
}
