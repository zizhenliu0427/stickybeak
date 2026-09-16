import { get, put } from './api';

export interface AnalyticsSummary {
  totalGmv: number;
  todayGmv: number;
  totalOrders: number;
  paidOrders: number;
  todayOrders: number;
  pendingShipmentOrders: number;
  pendingPaymentOrders: number;
}

export interface SalesTrendItem {
  date: string;
  orderCount: number;
  gmv: number;
}

export interface TopProduct {
  productId: number;
  productName: string;
  productImage: string | null;
  totalQuantity: number;
  totalRevenue: number;
}

export interface AdminOrderItem {
  id: number;
  productId: number;
  productName: string;
  productImage: string;
  price: number;
  qty: number;
}

export interface AdminOrder {
  id: number;
  orderNo: string;
  userId: number;
  addressSnapshot: string;
  totalAmount: number;
  payAmount: number;
  currency: string;
  exchangeRateAtPay: number;
  status: string;
  payTime: string | null;
  shipTime: string | null;
  completeTime: string | null;
  remark: string | null;
  createTime: string;
  updateTime: string;
  items: AdminOrderItem[];
  allowedNextStatuses: string[];
}

export interface AdminProduct {
  id: number;
  name: string;
  slug: string;
  description: string;
  categoryId: number;
  categoryName: string | null;
  price: number;
  priceCents: number;
  stock: number;
  sales: number;
  status: number; // 0: active, 1: unlisted
  featured: number;
  coverImage: string | null;
  createTime: string;
  updateTime: string;
  stockStatus: 'out_of_stock' | 'critical' | 'low' | 'normal';
}

export interface StockAlertSummary {
  totalAlerts: number;
  outOfStockCount: number;
  criticalCount: number;
  lowStockCount: number;
  alertProducts: AdminProduct[];
}

export interface PageResult<T> {
  current: number;
  size: number;
  total: number;
  records: T[];
}

// ================= Analytics API =================
export async function getAnalyticsSummary(): Promise<AnalyticsSummary> {
  return get<AnalyticsSummary>('/orders/admin/analytics/summary');
}

export async function getSalesTrend(days: number = 7): Promise<SalesTrendItem[]> {
  return get<SalesTrendItem[]>(`/orders/admin/analytics/trend?days=${days}`);
}

export async function getTopProducts(limit: number = 10): Promise<TopProduct[]> {
  return get<TopProduct[]>(`/orders/admin/analytics/top-products?limit=${limit}`);
}

// ================= Order Operations API =================
export interface AdminOrderQueryParams {
  page?: number;
  size?: number;
  orderNo?: string;
  status?: string;
  userId?: number;
  startDate?: string;
  endDate?: string;
}

export async function getAdminOrders(params: AdminOrderQueryParams): Promise<PageResult<AdminOrder>> {
  const query = new URLSearchParams();
  if (params.page) query.append('page', String(params.page));
  if (params.size) query.append('size', String(params.size));
  if (params.orderNo) query.append('orderNo', params.orderNo);
  if (params.status) query.append('status', params.status);
  if (params.userId) query.append('userId', String(params.userId));
  if (params.startDate) query.append('startDate', params.startDate);
  if (params.endDate) query.append('endDate', params.endDate);
  return get<PageResult<AdminOrder>>(`/orders/admin/page?${query.toString()}`);
}

export async function updateAdminOrderStatus(
  orderNo: string,
  payload: { status: string; trackingNo?: string; remark?: string }
): Promise<void> {
  return put<void>(`/orders/admin/${orderNo}/status`, payload);
}

// ================= Product & Stock API =================
export interface AdminProductQueryParams {
  page?: number;
  size?: number;
  keyword?: string;
  categoryId?: number;
  status?: number;
  stockAlert?: number;
  threshold?: number;
}

export async function getAdminProducts(params: AdminProductQueryParams): Promise<PageResult<AdminProduct>> {
  const query = new URLSearchParams();
  if (params.page) query.append('page', String(params.page));
  if (params.size) query.append('size', String(params.size));
  if (params.keyword) query.append('keyword', params.keyword);
  if (params.categoryId) query.append('categoryId', String(params.categoryId));
  if (params.status !== undefined && params.status !== null) query.append('status', String(params.status));
  if (params.stockAlert) query.append('stockAlert', String(params.stockAlert));
  if (params.threshold) query.append('threshold', String(params.threshold));
  return get<PageResult<AdminProduct>>(`/products/admin/page?${query.toString()}`);
}

export async function updateProductStatus(id: number, status: number): Promise<void> {
  return put<void>(`/products/admin/${id}/status`, { status });
}

export async function updateProductStock(
  id: number,
  payload: { stock?: number; delta?: number }
): Promise<void> {
  return put<void>(`/products/admin/${id}/stock`, payload);
}

export async function updateProductPrice(id: number, price: number): Promise<void> {
  return put<void>(`/products/admin/${id}/price`, { price });
}

export async function getStockAlerts(threshold: number = 10): Promise<StockAlertSummary> {
  return get<StockAlertSummary>(`/products/admin/stock-alerts?threshold=${threshold}`);
}
