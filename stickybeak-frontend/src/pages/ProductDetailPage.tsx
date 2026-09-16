import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Breadcrumb, message } from 'antd';
import { ShoppingCartOutlined } from '@ant-design/icons';
import Price from '../components/Price';
import ProductCard from '../components/ProductCard';
import QtyStepper from '../components/QtyStepper';
import { getProductBySlug, listRelated } from '../lib/catalog';
import type { Product } from '../lib/catalog';
import { useAppDispatch } from '../app/hooks';
import { addItem } from '../features/cart/cartSlice';
import { useI18n } from '../lib/i18n';

export default function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const dispatch = useAppDispatch();
  const [product, setProduct] = useState<Product | null>(null);
  const [related, setRelated] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeImg, setActiveImg] = useState(0);
  const [qty, setQty] = useState(1);
  const { t, locale } = useI18n();

  useEffect(() => {
    if (!slug) return;
    setLoading(true);
    setActiveImg(0);
    setQty(1);
    getProductBySlug(slug)
      .then((p) => {
        setProduct(p);
        if (p) {
          listRelated(p.category, p.slug).then(setRelated);
        }
      })
      .finally(() => setLoading(false));
  }, [slug]);

  if (loading) {
    return (
      <div className="grid animate-pulse gap-8 sm:grid-cols-2">
        <div className="aspect-square rounded-2xl bg-sand-100 dark:bg-stone-800" />
        <div className="space-y-4 pt-4">
          <div className="h-8 w-2/3 rounded bg-sand-100 dark:bg-stone-800" />
          <div className="h-6 w-1/4 rounded bg-sand-100 dark:bg-stone-800" />
          <div className="h-24 w-full rounded bg-sand-100 dark:bg-stone-800" />
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="py-20 text-center">
        <p className="text-lg text-gray-500 dark:text-stone-400">
          {locale === 'en-AU' ? 'Product not found or has been unlisted' : '商品不存在或已下架'}
        </p>
        <Link to="/products" className="mt-4 inline-block text-brand-600 hover:underline dark:text-brand-400">
          {t('detail.back')}
        </Link>
      </div>
    );
  }

  const soldOut = product.stock === 0;

  const onAddToCart = () => {
    dispatch(
      addItem({
        productId: product.id,
        slug: product.slug,
        name: product.name,
        imageUrl: product.images[0],
        priceCents: product.priceCents,
        qty,
        stock: product.stock,
      }),
    );
    message.success(t('detail.addedSuccess'));
  };

  return (
    <div className="space-y-14">
      <Breadcrumb
        items={[
          { title: <Link to="/">{t('nav.brand')}</Link> },
          { title: <Link to="/products">{t('nav.shop')}</Link> },
          { title: product.name },
        ]}
      />

      <div className="grid gap-8 sm:grid-cols-2">
        {/* 图集 */}
        <div>
          <div className="overflow-hidden rounded-2xl border border-sand-200 bg-white dark:border-stone-800 dark:bg-stone-900">
            <img
              src={product.images[activeImg]}
              alt={product.name}
              className="aspect-square w-full object-cover"
            />
          </div>
          {product.images.length > 1 && (
            <div className="mt-3 flex gap-2">
              {product.images.map((img, i) => (
                <button
                  key={img}
                  onClick={() => setActiveImg(i)}
                  className={`h-16 w-16 overflow-hidden rounded-lg border-2 transition-colors ${
                    i === activeImg ? 'border-brand-500' : 'border-transparent'
                  }`}
                >
                  <img src={img} alt={`${product.name} ${i + 1}`} className="h-full w-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* 信息与加购 */}
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-stone-100">{product.name}</h1>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {product.tags.map((tag) => (
              <span key={tag} className="rounded bg-sand-100 px-2 py-0.5 text-xs text-gray-500 dark:bg-stone-800 dark:text-stone-400">
                {tag}
              </span>
            ))}
          </div>

          <div className="mt-5 flex items-end gap-3">
            <Price cents={product.priceCents} className="text-3xl font-bold text-accent-600 dark:text-accent-400" />
            <span className="pb-1 text-xs text-gray-400 dark:text-stone-500">
              {product.sales} {t('common.sold')}
            </span>
          </div>

          <p className="mt-3 text-sm">
            {soldOut ? (
              <span className="text-gray-400 dark:text-stone-500">{t('detail.soldOut')}</span>
            ) : product.stock < 20 ? (
              <span className="text-accent-600 dark:text-accent-400">
                {locale === 'en-AU' ? `Low stock: only ${product.stock} remaining` : `库存紧张，仅剩 ${product.stock} 件`}
              </span>
            ) : (
              <span className="text-brand-600 dark:text-brand-400">
                {locale === 'en-AU' ? 'In stock and ready to post' : '现货充足'}
              </span>
            )}
          </p>

          <div className="mt-6 flex items-center gap-4">
            <QtyStepper value={qty} onChange={setQty} max={product.stock} />
            <button
              onClick={onAddToCart}
              disabled={soldOut}
              className="inline-flex items-center gap-2 rounded-lg bg-accent-500 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-accent-600 disabled:cursor-not-allowed disabled:bg-gray-300 dark:disabled:bg-stone-700"
            >
              <ShoppingCartOutlined /> {t('detail.addToTrolley')}
            </button>
          </div>

          {product.description && (
            <div className="mt-8 border-t border-sand-200 pt-6 dark:border-stone-800">
              <h2 className="mb-3 text-sm font-semibold text-gray-900 dark:text-stone-100">
                {locale === 'en-AU' ? 'Product Story' : '商品介绍'}
              </h2>
              <p className="whitespace-pre-line text-sm leading-6 text-gray-600 dark:text-stone-300">
                {product.description}
              </p>
            </div>
          )}
        </div>
      </div>

      {/* 同类推荐 */}
      {related.length > 0 && (
        <section>
          <h2 className="mb-5 text-xl font-bold text-gray-900 dark:text-stone-100">{t('detail.related')}</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {related.map((p) => (
              <ProductCard key={p.slug} product={p} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
