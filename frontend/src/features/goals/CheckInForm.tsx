import { useState, type FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { todayIso, type IsoDate } from '../../shared/date/calendar';
import { currentTimeZone, timeInZone, zonedWallTimeToUtc } from '../../shared/date/timeZone';
import { normalizeDigits, parseDecimal } from '../../shared/format';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import { JalaliDateField } from '../../shared/ui/JalaliDateField';
import { TextField } from '../../shared/ui/TextField';
import { goalsApi } from './api';
import type { KeyResult } from './types';

/**
 * measuredAt is a timestamp (not date-only): the chosen Jalali date + wall-clock time in the user's configured
 * timezone becomes a UTC instant. Date-only fields never go this way.
 */
function toUtcTimestamp(iso: IsoDate, time: string): string | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(normalizeDigits(time));
  if (!match) return null;
  const [hours, minutes] = [Number(match[1]), Number(match[2])];
  if (hours > 23 || minutes > 59) return null;
  return zonedWallTimeToUtc(iso, hours, minutes, currentTimeZone()).toISOString();
}

export function CheckInForm({ keyResult, onSaved }: { keyResult: KeyResult; onSaved: () => void }) {
  const [value, setValue] = useState('');
  const [date, setDate] = useState<IsoDate | null>(() => todayIso());
  const [time, setTime] = useState(() => timeInZone(new Date(), currentTimeZone()));
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const next: FieldErrors = {};
    const parsed = parseDecimal(value);
    if (!parsed.ok) {
      next.value = parsed.reason === 'empty' ? 'required' : parsed.reason === 'precision' ? 'precision_exceeded' : 'invalid_number';
    }
    if (!date) next.measuredAt = 'required';
    const measuredAt = date ? toUtcTimestamp(date, time) : null;
    if (date && !measuredAt) next.time = 'invalid_time';
    setErrors(next);
    setServerError(null);
    if (Object.keys(next).length > 0 || !parsed.ok || !measuredAt) return;

    setSubmitting(true);
    try {
      await goalsApi.createCheckIn(keyResult.id, { value: parsed.value, measuredAt, note: note.trim() || undefined });
      setValue('');
      setNote('');
      onSaved();
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldCodes());
      setServerError(error);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="form form--inline" onSubmit={handleSubmit} noValidate aria-label={`ثبت اندازه‌گیری برای ${keyResult.name}`}>
      <div className="form__row">
        <TextField
          label={`مقدار جدید${keyResult.unit ? ` (${keyResult.unit})` : ''}`}
          value={value}
          onChange={setValue}
          required
          inputMode="decimal"
          dir="ltr"
          errorCode={errors.value}
        />
        <TextField label="ساعت" value={time} onChange={setTime} dir="ltr" inputMode="numeric" hint={`۲۴ساعته، مثلاً 14:30 — منطقه زمانی: ${currentTimeZone()}`} errorCode={errors.time} />
      </div>
      <JalaliDateField label="تاریخ اندازه‌گیری" value={date} onChange={setDate} required errorCode={errors.measuredAt} />
      <TextField label="یادداشت" value={note} onChange={setNote} maxLength={1000} errorCode={errors.note} />
      {serverError != null && <ErrorMessage error={serverError} />}
      <div className="form__actions">
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'در حال ثبت…' : 'ثبت اندازه‌گیری'}
        </button>
      </div>
    </form>
  );
}
