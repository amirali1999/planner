import { useEffect, useId, useState, type FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { todayIso, type IsoDate } from '../../shared/date/calendar';
import { formatJalaliDateWithWeekday, formatTimestamp } from '../../shared/format';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import { JalaliDateField } from '../../shared/ui/JalaliDateField';
import { TextField } from '../../shared/ui/TextField';
import { useApiData } from '../../shared/ui/useApiData';
import { milestonesApi } from './api';
import { FILTER_LABELS, STATE_LABELS, milestoneState } from './milestoneModel';
import type { Milestone, MilestoneStatusFilter, UpdateMilestoneRequest } from './types';

function MilestoneForm({
  initial,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial?: Milestone;
  submitLabel: string;
  onSubmit: (v: { title: string; description: string | null; dueDate: IsoDate | null }) => Promise<void>;
  onCancel?: () => void;
}) {
  const [title, setTitle] = useState(initial?.title ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [dueDate, setDueDate] = useState<IsoDate | null>(initial?.dueDate ?? null);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function handle(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    if (!title.trim()) {
      setErrors({ title: 'required' });
      return;
    }
    setErrors({});
    setServerError(null);
    setBusy(true);
    try {
      await onSubmit({ title: title.trim(), description: description.trim() || null, dueDate });
      if (!initial) {
        setTitle('');
        setDescription('');
        setDueDate(null);
      }
    } catch (e) {
      if (e instanceof ApiError) setErrors(e.fieldCodes());
      setServerError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="form form--inline" onSubmit={handle} noValidate aria-label={initial ? `ویرایش ${initial.title}` : 'نقطه تحویل جدید'}>
      <TextField label="عنوان" value={title} onChange={setTitle} required maxLength={200} errorCode={errors.title} />
      <JalaliDateField label="موعد (اختیاری)" value={dueDate} onChange={setDueDate} clearable errorCode={errors.dueDate} />
      <TextField label="شرح (اختیاری)" value={description} onChange={setDescription} multiline errorCode={errors.description} />
      {serverError != null && <ErrorMessage error={serverError} />}
      <div className="form__actions">
        <button type="submit" className="button" disabled={busy}>
          {busy ? 'در حال ذخیره…' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="button button--secondary" onClick={onCancel}>
            انصراف
          </button>
        )}
      </div>
    </form>
  );
}

function MilestoneItem({ m, today, readOnly, onChanged }: { m: Milestone; today: IsoDate; readOnly: boolean; onChanged: () => void }) {
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  // Optimistic checkbox: show the click immediately, keep it until the reloaded server data agrees, revert on error.
  const [pendingCompleted, setPendingCompleted] = useState<boolean | null>(null);
  useEffect(() => {
    if (pendingCompleted !== null && m.completed === pendingCompleted) setPendingCompleted(null);
  }, [m.completed, pendingCompleted]);
  const state = milestoneState(m, today);
  const archived = m.archivedAt != null;

  async function patch(body: UpdateMilestoneRequest) {
    setBusy(true);
    setError(null);
    try {
      await milestonesApi.update(m.id, body);
      onChanged();
    } catch (e) {
      setError(e);
      setPendingCompleted(null);
    } finally {
      setBusy(false);
    }
  }

  if (editing) {
    return (
      <li className="milestone">
        <MilestoneForm
          initial={m}
          submitLabel="ذخیره"
          onCancel={() => setEditing(false)}
          onSubmit={async (v) => {
            const body: UpdateMilestoneRequest = {};
            if (v.title !== m.title) body.title = v.title;
            if (v.description !== m.description) body.description = v.description;
            if (v.dueDate !== m.dueDate) body.dueDate = v.dueDate;
            if (Object.keys(body).length > 0) await milestonesApi.update(m.id, body);
            setEditing(false);
            onChanged();
          }}
        />
      </li>
    );
  }

  return (
    <li className={`milestone milestone--${state}${archived ? ' milestone--archived' : ''}`}>
      <div className="milestone__main">
        <input
          id={`${id}-done`}
          type="checkbox"
          checked={pendingCompleted ?? m.completed}
          disabled={busy || readOnly || archived}
          onChange={(e) => {
            setPendingCompleted(e.target.checked);
            patch({ completed: e.target.checked });
          }}
        />
        <label htmlFor={`${id}-done`} className="milestone__title">
          {m.title}
        </label>
        <span className={`badge milestone__state milestone__state--${state}`}>{STATE_LABELS[state]}</span>
        {archived && <span className="badge badge--muted">بایگانی‌شده</span>}
      </div>
      <p className="meta">
        {m.dueDate ? <>موعد: <time dateTime={m.dueDate}>{formatJalaliDateWithWeekday(m.dueDate)}</time></> : 'بدون موعد'}
        {m.completedAt && (
          <>
            {' · '}تکمیل: <time dateTime={m.completedAt}>{formatTimestamp(m.completedAt)}</time>
          </>
        )}
      </p>
      {m.description && <p className="description">{m.description}</p>}
      {error != null && <ErrorMessage error={error} />}
      {!readOnly && (
        <div className="kr__actions">
          {!archived && (
            <button type="button" className="button button--link" onClick={() => setEditing(true)} disabled={busy}>
              ویرایش
            </button>
          )}
          <button type="button" className="button button--link" onClick={() => patch({ archived: !archived })} disabled={busy}>
            {archived ? 'خروج از بایگانی' : 'بایگانی'}
          </button>
        </div>
      )}
    </li>
  );
}

/**
 * Goal milestones: delivery checkpoints. They are shown apart from KRs and never feed Outcome or Execution.
 * {@code readOnly} for archived goals (the backend refuses changes with 409 anyway).
 */
export function MilestonesSection({ goalId, readOnly }: { goalId: string; readOnly: boolean }) {
  const today = todayIso();
  const [filter, setFilter] = useState<MilestoneStatusFilter>('all');
  const [showArchived, setShowArchived] = useState(false);
  const [adding, setAdding] = useState(false);
  const list = useApiData((signal) => milestonesApi.list(goalId, filter, today, showArchived, signal), [goalId, filter, today, showArchived]);

  return (
    <section aria-labelledby="milestones-heading" className="section">
      <h2 id="milestones-heading">نقاط تحویل (Milestones)</h2>
      <p className="meta">
        نقطه تحویل یک مرحله یا خروجی مهم است، نه نتیجه کلیدی. تکمیل آن در پیشرفت نتیجه یا پیشرفت اجرا حساب نمی‌شود.
      </p>
      <div className="filters">
        <div className="field field--compact">
          <label htmlFor={`${goalId}-ms-filter`}>فیلتر نقاط تحویل</label>
          <select id={`${goalId}-ms-filter`} value={filter} onChange={(e) => setFilter(e.target.value as MilestoneStatusFilter)}>
            {(Object.keys(FILTER_LABELS) as MilestoneStatusFilter[]).map((f) => (
              <option key={f} value={f}>
                {FILTER_LABELS[f]}
              </option>
            ))}
          </select>
        </div>
        <label className="checkbox">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> نمایش بایگانی‌شده‌ها
        </label>
      </div>
      {list.status === 'loading' && !list.data && <Loading />}
      {list.status === 'error' && <ErrorMessage error={list.error} onRetry={list.reload} />}
      {list.data && list.data.length === 0 && (
        <EmptyState title={filter === 'all' && !showArchived ? 'این هدف هنوز نقطه تحویلی ندارد.' : 'نقطه تحویلی با این فیلتر پیدا نشد.'} />
      )}
      {list.data && list.data.length > 0 && (
        <ul className="milestone-list" aria-label="نقاط تحویل">
          {list.data.map((m) => (
            <MilestoneItem key={m.id} m={m} today={today} readOnly={readOnly} onChanged={list.reload} />
          ))}
        </ul>
      )}
      {!readOnly &&
        (adding ? (
          <MilestoneForm
            submitLabel="افزودن نقطه تحویل"
            onCancel={() => setAdding(false)}
            onSubmit={async (v) => {
              await milestonesApi.create(goalId, {
                title: v.title,
                ...(v.description ? { description: v.description } : {}),
                ...(v.dueDate ? { dueDate: v.dueDate } : {}),
              });
              list.reload();
            }}
          />
        ) : (
          <button type="button" className="button button--secondary" onClick={() => setAdding(true)}>
            افزودن نقطه تحویل
          </button>
        ))}
    </section>
  );
}
