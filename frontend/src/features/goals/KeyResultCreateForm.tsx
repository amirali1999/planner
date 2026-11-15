import { useState, type FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { parseDecimal } from '../../shared/format';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import { TextField } from '../../shared/ui/TextField';
import { goalsApi } from './api';
import type { KeyResult } from './types';

function decimalError(raw: string, required: boolean): string | undefined {
  const parsed = parseDecimal(raw);
  if (parsed.ok) return undefined;
  if (parsed.reason === 'empty') return required ? 'required' : undefined;
  return parsed.reason === 'precision' ? 'precision_exceeded' : 'invalid_number';
}

export function KeyResultCreateForm({ goalId, onCreated }: { goalId: string; onCreated: (kr: KeyResult) => void }) {
  const [name, setName] = useState('');
  const [unit, setUnit] = useState('');
  const [baseline, setBaseline] = useState('0');
  const [target, setTarget] = useState('');
  const [weight, setWeight] = useState('1');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const next: FieldErrors = {};
    if (!name.trim()) next.name = 'required';
    const baselineError = decimalError(baseline, true);
    const targetError = decimalError(target, true);
    const weightError = decimalError(weight, true);
    if (baselineError) next.baseline = baselineError;
    if (targetError) next.target = targetError;
    if (weightError) next.weight = weightError;
    const b = parseDecimal(baseline);
    const t = parseDecimal(target);
    const w = parseDecimal(weight);
    if (b.ok && t.ok && b.value === t.value) next.target = 'equals_baseline';
    if (w.ok && w.value <= 0) next.weight = 'must_be_positive';
    setErrors(next);
    setServerError(null);
    if (Object.keys(next).length > 0 || !b.ok || !t.ok || !w.ok) return;

    setSubmitting(true);
    try {
      const kr = await goalsApi.createKeyResult(goalId, {
        name: name.trim(),
        unit: unit.trim() || undefined,
        baseline: b.value,
        target: t.value,
        weight: w.value,
      });
      setName('');
      setUnit('');
      setBaseline('0');
      setTarget('');
      setWeight('1');
      onCreated(kr);
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldCodes());
      setServerError(error);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card form" onSubmit={handleSubmit} noValidate aria-labelledby="kr-create-heading">
      <h3 id="kr-create-heading">نتیجه کلیدی جدید</h3>
      <div className="form__row">
        <TextField label="نام" value={name} onChange={setName} required maxLength={200} errorCode={errors.name} />
        <TextField label="واحد" value={unit} onChange={setUnit} maxLength={32} hint="مثلاً ٪ یا ساعت" errorCode={errors.unit} />
      </div>
      <div className="form__row">
        <TextField label="مقدار پایه" value={baseline} onChange={setBaseline} required inputMode="decimal" dir="ltr" errorCode={errors.baseline} />
        <TextField
          label="مقدار هدف"
          value={target}
          onChange={setTarget}
          required
          inputMode="decimal"
          dir="ltr"
          hint="می‌تواند کمتر از مقدار پایه باشد (هدف کاهشی)."
          errorCode={errors.target}
        />
        <TextField label="وزن" value={weight} onChange={setWeight} required inputMode="decimal" dir="ltr" errorCode={errors.weight} />
      </div>
      {serverError != null && <ErrorMessage error={serverError} />}
      <div className="form__actions">
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'در حال ذخیره…' : 'افزودن نتیجه کلیدی'}
        </button>
      </div>
    </form>
  );
}
