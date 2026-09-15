import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import type { Locale } from '../../lib/i18n';

const STORAGE_KEY = 'sb_locale';

function getInitialLocale(): Locale {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'en-AU' || saved === 'zh-CN') {
      return saved;
    }
    const navLang = typeof navigator !== 'undefined' ? navigator.language : '';
    if (navLang.toLowerCase().startsWith('zh')) {
      return 'zh-CN';
    }
  } catch {
    // ignore
  }
  return 'en-AU';
}

interface LocaleState {
  locale: Locale;
}

const initialState: LocaleState = {
  locale: getInitialLocale(),
};

export const localeSlice = createSlice({
  name: 'locale',
  initialState,
  reducers: {
    setLocale: (state, action: PayloadAction<Locale>) => {
      state.locale = action.payload;
      try {
        localStorage.setItem(STORAGE_KEY, action.payload);
      } catch {
        // ignore
      }
    },
  },
});

export const { setLocale } = localeSlice.actions;
export default localeSlice.reducer;
