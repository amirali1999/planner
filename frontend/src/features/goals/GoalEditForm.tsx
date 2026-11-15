import { useState, type FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import type { IsoDate } from '../../shared/date/calendar';
import type { DateRange } from '../../shared/date/periods';
import { JalaliDateField } from '../../shared/ui/JalaliDateField';
import { PeriodPicker } from '../../shared/ui/PeriodPicker';
import { TextField } from '../../shared/ui/TextField';
import { goalsApi } from './api';
import { eligibleParents } from './hierarchy';
import { PERIOD_LABELS, STATUS_LABELS, STATUS_ORDER } from './labels';
import type { Goal, GoalStatus, UpdateGoalRequest } from './types';

interface GoalEditFormProps {
  goal: Goal;
  /** All goals (incl. archived) for the parent picker and names. */
  allGoals: Goal[];
  onSaved: (goal: Goal) => void;
  onCancel: () => void;
}

/** Edits a goal; sends only changed fields. The period type is fixed after creation. */
export function GoalEditForm({ goal, allGoals, onSaved, onCancel }: GoalEditFormProps) {
  const [title, setTitle] = useState(goal.title);
  const [description, setDescription] = useState(goal.description ?? '');
  const [status, setStatus] = useState<GoalStatus>(goal.status);
  const [range, setRange] = useState<DateRange>({ start: goal.periodStart, end: goal.periodEnd });
  const [deadline, setDeadline] = useState<IsoDate | null>(goal.deadline);
  const [parentId, setParentId] = useState(goal.parentGoalId ?? '');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const parents = eligibleParents(goal, allGoals);
  // keep showing the current parent even if it is archived or no longer eligible; changing away is allowed
  const currentParent = goal.parentGoalId ? allGoals.find((g) => g.id === goal.parentGoalId) : undefined;
  const parentOptions = currentParent && !parents.some((p) => p.id === currentParent.id) ? [currentParent, ...parents] : parents;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    const next: FieldErrors = {};
    if (!title.trim()) next.title = 'required';
    if (range.end < range.start) next.periodEnd = 'before_period_start';
    setErrors(next);
    setServerError(null);
    if (Object.keys(next).length > 0) return;

    const patch: UpdateGoalRequest = {};
    if (title.trim() !== goal.title) patch.title = title.trim();
    const desc = description.trim() || null;
    if (desc !== goal.description) patch.description = desc;
    if (status !== goal.status) patch.status = status;
    if (range.start !== goal.periodStart) patch.periodStart = range.start;
    if (range.end !== goal.periodEnd) patch.periodEnd = range.end;
    if (deadline !== goal.deadline) patch.deadline = deadline;
    const newParent = parentId || null;
    if (newParent !== goal.parentGoalId) patch.parentGoalId = newParent;

    setSubmitting(true);
    try {
      onSaved(Object.keys(patch).length === 0 ? goal : await goalsApi.update(goal.id, patch));
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldCodes());
      setServerError(error);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="card form" onSubmit={handleSubmit} noValidate aria-labelledby="goal-edit-heading">
      <h2 id="goal-edit-heading">ویرایش هدف</h2>
      <TextField label="عنوان" value={title} onChange={setTitle} required maxLength={200} errorCode={errors.title} />
      <TextField label="توضیح" value={description} onChange={setDescription} multiline errorCode={errors.description} />
      <div className="form__row">
        <div className="field">
          <label htmlFor="goal-edit-status">وضعیت</label>
          <select id="goal-edit-status" value={status} onChange={(ev) => setStatus(ev.target.value as GoalStatus)}>
            {STATUS_ORDER.map((st) => (
              <option key={st} value={st}>
                {STATUS_LABELS[st]}
              </option>
            ))}
          </select>
          <p className="field__hint">وضعیت را خودتان تعیین می‌کنید؛ درصد پیشرفت آن را تغییر نمی‌دهد.</p>
        </div>
        <div className="field">
          <label htmlFor="goal-edit-parent">هدف والد (اختیاری)</label>
          <select id="goal-edit-parent" value={parentId} onChange={(ev) => setParentId(ev.target.value)} aria-describedby="goal-edit-parent-hint">
            <option value="">بدون والد (هدف مستقل)</option>
            {parentOptions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.title} ({PERIOD_LABELS[p.periodType]}){p.archivedAt ? ' — بایگانی‌شده' : ''}
              </option>
            ))}
          </select>
          <p className="field__hint" id="goal-edit-parent-hint">
            فقط اهداف فعال با دوره بزرگ‌تر از «{PERIOD_LABELS[goal.periodType]}» قابل انتخاب‌اند (پرش از یک سطح مجاز است).
          </p>
        </div>
      </div>
      <p className="meta">نوع دوره: {PERIOD_LABELS[goal.periodType]} (پس از ایجاد قابل تغییر نیست)</p>
      <PeriodPicker
        periodType={goal.periodType}
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
        hint="موعد مستقل از پایان دوره است."
        errorCode={errors.deadline}
      />
      <p className="field__hint">ناهمخوانی تاریخ با هدف والد یا زیرهدف‌ها فقط هشدار داده می‌شود و تاریخ‌ها خودکار تغییر نمی‌کنند.</p>
      {serverError != null && <ErrorMessage error={serverError} />}
      <div className="form__actions">
        <button type="submit" className="button" disabled={submitting}>
          {submitting ? 'در حال ذخیره…' : 'ذخیره تغییرات'}
        </button>
        <button type="button" className="button button--secondary" onClick={onCancel}>
          انصراف
        </button>
      </div>
    </form>
  );
}
