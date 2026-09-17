import { del, get, post, put } from './api';

/**
 * 注意：所有 ID 均为字符串（后端雪花 Long 序列化为字符串，防 JS 精度丢失）。
 */
export interface ServerCartItem {
  id: string;
  productId: string;
  slug: string;
  name: string;
  imageUrl: string;
  priceCents: number;
  priceAtAddCents?: number;
  qty: number;
  stock: number;
}

export interface ServerCartVO {
  id: string;
  items: ServerCartItem[];
  totalCents: number;
  itemCount: number;
}

export interface MergeItemPayload {
  productId: string;
  qty: number;
  priceCents: number;
}

export interface WishlistItemVO {
  id: string;
  productId: string;
  slug: string;
  name: string;
  imageUrl: string;
  priceCents: number;
  stock: number;
}

// Cart endpoints
export async function getCartApi(): Promise<ServerCartVO> {
  return get<ServerCartVO>('/cart');
}

export async function addCartItemApi(productId: string, qty: number): Promise<ServerCartVO> {
  return post<ServerCartVO>('/cart/items', { productId, qty });
}

export async function updateCartItemApi(itemId: string, qty: number): Promise<ServerCartVO> {
  return put<ServerCartVO>(`/cart/items/${itemId}`, { qty });
}

export async function removeCartItemApi(itemId: string): Promise<ServerCartVO> {
  return del<ServerCartVO>(`/cart/items/${itemId}`);
}

export async function clearCartApi(): Promise<void> {
  return del<void>('/cart');
}

export async function mergeCartApi(items: MergeItemPayload[]): Promise<ServerCartVO> {
  return post<ServerCartVO>('/cart/merge', { items });
}

// Wishlist endpoints
export async function listWishlistApi(): Promise<WishlistItemVO[]> {
  return get<WishlistItemVO[]>('/wishlist');
}

export async function toggleWishlistApi(productId: string): Promise<{ productId: string; inWishlist: boolean }> {
  return post<{ productId: string; inWishlist: boolean }>(`/wishlist/toggle/${productId}`);
}

export async function moveToCartApi(productId: string): Promise<void> {
  return post<void>(`/wishlist/move-to-cart/${productId}`);
}
