import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Checkbox,
  Empty,
  Form,
  Input,
  Modal,
  Radio,
  Tag,
  message,
} from 'antd';
import {
  CreditCardOutlined,
  AlipayCircleOutlined,
  WechatOutlined,
  PlusOutlined,
  LockOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { createAddress, fetchAddresses } from '../features/auth/authSlice';
import type { Address, AddressPayload } from '../features/auth/authSlice';
import { selectCartTotalCents } from '../features/cart/cartSlice';
import { createCheckout } from '../lib/order';
import { useI18n } from '../lib/i18n';
import Price from '../components/Price';

const AU_STATES = ['NSW', 'VIC', 'QLD', 'WA', 'SA', 'TAS', 'ACT', 'NT'];
const FREE_SHIPPING_THRESHOLD_CENTS = 4900;
const SHIPPING_FEE_CENTS = 895; // $8.95 AUD

export default function CheckoutPage() {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { t, locale } = useI18n();

  const cartItems = useAppSelector((s) => s.cart.items);
  const totalCents = useAppSelector(selectCartTotalCents);
  const currency = useAppSelector((s) => s.currency.currency);
  const audToCny = useAppSelector((s) => s.currency.audToCny);

  const [addresses, setAddresses] = useState<Address[]>([]);
  const [loadingAddr, setLoadingAddr] = useState(true);
  const [selectedAddrId, setSelectedAddrId] = useState<number | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<'card' | 'alipay' | 'wechat_pay'>('card');
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Quick Add Address Modal
  const [addrModalOpen, setAddrModalOpen] = useState(false);
  const [addrForm] = Form.useForm();

  const loadAddresses = () => {
    setLoadingAddr(true);
    dispatch(fetchAddresses())
      .unwrap()
      .then((list) => {
        setAddresses(list);
        if (list.length > 0) {
          const defaultAddr = list.find((a) => a.isDefault) || list[0];
          setSelectedAddrId(defaultAddr.id);
        }
      })
      .catch(() => setAddresses([]))
      .finally(() => setLoadingAddr(false));
  };

  useEffect(() => {
    loadAddresses();
  }, [dispatch]);

  if (cartItems.length === 0) {
    return (
      <div className="mx-auto max-w-xl py-16">
        <Empty description={t('common.emptyTrolley')}>
          <Button type="primary" onClick={() => navigate('/products')}>
            {t('common.haveABrowse')}
          </Button>
        </Empty>
      </div>
    );
  }

  const isFreeShipping = totalCents >= FREE_SHIPPING_THRESHOLD_CENTS;
  const shippingCents = isFreeShipping ? 0 : SHIPPING_FEE_CENTS;
  const grandTotalCents = totalCents + shippingCents;

  const handleSaveNewAddress = async () => {
    try {
      const values = (await addrForm.validateFields()) as AddressPayload;
      const created = await dispatch(createAddress(values)).unwrap();
      message.success(locale === 'en-AU' ? 'Address added' : '地址已添加');
      setAddrModalOpen(false);
      addrForm.resetFields();
      await loadAddresses();
      if (created?.id) {
        setSelectedAddrId(created.id);
      }
    } catch (e) {
      message.error(String(e));
    }
  };

  const handleCheckout = async () => {
    if (!selectedAddrId) {
      message.warning(t('checkout.selectAddressPrompt'));
      return;
    }

    setSubmitting(true);
    try {
      const res = await createCheckout({
        addressId: selectedAddrId,
        currency,
        paymentMethod,
        remark: remark.trim() || undefined,
      });

      if (res.checkoutUrl) {
        message.loading(t('checkout.processing'), 1.5);
        window.location.href = res.checkoutUrl;
      } else {
        navigate(`/checkout/success?order_no=${res.orderNo}&session_id=${res.sessionId}`);
      }
    } catch (e) {
      message.error(String(e));
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-stone-100">{t('checkout.title')}</h1>
        <Tag color="cyan" icon={<LockOutlined />}>
          SSL 256-bit Encrypted
        </Tag>
      </div>

      <div className="grid gap-6 md:grid-cols-[1fr_320px]">
        {/* Left Column: Details */}
        <div className="space-y-6">
          {/* 1. Shipping Address */}
          <Card
            title={t('checkout.shippingAddress')}
            extra={
              <Button
                type="link"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => {
                  addrForm.resetFields();
                  setAddrModalOpen(true);
                }}
              >
                {t('checkout.addAddress')}
              </Button>
            }
            loading={loadingAddr}
          >
            {addresses.length === 0 ? (
              <div className="py-4 text-center">
                <p className="text-gray-500 dark:text-stone-400">{t('checkout.selectAddressPrompt')}</p>
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  className="mt-2"
                  onClick={() => setAddrModalOpen(true)}
                >
                  {t('checkout.addAddress')}
                </Button>
              </div>
            ) : (
              <Radio.Group
                className="w-full space-y-3"
                value={selectedAddrId}
                onChange={(e) => setSelectedAddrId(e.target.value)}
              >
                {addresses.map((a) => (
                  <label
                    key={a.id}
                    className={`flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition-colors ${
                      selectedAddrId === a.id
                        ? 'border-brand-600 bg-brand-50/50 dark:border-brand-500 dark:bg-brand-950/20'
                        : 'border-sand-200 hover:border-brand-300 dark:border-stone-800'
                    }`}
                  >
                    <Radio value={a.id} className="mt-1" />
                    <div className="flex-1">
                      <div className="font-semibold text-gray-900 dark:text-stone-100">
                        {a.receiver} · {a.phone}
                        {a.isDefault && (
                          <Tag color="green" className="ml-2">
                            default
                          </Tag>
                        )}
                      </div>
                      <div className="mt-1 text-sm text-gray-600 dark:text-stone-300">
                        {a.detail}, {a.city}, {a.state} {a.postcode}, {a.country}
                      </div>
                    </div>
                  </label>
                ))}
              </Radio.Group>
            )}
          </Card>

          {/* 2. Payment Method */}
          <Card title={t('checkout.paymentMethod')}>
            <Radio.Group
              className="w-full space-y-3"
              value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value)}
            >
              <label
                className={`flex cursor-pointer items-center justify-between rounded-xl border p-4 transition-colors ${
                  paymentMethod === 'card'
                    ? 'border-brand-600 bg-brand-50/50 dark:border-brand-500 dark:bg-brand-950/20'
                    : 'border-sand-200 hover:border-brand-300 dark:border-stone-800'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Radio value="card" />
                  <CreditCardOutlined className="text-xl text-brand-700 dark:text-brand-400" />
                  <span className="font-medium text-gray-900 dark:text-stone-100">
                    {t('checkout.card')}
                  </span>
                </div>
                <div className="flex gap-1 text-xs text-gray-400">
                  <span>Visa</span> · <span>Mastercard</span> · <span>AMEX</span>
                </div>
              </label>

              <label
                className={`flex cursor-pointer items-center justify-between rounded-xl border p-4 transition-colors ${
                  paymentMethod === 'alipay'
                    ? 'border-brand-600 bg-brand-50/50 dark:border-brand-500 dark:bg-brand-950/20'
                    : 'border-sand-200 hover:border-brand-300 dark:border-stone-800'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Radio value="alipay" />
                  <AlipayCircleOutlined className="text-xl text-blue-500" />
                  <span className="font-medium text-gray-900 dark:text-stone-100">
                    {t('checkout.alipay')}
                  </span>
                </div>
                <Tag color="blue">CNY / AUD</Tag>
              </label>

              <label
                className={`flex cursor-pointer items-center justify-between rounded-xl border p-4 transition-colors ${
                  paymentMethod === 'wechat_pay'
                    ? 'border-brand-600 bg-brand-50/50 dark:border-brand-500 dark:bg-brand-950/20'
                    : 'border-sand-200 hover:border-brand-300 dark:border-stone-800'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Radio value="wechat_pay" />
                  <WechatOutlined className="text-xl text-emerald-500" />
                  <span className="font-medium text-gray-900 dark:text-stone-100">
                    {t('checkout.wechatPay')}
                  </span>
                </div>
                <Tag color="green">CNY / AUD</Tag>
              </label>
            </Radio.Group>
          </Card>

          {/* 3. Remarks */}
          <Card title={t('checkout.remark')}>
            <Input.TextArea
              rows={2}
              maxLength={200}
              placeholder={t('checkout.remarkPlaceholder')}
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
            />
          </Card>
        </div>

        {/* Right Column: Order Summary */}
        <div>
          <Card title={t('checkout.orderSummary')} className="sticky top-20">
            {/* Items review */}
            <div className="max-h-56 space-y-3 overflow-y-auto pr-1">
              {cartItems.map((item) => (
                <div key={item.productId} className="flex items-center gap-3 text-sm">
                  <img
                    src={item.imageUrl}
                    alt={item.name}
                    className="h-12 w-12 rounded-lg border border-sand-200 object-cover dark:border-stone-700"
                  />
                  <div className="flex-1 truncate">
                    <p className="truncate font-medium text-gray-800 dark:text-stone-200">{item.name}</p>
                    <p className="text-xs text-gray-400">× {item.qty}</p>
                  </div>
                  <Price cents={item.priceCents * item.qty} className="font-medium text-gray-900 dark:text-stone-100" />
                </div>
              ))}
            </div>

            <div className="mt-4 space-y-2 border-t border-sand-200 pt-4 text-sm dark:border-stone-800">
              <div className="flex justify-between text-gray-600 dark:text-stone-300">
                <span>{t('common.subtotal')}</span>
                <Price cents={totalCents} />
              </div>
              <div className="flex justify-between text-gray-600 dark:text-stone-300">
                <span>{t('checkout.shippingFee')}</span>
                <span>{isFreeShipping ? <span className="text-emerald-600 font-medium">FREE</span> : <Price cents={SHIPPING_FEE_CENTS} />}</span>
              </div>
              {currency === 'CNY' && (
                <div className="rounded-lg bg-sand-100 p-2 text-xs text-gray-600 dark:bg-stone-800 dark:text-stone-300">
                  {t('checkout.rateNotice', { rate: audToCny })}
                </div>
              )}
              <div className="flex justify-between border-t border-sand-200 pt-3 text-base font-bold text-gray-900 dark:border-stone-800 dark:text-stone-100">
                <span>{t('cart.total')}</span>
                <Price cents={grandTotalCents} className="text-lg text-accent-600 dark:text-accent-400" />
              </div>
            </div>

            <Button
              type="primary"
              block
              size="large"
              className="mt-6"
              loading={submitting}
              disabled={!selectedAddrId}
              onClick={handleCheckout}
            >
              {t('checkout.payNow')}
            </Button>

            <div className="mt-4 flex items-center justify-center gap-2 text-xs text-gray-400 dark:text-stone-500">
              <CheckCircleOutlined className="text-emerald-600" />
              <span>Stripe Official Hosted Checkout</span>
            </div>
          </Card>
        </div>
      </div>

      {/* Quick Add Address Modal */}
      <Modal
        title={t('checkout.addAddress')}
        open={addrModalOpen}
        onOk={handleSaveNewAddress}
        onCancel={() => setAddrModalOpen(false)}
        okText="Save"
        destroyOnHidden
      >
        <Form form={addrForm} layout="vertical" initialValues={{ country: 'Australia' }}>
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item name="receiver" label="Receiver" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label="Phone" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          </div>
          <Form.Item name="detail" label="Street address" rules={[{ required: true }]}>
            <Input placeholder="Unit / street number / street name" />
          </Form.Item>
          <div className="grid grid-cols-3 gap-x-4">
            <Form.Item name="city" label="City" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item
              name="state"
              label="State"
              rules={[
                { required: true },
                {
                  validator: (_, v) =>
                    !v || AU_STATES.includes(String(v).toUpperCase())
                      ? Promise.resolve()
                      : Promise.reject(new Error(`One of ${AU_STATES.join('/')}`)),
                },
              ]}
            >
              <Input placeholder="NSW" />
            </Form.Item>
            <Form.Item
              name="postcode"
              label="Postcode"
              rules={[{ required: true }, { pattern: /^\d{4}$/, message: '4 digits' }]}
            >
              <Input placeholder="2000" maxLength={4} />
            </Form.Item>
          </div>
          <Form.Item name="isDefault" valuePropName="checked">
            <Checkbox>Set as default address</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
