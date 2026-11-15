import { formatPercent } from '../format';

interface ProgressRingProps {
  /** 0..100, or null = not measured (drawn as an empty dashed ring, never as 0%). */
  value: number | null;
  size?: number;
  stroke?: number;
  /** Show the percentage inside the ring. */
  showValue?: boolean;
  /** Accessible name, e.g. "پیشرفت نتیجه «مطالعه ۱۰ کتاب»". */
  label: string;
  tone?: 'accent' | 'green' | 'purple' | 'blue' | 'teal';
}

/** Circular progress (SVG). Exposes the value to assistive tech via role="img" + aria-label. */
export function ProgressRing({ value, size = 64, stroke = 6, showValue = false, label, tone = 'accent' }: ProgressRingProps) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const clamped = value == null ? 0 : Math.max(0, Math.min(100, value));
  const text = value == null ? 'سنجیده نشده' : formatPercent(value);
  return (
    <span className={`ring ring--${tone}`} role="img" aria-label={`${label}: ${text}`} title={`${label}: ${text}`} style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        <circle className={value == null ? 'ring__track ring__track--empty' : 'ring__track'} cx={size / 2} cy={size / 2} r={r} strokeWidth={stroke} fill="none" />
        {value != null && (
          <circle
            className="ring__value"
            cx={size / 2}
            cy={size / 2}
            r={r}
            strokeWidth={stroke}
            fill="none"
            strokeLinecap="round"
            strokeDasharray={`${(clamped / 100) * c} ${c}`}
            transform={`rotate(-90 ${size / 2} ${size / 2})`}
          />
        )}
      </svg>
      {showValue && (
        <span className="ring__text" aria-hidden="true">
          {value == null ? '—' : formatPercent(Math.round(value))}
        </span>
      )}
    </span>
  );
}
