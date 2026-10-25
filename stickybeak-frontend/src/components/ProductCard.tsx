import { Link } from 'react-router-dom';
import type { Product } from '../lib/catalog';
import Price from './Price';
import { useI18n } from '../lib/i18n';
import { productDescription, productName } from '../lib/productCopy';

/** 商品卡片：先看画面，价格安静地放在名字下面 */
export default function ProductCard({ product }: { product: Product }) {
  const { t, locale } = useI18n();
  const cover = product.images[0];
  const videoFallback = product.media?.find((m) => m.mediaType === 'video')?.url;
  const name = productName(product.slug, product.name, locale);
  const line = productDescription(product.slug, product.description, locale)
    .replace(/\[[^\]]+\]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

  return (
    <Link to={`/products/${product.slug}`} className="product-card group block">
      <div className="aspect-[4/5] overflow-hidden bg-[#e7e0d2] dark:bg-[#221f1b]">
        {cover ? (
          <img
            src={cover}
            alt={name}
            loading="lazy"
            className="product-card-img h-full w-full object-cover"
          />
        ) : videoFallback ? (
          <video
            src={videoFallback}
            muted
            playsInline
            preload="metadata"
            className="product-card-img h-full w-full object-cover"
          />
        ) : (
          <div className="h-full w-full" />
        )}
      </div>
      <div className="pt-4">
        <h3 className="font-display line-clamp-2 text-2xl leading-tight">{name}</h3>
        {line ? <p className="mt-2 line-clamp-2 text-sm leading-6 muted">{line}</p> : null}
        <div className="mt-3 flex items-baseline justify-between gap-3">
          <Price cents={product.priceCents} className="text-sm tracking-wide" />
          {product.stock === 0 ? (
            <span className="text-[11px] tracking-[0.14em] uppercase muted">{t('detail.soldOut')}</span>
          ) : product.stock < 20 ? (
            <span className="text-[11px] tracking-[0.08em] muted">
              {locale === 'en-AU' ? `Only ${product.stock} left` : `仅剩 ${product.stock}`}
            </span>
          ) : null}
        </div>
      </div>
    </Link>
  );
}
