import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { focusApi } from '../focus/api';
import { goalsApi } from '../goals/api';
import { tasksApi } from './api';
import { TASK_STATUS_LABELS, TASK_STATUS_ORDER } from './labels';
import { TaskForm } from './TaskForm';
import { EMPTY_TASK_FORM, toCreateRequest } from './taskFormModel';
import { TaskItem } from './TaskItem';
import type { ArchiveFilter, TaskStatus } from './types';

export function TasksPage() {
  usePageTitle('کارها');
  const [params] = useSearchParams();
  const highlight = params.get('highlight');
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [archived, setArchived] = useState<ArchiveFilter>('false');
  const [showForm, setShowForm] = useState(false);
  const [formKey, setFormKey] = useState(0);
  const tasks = useApiData(
    (signal) => tasksApi.list({ status: status || undefined, archived }, signal),
    [status, archived],
  );
  const goals = useApiData((signal) => goalsApi.list('all', signal), []);
  const goalList = goals.data ?? [];
  const focus = useApiData((signal) => focusApi.list({ archived: 'all' }, signal), []);
  const focusList = focus.data ?? [];
  const filtered = status !== '' || archived !== 'false';
  useEffect(() => {
    if (highlight && tasks.data) document.querySelector(`[data-task-id="${highlight}"]`)?.scrollIntoView({ block: 'center' });
  }, [highlight, tasks.data]);

  return (
    <section aria-labelledby="tasks-heading">
      <div className="page-header">
        <h1 id="tasks-heading">کارها</h1>
        <button type="button" className="button" aria-expanded={showForm} onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'بستن فرم' : 'کار جدید'}
        </button>
      </div>

      {goals.status === 'error' && <ErrorMessage error={goals.error} onRetry={goals.reload} />}
      {showForm && (
        <TaskForm
          key={formKey}
          heading="کار جدید"
          initial={EMPTY_TASK_FORM}
          goals={goalList}
          focusItems={focusList}
          submitLabel="ایجاد کار"
          onSubmit={async (value) => {
            await tasksApi.create(toCreateRequest(value));
            setFormKey((k) => k + 1); // fresh empty form; prevents a second submit of the same values
            tasks.reload();
          }}
        />
      )}

      <div className="filters" role="group" aria-label="فیلتر کارها">
        <div className="field field--compact">
          <label htmlFor="task-filter-status">فیلتر وضعیت</label>
          <select id="task-filter-status" value={status} onChange={(e) => setStatus(e.target.value as TaskStatus | '')}>
            <option value="">همه</option>
            {TASK_STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {TASK_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="field field--compact">
          <label htmlFor="task-filter-archived">فیلتر بایگانی</label>
          <select id="task-filter-archived" value={archived} onChange={(e) => setArchived(e.target.value as ArchiveFilter)}>
            <option value="false">کارهای فعال</option>
            <option value="true">بایگانی‌شده‌ها</option>
            <option value="all">همه</option>
          </select>
        </div>
      </div>

      {tasks.status === 'loading' && !tasks.data && <Loading />}
      {tasks.status === 'error' && <ErrorMessage error={tasks.error} onRetry={tasks.reload} />}
      {tasks.data && tasks.data.length === 0 && (
        <EmptyState title={filtered ? 'کاری با این فیلتر پیدا نشد.' : 'هنوز کاری ثبت نکرده‌اید.'}>
          {!filtered && <p>با دکمه «کار جدید» یک کار مستقل یا مرتبط با یک هدف بسازید.</p>}
        </EmptyState>
      )}
      {tasks.data && tasks.data.length > 0 && (
        <ul className="task-list" aria-busy={tasks.status === 'loading'}>
          {tasks.data.map((task) => (
            <TaskItem key={task.id} task={task} goals={goalList} focusItems={focusList} onChanged={tasks.reload} highlighted={task.id === highlight} />
          ))}
        </ul>
      )}
    </section>
  );
}
