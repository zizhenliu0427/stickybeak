import { describe, expect, it } from 'vitest';
import { formatPrice } from '../lib/format';

describe('formatPrice', () => {
  it('formats AUD cents', () => {
    expect(formatPrice(1299, 'AUD')).toBe('$12.99');
  });

  it('formats CNY cents', () => {
    expect(formatPrice(1299, 'CNY')).toContain('12.99');
  });
});
