import { useState } from 'react';
import { Link } from 'react-router';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { goalsApi } from '../goals/api';
import { focusApi } from './api';
import { EMPTY_FOCUS_FORM, toCreateRequest } from './focusFormModel';
import { FocusItemForm } from './FocusItemForm';
import { GoalBadge } from './GoalBadge';
import { FOCUS_STATUS_LABELS, FOCUS_STATUS_ORDER, KIND_LABELS, KIND_ORDER } from './labels';
import type { FocusArchiveFilter, FocusKind, FocusStatus } from './types';

export function FocusPage() {
  usePageTitle('آیتم‌های تمرکز');
  const [kind, setKind] = useState<FocusKind | ''>('');
  const [status, setStatus] = useState<FocusStatus | ''>('');
  const [archived, setArchived] = useState<FocusArchiveFilter>('false');
  const [showForm, setShowForm] = useState(false);
  const [formKey, setFormKey] = useState(0);
  const items = useApiData(
    (signal) => focusApi.list({ archived, kind: kind || undefined, status: status || undefined }, signal),
    [archived, kind, status],
  );
  const goals = useApiData((signal) => goalsApi.list('all', signal), []);
  const filtered = kind !== '' || status !== '' || archived !== 'false';

  return (
    <section aria-labelledby="focus-heading">
      <div className="page-header">
        <h1 id="focus-heading">آیتم‌های تمرکز</h1>
        <button type="button" className="button" aria-expanded={showForm} onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'بستن فرم' : 'آیتم جدید'}
        </button>
      </div>
      <p className="meta">پروژه، کتاب، دوره یا حوزه‌ای که روی آن کار می‌کنید. اتصال به هدف اختیاری است.</p>

      {showForm && (
        <FocusItemForm
          key={formKey}
          heading="آیتم تمرکز جدید"
          initial={EMPTY_FOCUS_FORM}
          goals={goals.data ?? []}
          submitLabel="ایجاد آیتم"
          onSubmit={async (v) => {
            await focusApi.create(toCreateRequest(v));
            setFormKey((k) => k + 1);
            items.reload();
          }}
        />
      )}

      <div className="filters" role="group" aria-label="فیلتر آیتم‌های تمرکز">
        <div className="field field--compact">
          <label htmlFor="focus-filter-kind">فیلتر نوع</label>
          <select id="focus-filter-kind" value={kind} onChange={(e) => setKind(e.target.value as FocusKind | '')}>
            <option value="">همه</option>
            {KIND_ORDER.map((k) => (
              <option key={k} value={k}>
                {KIND_LABELS[k]}
              </option>
            ))}
          </select>
        </div>
        <div className="field field--compact">
          <label htmlFor="focus-filter-status">فیلتر وضعیت</label>
          <select id="focus-filter-status" value={status} onChange={(e) => setStatus(e.target.value as FocusStatus | '')}>
            <option value="">همه</option>
            {FOCUS_STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {FOCUS_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="field field--compact">
          <label htmlFor="focus-filter-archived">فیلتر بایگانی</label>
          <select id="focus-filter-archived" value={archived} onChange={(e) => setArchived(e.target.value as FocusArchiveFilter)}>
            <option value="false">فعال</option>
            <option value="true">بایگانی‌شده‌ها</option>
            <option value="all">همه</option>
          </select>
        </div>
      </div>

      {items.status === 'loading' && !items.data && <Loading />}
      {items.status === 'error' && <ErrorMessage error={items.error} onRetry={items.reload} />}
      {items.data && items.data.length === 0 && (
        <EmptyState title={filtered ? 'آیتمی با این فیلتر پیدا نشد.' : 'هنوز آیتم تمرکزی ندارید.'}>
          {!filtered && <p>با «آیتم جدید» یک پروژه، کتاب یا دوره بسازید؛ اتصال به هدف لازم نیست.</p>}
        </EmptyState>
      )}
      {items.data && items.data.length > 0 && (
        <ul className="goal-list" aria-label="آیتم‌های تمرکز">
          {items.data.map((f) => (
            <li key={f.id} className="card goal-list__item">
              <h2 className="goal-list__title">
                <Link to={`/focus/${f.id}`}>{f.title}</Link>
              </h2>
              <p className="meta">
                <span className="badge">{KIND_LABELS[f.kind]}</span>
                <span className={`badge badge--status-${f.status}`}>{FOCUS_STATUS_LABELS[f.status]}</span>
                {f.archivedAt && <span className="badge badge--muted">بایگانی‌شده</span>}
              </p>
              <p className="meta">
                <GoalBadge goalId={f.goalId} goals={goals.data ?? []} />
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
