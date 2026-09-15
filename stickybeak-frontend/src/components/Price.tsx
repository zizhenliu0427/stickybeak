import { useAppSelector } from '../app/hooks';
import { formatPrice } from '../lib/format';

/**
 * 全站唯一的价格展示组件：AUD cents → 当前币种。
 * 硬性规范：展示只走 formatPrice；换算只走这里（Sprint 4 汇率落后端后改 rate 来源即可）。
 */
export default function Price({
  cents,
  className = '',
}: {
  /** AUD cents（基准币种） */
  cents: number;
  className?: string;
}) {
  const currency = useAppSelector((s) => s.currency.currency);
  const rate = useAppSelector((s) => s.currency.audToCny);
  const displayCents = currency === 'CNY' ? Math.round(cents * rate) : cents;
  return <span className={className}>{formatPrice(displayCents, currency)}</span>;
}
