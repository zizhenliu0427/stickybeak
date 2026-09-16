import { get, post } from './api';

export interface AddressSnapshot {
  id?: number;
  receiver: string;
  phone: string;
  email?: string;
  country?: string;
  state: string;
  city: string;
  postcode: string;
  detail: string;
  isDefault?: boolean;
}

export interface CreateOrderPayload {
  addressId?: number;
  address?: AddressSnapshot;
  currency?: 'AUD' | 'CNY';
  paymentMethod?: 'card' | 'alipay' | 'wechat_pay';
  remark?: string;
}

export interface CheckoutResponseVO {
  orderNo: string;
  totalAmount: number;
  payAmount: number;
  currency: string;
  checkoutUrl: string;
  sessionId: string;
}

export interface OrderItemVO {
  id: number;
  productId: number;
  productName: string;
  productImage: string;
  price: number;
  qty: number;
}

export interface OrderVO {
  id: number;
  orderNo: string;
  userId: number;
  addressSnapshot: string;
  totalAmount: number;
  payAmount: number;
  currency: string;
  exchangeRateAtPay: number;
  status: 'pending' | 'paid' | 'processing' | 'shipped' | 'completed' | 'cancelled';
  payTime?: string;
  shipTime?: string;
  completeTime?: string;
  remark?: string;
  createTime: string;
  items: OrderItemVO[];
}

export interface ExchangeRateVO {
  baseCurrency: string;
  quoteCurrency: string;
  rate: number;
  fetchedAt: string;
}

export async function createCheckout(payload: CreateOrderPayload): Promise<CheckoutResponseVO> {
  return post<CheckoutResponseVO>('/orders/checkout', payload);
}

export async function getOrderDetail(orderNo: string): Promise<OrderVO> {
  return get<OrderVO>(`/orders/${orderNo}`);
}

export async function getMyOrders(status?: string): Promise<OrderVO[]> {
  const query = status && status !== 'all' ? { status } : undefined;
  return get<OrderVO[]>('/orders/my', query);
}

export async function cancelOrder(orderNo: string): Promise<void> {
  return post<void>(`/orders/${orderNo}/cancel`, {});
}

export async function getExchangeRate(base = 'AUD', quote = 'CNY'): Promise<ExchangeRateVO> {
  return get<ExchangeRateVO>('/payments/exchange-rate', { base, quote });
}
