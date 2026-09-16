import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import {
  addCartItemApi,
  clearCartApi,
  getCartApi,
  mergeCartApi,
  removeCartItemApi,
  updateCartItemApi,
  type ServerCartItem,
} from '../../lib/cart';

export interface CartItem {
  id?: number;
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
  loading: boolean;
  error: string | null;
}

const STORAGE_KEY = 'sb_cart';

function loadInitialItems(): CartItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const items = JSON.parse(raw) as CartItem[];
      if (Array.isArray(items)) {
        return items;
      }
    }
  } catch {
    // 损坏数据静默丢弃
  }
  return [];
}

const initialState: CartState = {
  items: loadInitialItems(),
  loading: false,
  error: null,
};

function mapServerItem(item: ServerCartItem): CartItem {
  return {
    id: item.id,
    productId: item.productId,
    slug: item.slug,
    name: item.name,
    imageUrl: item.imageUrl,
    priceCents: item.priceCents,
    qty: item.qty,
    stock: item.stock,
  };
}

// ===================================================================
// Async Thunks (Dual Mode: Server API if logged in, local if guest)
// ===================================================================

export const fetchCartAsync = createAsyncThunk('cart/fetchCart', async () => {
  const data = await getCartApi();
  return data.items.map(mapServerItem);
});

export const addCartItemAsync = createAsyncThunk<
  CartItem[],
  {
    product: {
      id: number;
      slug: string;
      name: string;
      imageUrl?: string;
      images?: string[];
      priceCents: number;
      stock: number;
    };
    qty?: number;
  },
  { state: { auth: { user: unknown }; cart: CartState } }
>('cart/addCartItem', async ({ product, qty = 1 }, { getState, dispatch }) => {
  const state = getState();
  const isLoggedIn = !!state.auth.user;
  if (isLoggedIn) {
    const data = await addCartItemApi(product.id, qty);
    return data.items.map(mapServerItem);
  } else {
    const cover = product.imageUrl || (product.images && product.images[0]) || '';
    dispatch(
      addItem({
        productId: product.id,
        slug: product.slug,
        name: product.name,
        imageUrl: cover,
        priceCents: product.priceCents,
        qty,
        stock: product.stock,
      }),
    );
    return getState().cart.items;
  }
});

export const updateQtyAsync = createAsyncThunk<
  CartItem[],
  { itemId?: number; productId: number; qty: number },
  { state: { auth: { user: unknown }; cart: CartState } }
>('cart/updateQty', async ({ itemId, productId, qty }, { getState, dispatch }) => {
  const state = getState();
  const isLoggedIn = !!state.auth.user;
  if (isLoggedIn && itemId != null) {
    const data = await updateCartItemApi(itemId, qty);
    return data.items.map(mapServerItem);
  } else {
    dispatch(setQty({ productId, qty }));
    return getState().cart.items;
  }
});

export const removeItemAsync = createAsyncThunk<
  CartItem[],
  { itemId?: number; productId: number },
  { state: { auth: { user: unknown }; cart: CartState } }
>('cart/removeItem', async ({ itemId, productId }, { getState, dispatch }) => {
  const state = getState();
  const isLoggedIn = !!state.auth.user;
  if (isLoggedIn && itemId != null) {
    const data = await removeCartItemApi(itemId);
    return data.items.map(mapServerItem);
  } else {
    dispatch(removeItem(productId));
    return getState().cart.items;
  }
});

export const clearCartAsync = createAsyncThunk<
  void,
  void,
  { state: { auth: { user: unknown }; cart: CartState } }
>('cart/clearCart', async (_, { getState, dispatch }) => {
  const state = getState();
  const isLoggedIn = !!state.auth.user;
  if (isLoggedIn) {
    try {
      await clearCartApi();
    } catch {
      // 忽略
    }
  }
  dispatch(clearCart());
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {}
});

export const mergeCartOnLogin = createAsyncThunk<
  CartItem[],
  void,
  { state: { cart: CartState } }
>('cart/mergeOnLogin', async (_, { getState }) => {
  const state = getState();
  const localItems = state.cart.items;
  const mergeItems = localItems.map((i) => ({
    productId: i.productId,
    qty: i.qty,
    priceCents: i.priceCents,
  }));
  const data = await mergeCartApi(mergeItems);
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {}
  return data.items.map(mapServerItem);
});

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    setCart(state, action: PayloadAction<CartItem[]>) {
      state.items = action.payload;
    },
    /** 加购：同商品数量累加并封顶库存 */
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
  extraReducers: (builder) => {
    builder
      // fetchCart
      .addCase(fetchCartAsync.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchCartAsync.fulfilled, (state, action) => {
        state.loading = false;
        state.items = action.payload;
      })
      .addCase(fetchCartAsync.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch cart';
      })
      // addItemAsync
      .addCase(addCartItemAsync.fulfilled, (state, action) => {
        state.items = action.payload;
      })
      // updateQtyAsync
      .addCase(updateQtyAsync.fulfilled, (state, action) => {
        state.items = action.payload;
      })
      // removeItemAsync
      .addCase(removeItemAsync.fulfilled, (state, action) => {
        state.items = action.payload;
      })
      // mergeCartOnLogin
      .addCase(mergeCartOnLogin.fulfilled, (state, action) => {
        state.items = action.payload;
      });
  },
});

export const { setCart, addItem, setQty, removeItem, clearCart } = cartSlice.actions;

export const selectCartCount = (s: { cart: CartState }) =>
  s.cart.items.reduce((sum, i) => sum + i.qty, 0);

export const selectCartTotalCents = (s: { cart: CartState }) =>
  s.cart.items.reduce((sum, i) => sum + i.priceCents * i.qty, 0);

export default cartSlice.reducer;

/** 在 store.ts 里订阅调用：仅在游客模式下持久化到 localStorage */
export function persistCart(state: { cart: CartState; auth?: { user: unknown } }) {
  if (state.auth?.user) {
    return; // 登录用户服务端是真实源，不写游客 localStorage
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state.cart.items));
  } catch {
    // 存储满等异常静默
  }
}
