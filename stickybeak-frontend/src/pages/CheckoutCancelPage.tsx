import { useNavigate } from 'react-router-dom';
import { Button, Card, Typography } from 'antd';
import { CloseCircleFilled, ShoppingCartOutlined, RedoOutlined } from '@ant-design/icons';
import { useI18n } from '../lib/i18n';

export default function CheckoutCancelPage() {
  const navigate = useNavigate();
  const { t } = useI18n();

  return (
    <div className="mx-auto max-w-lg py-16">
      <Card className="rounded-2xl border-sand-200 text-center dark:border-stone-800 dark:bg-stone-900">
        <div className="flex justify-center">
          <CloseCircleFilled className="text-6xl text-amber-500" />
        </div>

        <Typography.Title level={2} className="!mt-4 !mb-2 dark:text-stone-100">
          {t('checkout.cancelTitle')}
        </Typography.Title>

        <p className="mx-auto max-w-sm text-sm text-gray-500 dark:text-stone-400">
          {t('checkout.cancelDesc')}
        </p>

        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Button
            type="primary"
            icon={<RedoOutlined />}
            size="large"
            onClick={() => navigate('/checkout')}
          >
            {t('checkout.retry')}
          </Button>
          <Button
            icon={<ShoppingCartOutlined />}
            size="large"
            onClick={() => navigate('/cart')}
          >
            {t('checkout.returnToCart')}
          </Button>
        </div>
      </Card>
    </div>
  );
}
