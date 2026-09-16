import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Empty, Input, Pagination, Select, Slider } from 'antd';
import ProductCard from '../components/ProductCard';
import ProductGridSkeleton from '../components/ProductGridSkeleton';
import { listCategories, listProducts } from '../lib/catalog';
import type { Category, ProductListResult } from '../lib/catalog';
import { useAppSelector } from '../app/hooks';
import { formatPrice } from '../lib/format';
import { useI18n, type TranslationKey } from '../lib/i18n';

const PAGE_SIZE = 12;
/** 价格筛选上限（AUD cents） */
const PRICE_CAP = 4000;

export default function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [categories, setCategories] = useState<Category[]>([]);
  const [result, setResult] = useState<ProductListResult | null>(null);
  const [loading, setLoading] = useState(true);
  const currency = useAppSelector((s) => s.currency.currency);
  const rate = useAppSelector((s) => s.currency.audToCny);
  const { t } = useI18n();

  const sortOptions = [
    { value: 'new', label: t('products.sortNew') },
    { value: 'sales', label: t('products.sortSales') },
    { value: 'price-asc', label: t('products.sortPriceAsc') },
    { value: 'price-desc', label: t('products.sortPriceDesc') },
  ];

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
      {/* 工具栏（移动端自适应流式排列） */}
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center">
        <Input.Search
          placeholder={t('products.searchPlaceholder')}
          allowClear
          defaultValue={query.q}
          onSearch={(v) => patchParams({ q: v || undefined })}
          className="w-full sm:max-w-64"
        />
        <div className="flex flex-wrap items-center gap-3">
          <Select
            value={query.sort}
            options={sortOptions}
            onChange={(v) => patchParams({ sort: v === 'new' ? undefined : v })}
            className="w-36 sm:w-40"
          />
          <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-stone-400">
            <span>{t('products.filterPrice')}</span>
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
              className="w-28 sm:w-40"
              tooltip={{
                formatter: (v) => formatPrice(Math.round((v ?? 0) * 100), currency),
              }}
            />
          </div>
        </div>
      </div>

      {/* 品类滑动栏（触控高度 >= 44px 舒适可点） */}
      <div className="no-scrollbar flex overflow-x-auto pb-1 gap-2 sm:flex-wrap">
        <button
          type="button"
          onClick={() => patchParams({ category: undefined })}
          className={`flex min-h-[44px] shrink-0 items-center justify-center rounded-full px-5 py-2 text-sm font-medium transition-colors ${
            !query.category
              ? 'bg-brand-600 text-white shadow-sm'
              : 'border border-sand-200 bg-white text-gray-600 hover:border-brand-300 dark:border-stone-800 dark:bg-stone-900 dark:text-stone-300 dark:hover:border-brand-700'
          }`}
        >
          {t('common.all')}
        </button>
        {categories.map((c) => {
          const catKey = `cat.${c.slug}` as TranslationKey;
          const displayName = t(catKey) || c.name;
          return (
            <button
              key={c.slug}
              type="button"
              onClick={() => patchParams({ category: c.slug })}
              className={`flex min-h-[44px] shrink-0 items-center justify-center rounded-full px-5 py-2 text-sm font-medium transition-colors ${
                query.category === c.slug
                  ? 'bg-brand-600 text-white shadow-sm'
                  : 'border border-sand-200 bg-white text-gray-600 hover:border-brand-300 dark:border-stone-800 dark:bg-stone-900 dark:text-stone-300 dark:hover:border-brand-700'
              }`}
            >
              {displayName}
            </button>
          );
        })}
      </div>

      {/* 结果 */}
      {loading ? (
        <ProductGridSkeleton count={8} />
      ) : !result || result.items.length === 0 ? (
        <Empty description={t('products.noResults')} className="py-16" />
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
