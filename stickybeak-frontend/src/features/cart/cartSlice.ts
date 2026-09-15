import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';

export interface CartItem {
  productId: number;
  name: string;
  imageUrl: string;
  /** unit price in cents (base currency AUD) */
  priceCents: number;
  qty: number;
}

interface CartState {
  items: CartItem[];
}

const initialState: CartState = {
  items: [],
};

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    setCart(state, action: PayloadAction<CartItem[]>) {
      state.items = action.payload;
    },
    upsertItem(state, action: PayloadAction<CartItem>) {
      const idx = state.items.findIndex((i) => i.productId === action.payload.productId);
      if (idx >= 0) {
        state.items[idx] = action.payload;
      } else {
        state.items.push(action.payload);
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

export const { setCart, upsertItem, removeItem, clearCart } = cartSlice.actions;

export const selectCartCount = (s: { cart: CartState }) =>
  s.cart.items.reduce((sum, i) => sum + i.qty, 0);

export const selectCartTotalCents = (s: { cart: CartState }) =>
  s.cart.items.reduce((sum, i) => sum + i.priceCents * i.qty, 0);

export default cartSlice.reducer;
