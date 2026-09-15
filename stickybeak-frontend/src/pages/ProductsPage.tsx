import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Empty, Input, Pagination, Select, Slider } from 'antd';
import ProductCard from '../components/ProductCard';
import ProductGridSkeleton from '../components/ProductGridSkeleton';
import { listCategories, listProducts } from '../lib/catalog';
import type { Category, ProductListResult } from '../lib/catalog';
import { useAppSelector } from '../app/hooks';
import { formatPrice } from '../lib/format';

const SORT_OPTIONS = [
  { value: 'new', label: '最新' },
  { value: 'sales', label: '销量优先' },
  { value: 'price-asc', label: '价格从低到高' },
  { value: 'price-desc', label: '价格从高到低' },
];

const PAGE_SIZE = 12;
/** 价格筛选上限（AUD cents） */
const PRICE_CAP = 4000;

/**
 * 商品列表：筛选条件全部同步 URL query（可分享）。
 * mock 阶段由本地目录服务过滤；Sprint 2 切换为 GET /api/products 同参。
 */
export default function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [categories, setCategories] = useState<Category[]>([]);
  const [result, setResult] = useState<ProductListResult | null>(null);
  const [loading, setLoading] = useState(true);
  const currency = useAppSelector((s) => s.currency.currency);
  const rate = useAppSelector((s) => s.currency.audToCny);

  // 从 URL 读取当前筛选
  const query = useMemo(
    () => ({
      category: searchParams.get('category') ?? undefined,
      q: searchParams.get('q') ?? undefined,
      sort: searchParams.get('sort') ?? 'new',
      minPrice: searchParams.get('minPrice') ? Number(searchParams.get('minPrice')) : undefined,
      maxPrice: searchParams.get('maxPrice') ? Number(searchParams.get('maxPrice')) : undefined,
      page: Number(searchParams.get('page') ?? 1),
      size: PAGE_SIZE,
    }),
    [searchParams],
  );

  useEffect(() => {
    listCategories().then(setCategories);
  }, []);

  useEffect(() => {
    setLoading(true);
    listProducts(query)
      .then(setResult)
      .finally(() => setLoading(false));
  }, [query]);

  const patchParams = (patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams);
    for (const [k, v] of Object.entries(patch)) {
      if (v === undefined || v === '') {
        next.delete(k);
      } else {
        next.set(k, v);
      }
    }
    // 改筛选时回到第一页（翻页本身除外）
    if (!('page' in patch)) {
      next.delete('page');
    }
    setSearchParams(next);
  };

  const priceValue: [number, number] = [
    Math.round(((query.minPrice ?? 0) / 100) * (currency === 'CNY' ? rate : 1)),
    Math.round(((query.maxPrice ?? PRICE_CAP) / 100) * (currency === 'CNY' ? rate : 1)),
  ];

  return (
    <div className="space-y-5">
      {/* 工具栏 */}
      <div className="flex flex-wrap items-center gap-3">
        <Input.Search
          placeholder="搜商品 / 标签，如 T9、海鸥"
          allowClear
          defaultValue={query.q}
          onSearch={(v) => patchParams({ q: v || undefined })}
          className="max-w-64"
        />
        <Select
          value={query.sort}
          options={SORT_OPTIONS}
          onChange={(v) => patchParams({ sort: v === 'new' ? undefined : v })}
          className="w-36"
        />
        <div className="flex items-center gap-2 text-xs text-gray-500">
          <span>价格</span>
          <Slider
            range
            min={0}
            max={Math.round((PRICE_CAP / 100) * (currency === 'CNY' ? rate : 1))}
            value={priceValue}
            onChange={(v) => {
              // 展示币种 → 转回 AUD cents 存 URL（URL 永远以 AUD 为准）
              const toAudCents = (display: number) =>
                Math.round((display * 100) / (currency === 'CNY' ? rate : 1));
              patchParams({
                minPrice: v[0] > 0 ? String(toAudCents(v[0])) : undefined,
                maxPrice: v[1] < priceValue[1] || query.maxPrice ? String(toAudCents(v[1])) : undefined,
              });
            }}
            className="w-40"
            tooltip={{
              formatter: (v) => formatPrice(Math.round((v ?? 0) * 100), currency),
            }}
          />
        </div>
      </div>

      {/* 品类 tab */}
      <div className="flex flex-wrap gap-2">
        <button
          onClick={() => patchParams({ category: undefined })}
          className={`rounded-full px-4 py-1.5 text-sm transition-colors ${
            !query.category
              ? 'bg-brand-600 text-white'
              : 'border border-sand-200 bg-white text-gray-600 hover:border-brand-300'
          }`}
        >
          全部
        </button>
        {categories.map((c) => (
          <button
            key={c.slug}
            onClick={() => patchParams({ category: c.slug })}
            className={`rounded-full px-4 py-1.5 text-sm transition-colors ${
              query.category === c.slug
                ? 'bg-brand-600 text-white'
                : 'border border-sand-200 bg-white text-gray-600 hover:border-brand-300'
            }`}
          >
            {c.name}
          </button>
        ))}
      </div>

      {/* 结果 */}
      {loading ? (
        <ProductGridSkeleton count={8} />
      ) : !result || result.items.length === 0 ? (
        <Empty description="没有符合条件的商品" className="py-16" />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {result.items.map((p) => (
              <ProductCard key={p.slug} product={p} />
            ))}
          </div>
          <div className="flex justify-center pt-4">
            <Pagination
              current={result.page}
              pageSize={result.size}
              total={result.total}
              onChange={(page) => patchParams({ page: String(page) })}
              showSizeChanger={false}
            />
          </div>
        </>
      )}
    </div>
  );
}
