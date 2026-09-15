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
  audToCny: 1,
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
