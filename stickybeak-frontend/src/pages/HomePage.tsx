import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRightOutlined } from '@ant-design/icons';
import ProductCard from '../components/ProductCard';
import ProductGridSkeleton from '../components/ProductGridSkeleton';
import { listCategories, listFeatured } from '../lib/catalog';
import type { Category, Product } from '../lib/catalog';

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

  useEffect(() => {
    listFeatured().then(setFeatured);
    listCategories().then(setCategories);
  }, []);

  return (
    <div className="space-y-14">
      {/* Hero */}
      <section className="overflow-hidden rounded-2xl bg-brand-800 text-sand-50">
        <div className="grid items-center gap-6 px-8 py-14 sm:grid-cols-2 sm:px-12">
          <div>
            <p className="text-sm tracking-widest text-brand-200">G'DAY, MATE!</p>
            <h1 className="font-display mt-3 text-4xl leading-tight sm:text-5xl">
              把澳洲日常
              <br />
              贴上你的冰箱
            </h1>
            <p className="mt-4 max-w-md text-brand-100">
              公交站牌、悉尼火车、超市小票、抢食的海鸥——GDCUP
              手作冰箱贴，每一枚都是土澳生活的切片。
            </p>
            <Link
              to="/products"
              className="mt-6 inline-flex items-center gap-2 rounded-lg bg-accent-500 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-accent-600"
            >
              逛逛全部商品 <ArrowRightOutlined />
            </Link>
          </div>
          <div className="hidden justify-center sm:flex">
            <img
              src="/mock-products/aussie-uni-bus-stop-set/01.jpg"
              alt="StickyBeak 招牌冰箱贴"
              className="max-h-64 rotate-2 rounded-xl border-4 border-sand-50 object-cover shadow-lg"
            />
          </div>
        </div>
      </section>

      {/* 品类入口 */}
      <section>
        <div className="mb-5 flex items-end justify-between">
          <h2 className="text-xl font-bold text-gray-900">按系列逛</h2>
          <Link to="/products" className="text-sm text-brand-600 hover:underline">
            全部商品
          </Link>
        </div>
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-6">
          {categories.map((c) => (
            <Link
              key={c.slug}
              to={`/products?category=${c.slug}`}
              className="flex flex-col items-center gap-2 rounded-xl border border-sand-200 bg-white py-5 transition-colors hover:border-brand-300 hover:bg-brand-50"
            >
              <span className="text-2xl">{CATEGORY_EMOJI[c.slug] ?? '🧲'}</span>
              <span className="text-xs text-gray-700">{c.name}</span>
            </Link>
          ))}
        </div>
      </section>

      {/* 精选 */}
      <section>
        <div className="mb-5 flex items-end justify-between">
          <h2 className="text-xl font-bold text-gray-900">本周精选</h2>
          <Link to="/products?sort=sales" className="text-sm text-brand-600 hover:underline">
            按销量看
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
      <section className="rounded-2xl border border-sand-200 bg-white px-8 py-10 text-center">
        <h2 className="font-display text-2xl text-gray-900">StickyBeak 是什么鸟？</h2>
        <p className="mx-auto mt-3 max-w-2xl text-sm leading-6 text-gray-600">
          StickyBeak 是澳洲俚语里「爱凑热闹的人」——就像那只在 BBQ 上盯着你薯条的大葵花鹦鹉。
          我们的冰箱贴全部取材自真实的澳洲日常：八大名校的公交站牌、永远延误的 T9、
          Coles 和窝窝屎的每周特价。数据与图片素材来自小红书 GDCUP 授权内容。
        </p>
        <div className="mt-6 flex justify-center gap-8 text-xs text-gray-400">
          <span>🇦🇺 澳洲设计</span>
          <span>📦 满 A$49 包邮</span>
          <span>💳 支持支付宝/微信</span>
        </div>
      </section>
    </div>
  );
}
