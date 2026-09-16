import { MinusOutlined, PlusOutlined } from '@ant-design/icons';

/** 数量步进器（storefront 风格，已针对移动端触控优化 >= 44px） */
export default function QtyStepper({
  value,
  onChange,
  max,
  min = 1,
}: {
  value: number;
  onChange: (v: number) => void;
  max?: number;
  min?: number;
}) {
  const clamp = (v: number) => Math.max(min, max != null ? Math.min(max, v) : v);
  return (
    <div className="inline-flex items-center rounded-lg border border-sand-200 bg-white dark:border-stone-700 dark:bg-stone-900">
      <button
        type="button"
        aria-label="decrease"
        disabled={value <= min}
        onClick={() => onChange(clamp(value - 1))}
        className="flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-l-lg text-gray-600 transition-colors hover:bg-sand-100 disabled:cursor-not-allowed disabled:text-gray-300 disabled:hover:bg-transparent dark:text-stone-300 dark:hover:bg-stone-800 dark:disabled:text-stone-600"
      >
        <MinusOutlined />
      </button>
      <span className="w-10 text-center text-sm font-medium text-gray-900 dark:text-stone-100">{value}</span>
      <button
        type="button"
        aria-label="increase"
        disabled={max != null && value >= max}
        onClick={() => onChange(clamp(value + 1))}
        className="flex h-11 w-11 min-h-[44px] min-w-[44px] items-center justify-center rounded-r-lg text-gray-600 transition-colors hover:bg-sand-100 disabled:cursor-not-allowed disabled:text-gray-300 disabled:hover:bg-transparent dark:text-stone-300 dark:hover:bg-stone-800 dark:disabled:text-stone-600"
      >
        <PlusOutlined />
      </button>
    </div>
  );
}
