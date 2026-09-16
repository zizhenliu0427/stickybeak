import { useEffect, useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Empty,
  Popconfirm,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  ShoppingOutlined,
} from '@ant-design/icons';
import { getMyOrders, cancelOrder } from '../lib/order';
import type { OrderVO } from '../lib/order';
import { useI18n } from '../lib/i18n';

// 待支付超时时间：30 分钟 (毫秒)
const TIMEOUT_MILLIS = 30 * 60 * 1000;

function OrderCountdown({ createTime, onExpire }: { createTime: string; onExpire?: () => void }) {
  const { t } = useI18n();

  const calculateRemaining = useCallback(() => {
    const created = new Date(createTime).getTime();
    const remaining = created + TIMEOUT_MILLIS - Date.now();
    return Math.max(0, remaining);
  }, [createTime]);

  const [remainingMillis, setRemainingMillis] = useState<number>(calculateRemaining);

  useEffect(() => {
    const timer = setInterval(() => {
      const rem = calculateRemaining();
      setRemainingMillis(rem);
      if (rem <= 0) {
        clearInterval(timer);
        onExpire?.();
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [calculateRemaining, onExpire]);

  if (remainingMillis <= 0) {
    return (
      <span className="text-xs font-semibold text-rose-500 dark:text-rose-400">
        <ClockCircleOutlined className="mr-1" />
        {t('orders.expired')}
      </span>
    );
  }

  const totalSeconds = Math.floor(remainingMillis / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  const formattedTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;

  return (
    <span className="inline-flex items-center rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-950/40 dark:text-amber-300">
      <ClockCircleOutlined className="mr-1 animate-pulse" />
      {t('orders.countdown', { time: formattedTime })}
    </span>
  );
}

export default function OrdersPage() {
  const navigate = useNavigate();
  const { t } = useI18n();
  const [activeTab, setActiveTab] = useState<string>('all');
  const [orders, setOrders] = useState<OrderVO[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [cancellingOrderNo, setCancellingOrderNo] = useState<string | null>(null);

  const loadOrders = useCallback(async (status: string) => {
    setLoading(true);
    try {
      const data = await getMyOrders(status);
      setOrders(data || []);
    } catch (err) {
      message.error(String(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOrders(activeTab);
  }, [activeTab, loadOrders]);

  const handleCancelOrder = async (orderNo: string) => {
    setCancellingOrderNo(orderNo);
    try {
      await cancelOrder(orderNo);
      message.success(t('orders.cancelSuccess'));
      await loadOrders(activeTab);
    } catch (e) {
      message.error(String(e));
    } finally {
      setCancellingOrderNo(null);
    }
  };

  const getStatusTag = (status: OrderVO['status']) => {
    switch (status) {
      case 'pending':
        return (
          <Tag color="warning" icon={<ClockCircleOutlined />}>
            {t('orders.tabPending')}
          </Tag>
        );
      case 'paid':
        return (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            {t('orders.tabPaid')}
          </Tag>
        );
      case 'processing':
        return (
          <Tag color="processing" icon={<SyncOutlined spin />}>
            {t('orders.tabProcessing')}
          </Tag>
        );
      case 'shipped':
        return (
          <Tag color="cyan" icon={<CheckCircleOutlined />}>
            {t('orders.tabShipped')}
          </Tag>
        );
      case 'completed':
        return (
          <Tag color="purple" icon={<CheckCircleOutlined />}>
            {t('orders.tabCompleted')}
          </Tag>
        );
      case 'cancelled':
        return (
          <Tag color="default" icon={<CloseCircleOutlined />}>
            {t('orders.tabCancelled')}
          </Tag>
        );
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const tabItems = useMemo(
    () => [
      { key: 'all', label: t('orders.tabAll') },
      { key: 'pending', label: t('orders.tabPending') },
      { key: 'paid', label: t('orders.tabPaid') },
      { key: 'shipped', label: t('orders.tabShipped') },
      { key: 'cancelled', label: t('orders.tabCancelled') },
    ],
    [t]
  );

  return (
    <div className="mx-auto max-w-4xl space-y-6 pb-12">
      <div className="flex items-center justify-between">
        <Typography.Title level={2} className="!mb-0 dark:text-stone-100">
          {t('orders.title')}
        </Typography.Title>
      </div>

      <Card className="rounded-2xl border-sand-200 shadow-sm dark:border-stone-800 dark:bg-stone-900">
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={tabItems}
          className="mb-4"
        />

        {loading ? (
          <div className="flex justify-center py-16">
            <Spin size="large" />
          </div>
        ) : orders.length === 0 ? (
          <Empty description={t('orders.empty')} className="py-12">
            <Button
              type="primary"
              icon={<ShoppingOutlined />}
              onClick={() => navigate('/products')}
            >
              {t('orders.backToShop')}
            </Button>
          </Empty>
        ) : (
          <div className="space-y-4">
            {orders.map((order) => {
              const isPending = order.status === 'pending';
              const createdDateStr = new Date(order.createTime).toLocaleString();

              return (
                <div
                  key={order.id}
                  className="overflow-hidden rounded-xl border border-sand-200 bg-white transition-all hover:border-sand-300 dark:border-stone-800 dark:bg-stone-800/60"
                >
                  {/* 订单卡片头部 */}
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-sand-100 bg-sand-50/50 px-4 py-3 text-xs text-gray-600 sm:px-6 dark:border-stone-800 dark:bg-stone-800 dark:text-stone-300">
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
                      <span>
                        <span className="text-gray-400 dark:text-stone-400">
                          {t('orders.orderNo')}:{' '}
                        </span>
                        <span className="font-mono font-medium text-gray-800 dark:text-stone-200">
                          {order.orderNo}
                        </span>
                      </span>
                      <span>
                        <span className="text-gray-400 dark:text-stone-400">
                          {t('orders.createdAt')}:{' '}
                        </span>
                        <span>{createdDateStr}</span>
                      </span>
                    </div>

                    <div className="flex items-center gap-3">
                      {isPending && (
                        <OrderCountdown
                          createTime={order.createTime}
                          onExpire={() => loadOrders(activeTab)}
                        />
                      )}
                      {getStatusTag(order.status)}
                    </div>
                  </div>

                  {/* 订单条目快照 */}
                  <div className="divide-y divide-sand-100 px-4 py-2 sm:px-6 dark:divide-stone-800">
                    {order.items?.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center gap-4 py-3 text-sm"
                      >
                        <img
                          src={item.productImage}
                          alt={item.productName}
                          className="h-16 w-16 flex-shrink-0 rounded-lg border border-sand-200 object-cover dark:border-stone-700"
                        />
                        <div className="flex-1 min-w-0">
                          <p className="line-clamp-2 font-medium text-gray-800 dark:text-stone-200">
                            {item.productName}
                          </p>
                          <p className="mt-1 text-xs text-gray-400 dark:text-stone-400">
                            {order.currency === 'CNY' ? '¥' : 'A$'}{' '}
                            {Number(item.price).toFixed(2)} × {item.qty}
                          </p>
                        </div>
                        <div className="text-right font-medium text-gray-700 dark:text-stone-300">
                          {order.currency === 'CNY' ? '¥' : 'A$'}{' '}
                          {(Number(item.price) * item.qty).toFixed(2)}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* 订单底部金额与操作栏 */}
                  <div className="flex flex-wrap items-center justify-between gap-3 border-t border-sand-100 bg-sand-50/30 px-4 py-3 text-sm sm:px-6 dark:border-stone-800 dark:bg-stone-800/40">
                    <div className="flex items-baseline gap-2">
                      <span className="text-xs text-gray-500 dark:text-stone-400">
                        {t('orders.total')}:
                      </span>
                      <span className="text-base font-bold text-gray-900 dark:text-stone-100">
                        {order.currency === 'CNY' ? '¥' : 'A$'}{' '}
                        {Number(order.payAmount).toFixed(2)}
                      </span>
                      <span className="text-xs text-gray-400 dark:text-stone-500">
                        ({order.currency})
                      </span>
                    </div>

                    <Space size="middle">
                      {isPending && (
                        <>
                          <Popconfirm
                            title={t('orders.cancelConfirm')}
                            onConfirm={() => handleCancelOrder(order.orderNo)}
                            okText="Yes"
                            cancelText="No"
                          >
                            <Button
                              danger
                              size="small"
                              loading={cancellingOrderNo === order.orderNo}
                            >
                              {t('orders.cancelBtn')}
                            </Button>
                          </Popconfirm>

                          <Button
                            type="primary"
                            size="small"
                            onClick={() => navigate('/checkout')}
                          >
                            {t('orders.payNow')}
                          </Button>
                        </>
                      )}
                    </Space>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>
    </div>
  );
}
