import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';

export interface CartItem {
  productId: number;
  slug: string;
  name: string;
  imageUrl: string;
  /** unit price in cents (base currency AUD) */
  priceCents: number;
  qty: number;
  /** 加购时库存快照，步进器上限 */
  stock: number;
}

interface CartState {
  items: CartItem[];
}

const STORAGE_KEY = 'sb_cart';

function loadInitial(): CartState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const items = JSON.parse(raw) as CartItem[];
      if (Array.isArray(items)) {
        return { items };
      }
    }
  } catch {
    // 损坏数据静默丢弃
  }
  return { items: [] };
}

const initialState: CartState = loadInitial();

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    setCart(state, action: PayloadAction<CartItem[]>) {
      state.items = action.payload;
    },
    /** 加购：同商品数量累加并封顶库存（登录合并同此规则，Sprint 3 后端对齐） */
    addItem(state, action: PayloadAction<CartItem>) {
      const existing = state.items.find((i) => i.productId === action.payload.productId);
      if (existing) {
        existing.qty = Math.min(existing.stock, existing.qty + action.payload.qty);
      } else {
        state.items.push({
          ...action.payload,
          qty: Math.min(action.payload.stock, action.payload.qty),
        });
      }
    },
    setQty(state, action: PayloadAction<{ productId: number; qty: number }>) {
      const item = state.items.find((i) => i.productId === action.payload.productId);
      if (item) {
        item.qty = Math.max(1, Math.min(item.stock, action.payload.qty));
      }
    },
    removeItem(state, action: PayloadAction<number>) {
      state.items = state.items.filter((i) => i.productId !== action.payload);
    },
    clearCart(state) {
      state.items = [];
    },
  },
});

export const { setCart, addItem, setQty, removeItem, clearCart } = cartSlice.actions;

export const selectCartCount = (s: { cart: CartState }) =>
  s.cart.items.reduce((sum, i) => sum + i.qty, 0);

export const selectCartTotalCents = (s: { cart: CartState }) =>
  s.cart.items.reduce((sum, i) => sum + i.priceCents * i.qty, 0);

export default cartSlice.reducer;

/** 在 store.ts 里订阅调用：变更即持久化（游客车本地快照，Sprint 3 登录后与服务端合并） */
export function persistCart(state: { cart: CartState }) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state.cart.items));
  } catch {
    // 存储满等异常静默
  }
}
