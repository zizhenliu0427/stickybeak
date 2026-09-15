import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';

export type Currency = 'AUD' | 'CNY';

interface CurrencyState {
  currency: Currency;
  /** AUD -> CNY rate fetched from backend; 1 when currency is AUD */
  audToCny: number;
}

const initialState: CurrencyState = {
  currency: (localStorage.getItem('sb_currency') as Currency) || 'AUD',
  // 开发期固定演示汇率；Sprint 4 Issue 4.7 改为后端 t_exchange_rate 每日刷新值
  audToCny: 4.75,
};

const currencySlice = createSlice({
  name: 'currency',
  initialState,
  reducers: {
    setCurrency(state, action: PayloadAction<Currency>) {
      state.currency = action.payload;
      localStorage.setItem('sb_currency', action.payload);
    },
    setRate(state, action: PayloadAction<number>) {
      state.audToCny = action.payload;
    },
  },
});

export const { setCurrency, setRate } = currencySlice.actions;

/** Convert AUD cents into the active currency. Rendering uses formatPrice(). */
export const convertCents = (
  state: { currency: CurrencyState },
  audCents: number,
): { currency: Currency; cents: number } => {
  const { currency, audToCny } = state.currency;
  return currency === 'CNY'
    ? { currency, cents: Math.round(audCents * audToCny) }
    : { currency, cents: audCents };
};

export default currencySlice.reducer;
