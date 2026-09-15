import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRightOutlined } from '@ant-design/icons';
import ProductCard from '../components/ProductCard';
import ProductGridSkeleton from '../components/ProductGridSkeleton';
import { listCategories, listFeatured } from '../lib/catalog';
import type { Category, Product } from '../lib/catalog';
import { useI18n, type TranslationKey } from '../lib/i18n';

/** 品类入口的示意表情（storefront 氛围，非数据驱动） */
const CATEGORY_EMOJI: Record<string, string> = {
  'bus-sign': '🚌',
  supermarket: '🛒',
  train: '🚂',
  bird: '🦜',
  booze: '🍻',
  phonecase: '📱',
};

export default function HomePage() {
  const [featured, setFeatured] = useState<Product[] | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const { t } = useI18n();

  useEffect(() => {
    listFeatured().then(setFeatured);
    listCategories().then(setCategories);
  }, []);

  return (
    <div className="space-y-14">
      {/* Hero */}
      <section className="overflow-hidden rounded-2xl bg-brand-800 text-sand-50 dark:bg-brand-950 dark:border dark:border-stone-800">
        <div className="grid items-center gap-6 px-8 py-14 sm:grid-cols-2 sm:px-12">
          <div>
            <p className="text-sm tracking-widest text-brand-200">{t('hero.badge')}</p>
            <h1 className="font-display mt-3 text-4xl leading-tight sm:text-5xl whitespace-pre-line">
              {t('hero.title')}
            </h1>
            <p className="mt-4 max-w-md text-brand-100 dark:text-brand-200">
              {t('hero.desc')}
            </p>
            <Link
              to="/products"
              className="mt-6 inline-flex items-center gap-2 rounded-lg bg-accent-500 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-accent-600"
            >
              {t('hero.cta')} <ArrowRightOutlined />
            </Link>
          </div>
          <div className="hidden justify-center sm:flex">
            <img
              src="/mock-products/aussie-uni-bus-stop-set/01.jpg"
              alt="StickyBeak"
              className="max-h-64 rotate-2 rounded-xl border-4 border-sand-50 object-cover shadow-lg dark:border-stone-800"
            />
          </div>
        </div>
      </section>

      {/* 品类入口 */}
      <section>
        <div className="mb-5 flex items-end justify-between">
          <div>
            <h2 className="text-xl font-bold text-gray-900 dark:text-stone-100">{t('home.categories')}</h2>
            <p className="text-xs text-gray-500 dark:text-stone-400 mt-1">{t('home.categoriesSubtitle')}</p>
          </div>
          <Link to="/products" className="text-sm text-brand-600 hover:underline dark:text-brand-400">
            {t('home.viewAll')}
          </Link>
        </div>
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-6">
          {categories.map((c) => {
            const catKey = `cat.${c.slug}` as TranslationKey;
            const displayName = t(catKey) || c.name;
            return (
              <Link
                key={c.slug}
                to={`/products?category=${c.slug}`}
                className="flex flex-col items-center gap-2 rounded-xl border border-sand-200 bg-white py-5 transition-colors hover:border-brand-300 hover:bg-brand-50 dark:border-stone-800 dark:bg-stone-900 dark:hover:border-brand-700 dark:hover:bg-stone-800"
              >
                <span className="text-2xl">{CATEGORY_EMOJI[c.slug] ?? '🧲'}</span>
                <span className="text-xs text-center px-1 text-gray-700 dark:text-stone-300">{displayName}</span>
              </Link>
            );
          })}
        </div>
      </section>

      {/* 精选 */}
      <section>
        <div className="mb-5 flex items-end justify-between">
          <div>
            <h2 className="text-xl font-bold text-gray-900 dark:text-stone-100">{t('home.featured')}</h2>
            <p className="text-xs text-gray-500 dark:text-stone-400 mt-1">{t('home.featuredSubtitle')}</p>
          </div>
          <Link to="/products?sort=sales" className="text-sm text-brand-600 hover:underline dark:text-brand-400">
            {t('products.sortSales')}
          </Link>
        </div>
        {featured === null ? (
          <ProductGridSkeleton count={4} />
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {featured.map((p) => (
              <ProductCard key={p.slug} product={p} />
            ))}
          </div>
        )}
      </section>

      {/* 品牌条 */}
      <section className="rounded-2xl border border-sand-200 bg-white px-8 py-10 text-center dark:border-stone-800 dark:bg-stone-900">
        <h2 className="font-display text-2xl text-gray-900 dark:text-stone-100">
          {t('nav.brand')} — What's in a Name?
        </h2>
        <p className="mx-auto mt-3 max-w-2xl text-sm leading-6 text-gray-600 dark:text-stone-300">
          StickyBeak is classic Aussie slang for someone who's endlessly curious and loves to poke their beak into everything — just like the cheeky cockatoos sizing up your hot chips at a Sunday barbie. Our magnets capture that authentic Down Under spirit.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-6 sm:gap-8 text-xs text-gray-400 dark:text-stone-500">
          <span>🇦🇺 Aussie Design</span>
          <span>📦 Free AU Shipping over A$49</span>
          <span>💳 Card / Alipay / WeChat Pay</span>
        </div>
      </section>
    </div>
  );
}
