import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { todayIso, type IsoDate } from '../../shared/date/calendar';
import { periodRange, type DateRange } from '../../shared/date/periods';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import { JalaliDateField } from '../../shared/ui/JalaliDateField';
import { PeriodPicker } from '../../shared/ui/PeriodPicker';
import { Icon } from '../../shared/ui/Icon';
import { TextField } from '../../shared/ui/TextField';
import { goalsApi } from './api';
import { PERIOD_LABELS, PERIOD_ORDER, STATUS_LABELS, STATUS_ORDER } from './labels';
import type { CreateGoalRequest, GoalStatus, PeriodType } from './types';

const DEFAULT_PERIOD: PeriodType = 'quarterly';

interface GoalCreateFormProps {
  onCreated: (goalId: string) => void;
  /** Prefill (e.g. from an inbox note). */
  initialTitle?: string;
  initialDescription?: string;
  /** Override how the goal is created (inbox triage creates it inside its own transaction). */
  create?: (body: CreateGoalRequest) => Promise<{ id: string }>;
  heading?: string;
  /** "card": laid out as a goal card on the goals board (header + scrolling body + actions), with «انصراف». */
  variant?: 'card';
  onCancel?: () => void;
}

export function GoalCreateForm({ onCreated, initialTitle = '', initialDescription = '', create, heading = 'هدف جدید', variant, onCancel }: GoalCreateFormProps) {
  const card = variant === 'card';
  const formRef = useRef<HTMLFormElement>(null);
  // the card opens ready to type (and Escape then reaches the form)
  useEffect(() => {
    if (card) formRef.current?.querySelector<HTMLInputElement>('input')?.focus({ preventScroll: true });
  }, [card]);
  const [title, setTitle] = useState(initialTitle);
  const [description, setDescription] = useState(initialDescription);
  const [periodType, setPeriodType] = useState<PeriodType>(DEFAULT_PERIOD);
  const [status, setStatus] = useState<GoalStatus>('not_started');
  const [range, setRange] = useState<DateRange>(() => periodRange(DEFAULT_PERIOD, todayIso()));
  const [deadline, setDeadline] = useState<IsoDate | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  /**
   * On period-type change, select the standard period of the new type around a reference date: today while the
   * current range contains today (quarterly → weekly gives this week), otherwise the current start date.
   */
  function changePeriodType(type: PeriodType) {
    const today = todayIso();
    const reference = range.start <= today && today <= range.end ? today : range.start;
    setPeriodType(type);
    setRange(periodRange(type, reference));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    const next: FieldErrors = {};
    if (!title.trim()) next.title = 'required';
    if (range.end < range.start) next.periodEnd = 'before_period_start';
    setErrors(next);
    setServerError(null);
    if (Object.keys(next).length > 0) return;

    setSubmitting(true);
    try {
      const body: CreateGoalRequest = {
        title: title.trim(),
        description: description.trim() || undefined,
        periodType,
        periodStart: range.start,
        periodEnd: range.end,
        deadline: deadline ?? undefined,
        status,
      };
      const created = await (create ?? goalsApi.create)(body);
      onCreated(created.id);
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldCodes());
      setServerError(error);
    } finally {
      setSubmitting(false);
    }
  }

  const fields = (
    <>
      <TextField label="عنوان" value={title} onChange={setTitle} required maxLength={200} errorCode={errors.title} />
      <TextField label="توضیح" value={description} onChange={setDescription} multiline errorCode={errors.description} />

      <div className="form__row">
        <div className="field">
          <label htmlFor="goal-period-type">نوع دوره</label>
          <select id="goal-period-type" value={periodType} onChange={(e) => changePeriodType(e.target.value as PeriodType)}>
            {PERIOD_ORDER.map((type) => (
              <option key={type} value={type}>
                {PERIOD_LABELS[type]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="goal-status">وضعیت</label>
          <select id="goal-status" value={status} onChange={(e) => setStatus(e.target.value as GoalStatus)}>
            {STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
      </div>

      <PeriodPicker
        key={periodType}
        periodType={periodType}
        start={range.start}
        end={range.end}
        onChange={setRange}
        startError={errors.periodStart}
        endError={errors.periodEnd}
      />
      <JalaliDateField
        label="موعد (اختیاری)"
        value={deadline}
        onChange={setDeadline}
        clearable
        hint="موعد مستقل از پایان دوره است؛ اگر خارج از بازه باشد فقط هشدار داده می‌شود."
        errorCode={errors.deadline}
      />

      {serverError != null && <ErrorMessage error={serverError} />}
    </>
  );

  if (card) {
    return (
      <form ref={formRef} className="goal-card goal-card--new" onSubmit={handleSubmit} noValidate aria-labelledby="goal-create-heading"
        onKeyDown={(e) => e.key === 'Escape' && onCancel?.()}>
        <h2 id="goal-create-heading" className="goal-new__heading"><Icon name="target" size={24} /> {heading}</h2>
        <div className="goal-new__body form">{fields}</div>
        <div className="goal-new__actions">
          <button type="submit" className="button" disabled={submitting}>{submitting ? 'در حال ذخیره…' : 'تایید'}</button>
          {onCancel && <button type="button" className="button button--secondary" onClick={onCancel}>انصراف</button>}
        </div>
      </form>
    );
  }

  return (
    <form className="card form" onSubmit={handleSubmit} noValidate aria-labelledby="goal-create-heading">
      <h2 id="goal-create-heading">{heading}</h2>
      {fields}
      <div className="form__actions">
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'در حال ذخیره…' : 'ایجاد هدف'}
        </button>
      </div>
    </form>
  );
}
