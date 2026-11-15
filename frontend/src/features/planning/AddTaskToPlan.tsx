import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import type { IsoDate } from '../../shared/date/calendar';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { useApiData } from '../../shared/ui/useApiData';
import { tasksApi } from '../tasks/api';
import { TASK_STATUS_LABELS } from '../tasks/labels';
import { dailyPlanApi } from './api';

interface AddTaskToPlanProps {
  date: IsoDate;
  plannedTaskIds: Set<string>;
  onAdded: () => void;
}

/** Picks an existing active task that is not yet on this day's plan. Tasks are never created or copied here. */
export function AddTaskToPlan({ date, plannedTaskIds, onAdded }: AddTaskToPlanProps) {
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'false' }, signal), []);
  const [taskId, setTaskId] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const available = (tasks.data ?? []).filter((t) => !plannedTaskIds.has(t.id));

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!taskId || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await dailyPlanApi.addTask(date, taskId);
      setTaskId('');
      onAdded();
    } catch (e) {
      setError(e);
    } finally {
      setSubmitting(false);
    }
  }

  if (tasks.status === 'loading' && !tasks.data) return <Loading label="در حال بارگذاری کارها…" />;
  if (tasks.status === 'error') return <ErrorMessage error={tasks.error} onRetry={tasks.reload} />;
  if ((tasks.data ?? []).length === 0) {
    return (
      <EmptyState title="هنوز کاری برای افزودن وجود ندارد.">
        <p>
          ابتدا در بخش <Link to="/tasks">کارها</Link> یک کار بسازید.
        </p>
      </EmptyState>
    );
  }
  if (available.length === 0) {
    return <p className="meta">همه کارهای فعال در برنامه این روز هستند.</p>;
  }

  return (
    <form className="card form form--row" onSubmit={handleSubmit} aria-label="افزودن کار به برنامه روز">
      <div className="field">
        <label htmlFor="plan-add-task">افزودن کار موجود</label>
        <select id="plan-add-task" value={taskId} onChange={(e) => setTaskId(e.target.value)}>
          <option value="">— انتخاب کار —</option>
          {available.map((t) => (
            <option key={t.id} value={t.id}>
              {t.title} ({TASK_STATUS_LABELS[t.status]})
            </option>
          ))}
        </select>
      </div>
      <div className="form__actions">
        <button type="submit" className="button" disabled={!taskId || submitting}>
          {submitting ? 'در حال افزودن…' : 'افزودن به برنامه'}
        </button>
      </div>
      {error != null && <ErrorMessage error={error} />}
    </form>
  );
}
