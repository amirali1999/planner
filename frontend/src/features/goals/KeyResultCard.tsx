import { useId, useState } from 'react';
import { formatNumber, formatPercent, formatTimestamp } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { TextField } from '../../shared/ui/TextField';
import { useApiData } from '../../shared/ui/useApiData';
import { goalsApi } from './api';
import { CheckInForm } from './CheckInForm';
import { KeyResultEditForm } from './KeyResultEditForm';
import { buildTimeline, type TimelineEntry } from './krTimeline';
import type { KeyResult, KeyResultHistoryEntry } from './types';

function withUnit(value: number | string | null | undefined, unit: string | null): string {
  if (value == null) return '—';
  const text = formatNumber(Number(value));
  return unit ? `${text} ${unit}` : text;
}

const FIELD_LABELS: Record<string, string> = { name: 'نام', unit: 'واحد', baseline: 'پایه', target: 'هدف', weight: 'وزن' };
const NUMERIC = new Set(['baseline', 'target', 'weight']);

function describeChange(h: KeyResultHistoryEntry, unit: string | null): string {
  if (h.eventType === 'key_result_created') {
    return `ایجاد شد: پایه ${withUnit(h.after.baseline, unit)}، هدف ${withUnit(h.after.target, unit)}، وزن ${withUnit(h.after.weight, null)}`;
  }
  if (h.eventType === 'key_result_archived') return 'بایگانی شد (دیگر در پیشرفت نتیجه حساب نمی‌شود)';
  const parts = Object.keys(h.after).map((field) => {
    const fmt = (v: string | null | undefined) => (NUMERIC.has(field) ? withUnit(v, field === 'weight' ? null : unit) : (v ?? '—'));
    return `${FIELD_LABELS[field] ?? field}: ${fmt(h.before[field])} ← ${fmt(h.after[field])}`;
  });
  const structural = Object.keys(h.after).some((f) => NUMERIC.has(f));
  return `${structural ? 'بازتنظیم' : 'ویرایش'} — ${parts.join('، ')}`;
}

/** Newest-first timeline of measurements and audited changes; {@code limit} shows a preview. */
function KrTimeline({ kr, version, limit }: { kr: KeyResult; version: number; limit?: number }) {
  const data = useApiData(
    async (signal) => {
      const [checkIns, changes] = await Promise.all([goalsApi.listCheckIns(kr.id, signal), goalsApi.keyResultHistory(kr.id, signal)]);
      return buildTimeline(checkIns, changes);
    },
    [kr.id, version],
  );
  if (data.status === 'loading' && !data.data) return <Loading />;
  if (data.status === 'error') return <ErrorMessage error={data.error} onRetry={data.reload} />;
  const all: TimelineEntry[] = data.data ?? [];
  if (all.length === 0) return <p className="meta">هنوز اندازه‌گیری یا تغییری ثبت نشده است.</p>;
  const shown = limit == null ? all : all.slice(0, limit);
  return (
    <>
      {shown.length < all.length && <p className="meta">{`${formatNumber(shown.length)} مورد آخر از ${formatNumber(all.length)}`}</p>}
      <ol className="history" aria-label="اندازه‌گیری‌ها و تغییرات، جدیدترین اول">
        {shown.map((e) =>
          e.kind === 'check_in' ? (
            <li key={e.checkIn.id}>
              <strong>{withUnit(e.checkIn.value, kr.unit)}</strong> — اندازه‌گیری:{' '}
              <time dateTime={e.checkIn.measuredAt}>{formatTimestamp(e.checkIn.measuredAt)}</time>
              <span className="history__note">
                {' '}
                (ثبت: <time dateTime={e.checkIn.createdAt}>{formatTimestamp(e.checkIn.createdAt)}</time>)
              </span>
              {e.checkIn.note && <span className="history__note"> · {e.checkIn.note}</span>}
            </li>
          ) : (
            <li key={e.change.id} className="history__change">
              {describeChange(e.change, kr.unit)} — <time dateTime={e.change.createdAt}>{formatTimestamp(e.change.createdAt)}</time>
              {e.change.note && <span className="history__note"> · دلیل: {e.change.note}</span>}
            </li>
          ),
        )}
      </ol>
    </>
  );
}

