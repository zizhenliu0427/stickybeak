import type { Currency } from '../features/currency/currencySlice';

const LOCALES: Record<Currency, string> = {
  AUD: 'en-AU',
  CNY: 'zh-CN',
};

const ISO: Record<Currency, string> = {
  AUD: 'AUD',
  CNY: 'CNY',
};

/** The ONLY place components should turn cents into display text. */
export function formatPrice(cents: number, currency: Currency): string {
  return new Intl.NumberFormat(LOCALES[currency], {
    style: 'currency',
    currency: ISO[currency],
  }).format(cents / 100);
}
