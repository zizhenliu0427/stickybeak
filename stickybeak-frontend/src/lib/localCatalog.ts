import type { Category, Product, ProductListResult, ProductQuery } from './catalog';
import catalog from '../mocks/catalog.json';

export function localCategories(): Category[] {
  return catalog.categories.map((c, index) => ({
    id: c.slug,
    slug: c.slug,
    name: c.name,
    sortOrder: c.sortOrder ?? index,
  }));
}

export function localProducts(): Product[] {
  return catalog.products.map((p) => ({
    id: String(p.id),
    slug: p.slug,
    name: p.name,
    description: p.description,
    category: p.category,
    priceCents: p.priceCents,
    stock: p.stock,
    sales: p.sales,
    tags: p.tags,
    images: p.images,
    media: p.images.map((url) => ({ url, mediaType: 'image' as const })),
    sourceNoteId: p.sourceNoteId,
    featured: p.featured,
  }));
}

/** 后端不可用时，详情页按 slug 从本地目录取。 */
export function localProductBySlug(slug: string): Product | null {
  return localProducts().find((p) => p.slug === slug) ?? null;
}

export function localRelated(category: string, excludeSlug: string, limit = 4): Product[] {
  return localProducts()
    .filter((p) => p.category === category && p.slug !== excludeSlug)
    .slice(0, limit);
}

/** 后端不可用时，选择页仍用本地目录筛选。 */
export function filterLocal(query: ProductQuery): ProductListResult {
  let items = localProducts();
  if (query.category) items = items.filter((p) => p.category === query.category);
  if (query.q) {
    const q = query.q.toLowerCase();
    items = items.filter((p) =>
      [p.name, p.description, p.slug, ...p.tags].join(' ').toLowerCase().includes(q),
    );
  }
  if (query.minPrice != null) items = items.filter((p) => p.priceCents >= query.minPrice!);
  if (query.maxPrice != null) items = items.filter((p) => p.priceCents <= query.maxPrice!);

  const sort = query.sort ?? 'new';
  items = [...items].sort((a, b) => {
    if (sort === 'sales') return b.sales - a.sales;
    if (sort === 'price-asc') return a.priceCents - b.priceCents;
    if (sort === 'price-desc') return b.priceCents - a.priceCents;
    return Number(b.id) - Number(a.id);
  });

  const page = query.page ?? 1;
  const size = query.size ?? 12;
  const start = (page - 1) * size;
  return {
    items: items.slice(start, start + size),
    total: items.length,
    page,
    size,
  };
}
