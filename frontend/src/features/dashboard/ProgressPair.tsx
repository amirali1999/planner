import { formatNumber, formatPercent, formatTimestamp } from '../../shared/format';
import type { ProgressValues } from './types';

function Metric({ title, value, caption }: { title: string; value: number | null; caption: string }) {
  return (
    <div className="metric">
      <p className="metric__title">{title}</p>
      {value == null ? (
        <p className="metric__value metric__value--empty">سنجیده نشده</p>
      ) : (
        <>
          <p className="metric__value">{formatPercent(value)}</p>
          <meter min={0} max={100} value={value} aria-label={`${title}: ${formatPercent(value)}`} />
        </>
      )}
      <p className="metric__caption">{caption}</p>
    </div>
  );
}

/**
 * Outcome and Execution side by side, exactly as computed by the backend. The UI formats only;
 * it never averages or derives a combined number.
 */
export function ProgressPair({ progress }: { progress: ProgressValues }) {
  const outcomeCaption =
    progress.outcomeSource === 'own_key_results' && progress.outcomeProgress != null
      ? `میانگین وزنی ${formatNumber(progress.keyResultsCount)} نتیجه کلیدی` +
        (progress.measuredAt ? ` · آخرین اندازه‌گیری ${formatTimestamp(progress.measuredAt)}` : ' · فقط مقدار پایه ثبت شده')
      : progress.outcomeSource === 'child_rollup' && progress.outcomeProgress != null
        ? `بدون نتیجه کلیدی مستقیم؛ میانگین ساده پیشرفت نتیجه ${formatNumber(progress.contributingChildCount)} زیرهدف فعال`
        : 'نه این هدف نتیجه کلیدی فعال دارد و نه زیرهدف فعالی با پیشرفت نتیجه.';
  const executionCaption =
    progress.executionProgress == null
      ? 'کار فعالی به این هدف یا زیرهدف‌های فعالش وصل نیست.'
      : `${formatNumber(progress.doneTaskCount)} از ${formatNumber(progress.activeTaskCount)} کار انجام شده (این هدف و زیرهدف‌های فعال، هر کار یک بار)`;
  return (
    <div className="metrics">
      <Metric title="پیشرفت نتیجه (Outcome)" value={progress.outcomeProgress} caption={outcomeCaption} />
      <Metric title="پیشرفت اجرا (Execution)" value={progress.executionProgress} caption={executionCaption} />
    </div>
  );
}

export const PROGRESS_NOTE = 'این دو عدد جدا سنجیده می‌شوند و با هم میانگین گرفته نمی‌شوند. وضعیت هدف را خودتان تعیین می‌کنید.';
