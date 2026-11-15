import { useId, useState, type FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import { JalaliDateField } from '../../shared/ui/JalaliDateField';
import { TextField } from '../../shared/ui/TextField';
import type { FocusItem } from '../focus/types';
import type { Goal } from '../goals/types';
import { TASK_PRIORITY_LABELS, TASK_PRIORITY_ORDER, TASK_STATUS_LABELS, TASK_STATUS_ORDER } from './labels';
import { parseTaskForm, type ParsedTaskForm, type TaskFormValues } from './taskFormModel';

interface TaskFormProps {
  heading: string;
  initial: TaskFormValues;
  goals: Goal[];
  /** All focus items (incl. archived) for names; only active ones are offered as new links. */
  focusItems: FocusItem[];
  submitLabel: string;
  onSubmit: (value: ParsedTaskForm) => Promise<void>;
  onCancel?: () => void;
}

/** Shared create/edit form. Goal link is optional; due date is a deadline, not a schedule. */
export function TaskForm({ heading, initial, goals, focusItems, submitLabel, onSubmit, onCancel }: TaskFormProps) {
  const id = useId();
  const [values, setValues] = useState<TaskFormValues>(initial);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const set = <K extends keyof TaskFormValues>(key: K) => (value: TaskFormValues[K]) =>
    setValues((v) => ({ ...v, [key]: value }));

  // Archived goals are only offered when the task is already linked to one.
  const goalOptions = goals.filter((g) => !g.archivedAt || g.id === initial.goalId);
  const focusOptions = focusItems.filter((f) => !f.archivedAt || f.id === initial.focusItemId);
  const selectedFocus = focusItems.find((f) => f.id === values.focusItemId);
  const focusGoal = selectedFocus?.goalId ? goals.find((g) => g.id === selectedFocus.goalId) : undefined;
  const conflict = Boolean(values.goalId && selectedFocus?.goalId && selectedFocus.goalId !== values.goalId);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    const parsed = parseTaskForm(values);
    setServerError(null);
    if (!parsed.ok) {
      setErrors(parsed.errors);
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      await onSubmit(parsed.value);
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldCodes());
      setServerError(error);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card form" onSubmit={handleSubmit} noValidate aria-label={heading}>
      <h2 className="form__heading">{heading}</h2>
      <TextField label="عنوان" value={values.title} onChange={set('title')} required maxLength={200} errorCode={errors.title} />
      <TextField label="توضیح" value={values.description} onChange={set('description')} multiline errorCode={errors.description} />
      <div className="form__row">
        <div className="field">
          <label htmlFor={`${id}-goal`}>هدف مرتبط (اختیاری)</label>
          <select id={`${id}-goal`} value={values.goalId} onChange={(e) => set('goalId')(e.target.value)}>
            <option value="">بدون هدف</option>
            {goalOptions.map((g) => (
              <option key={g.id} value={g.id}>
                {g.title}
                {g.archivedAt ? ' (بایگانی‌شده)' : ''}
              </option>
            ))}
          </select>
          {errors.goalId && <p className="field__error">هدف انتخاب‌شده معتبر نیست.</p>}
        </div>
        <div className="field">
          <label htmlFor={`${id}-focus`}>آیتم تمرکز (اختیاری)</label>
          <select id={`${id}-focus`} value={values.focusItemId} onChange={(e) => set('focusItemId')(e.target.value)}>
            <option value="">بدون آیتم تمرکز</option>
            {focusOptions.map((f) => (
              <option key={f.id} value={f.id}>
                {f.title}
                {f.archivedAt ? ' (بایگانی‌شده)' : ''}
              </option>
            ))}
          </select>
          {errors.focusItemId && <p className="field__error">آیتم تمرکز انتخاب‌شده معتبر نیست.</p>}
          {!values.goalId && focusGoal && <p className="field__hint">هدف این کار از آیتم تمرکز گرفته می‌شود: «{focusGoal.title}»</p>}
          {conflict && (
            <p className="field__hint field__hint--warning" role="status">
              هدف مستقیم با هدف آیتم تمرکز{focusGoal ? ` («${focusGoal.title}»)` : ''} فرق دارد؛ هدف مستقیم ملاک است. اگر عمدی است
              می‌توانید ذخیره کنید.
            </p>
          )}
        </div>
        <div className="field">
          <label htmlFor={`${id}-status`}>وضعیت</label>
          <select id={`${id}-status`} value={values.status} onChange={(e) => set('status')(e.target.value as TaskFormValues['status'])}>
            {TASK_STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {TASK_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${id}-priority`}>اولویت</label>
          <select
            id={`${id}-priority`}
            value={values.priority}
            onChange={(e) => set('priority')(e.target.value as TaskFormValues['priority'])}
          >
            {TASK_PRIORITY_ORDER.map((p) => (
              <option key={p} value={p}>
                {TASK_PRIORITY_LABELS[p]}
              </option>
            ))}
          </select>
        </div>
        <TextField
          label="برآورد زمان (دقیقه)"
          value={values.estimatedMinutes}
          onChange={set('estimatedMinutes')}
          inputMode="numeric"
          dir="ltr"
          hint="خالی یعنی برآورد نشده؛ ۰ هم مجاز است."
          errorCode={errors.estimatedMinutes}
        />
      </div>
      <JalaliDateField
        label="موعد (ددلاین، اختیاری)"
        value={values.dueDate}
        onChange={set('dueDate')}
        clearable
        hint="موعد فقط ددلاین واقعی کار است و تعیین نمی‌کند کار در کدام روز برنامه‌ریزی شود."
        errorCode={errors.dueDate}
      />
      <TextField
        label="مانع (اختیاری)"
        value={values.blockedReason}
        onChange={set('blockedReason')}
        maxLength={1000}
        hint="اگر کار متوقف شده، دلیلش را بنویسید؛ وضعیت کار تغییر نمی‌کند."
        errorCode={errors.blockedReason}
      />
      {serverError != null && <ErrorMessage error={serverError} />}
      <div className="form__actions">
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'در حال ذخیره…' : submitLabel}
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
