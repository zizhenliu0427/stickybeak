import { MinusOutlined, PlusOutlined } from '@ant-design/icons';

/** 数量步进器（storefront 风格，Tailwind） */
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
    <div className="inline-flex items-center rounded-lg border border-sand-200">
      <button
        type="button"
        aria-label="decrease"
        disabled={value <= min}
        onClick={() => onChange(clamp(value - 1))}
        className="flex h-9 w-9 items-center justify-center text-gray-600 disabled:text-gray-300"
      >
        <MinusOutlined />
      </button>
      <span className="w-10 text-center text-sm font-medium">{value}</span>
      <button
        type="button"
        aria-label="increase"
        disabled={max != null && value >= max}
        onClick={() => onChange(clamp(value + 1))}
        className="flex h-9 w-9 items-center justify-center text-gray-600 disabled:text-gray-300"
      >
        <PlusOutlined />
      </button>
    </div>
  );
}
