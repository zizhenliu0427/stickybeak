import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export type ThemeMode = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'sb_theme';

export function getSystemIsDark(): boolean {
  if (typeof window !== 'undefined' && window.matchMedia) {
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
  return false;
}

export function applyThemeToDOM(isDark: boolean) {
  if (typeof document === 'undefined') return;

  const root = document.documentElement;

  if (isDark) {
    root.classList.add('dark');
    root.style.colorScheme = 'dark';

    // Dark Reader 插件兼容：当启用网站原生暗黑模式时，注入 <meta name="darkreader-lock">
    // 告知 Dark Reader 本站已有暗黑主题，防止其进行二次反色导致界面发白或失真
    let lockMeta = document.querySelector('meta[name="darkreader-lock"]');
    if (!lockMeta) {
      lockMeta = document.createElement('meta');
      lockMeta.setAttribute('name', 'darkreader-lock');
      document.head.appendChild(lockMeta);
    }
  } else {
    root.classList.remove('dark');
    root.style.colorScheme = 'light';

    // 浅色模式下移除 lock，允许用户使用 Dark Reader 自由深色化
    const lockMeta = document.querySelector('meta[name="darkreader-lock"]');
    if (lockMeta) {
      lockMeta.remove();
    }
  }
}

function getInitialThemeMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'light' || saved === 'dark' || saved === 'system') {
      return saved;
    }
  } catch {
    // ignore
  }
  return 'system';
}

interface ThemeState {
  mode: ThemeMode;
  isDark: boolean;
}

const initialMode = getInitialThemeMode();
const initialIsDark = initialMode === 'dark' || (initialMode === 'system' && getSystemIsDark());

// 立即在模块加载时应用，防止首屏闪烁
if (typeof window !== 'undefined') {
  applyThemeToDOM(initialIsDark);
}

const initialState: ThemeState = {
  mode: initialMode,
  isDark: initialIsDark,
};

export const themeSlice = createSlice({
  name: 'theme',
  initialState,
  reducers: {
    setTheme: (state, action: PayloadAction<ThemeMode>) => {
      state.mode = action.payload;
      state.isDark =
        action.payload === 'dark' || (action.payload === 'system' && getSystemIsDark());
      try {
        localStorage.setItem(STORAGE_KEY, action.payload);
      } catch {
        // ignore
      }
      applyThemeToDOM(state.isDark);
    },
    syncSystemTheme: (state) => {
      if (state.mode === 'system') {
        const isDark = getSystemIsDark();
        state.isDark = isDark;
        applyThemeToDOM(isDark);
      }
    },
  },
});

export const { setTheme, syncSystemTheme } = themeSlice.actions;
export default themeSlice.reducer;
