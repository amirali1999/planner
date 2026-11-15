import { useState, type FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { parseDecimal } from '../../shared/format';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import { TextField } from '../../shared/ui/TextField';
import { goalsApi } from './api';
import { isRecalibration } from './krTimeline';
import type { KeyResult, UpdateKeyResultRequest } from './types';

function decimalCode(raw: string): string | undefined {
  const p = parseDecimal(raw);
  if (p.ok) return undefined;
  return p.reason === 'empty' ? 'required' : p.reason === 'precision' ? 'precision_exceeded' : 'invalid_number';
}

/**
 * Edits a KR. Renaming is free; changing baseline/target/weight is a recalibration that needs a short note.
 * Recorded check-ins are never changed — the score is recomputed from them.
 */
export function KeyResultEditForm({ kr, onSaved, onCancel }: { kr: KeyResult; onSaved: () => void; onCancel: () => void }) {
  const [name, setName] = useState(kr.name);
  const [unit, setUnit] = useState(kr.unit ?? '');
  const [baseline, setBaseline] = useState(String(kr.baseline));
  const [target, setTarget] = useState(String(kr.target));
  const [weight, setWeight] = useState(String(kr.weight));
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const b = parseDecimal(baseline);
  const t = parseDecimal(target);
  const w = parseDecimal(weight);
  const recalibrating = b.ok && t.ok && w.ok && isRecalibration(kr, { baseline: b.value, target: t.value, weight: w.value });

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    const next: FieldErrors = {};
    if (!name.trim()) next.name = 'required';
    for (const [field, raw] of [['baseline', baseline], ['target', target], ['weight', weight]] as const) {
      const code = decimalCode(raw);
      if (code) next[field] = code;
    }
    if (b.ok && t.ok && b.value === t.value) next.target = 'equals_baseline';
    if (w.ok && w.value <= 0) next.weight = 'must_be_positive';
    if (recalibrating && !note.trim()) next.note = 'required_for_recalibration';
    setErrors(next);
    setServerError(null);
    if (Object.keys(next).length > 0 || !b.ok || !t.ok || !w.ok) return;

    const patch: UpdateKeyResultRequest = {};
    if (name.trim() !== kr.name) patch.name = name.trim();
    if ((unit.trim() || null) !== kr.unit) patch.unit = unit.trim() || null;
    if (b.value !== kr.baseline) patch.baseline = b.value;
    if (t.value !== kr.target) patch.target = t.value;
    if (w.value !== kr.weight) patch.weight = w.value;
    if (note.trim()) patch.note = note.trim();
    if (Object.keys(patch).filter((k) => k !== 'note').length === 0) {
      onCancel();
      return;
    }
    setSubmitting(true);
    try {
      await goalsApi.updateKeyResult(kr.id, patch);
      onSaved();
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldCodes());
      setServerError(error);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="form form--inline" onSubmit={handleSubmit} noValidate aria-label={`ویرایش ${kr.name}`}>
      <div className="form__row">
        <TextField label="نام" value={name} onChange={setName} required maxLength={200} errorCode={errors.name} />
        <TextField label="واحد" value={unit} onChange={setUnit} maxLength={32} errorCode={errors.unit} />
      </div>
      <div className="form__row">
        <TextField label="مقدار پایه" value={baseline} onChange={setBaseline} required inputMode="decimal" dir="ltr" errorCode={errors.baseline} />
        <TextField label="مقدار هدف" value={target} onChange={setTarget} required inputMode="decimal" dir="ltr" errorCode={errors.target} />
        <TextField label="وزن" value={weight} onChange={setWeight} required inputMode="decimal" dir="ltr" errorCode={errors.weight} />
      </div>
      <TextField
        label={recalibrating ? 'دلیل تغییر (الزامی)' : 'یادداشت (اختیاری)'}
        value={note}
        onChange={setNote}
        required={recalibrating}
        maxLength={1000}
        hint={
          recalibrating
            ? 'تغییر پایه، هدف یا وزن یک «بازتنظیم» است و با دلیل در تاریخچه ثبت می‌شود. اندازه‌گیری‌های قبلی تغییر نمی‌کنند و امتیاز دوباره از آن‌ها محاسبه می‌شود.'
            : undefined
        }
        errorCode={errors.note}
      />
      {serverError != null && <ErrorMessage error={serverError} />}
      <div className="form__actions">
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'در حال ذخیره…' : 'ذخیره'}
        </button>
        <button type="button" className="button button--secondary" onClick={onCancel}>
          انصراف
        </button>
      </div>
    </form>
  );
}