function ArchiveKeyResult({ kr, onDone }: { kr: KeyResult; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  if (!open) {
    return (
      <button type="button" className="button button--secondary" onClick={() => setOpen(true)}>
        بایگانی
      </button>
    );
  }
  return (
    <div className="state state--notice" role="alertdialog" aria-label={`بایگانی ${kr.name}`}>
      <p>
        «{kr.name}» از محاسبه پیشرفت نتیجه هدف خارج می‌شود. اندازه‌گیری‌ها و تاریخچه آن حذف نمی‌شوند و در بخش نتایج کلیدی
        بایگانی‌شده قابل مشاهده می‌مانند.
      </p>
      <TextField label="دلیل (اختیاری)" value={note} onChange={setNote} maxLength={1000} />
      <div className="form__actions">
        <button
          type="button"
          className="button"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await goalsApi.archiveKeyResult(kr.id, note.trim() || undefined);
              onDone();
            } catch (e) {
              setError(e);
            } finally {
              setBusy(false);
            }
          }}
        >
          {busy ? 'در حال بایگانی…' : 'بله، بایگانی شود'}
        </button>
        <button type="button" className="button button--secondary" onClick={() => setOpen(false)}>
          انصراف
        </button>
      </div>
      {error != null && <ErrorMessage error={error} />}
    </div>
  );
}

interface KeyResultCardProps {
  initial: KeyResult;
  /** Called after any change (check-in, edit, archive) so the page can refresh lists and progress. */
  onChanged?: () => void;
  /** Archived KR or archived goal: history only, no actions that change data. */
  readOnly?: boolean;
}

/** One KR: values and score come from the backend; refetched after every change. */
export function KeyResultCard({ initial, onChanged, readOnly }: KeyResultCardProps) {
  const headingId = useId();
  const [version, setVersion] = useState(0);
  const [panel, setPanel] = useState<'none' | 'check-in' | 'edit'>('none');
  const [showAll, setShowAll] = useState(false);
  const fresh = useApiData(
    (signal) => (version === 0 ? Promise.resolve(initial) : goalsApi.getKeyResult(initial.id, signal)),
    [initial, version],
  );
  const kr = fresh.data ?? initial;
  const measured = kr.currentSource === 'check_in';
  const editable = !readOnly && !kr.archivedAt;
  const changed = () => {
    setVersion((v) => v + 1);
    setPanel('none');
    onChanged?.();
  };

  return (
    <article className={`card kr${kr.archivedAt ? ' kr--archived' : ''}`} aria-labelledby={headingId}>
      <h3 id={headingId} className="kr__name">
        {kr.name} {kr.archivedAt && <span className="badge badge--muted">بایگانی‌شده</span>}
      </h3>
      <dl className="kr__facts">
        <div>
          <dt>پایه</dt>
          <dd>{withUnit(kr.baseline, kr.unit)}</dd>
        </div>
        <div>
          <dt>هدف</dt>
          <dd>
            {withUnit(kr.target, kr.unit)} {kr.target < kr.baseline && <span className="badge badge--muted">کاهشی</span>}
          </dd>
        </div>
        <div>
          <dt>فعلی</dt>
          <dd>{withUnit(kr.current, kr.unit)}</dd>
        </div>
        <div>
          <dt>وزن</dt>
          <dd>{formatNumber(kr.weight)}</dd>
        </div>
      </dl>

      <div className="score">
        <span className="score__label">امتیاز نتیجه کلیدی</span>
        <meter min={0} max={100} value={kr.score} aria-label={`امتیاز ${kr.name}`}>
          {formatPercent(kr.score)}
        </meter>
        <strong className="score__value">{formatPercent(kr.score)}</strong>
      </div>
      <p className="meta" aria-live="polite">
        {measured && kr.currentMeasuredAt ? (
          <>
            مقدار فعلی از آخرین اندازه‌گیری: <time dateTime={kr.currentMeasuredAt}>{formatTimestamp(kr.currentMeasuredAt)}</time>
          </>
        ) : (
          'هنوز اندازه‌گیری ثبت نشده؛ مقدار فعلی همان مقدار پایه است (۰٪ نقطه شروع).'
        )}
      </p>
      {fresh.status === 'error' && <ErrorMessage error={fresh.error} onRetry={fresh.reload} />}

      <div className="kr__actions">
        {editable && (
          <>
            <button
              type="button"
              className="button button--secondary"
              aria-expanded={panel === 'check-in'}
              onClick={() => setPanel(panel === 'check-in' ? 'none' : 'check-in')}
            >
              {panel === 'check-in' ? 'بستن' : 'ثبت اندازه‌گیری'}
            </button>
            <button
              type="button"
              className="button button--secondary"
              aria-expanded={panel === 'edit'}
              onClick={() => setPanel(panel === 'edit' ? 'none' : 'edit')}
            >
              ویرایش
            </button>
            <ArchiveKeyResult kr={kr} onDone={changed} />
          </>
        )}
        <button type="button" className="button button--secondary" aria-expanded={showAll} onClick={() => setShowAll((v) => !v)}>
          {showAll ? 'فقط آخرین‌ها' : 'تاریخچه کامل'}
        </button>
      </div>
      {panel === 'check-in' && <CheckInForm keyResult={kr} onSaved={changed} />}
      {panel === 'edit' && <KeyResultEditForm kr={kr} onSaved={changed} onCancel={() => setPanel('none')} />}
      <KrTimeline kr={kr} version={version} limit={showAll ? undefined : 3} />
    </article>
  );
}
