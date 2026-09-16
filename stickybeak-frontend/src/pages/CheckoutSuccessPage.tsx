import { useEffect, useState, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Button, Card, Spin, Tag, Typography } from 'antd';
import {
  CheckCircleFilled,
  ShoppingOutlined,
  OrderedListOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { useAppDispatch } from '../app/hooks';
import { clearCart } from '../features/cart/cartSlice';
import { getOrderDetail } from '../lib/order';
import type { OrderVO } from '../lib/order';
import { useI18n } from '../lib/i18n';

export default function CheckoutSuccessPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { t } = useI18n();

  const orderNo = searchParams.get('order_no') || '';

  const [order, setOrder] = useState<OrderVO | null>(null);
  const [loading, setLoading] = useState(true);
  const pollTimerRef = useRef<number | null>(null);

  useEffect(() => {
    // Clear cart immediately upon reaching success page
    dispatch(clearCart());
  }, [dispatch]);

  useEffect(() => {
    if (!orderNo) {
      setLoading(false);
      return;
    }

    const poll = async (attempt: number) => {
      try {
        const data = await getOrderDetail(orderNo);
        setOrder(data);

        if (data.status === 'paid' || attempt >= 10) {
          setLoading(false);
          return;
        }

        // Keep polling if still pending
        pollTimerRef.current = window.setTimeout(() => {
          poll(attempt + 1);
        }, 1500);
      } catch {
        // Fallback: stop loading if error
        setLoading(false);
      }
    };

    poll(1);

    return () => {
      if (pollTimerRef.current) {
        clearTimeout(pollTimerRef.current);
      }
    };
  }, [orderNo]);

  const isPaid = order?.status === 'paid';

  return (
    <div className="mx-auto max-w-2xl py-12">
      <Card className="rounded-2xl border-sand-200 text-center dark:border-stone-800 dark:bg-stone-900">
        <div className="flex justify-center">
          {loading && !isPaid ? (
            <div className="flex flex-col items-center gap-3">
              <Spin size="large" />
              <p className="text-sm text-gray-500 dark:text-stone-400">{t('checkout.polling')}</p>
            </div>
          ) : isPaid ? (
            <CheckCircleFilled className="text-6xl text-emerald-500" />
          ) : (
            <ClockCircleOutlined className="text-6xl text-amber-500" />
          )}
        </div>

        <Typography.Title level={2} className="!mt-4 !mb-2 dark:text-stone-100">
          {isPaid ? t('checkout.successTitle') : t('checkout.title')}
        </Typography.Title>

        <p className="mx-auto max-w-md text-sm text-gray-500 dark:text-stone-400">
          {isPaid
            ? t('checkout.successDesc')
            : 'Your order has been placed. Payment confirmation will update shortly.'}
        </p>

        {orderNo && (
          <div className="my-6 inline-flex items-center gap-2 rounded-full bg-sand-100 px-4 py-1.5 text-sm dark:bg-stone-800">
            <span className="text-gray-500 dark:text-stone-400">{t('checkout.orderNo')}:</span>
            <span className="font-mono font-bold text-gray-800 dark:text-stone-200">{orderNo}</span>
          </div>
        )}

        {order && (
          <div className="mb-6 rounded-xl border border-sand-200 bg-sand-50/50 p-4 text-left dark:border-stone-800 dark:bg-stone-800/40">
            <div className="flex justify-between border-b border-sand-200 pb-2 text-sm dark:border-stone-700">
              <span className="text-gray-500 dark:text-stone-400">{t('checkout.amountPaid')}</span>
              <span className="font-bold text-gray-900 dark:text-stone-100">
                {order.currency} ${Number(order.payAmount).toFixed(2)}
              </span>
            </div>
            <div className="flex justify-between pt-2 text-sm">
              <span className="text-gray-500 dark:text-stone-400">Status</span>
              <Tag color={isPaid ? 'green' : 'gold'}>{order.status.toUpperCase()}</Tag>
            </div>

            {order.items && order.items.length > 0 && (
              <div className="mt-4 space-y-2 border-t border-sand-200 pt-3 dark:border-stone-700">
                <p className="text-xs font-semibold uppercase tracking-wider text-gray-400">
                  {t('checkout.orderSummary')}
                </p>
                {order.items.map((item) => (
                  <div key={item.id || item.productId} className="flex items-center justify-between text-xs">
                    <span className="text-gray-700 dark:text-stone-300">
                      {item.productName} × {item.qty}
                    </span>
                    <span className="font-medium text-gray-900 dark:text-stone-100">
                      ${(Number(item.price) * item.qty).toFixed(2)}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        <div className="flex flex-wrap justify-center gap-3">
          <Button
            type="primary"
            icon={<ShoppingOutlined />}
            size="large"
            onClick={() => navigate('/products')}
          >
            {t('checkout.continueShopping')}
          </Button>
          <Button
            icon={<OrderedListOutlined />}
            size="large"
            onClick={() => navigate('/orders')}
          >
            {t('orders.title')}
          </Button>
        </div>
      </Card>
    </div>
  );
}
