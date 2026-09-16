import { describe, it, expect, beforeEach } from 'vitest';
import themeReducer, { setTheme, applyThemeToDOM } from '../features/theme/themeSlice';

describe('themeSlice & Dark Reader compatibility', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    const lock = document.querySelector('meta[name="darkreader-lock"]');
    if (lock) lock.remove();
  });

  it('主题切换：显式暗黑模式', () => {
    let state = themeReducer(undefined, { type: '@@INIT' });
    state = themeReducer(state, setTheme('dark'));
    expect(state.mode).toBe('dark');
    expect(state.isDark).toBe(true);
    expect(localStorage.getItem('sb_theme')).toBe('dark');
  });

  it('主题切换：显式明亮模式', () => {
    let state = themeReducer(undefined, { type: '@@INIT' });
    state = themeReducer(state, setTheme('light'));
    expect(state.mode).toBe('light');
    expect(state.isDark).toBe(false);
    expect(localStorage.getItem('sb_theme')).toBe('light');
  });

  it('Dark Reader 兼容性：原生暗黑模式自动注入 darkreader-lock 元标签', () => {
    applyThemeToDOM(true);
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.style.colorScheme).toBe('dark');

    const lockMeta = document.querySelector('meta[name="darkreader-lock"]');
    expect(lockMeta).not.toBeNull();
  });

  it('Dark Reader 兼容性：切换浅色模式时移除 darkreader-lock', () => {
    // 先进暗黑模式获得 lock
    applyThemeToDOM(true);
    expect(document.querySelector('meta[name="darkreader-lock"]')).not.toBeNull();

    // 切换为浅色
    applyThemeToDOM(false);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.style.colorScheme).toBe('light');

    const lockMeta = document.querySelector('meta[name="darkreader-lock"]');
    expect(lockMeta).toBeNull();
  });
});
