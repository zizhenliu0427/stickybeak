import { Link } from 'react-router-dom';
import type { Product } from '../lib/catalog';
import Price from './Price';
import { useI18n } from '../lib/i18n';

/** 商品卡片：封面图 + 名称 + 价格 + 标签，整卡可点 */
export default function ProductCard({ product }: { product: Product }) {
  const { t, locale } = useI18n();

  return (
    <Link
      to={`/products/${product.slug}`}
      className="product-card group block overflow-hidden rounded-xl border border-sand-200 bg-white transition-colors hover:border-brand-300 dark:border-stone-800 dark:bg-stone-900 dark:hover:border-brand-700"
    >
      <div className="aspect-square overflow-hidden bg-sand-100 dark:bg-stone-800">
        <img
          src={product.images[0]}
          alt={product.name}
          loading="lazy"
          className="product-card-img h-full w-full object-cover"
        />
      </div>
      <div className="p-3">
        <h3 className="line-clamp-2 min-h-10 text-sm font-medium text-gray-800 group-hover:text-brand-700 dark:text-stone-200 dark:group-hover:text-brand-300">
          {product.name}
        </h3>
        <div className="mt-2 flex items-center justify-between">
          <Price cents={product.priceCents} className="text-base font-bold text-accent-600 dark:text-accent-400" />
          {product.stock === 0 ? (
            <span className="text-xs text-gray-400 dark:text-stone-500">{t('detail.soldOut')}</span>
          ) : product.stock < 20 ? (
            <span className="text-xs text-accent-500 dark:text-accent-400">
              {locale === 'en-AU' ? `Only ${product.stock} left` : `仅剩 ${product.stock}`}
            </span>
          ) : null}
        </div>
        {product.tags.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {product.tags.slice(0, 3).map((tag) => (
              <span
                key={tag}
                className="rounded bg-sand-100 px-1.5 py-0.5 text-[11px] text-gray-500 dark:bg-stone-800 dark:text-stone-400"
              >
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>
    </Link>
  );
}
