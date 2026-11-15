import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { formatTimestamp } from '../../shared/format';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { goalsApi } from '../goals/api';
import { tasksApi } from '../tasks/api';
import { TASK_STATUS_LABELS } from '../tasks/labels';
import { TaskAttribution } from '../tasks/TaskAttribution';
import { focusApi } from './api';
import { formFromItem, toUpdateRequest } from './focusFormModel';
import { FocusItemForm } from './FocusItemForm';
import { GoalBadge } from './GoalBadge';
import { FOCUS_STATUS_LABELS, KIND_LABELS } from './labels';

export function FocusDetailPage() {
  const { focusItemId = '' } = useParams();
  const item = useApiData((signal) => focusApi.get(focusItemId, signal), [focusItemId]);
  const goals = useApiData((signal) => goalsApi.list('all', signal), []);
  const tasks = useApiData((signal) => tasksApi.list({ focusItemId, archived: 'false' }, signal), [focusItemId]);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  usePageTitle(item.data?.title ?? 'آیتم تمرکز');

  if (item.status === 'loading' && !item.data) return <Loading />;
  if (item.status === 'error' && !item.data) {
    const notFound = item.error instanceof ApiError && item.error.status === 404;
    return (
      <section>
        <h1>{notFound ? 'آیتم تمرکز پیدا نشد' : 'خطا در بارگذاری'}</h1>
        <ErrorMessage error={item.error} onRetry={notFound ? undefined : item.reload} />
        <p>
          <Link to="/focus">بازگشت به آیتم‌های تمرکز</Link>
        </p>
      </section>
    );
  }
  const f = item.data;
  if (!f) return null;
  const archived = f.archivedAt != null;

  async function toggleArchive() {
    setBusy(true);
    setError(null);
    try {
      await focusApi.update(f!.id, { archived: !archived });
      item.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="focus-item-heading">
      <p>
        <Link to="/focus">→ همه آیتم‌های تمرکز</Link>
      </p>
      <div className="page-header">
        <h1 id="focus-item-heading">{f.title}</h1>
        {!archived && !editing && (
          <button type="button" className="button" onClick={() => setEditing(true)}>
            ویرایش
          </button>
        )}
      </div>
      <p className="meta">
        <span className="badge">{KIND_LABELS[f.kind]}</span>
        <span className={`badge badge--status-${f.status}`}>{FOCUS_STATUS_LABELS[f.status]}</span>
        {archived && <span className="badge badge--muted">بایگانی‌شده</span>}
      </p>
      {editing ? (
        <FocusItemForm
          heading={`ویرایش «${f.title}»`}
          initial={formFromItem(f)}
          goals={goals.data ?? []}
          submitLabel="ذخیره تغییرات"
          onCancel={() => setEditing(false)}
          onSubmit={async (v) => {
            const patch = toUpdateRequest(f, v);
            if (Object.keys(patch).length > 0) await focusApi.update(f.id, patch);
            setEditing(false);
            item.reload();
          }}
        />
      ) : (
        <>
          <p>
            <GoalBadge goalId={f.goalId} goals={goals.data ?? []} />
          </p>
          {f.description && <p className="description">{f.description}</p>}
          <p className="meta">ایجاد: {formatTimestamp(f.createdAt)}</p>
        </>
      )}

      <section className="section" aria-labelledby="focus-tasks-heading">
        <h2 id="focus-tasks-heading">کارهای مرتبط</h2>
        <p className="meta">
          کارهایی که این آیتم تمرکز را دارند. کاری که هدف مستقیم هم دارد، به همان هدف مستقیم نسبت داده می‌شود.
        </p>
        {tasks.status === 'loading' && !tasks.data && <Loading />}
        {tasks.status === 'error' && <ErrorMessage error={tasks.error} onRetry={tasks.reload} />}
        {tasks.data && tasks.data.length === 0 && (
          <EmptyState title="کاری به این آیتم تمرکز وصل نیست.">
            <p>
              در بخش <Link to="/tasks">کارها</Link> هنگام ساخت یا ویرایش کار، این آیتم تمرکز را انتخاب کنید.
            </p>
          </EmptyState>
        )}
        {tasks.data && tasks.data.length > 0 && (
          <ul className="child-list" aria-label="کارهای این آیتم تمرکز">
            {tasks.data.map((t) => (
              <li key={t.id}>
                {t.title} <span className={`badge badge--status-${t.status}`}>{TASK_STATUS_LABELS[t.status]}</span>
                <span className="meta">
                  {' · '}
                  <TaskAttribution task={t} goals={goals.data ?? []} focusItems={[f]} />
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="section" aria-labelledby="focus-archive-heading">
        <h2 id="focus-archive-heading">بایگانی</h2>
        <p className="meta">
          {archived
            ? 'این آیتم بایگانی شده و فقط قابل مشاهده است؛ چیزی حذف نشده است.'
            : 'بایگانی، آیتم را از فهرست فعال خارج می‌کند؛ هدف مرتبط و داده‌ها دست نمی‌خورند.'}
        </p>
        <button type="button" className="button button--secondary" disabled={busy} onClick={toggleArchive}>
          {archived ? 'خروج از بایگانی' : 'بایگانی'}
        </button>
        {error != null && <ErrorMessage error={error} />}
      </section>
    </section>
  );
}
