import { describe, it, expect, beforeEach } from 'vitest';
import localeReducer, { setLocale } from '../features/locale/localeSlice';
import { t } from '../lib/i18n';

describe('localeSlice & i18n', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('切换语言并在 localStorage 中持久化', () => {
    let state = localeReducer(undefined, { type: '@@INIT' });
    expect(state.locale).toBeDefined();

    state = localeReducer(state, setLocale('zh-CN'));
    expect(state.locale).toBe('zh-CN');
    expect(localStorage.getItem('sb_locale')).toBe('zh-CN');

    state = localeReducer(state, setLocale('en-AU'));
    expect(state.locale).toBe('en-AU');
    expect(localStorage.getItem('sb_locale')).toBe('en-AU');
  });

  it('字典正确翻译双语并使用澳洲英语地道词汇', () => {
    // 澳洲英语特色词汇：Trolley（非 Cart）、Catalogue 等
    expect(t('en-AU', 'nav.trolley')).toBe('Trolley');
    expect(t('en-AU', 'nav.shop')).toBe('Catalogue');
    expect(t('en-AU', 'hero.badge')).toBe("G'DAY, MATE!");

    // 中文翻译
    expect(t('zh-CN', 'nav.trolley')).toBe('购物车');
    expect(t('zh-CN', 'nav.shop')).toBe('全部商品');
  });

  it('支持参数插值替换', () => {
    expect(t('en-AU', 'footer.copyright', { year: 2026 })).toContain('2026');
    expect(t('zh-CN', 'cart.itemCount', { count: 3 })).toBe('共 3 件商品');
  });
});
