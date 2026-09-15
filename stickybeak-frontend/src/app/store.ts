import { configureStore } from '@reduxjs/toolkit';
import authReducer from '../features/auth/authSlice';
import cartReducer, { persistCart } from '../features/cart/cartSlice';
import currencyReducer from '../features/currency/currencySlice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    cart: cartReducer,
    currency: currencyReducer,
  },
});

// 购物车变更持久化到 localStorage（游客车快照）
store.subscribe(() => persistCart(store.getState()));

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
