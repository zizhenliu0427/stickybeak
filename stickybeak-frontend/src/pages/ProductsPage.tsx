import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Empty, Input, Pagination, Select, Slider } from 'antd';
import ProductCard from '../components/ProductCard';
import ProductGridSkeleton from '../components/ProductGridSkeleton';
import { listCategories, listProducts } from '../lib/catalog';
import type { Category, ProductListResult } from '../lib/catalog';
import { filterLocal, localCategories } from '../lib/localCatalog';
import { useAppSelector } from '../app/hooks';
import { formatPrice } from '../lib/format';
import { useI18n, type TranslationKey } from '../lib/i18n';

const PAGE_SIZE = 24;
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

  const keyword = searchParams.get('q') ?? undefined;
  // 有关键词时默认按相关度排序（ES），否则默认最新
  const defaultSort = keyword ? 'relevance' : 'new';

  const sortOptions = [
    ...(keyword ? [{ value: 'relevance', label: t('products.sortRelevance') }] : []),
    { value: 'new', label: t('products.sortNew') },
    { value: 'sales', label: t('products.sortSales') },
    { value: 'price-asc', label: t('products.sortPriceAsc') },
    { value: 'price-desc', label: t('products.sortPriceDesc') },
  ];

  // 从 URL 读取当前筛选
  const query = useMemo(
    () => ({
      category: searchParams.get('category') ?? undefined,
      q: keyword,
      sort: searchParams.get('sort') ?? defaultSort,
      minPrice: searchParams.get('minPrice') ? Number(searchParams.get('minPrice')) : undefined,
      maxPrice: searchParams.get('maxPrice') ? Number(searchParams.get('maxPrice')) : undefined,
      page: Number(searchParams.get('page') ?? 1),
      size: PAGE_SIZE,
    }),
    [searchParams, keyword, defaultSort],
  );

  useEffect(() => {
    listCategories()
      .then((list) => setCategories(list.length > 0 ? list : localCategories()))
      .catch(() => setCategories(localCategories()));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const unfiltered =
      !query.category && !query.q && query.minPrice == null && query.maxPrice == null;
    listProducts(query)
      .then((res) => {
        if (cancelled) return;
        // 库里东西不多。接口没数据、或默认条件下是空的，就直接把本地目录铺出来。
        if (unfiltered && res.items.length === 0) {
          setResult(filterLocal(query));
          setCategories((prev) => (prev.length > 0 ? prev : localCategories()));
          return;
        }
        setResult(res);
      })
      .catch(() => {
        if (!cancelled) {
          setResult(filterLocal(query));
          setCategories((prev) => (prev.length > 0 ? prev : localCategories()));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
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
    <div className="mx-auto w-full max-w-[1440px] space-y-10 px-6 py-16 sm:px-10 lg:px-16">
      <header className="max-w-xl pt-2">
        <p className="eyebrow muted">01 — {t('nav.series')}</p>
        <h1 className="font-display mt-3 text-4xl sm:text-6xl">{t('products.title')}</h1>
        <p className="mt-4 text-sm leading-7 muted">{t('products.subtitle')}</p>
      </header>

      {/* 工具栏（移动端自适应流式排列） */}
      <div className="flex flex-col gap-3 border-t border-black/10 pt-5 sm:flex-row sm:flex-wrap sm:items-center dark:border-white/10">
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
            onChange={(v) => patchParams({ sort: v === defaultSort ? undefined : v })}
            className="w-36 sm:w-40"
          />
          <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-stone-400">
            <span>{t('products.filterPrice')}</span>
            <Slider
              range
              min={0}
              max={Math.round((PRICE_CAP / 100) * (currency === 'CNY' ? rate : 1))}
              value={priceValue}
              onChangeComplete={(v) => {
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
      <div className="no-scrollbar flex gap-x-5 overflow-x-auto pb-1 sm:flex-wrap">
        <button
          type="button"
          onClick={() => patchParams({ category: undefined })}
          className={`flex min-h-[44px] shrink-0 items-center justify-center border-b px-1 text-sm tracking-wide transition-colors ${
            !query.category
              ? 'border-current'
              : 'border-transparent muted hover:opacity-80'
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
              className={`flex min-h-[44px] shrink-0 items-center justify-center border-b px-1 text-sm tracking-wide transition-colors ${
                query.category === c.slug
                  ? 'border-current'
                  : 'border-transparent muted hover:opacity-80'
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
          <div className="grid grid-cols-1 gap-x-6 gap-y-12 sm:grid-cols-2 lg:grid-cols-3">
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
