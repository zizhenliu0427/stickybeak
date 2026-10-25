import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { message } from 'antd';
import { ShoppingCartOutlined, HeartOutlined, HeartFilled } from '@ant-design/icons';
import Price from '../components/Price';
import ProductCard from '../components/ProductCard';
import QtyStepper from '../components/QtyStepper';
import { getProductBySlug, listRelated } from '../lib/catalog';
import type { Product } from '../lib/catalog';
import { localProductBySlug, localRelated } from '../lib/localCatalog';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { addCartItemAsync } from '../features/cart/cartSlice';
import { listWishlistApi, toggleWishlistApi } from '../lib/cart';
import { useI18n } from '../lib/i18n';
import { productDescription, productName } from '../lib/productCopy';

export default function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const dispatch = useAppDispatch();
  const user = useAppSelector((s) => s.auth.user);
  const [product, setProduct] = useState<Product | null>(null);
  const [related, setRelated] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeImg, setActiveImg] = useState(0);
  const [qty, setQty] = useState(1);
  const [inWishlist, setInWishlist] = useState(false);
  const { t, locale } = useI18n();

  useEffect(() => {
    if (!slug) return;
    setLoading(true);
    setActiveImg(0);
    setQty(1);
    getProductBySlug(slug)
      .catch(() => localProductBySlug(slug))
      .then((p) => {
        setProduct(p);
        if (p) {
          listRelated(p.category, p.slug)
            .then(setRelated)
            .catch(() => setRelated(localRelated(p.category, p.slug)));
        }
      })
      .finally(() => setLoading(false));
  }, [slug]);

  useEffect(() => {
    if (user && product) {
      listWishlistApi()
        .then((list) => setInWishlist(list.some((w) => w.productId === product.id)))
        .catch(() => {});
    } else {
      setInWishlist(false);
    }
  }, [user, product]);

  if (loading) {
    return (
      <div className="grid animate-pulse gap-10 sm:grid-cols-2">
        <div className="aspect-[4/5] bg-[#e4dccb] dark:bg-[#221f1b]" />
        <div className="space-y-4 pt-4">
          <div className="h-10 w-2/3 bg-[#e4dccb] dark:bg-[#221f1b]" />
          <div className="h-6 w-1/4 bg-[#e4dccb] dark:bg-[#221f1b]" />
          <div className="h-24 w-full bg-[#e4dccb] dark:bg-[#221f1b]" />
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="py-20 text-center">
        <p className="font-display text-3xl">
          {locale === 'en-AU' ? 'Product not found or has been unlisted' : '商品不存在或已下架'}
        </p>
        <Link to="/products" className="mt-6 inline-block text-sm tracking-[0.16em]">
          {t('detail.back')}
        </Link>
      </div>
    );
  }

  const soldOut = product.stock === 0;
  const name = productName(product.slug, product.name, locale);
  const description = productDescription(product.slug, product.description, locale);
  const tags =
    locale === 'en-AU' ? product.tags.filter((tag) => !/[\u4e00-\u9fff]/.test(tag)) : product.tags;

  const onAddToCart = async () => {
    try {
      await dispatch(addCartItemAsync({ product, qty })).unwrap();
      message.success(t('detail.addedSuccess'));
    } catch (e) {
      message.error(e instanceof Error ? e.message : 'Failed to add to trolley');
    }
  };

  const onToggleWishlist = async () => {
    if (!user) {
      message.info(t('wishlist.requireLogin'));
      return;
    }
    try {
      const res = await toggleWishlistApi(product.id);
      setInWishlist(res.inWishlist);
      message.success(t(res.inWishlist ? 'wishlist.addSuccess' : 'wishlist.removeSuccess'));
    } catch {
      message.error('Failed to update wishlist');
    }
  };

  return (
    <div className="space-y-20 pb-24 sm:pb-0">
      <p className="text-[11px] tracking-[0.22em] uppercase muted">
        <Link to="/">{t('nav.brand')}</Link>
        <span className="mx-2">/</span>
        <Link to="/products">{t('nav.series')}</Link>
      </p>

      <div className="grid items-start gap-10 lg:grid-cols-2 lg:gap-16">
        {/* 图集（图片 + 视频） */}
        <div>
          {(() => {
            // media 为空时退化用 images（全按 image）
            const mediaList =
              product.media && product.media.length > 0
                ? product.media
                : product.images.map((url) => ({ url, mediaType: 'image' as const }));
            const active = mediaList[activeImg];
            return (
              <>
                <div className="overflow-hidden bg-[#e7e0d2] dark:bg-[#221f1b]">
                  {active?.mediaType === 'video' ? (
                    <video
                      key={active.url}
                      src={active.url}
                      controls
                      playsInline
                      className="aspect-[4/5] w-full object-cover bg-black"
                    />
                  ) : (
                    <img
                      src={active?.url}
                      alt={name}
                      className="aspect-[4/5] w-full object-cover"
                    />
                  )}
                </div>
                {mediaList.length > 1 && (
                  <div className="mt-3 flex gap-2">
                    {mediaList.map((m, i) => (
                      <button
                        key={m.url}
                        onClick={() => setActiveImg(i)}
                        className={`relative h-16 w-16 overflow-hidden border transition-colors ${
                          i === activeImg ? 'border-current' : 'border-transparent opacity-70'
                        }`}
                      >
                        {m.mediaType === 'video' ? (
                          <>
                            <video src={m.url} muted preload="metadata" className="h-full w-full object-cover" />
                            <span className="absolute inset-0 flex items-center justify-center bg-black/30 text-white text-xs">
                              ▶
                            </span>
                          </>
                        ) : (
                          <img src={m.url} alt={`${name} ${i + 1}`} className="h-full w-full object-cover" />
                        )}
                      </button>
                    ))}
                  </div>
                )}
              </>
            );
          })()}
        </div>

        {/* 信息与加购 */}
        <div>
          <h1 className="font-display text-4xl leading-tight sm:text-5xl">{name}</h1>
          {tags.length > 0 && (
            <p className="mt-3 text-[11px] tracking-[0.16em] uppercase muted">{tags.slice(0, 4).join('  ·  ')}</p>
          )}

          <div className="mt-6">
            <Price cents={product.priceCents} className="font-display text-3xl" />
          </div>

          <p className="mt-3 text-sm muted">
            {soldOut
              ? t('detail.soldOut')
              : product.stock < 20
                ? locale === 'en-AU'
                  ? `Low stock: only ${product.stock} remaining`
                  : `库存紧张，仅剩 ${product.stock} 件`
                : locale === 'en-AU'
                  ? 'In stock and ready to post'
                  : '现货，可以寄出'}
          </p>

          <div className="mt-6 flex items-center gap-4">
            <QtyStepper value={qty} onChange={setQty} max={product.stock} />
            <button
              onClick={onAddToCart}
              disabled={soldOut}
              className="btn-ink"
            >
              <ShoppingCartOutlined /> {t('detail.addToTrolley')}
            </button>
            <button
              type="button"
              onClick={onToggleWishlist}
              className={`flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center border text-lg transition-colors ${
                inWishlist
                  ? 'border-current'
                  : 'border-black/15 text-current/60 hover:border-current dark:border-white/20'
              }`}
              title={t('wishlist.title')}
              aria-label={t('wishlist.title')}
            >
              {inWishlist ? <HeartFilled /> : <HeartOutlined />}
            </button>
          </div>

          {product.description && (
            <div className="mt-8 border-t border-black/10 pt-6 dark:border-white/10">
              <h2 className="mb-3 text-[11px] tracking-[0.2em] uppercase muted">
                {locale === 'en-AU' ? 'Product Story' : '这一枚'}
              </h2>
              <p className="whitespace-pre-line text-sm leading-7 muted">
                {description}
              </p>
            </div>
          )}
        </div>
      </div>

      {/* 同类推荐 */}
      {related.length > 0 && (
        <section>
          <h2 className="font-display mb-8 text-3xl">{t('detail.related')}</h2>
          <div className="grid grid-cols-1 gap-x-6 gap-y-12 sm:grid-cols-2 lg:grid-cols-4">
            {related.map((p) => (
              <ProductCard key={p.slug} product={p} />
            ))}
          </div>
        </section>
      )}

      {/* 移动端吸底快捷操作条（单手可加购） */}
      <div className="fixed bottom-0 left-0 right-0 z-30 flex items-center justify-between gap-3 border-t border-black/10 bg-[rgba(246,241,232,0.96)] px-4 py-2.5 pb-[max(0.625rem,env(safe-area-inset-bottom))] backdrop-blur sm:hidden dark:border-white/10 dark:bg-[rgba(20,19,17,0.96)]">
        <div className="flex items-center gap-3">
          <Price cents={product.priceCents} className="font-display text-2xl" />
          <button
            type="button"
            onClick={onToggleWishlist}
            className="flex h-10 w-10 items-center justify-center border border-black/15 dark:border-white/20"
          >
            {inWishlist ? <HeartFilled /> : <HeartOutlined />}
          </button>
        </div>
        <button onClick={onAddToCart} disabled={soldOut} className="btn-ink flex-1">
          <ShoppingCartOutlined /> {t('detail.stickyAdd')}
        </button>
      </div>
    </div>
  );
}
