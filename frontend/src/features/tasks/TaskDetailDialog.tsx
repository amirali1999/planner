import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../shared/api/client';
import type { IsoDate } from '../../shared/date/calendar';
import { formatJalaliDateWithWeekday, formatNumber, formatTimestamp } from '../../shared/format';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import { fieldMessage, type FieldErrors } from '../../shared/ui/fieldErrors';
import { Icon, type IconName } from '../../shared/ui/Icon';
import { JalaliDateField } from '../../shared/ui/JalaliDateField';
import type { FocusItem } from '../focus/types';
import type { Goal } from '../goals/types';
import { tasksApi } from './api';
import { TASK_PRIORITY_LABELS, TASK_PRIORITY_ORDER, TASK_STATUS_LABELS, TASK_STATUS_ORDER } from './labels';
import { formFromTask, parseTaskForm, toUpdateRequest, type TaskFormValues } from './taskFormModel';
import type { Task } from './types';

interface Props {
  /** The live task to show; null = closed. */
  task: Task | null;
  /** All goals / focus items (incl. archived, for names); archived ones are not linked nor offered. */
  goals: Goal[];
  focusItems: FocusItem[];
  /** Days of the shown week the task is planned on; omitted = the «برنامهٔ این هفته» line is not shown. */
  plannedDays?: IsoDate[];
  /** On the shown week without a day (weekly plan item). */
  onWeekWithoutDay?: boolean;
  /** Open already in edit mode (e.g. from a ⋮ «ویرایش» menu); «انصراف» then closes the pop-up. */
  startEditing?: boolean;
  onClose: () => void;
  /** After a save; the parent reloads its data (the dialog keeps showing the task by id). */
  onChanged: () => void;
}

/**
 * Task details as a modal (native <dialog>: focus trap, Esc, backdrop click). «ویرایش» keeps the very same layout and
 * turns each value into its input in place; «ذخیره» sends only the changed fields (PATCH).
 */
