import { Link } from 'react-router-dom';
import type { Product } from '../lib/catalog';
import Price from './Price';

/** 商品卡片：封面图 + 名称 + 价格 + 标签，整卡可点 */
export default function ProductCard({ product }: { product: Product }) {
  return (
    <Link
      to={`/products/${product.slug}`}
      className="product-card group block overflow-hidden rounded-xl border border-sand-200 bg-white transition-colors hover:border-brand-300"
    >
      <div className="aspect-square overflow-hidden bg-sand-100">
        <img
          src={product.images[0]}
          alt={product.name}
          loading="lazy"
          className="product-card-img h-full w-full object-cover"
        />
      </div>
      <div className="p-3">
        <h3 className="line-clamp-2 min-h-10 text-sm font-medium text-gray-800 group-hover:text-brand-700">
          {product.name}
        </h3>
        <div className="mt-2 flex items-center justify-between">
          <Price cents={product.priceCents} className="text-base font-bold text-accent-600" />
          {product.stock === 0 ? (
            <span className="text-xs text-gray-400">售罄</span>
          ) : product.stock < 20 ? (
            <span className="text-xs text-accent-500">仅剩 {product.stock}</span>
          ) : null}
        </div>
        {product.tags.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {product.tags.slice(0, 3).map((t) => (
              <span
                key={t}
                className="rounded bg-sand-100 px-1.5 py-0.5 text-[11px] text-gray-500"
              >
                {t}
              </span>
            ))}
          </div>
        )}
      </div>
    </Link>
  );
}
