import { useId, useState } from 'react';
import { todayIso } from '../../shared/date/calendar';
import { formatJalaliDate, formatNumber } from '../../shared/format';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import type { FocusItem } from '../focus/types';
import type { Goal } from '../goals/types';
import { TaskAttribution } from './TaskAttribution';
import { tasksApi } from './api';
import { TASK_PRIORITY_LABELS, TASK_STATUS_LABELS, TASK_STATUS_ORDER } from './labels';
import { TaskForm } from './TaskForm';
import { formFromTask, toUpdateRequest } from './taskFormModel';
import type { Task, TaskStatus } from './types';

interface TaskItemProps {
  task: Task;
  goals: Goal[];
  focusItems: FocusItem[];
  onChanged: () => void;
  /** Just created (e.g. from inbox triage). */
  highlighted?: boolean;
}

export function TaskItem({ task, goals, focusItems, onChanged, highlighted }: TaskItemProps) {
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const archived = task.archivedAt != null;
  const overdue = task.dueDate != null && task.status !== 'done' && task.dueDate < todayIso();

  async function patch(body: Parameters<typeof tasksApi.update>[1]) {
    setBusy(true);
    setError(null);
    try {
      await tasksApi.update(task.id, body);
      onChanged();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  if (editing) {
    return (
      <li className="task-list__item">
        <TaskForm
          heading={`ویرایش «${task.title}»`}
          initial={formFromTask(task)}
          goals={goals}
          focusItems={focusItems}
          submitLabel="ذخیره تغییرات"
          onCancel={() => setEditing(false)}
          onSubmit={async (value) => {
            const body = toUpdateRequest(task, value);
            if (Object.keys(body).length > 0) await tasksApi.update(task.id, body);
            setEditing(false);
            onChanged();
          }}
        />
      </li>
    );
  }

  return (
    <li
      className={`card task-list__item task task--${task.status}${highlighted ? ' task--highlighted' : ''}`}
      aria-labelledby={`${id}-title`}
      data-task-id={task.id}
      aria-current={highlighted ? 'true' : undefined}
    >
      <div className="task__main">
        <h2 id={`${id}-title`} className="task__title">
          {task.title}
        </h2>
        <div className="field field--compact">
          <label htmlFor={`${id}-status`} className="visually-hidden">
            وضعیت «{task.title}»
          </label>
          <select
            id={`${id}-status`}
            value={task.status}
            disabled={busy || archived}
            onChange={(e) => patch({ status: e.target.value as TaskStatus })}
          >
            {TASK_STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {TASK_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
      </div>
      <p className="meta">
        <TaskAttribution task={task} goals={goals} focusItems={focusItems} />
        {task.priority && <span className="badge"> اولویت {TASK_PRIORITY_LABELS[task.priority]}</span>}
        {archived && <span className="badge badge--muted">بایگانی‌شده</span>}
      </p>
      <p className="meta">
        {task.estimatedMinutes == null ? 'برآورد نشده' : `برآورد: ${formatNumber(task.estimatedMinutes)} دقیقه`}
        {task.dueDate && (
          <>
            {' · '}موعد: {formatJalaliDate(task.dueDate)}
            {overdue && <span className="badge badge--danger">گذشته</span>}
          </>
        )}
      </p>
      {task.blockedReason && <p className="task__blocked">مانع: {task.blockedReason}</p>}
      {task.description && <p className="description">{task.description}</p>}
      {error != null && <ErrorMessage error={error} />}
      <div className="kr__actions">
        {!archived && (
          <button type="button" className="button button--secondary" onClick={() => setEditing(true)} disabled={busy}>
            ویرایش
          </button>
        )}
        <button
          type="button"
          className="button button--secondary"
          disabled={busy}
          onClick={() => patch({ archived: !archived })}
        >
          {archived ? 'خروج از بایگانی' : 'بایگانی'}
        </button>
      </div>
    </li>
  );
}
