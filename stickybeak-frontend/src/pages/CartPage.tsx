import { Link, useNavigate } from 'react-router-dom';
import { Button, Empty, Popconfirm, message } from 'antd';
import { DeleteOutlined, ArrowRightOutlined } from '@ant-design/icons';
import Price from '../components/Price';
import QtyStepper from '../components/QtyStepper';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import {
  clearCartAsync,
  removeItemAsync,
  selectCartTotalCents,
  updateQtyAsync,
} from '../features/cart/cartSlice';
import { useI18n } from '../lib/i18n';

/** 满额包邮线（AUD cents） */
const FREE_SHIPPING_THRESHOLD = 4900;

export default function CartPage() {
  const items = useAppSelector((s) => s.cart.items);
  const totalCents = useAppSelector(selectCartTotalCents);
  const user = useAppSelector((s) => s.auth.user);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { t, locale } = useI18n();

  if (items.length === 0) {
    return (
      <div className="py-16">
        <Empty description={t('cart.emptyTitle')}>
          <Link to="/products">
            <Button type="primary">{t('cart.emptyCta')}</Button>
          </Link>
        </Empty>
      </div>
    );
  }

  const toFreeShipping = FREE_SHIPPING_THRESHOLD - totalCents;

  const onCheckout = () => {
    // Sprint 4 接入 Stripe Checkout；现在先引导登录态
    if (!user) {
      message.info(locale === 'en-AU' ? 'Please sign in before checkout' : '结账前请先登录');
      navigate('/login', { state: { from: '/cart' } });
      return;
    }
    message.info(locale === 'en-AU' ? 'Stripe Checkout is coming in Sprint 4!' : '结账功能在 Sprint 4 上线，敬请期待');
  };

  return (
    <div className="mx-auto max-w-4xl">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-stone-100">{t('cart.title')}</h1>
        <Popconfirm
          title={locale === 'en-AU' ? 'Clear entire trolley?' : '确定清空购物车？'}
          onConfirm={() => dispatch(clearCartAsync())}
        >
          <Button type="text" danger size="small">
            {t('cart.clear')}
          </Button>
        </Popconfirm>
      </div>

      <div className="grid gap-8 sm:grid-cols-[1fr_280px]">
        {/* 明细 */}
        <ul className="divide-y divide-sand-200 rounded-2xl border border-sand-200 bg-white dark:divide-stone-800 dark:border-stone-800 dark:bg-stone-900">
          {items.map((item) => (
            <li key={item.productId} className="flex gap-4 p-4">
              <Link to={`/products/${item.slug}`} className="shrink-0">
                <img
                  src={item.imageUrl}
                  alt={item.name}
                  className="h-20 w-20 rounded-lg border border-sand-200 object-cover dark:border-stone-700"
                />
              </Link>
              <div className="flex flex-1 flex-col">
                <Link
                  to={`/products/${item.slug}`}
                  className="text-sm font-medium text-gray-800 hover:text-brand-700 dark:text-stone-200 dark:hover:text-brand-300"
                >
                  {item.name}
                </Link>
                <div className="mt-1 text-xs text-gray-400 dark:text-stone-400">
                  {locale === 'en-AU' ? 'Unit price' : '单价'} <Price cents={item.priceCents} />
                </div>
                <div className="mt-auto flex items-center justify-between pt-2">
                  <QtyStepper
                    value={item.qty}
                    onChange={(v) =>
                      dispatch(updateQtyAsync({ itemId: item.id, productId: item.productId, qty: v }))
                    }
                    max={item.stock}
                  />
                  <div className="flex items-center gap-3">
                    <Price cents={item.priceCents * item.qty} className="font-bold text-gray-900 dark:text-stone-100" />
                    <Popconfirm
                      title={locale === 'en-AU' ? 'Remove from trolley?' : '移出购物车？'}
                      onConfirm={() =>
                        dispatch(removeItemAsync({ itemId: item.id, productId: item.productId }))
                      }
                    >
                      <Button type="text" danger size="small" icon={<DeleteOutlined />} />
                    </Popconfirm>
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>

        {/* 汇总 */}
        <aside className="h-fit rounded-2xl border border-sand-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
          <h2 className="text-sm font-semibold text-gray-900 dark:text-stone-100">
            {locale === 'en-AU' ? 'Order Summary' : '订单汇总'}
          </h2>
          <div className="mt-4 flex justify-between text-sm text-gray-600 dark:text-stone-300">
            <span>{t('common.subtotal')}</span>
            <Price cents={totalCents} />
          </div>
          <div className="mt-2 flex justify-between text-sm text-gray-600 dark:text-stone-300">
            <span>{locale === 'en-AU' ? 'Delivery' : '运费'}</span>
            <span>
              {toFreeShipping <= 0
                ? locale === 'en-AU'
                  ? 'FREE Delivery'
                  : '免运费'
                : locale === 'en-AU'
                  ? 'Calculated at checkout'
                  : '结账时计算'}
            </span>
          </div>
          {toFreeShipping > 0 && (
            <p className="mt-3 rounded-lg bg-brand-50 px-3 py-2 text-xs text-brand-700 dark:bg-stone-800 dark:text-brand-300">
              {locale === 'en-AU' ? 'Add ' : '再买 '}
              <Price cents={toFreeShipping} />
              {locale === 'en-AU' ? ' for free AU delivery!' : ' 即可免运费'}
            </p>
          )}
          <div className="mt-4 border-t border-sand-200 pt-4 dark:border-stone-800">
            <div className="flex justify-between">
              <span className="font-semibold text-gray-900 dark:text-stone-100">{t('cart.total')}</span>
              <Price cents={totalCents} className="text-lg font-bold text-accent-600 dark:text-accent-400" />
            </div>
          </div>
          <Button type="primary" block className="mt-4" onClick={onCheckout}>
            {t('cart.checkout')} <ArrowRightOutlined />
          </Button>
          <p className="mt-3 text-center text-xs text-gray-400 dark:text-stone-500">
            {locale === 'en-AU' ? 'AU Card / Alipay / WeChat Pay' : '支持澳洲银行卡 / 支付宝 / 微信'}
          </p>
        </aside>
      </div>
    </div>
  );
}