export function TaskDetailDialog({ task, goals, focusItems, plannedDays, onWeekWithoutDay = false, startEditing = false, onClose, onChanged }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [values, setValues] = useState<TaskFormValues | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const open = task !== null;
  const taskId = task?.id;

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  useEffect(() => {
    if (!task) return;
    setEditing(startEditing);
    setValues(startEditing ? formFromTask(task) : null);
    setErrors({});
    setServerError(null);
  }, [taskId, startEditing]); // eslint-disable-line react-hooks/exhaustive-deps

  const v = task && editing && values ? values : null;
  const set = <K extends keyof TaskFormValues>(key: K) => (value: TaskFormValues[K]) =>
    setValues((prev) => (prev ? { ...prev, [key]: value } : prev));

  function beginEdit() {
    if (!task) return;
    setValues(formFromTask(task));
    setErrors({});
    setServerError(null);
    setEditing(true);
  }
  function cancelEdit() {
    if (startEditing) onClose();
    else setEditing(false);
  }
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!task || !values || busy) return;
    const parsed = parseTaskForm(values);
    setServerError(null);
    if (!parsed.ok) {
      setErrors(parsed.errors);
      return;
    }
    setErrors({});
    setBusy(true);
    try {
      const body = toUpdateRequest(task, parsed.value);
      if (Object.keys(body).length > 0) await tasksApi.update(task.id, body);
      setEditing(false);
      onChanged();
    } catch (err) {
      if (err instanceof ApiError) setErrors(err.fieldCodes());
      setServerError(err);
    } finally {
      setBusy(false);
    }
  }
  async function toggleDone() {
    if (!task) return;
    setBusy(true);
    setServerError(null);
    try {
      await tasksApi.update(task.id, { status: task.status === 'done' ? 'not_started' : 'done' });
      onChanged();
    } catch (err) {
      setServerError(err);
    } finally {
      setBusy(false);
    }
  }

  const err = (key: string) => errors[key] && <span className="field__error">{fieldMessage(errors[key])}</span>;
  /** One line of the details grid: label + value (read) or its control (edit). */
  const line = (icon: IconName, label: string, control: string | null, read: ReactNode, edit?: ReactNode) => (
    <>
      <dt>
        <Icon name={icon} size={18} />
        {v && edit && control ? <label htmlFor={control}>{label}</label> : label}
      </dt>
      <dd>{v && edit ? edit : read}</dd>
    </>
  );

  return (
    <dialog ref={ref} className="modal task-modal" aria-labelledby="task-modal-heading" onClose={() => open && onClose()}
      onClick={(e) => e.target === e.currentTarget && onClose()}>
      {task && (() => {
        const goal = task.effectiveGoalId ? goals.find((g) => g.id === task.effectiveGoalId && !g.archivedAt) : undefined;
        const item = task.focusItemId ? focusItems.find((f) => f.id === task.focusItemId && !f.archivedAt) : undefined;
        const waiting = task.status !== 'done' && task.blockedReason;
        const goalOptions = goals.filter((g) => !g.archivedAt || g.id === v?.goalId);
        const focusOptions = focusItems.filter((f) => !f.archivedAt || f.id === v?.focusItemId);
        return (
          <div className="modal__body">
            <button type="button" className="icon-button modal__close" aria-label="بستن" onClick={onClose}>✕</button>
            <form className={`card task-detail${v ? ' task-detail--editing' : ''}`} onSubmit={save} noValidate>
              <header className="task-detail__head">
                {v ? (
                  <>
                    <label htmlFor={`${id}-title`} id="task-modal-heading" className="visually-hidden">عنوان تسک</label>
                    <input id={`${id}-title`} className="task-detail__title-input" value={v.title} maxLength={200} autoFocus
                      aria-invalid={errors.title ? true : undefined} onChange={(e) => set('title')(e.target.value)} />
                    {err('title')}
                  </>
                ) : (
                  <h2 id="task-modal-heading" className={task.status === 'done' ? 'task-detail__title--done' : undefined}>{task.title}</h2>
                )}
                <div className="task-detail__chips">
                  {v ? (
                    <>
                      <select aria-label="وضعیت" className={`task-detail__chip-select cal-chip cal-chip--${v.status}`} value={v.status}
                        onChange={(e) => set('status')(e.target.value as TaskFormValues['status'])}>
                        {TASK_STATUS_ORDER.map((s) => <option key={s} value={s}>{TASK_STATUS_LABELS[s]}</option>)}
                      </select>
                      <select aria-label="اولویت" className={`task-detail__chip-select prio prio--${v.priority}`} value={v.priority}
                        onChange={(e) => set('priority')(e.target.value as TaskFormValues['priority'])}>
                        {TASK_PRIORITY_ORDER.map((p) => <option key={p} value={p}>{TASK_PRIORITY_LABELS[p]}</option>)}
                      </select>
                    </>
                  ) : (
                    <>
                      <span className={`cal-chip cal-chip--${task.status}`}>{waiting ? 'در انتظار' : TASK_STATUS_LABELS[task.status]}</span>
                      {task.priority && <span className={`prio prio--${task.priority}`}>{TASK_PRIORITY_LABELS[task.priority]}</span>}
                    </>
                  )}
                </div>
              </header>

              {v ? (
                <>
                  <textarea aria-label="توضیح" className="task-detail__desc-input" value={v.description} rows={3}
                    placeholder="توضیحی ثبت نشده است." onChange={(e) => set('description')(e.target.value)} />
                  {err('description')}
                </>
              ) : task.description ? (
                <p className="task-detail__desc">{task.description}</p>
              ) : (
                <p className="task-detail__desc task-detail__desc--empty">توضیحی ثبت نشده است.</p>
              )}

              <dl className="task-detail__grid">
                {line('target', 'هدف', `${id}-goal`,
                  <>
                    {goal ? <Link to={`/goals/${goal.id}`}>{goal.title}</Link> : '—'}
                    {goal && task.effectiveGoalSource === 'focus_item' && <span className="meta"> (از آیتم تمرکز)</span>}
                  </>,
                  <>
                    <select id={`${id}-goal`} value={v?.goalId} onChange={(e) => set('goalId')(e.target.value)}>
                      <option value="">بدون هدف مستقیم</option>
                      {goalOptions.map((g) => <option key={g.id} value={g.id}>{g.title}</option>)}
                    </select>
                    {err('goalId')}
                  </>)}
                {line('layers', 'آیتم تمرکز', `${id}-focus`,
                  item ? <Link to={`/focus/${item.id}`}>{item.title}</Link> : '—',
                  <>
                    <select id={`${id}-focus`} value={v?.focusItemId} onChange={(e) => set('focusItemId')(e.target.value)}>
                      <option value="">بدون آیتم تمرکز</option>
                      {focusOptions.map((f) => <option key={f.id} value={f.id}>{f.title}</option>)}
                    </select>
                    {err('focusItemId')}
                  </>)}
                {line('calendar', 'ددلاین', null,
                  task.dueDate ? formatJalaliDateWithWeekday(task.dueDate) : '—',
                  <div className="task-detail__date">
                    <JalaliDateField label="ددلاین" value={v?.dueDate ?? null} onChange={set('dueDate')} clearable errorCode={errors.dueDate} />
                  </div>)}
                {plannedDays && line('today', 'برنامهٔ این هفته', null,
                  plannedDays.length > 0
                    ? plannedDays.map((d) => formatJalaliDateWithWeekday(d)).join('، ')
                    : onWeekWithoutDay ? 'بدون روز' : '—')}
                {line('status', 'برآورد زمان', `${id}-mins`,
                  task.estimatedMinutes != null ? `${formatNumber(task.estimatedMinutes)} دقیقه` : '—',
                  <>
                    <span className="task-detail__mins">
                      <input id={`${id}-mins`} inputMode="numeric" dir="ltr" value={v?.estimatedMinutes} placeholder="—"
                        aria-invalid={errors.estimatedMinutes ? true : undefined} onChange={(e) => set('estimatedMinutes')(e.target.value)} />
                      دقیقه
                    </span>
                    {err('estimatedMinutes')}
                  </>)}
                {(task.blockedReason || v) && line('flag', 'مانع', `${id}-blocked`,
                  task.blockedReason,
                  <>
                    <input id={`${id}-blocked`} value={v?.blockedReason} maxLength={1000} placeholder="اگر کار متوقف شده، دلیلش"
                      onChange={(e) => set('blockedReason')(e.target.value)} />
                    {err('blockedReason')}
                  </>)}
                {line('file', 'ایجاد', null, formatTimestamp(task.createdAt))}
                {task.completedAt && line('check', 'انجام', null, formatTimestamp(task.completedAt))}
              </dl>

              {!v && task.warnings.length > 0 && (
                <ul className="task-detail__warnings">{task.warnings.map((w) => <li key={w.code}>{w.message}</li>)}</ul>
              )}
              {serverError != null && <ErrorMessage error={serverError} />}
              <div className="form__actions">
                {v ? (
                  <>
                    {/* distinct keys: React must not turn the clicked «ویرایش» button into this submit button mid-click */}
                    <button key="save" type="submit" className="button" disabled={busy}>{busy ? 'در حال ذخیره…' : 'ذخیره'}</button>
                    <button key="cancel" type="button" className="button button--secondary" onClick={cancelEdit}>انصراف</button>
                  </>
                ) : (
                  <>
                    <button key="edit" type="button" className="button" onClick={beginEdit} autoFocus>ویرایش</button>
                    <button type="button" className="button button--secondary" disabled={busy} onClick={toggleDone}>
                      {task.status === 'done' ? 'برگرداندن به انجام‌نشده' : 'انجام شد'}
                    </button>
                    <button type="button" className="button button--secondary" onClick={onClose}>بستن</button>
                  </>
                )}
              </div>
              <p className="meta task-detail__updated">آخرین تغییر: {formatTimestamp(task.updatedAt)}</p>
            </form>
          </div>
        );
      })()}
    </dialog>
  );
}
