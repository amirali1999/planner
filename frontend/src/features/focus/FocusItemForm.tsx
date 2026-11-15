import { useId, useState, type FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import { TextField } from '../../shared/ui/TextField';
import { PERIOD_LABELS } from '../goals/labels';
import type { Goal } from '../goals/types';
import { FOCUS_STATUS_LABELS, FOCUS_STATUS_ORDER, KIND_LABELS, KIND_ORDER } from './labels';
import { validateFocusForm, type FocusFormValues } from './focusFormModel';
import type { FocusKind, FocusStatus } from './types';

interface Props {
  heading: string;
  initial: FocusFormValues;
  /** All goals (incl. archived) for names; only active ones are offered as new links. */
  goals: Goal[];
  submitLabel: string;
  onSubmit: (v: FocusFormValues) => Promise<void>;
  onCancel?: () => void;
}

export function FocusItemForm({ heading, initial, goals, submitLabel, onSubmit, onCancel }: Props) {
  const id = useId();
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const set = <K extends keyof FocusFormValues>(k: K) => (v: FocusFormValues[K]) => setValues((p) => ({ ...p, [k]: v }));
  const goalOptions = goals.filter((g) => !g.archivedAt || g.id === initial.goalId);

  async function handle(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    const next = validateFocusForm(values);
    setErrors(next);
    setServerError(null);
    if (Object.keys(next).length > 0) return;
    setBusy(true);
    try {
      await onSubmit(values);
    } catch (e) {
      if (e instanceof ApiError) setErrors(e.fieldCodes());
      setServerError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card form" onSubmit={handle} noValidate aria-label={heading}>
      <h2 className="form__heading">{heading}</h2>
      <TextField label="عنوان" value={values.title} onChange={set('title')} required maxLength={200} errorCode={errors.title} />
      <div className="form__row">
        <div className="field">
          <label htmlFor={`${id}-kind`}>نوع</label>
          <select id={`${id}-kind`} value={values.kind} onChange={(e) => set('kind')(e.target.value as FocusKind)}>
            {KIND_ORDER.map((k) => (
              <option key={k} value={k}>
                {KIND_LABELS[k]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${id}-status`}>وضعیت</label>
          <select id={`${id}-status`} value={values.status} onChange={(e) => set('status')(e.target.value as FocusStatus)}>
            {FOCUS_STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {FOCUS_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${id}-goal`}>هدف مرتبط (اختیاری)</label>
          <select id={`${id}-goal`} value={values.goalId} onChange={(e) => set('goalId')(e.target.value)}>
            <option value="">بدون هدف</option>
            {goalOptions.map((g) => (
              <option key={g.id} value={g.id}>
                {g.title} ({PERIOD_LABELS[g.periodType]}){g.archivedAt ? ' — بایگانی‌شده' : ''}
              </option>
            ))}
          </select>
          {errors.goalId && <p className="field__error">هدف انتخاب‌شده معتبر نیست.</p>}
        </div>
      </div>
      <TextField label="توضیح" value={values.description} onChange={set('description')} multiline errorCode={errors.description} />
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
