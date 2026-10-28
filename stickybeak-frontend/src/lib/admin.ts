import { get, post, put } from './api';

/**
 * 管理端 API 客户端。所有实体 ID 均为字符串（后端雪花 Long 序列化为字符串，防 JS 精度丢失）；
 * MP Page / 计数类 Long 字段在边界处用 Number() 归一化。
 */

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
  productId: string;
  productName: string;
  productImage: string | null;
  totalQuantity: number;
  totalRevenue: number;
}

export interface AdminOrderItem {
  id: string;
  productId: string;
  productName: string;
  productImage: string;
  price: number;
  qty: number;
}

export interface AdminOrder {
  id: string;
  orderNo: string;
  userId: string;
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
  id: string;
  name: string;
  slug: string;
  description: string;
  categoryId: string;
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

/** MP Page 的 current/size/total 是 Long → 序列化为字符串，归一化回 number */
function normalizePage<T>(raw: PageResult<T>): PageResult<T> {
  return {
    ...raw,
    current: Number(raw.current),
    size: Number(raw.size),
    total: Number(raw.total),
  };
}

// ================= Analytics API =================
export async function getAnalyticsSummary(): Promise<AnalyticsSummary> {
  const raw = await get<Record<string, unknown>>('/orders/admin/analytics/summary');
  // 后端 Long 计数被序列化为字符串，这里归一化回 number
  return {
    ...raw,
    totalOrders: Number(raw.totalOrders),
    paidOrders: Number(raw.paidOrders),
    todayOrders: Number(raw.todayOrders),
    pendingShipmentOrders: Number(raw.pendingShipmentOrders),
    pendingPaymentOrders: Number(raw.pendingPaymentOrders),
  } as AnalyticsSummary;
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
  userId?: string;
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
  const raw = await get<PageResult<AdminOrder>>(`/orders/admin/page?${query.toString()}`);
  return normalizePage(raw);
}

export async function updateAdminOrderStatus(
  orderNo: string,
  payload: { status: string; trackingNo?: string; remark?: string }
): Promise<void> {
  return put<void>(`/orders/admin/${orderNo}/status`, payload);
}

/** 全额退款（已支付 / 备货中）；对退款中的订单再次调用即重试 */
export async function refundAdminOrder(orderNo: string, reason?: string): Promise<void> {
  return post<void>(`/orders/admin/${orderNo}/refund`, { reason });
}

// ================= Product & Stock API =================
export interface AdminProductQueryParams {
  page?: number;
  size?: number;
  keyword?: string;
  categoryId?: string;
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
  const raw = await get<PageResult<AdminProduct>>(`/products/admin/page?${query.toString()}`);
  return normalizePage(raw);
}

export async function updateProductStatus(id: string, status: number): Promise<void> {
  return put<void>(`/products/admin/${id}/status`, { status });
}

export async function updateProductStock(
  id: string,
  payload: { stock?: number; delta?: number }
): Promise<void> {
  return put<void>(`/products/admin/${id}/stock`, payload);
}

export async function updateProductPrice(id: string, price: number): Promise<void> {
  return put<void>(`/products/admin/${id}/price`, { price });
}

export async function getStockAlerts(threshold: number = 10): Promise<StockAlertSummary> {
  return get<StockAlertSummary>(`/products/admin/stock-alerts?threshold=${threshold}`);
}

// ================= Product Create & Upload API =================

export interface AdminCategory {
  id: string;
  name: string;
  slug: string;
  sortOrder: number;
}

export interface PresignedUpload {
  objectKey: string;
  uploadUrl: string;
  publicUrl: string;
}

export interface ProductCreatePayload {
  name: string;
  slug?: string;
  categoryId: string;
  /** AUD 元（后端 BigDecimal） */
  price: number;
  stock: number;
  description?: string;
  /** 媒体列表（优先）；第一张图片为封面 */
  media?: { url: string; mediaType: 'image' | 'video' }[];
  /** 旧字段：纯图片 URL 列表 */
  images?: string[];
  tags?: string[];
  featured?: boolean;
}

export async function getAdminCategories(): Promise<AdminCategory[]> {
  return get<AdminCategory[]>('/categories');
}

/** 申请 MinIO 预签名上传 URL（浏览器直传） */
export async function getUploadUrl(filename: string, contentType: string): Promise<PresignedUpload> {
  return post<PresignedUpload>('/products/admin/upload-url', { filename, contentType });
}

/** PUT 文件到预签名 URL（直传 MinIO，不经网关） */
export async function uploadToPresigned(uploadUrl: string, file: File): Promise<void> {
  const resp = await fetch(uploadUrl, { method: 'PUT', body: file });
  if (!resp.ok) {
    throw new Error(`upload failed: ${resp.status}`);
  }
}

export async function createProduct(payload: ProductCreatePayload): Promise<{ id: string }> {
  return post<{ id: string }>('/products/admin', payload);
}
