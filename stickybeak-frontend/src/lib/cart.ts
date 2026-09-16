import { del, get, post, put } from './api';

export interface ServerCartItem {
  id: number;
  productId: number;
  slug: string;
  name: string;
  imageUrl: string;
  priceCents: number;
  priceAtAddCents?: number;
  qty: number;
  stock: number;
}

export interface ServerCartVO {
  id: number;
  items: ServerCartItem[];
  totalCents: number;
  itemCount: number;
}

export interface MergeItemPayload {
  productId: number;
  qty: number;
  priceCents: number;
}

export interface WishlistItemVO {
  id: number;
  productId: number;
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

export async function addCartItemApi(productId: number, qty: number): Promise<ServerCartVO> {
  return post<ServerCartVO>('/cart/items', { productId, qty });
}

export async function updateCartItemApi(itemId: number, qty: number): Promise<ServerCartVO> {
  return put<ServerCartVO>(`/cart/items/${itemId}`, { qty });
}

export async function removeCartItemApi(itemId: number): Promise<ServerCartVO> {
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

export async function toggleWishlistApi(productId: number): Promise<{ productId: number; inWishlist: boolean }> {
  return post<{ productId: number; inWishlist: boolean }>(`/wishlist/toggle/${productId}`);
}

export async function moveToCartApi(productId: number): Promise<void> {
  return post<void>(`/wishlist/move-to-cart/${productId}`);
}
