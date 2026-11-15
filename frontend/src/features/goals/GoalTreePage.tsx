import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { todayIso } from '../../shared/date/calendar';
import { formatNumber } from '../../shared/format';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { goalsApi } from './api';
import { GoalTree } from './GoalTree';
import { STATUS_LABELS, STATUS_ORDER } from './labels';
import { filterTreeByStatus } from './treeFilter';
import { allParentIds } from './treeNav';
import type { GoalArchiveFilter, GoalStatus } from './types';

export function GoalTreePage() {
  usePageTitle('درخت اهداف');
  const navigate = useNavigate();
  const [archived, setArchived] = useState<GoalArchiveFilter>('false');
  const tree = useApiData((signal) => goalsApi.tree(archived, signal), [archived]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [initialised, setInitialised] = useState<string | null>(null);
  const [statuses, setStatuses] = useState<GoalStatus[]>([]);
  const today = todayIso();
  const filtered = tree.data ? filterTreeByStatus(tree.data.roots, statuses) : null;

  // Expand everything on first load of each filter (personal-scale trees are small). useApiData keeps the previous
  // filter's data visible while loading, so wait until the response is for the selected filter.
  useEffect(() => {
    if (tree.data && tree.data.archived === archived && initialised !== archived) {
      setExpanded(allParentIds(tree.data.roots));
      setInitialised(archived);
    }
  }, [tree.data, archived, initialised]);

  const toggle = (id: string, expand: boolean) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (expand) next.add(id);
      else next.delete(id);
      return next;
    });

  return (
    <section aria-labelledby="tree-heading">
      <div className="page-header">
        <h1 id="tree-heading">درخت اهداف</h1>
        <Link to="/goals">نمای فهرستی</Link>
      </div>
      <p className="meta">
        اهداف مستقل و اهدافی که والدشان در این فیلتر نیست در سطح اول قرار می‌گیرند. با کلیدهای جهت حرکت کنید (← باز کردن، →
        بستن)، Enter جزئیات را باز می‌کند.
      </p>
      <div className="filters">
        <div className="field field--compact">
          <label htmlFor="tree-filter-archived">فیلتر بایگانی</label>
          <select id="tree-filter-archived" value={archived} onChange={(e) => setArchived(e.target.value as GoalArchiveFilter)}>
            <option value="false">اهداف فعال</option>
            <option value="true">بایگانی‌شده‌ها</option>
            <option value="all">همه</option>
          </select>
        </div>
        <fieldset className="field field--compact filters__statuses">
          <legend>وضعیت</legend>
          {STATUS_ORDER.map((s) => (
            <label key={s} className="checkbox">
              <input
                type="checkbox"
                checked={statuses.includes(s)}
                onChange={(e) =>
                  setStatuses((prev) => (e.target.checked ? STATUS_ORDER.filter((x) => x === s || prev.includes(x)) : prev.filter((x) => x !== s)))
                }
              />
              {STATUS_LABELS[s]}
            </label>
          ))}
        </fieldset>
        {tree.data && tree.data.goalCount > 0 && (
          <div className="form__actions tree__actions">
            <button type="button" className="button button--secondary" onClick={() => tree.data && setExpanded(allParentIds(tree.data.roots))}>
              باز کردن همه
            </button>
            <button type="button" className="button button--secondary" onClick={() => setExpanded(new Set())}>
              بستن همه
            </button>
          </div>
        )}
      </div>

      {tree.status === 'loading' && !tree.data && <Loading />}
      {tree.status === 'error' && <ErrorMessage error={tree.error} onRetry={tree.reload} />}
      {tree.data && tree.data.goalCount === 0 && (
        <EmptyState title={archived === 'true' ? 'هدف بایگانی‌شده‌ای وجود ندارد.' : 'هدفی برای نمایش وجود ندارد.'}>
          {archived !== 'true' && (
            <p>
              از <Link to="/goals">فهرست اهداف</Link> یک هدف بسازید.
            </p>
          )}
        </EmptyState>
      )}
      {tree.data && tree.data.goalCount > 0 && filtered && (
        <>
          <p className="meta" aria-live="polite">
            {statuses.length === 0
              ? `${formatNumber(tree.data.goalCount)} هدف`
              : `${formatNumber(filtered.matchCount)} هدف با وضعیت انتخاب‌شده از ${formatNumber(tree.data.goalCount)}؛ والدهای کم‌رنگ فقط برای نمایش جایگاه آمده‌اند.`}
          </p>
          {filtered.roots.length === 0 ? (
            <EmptyState title="هدفی با این وضعیت در این فیلتر نیست." />
          ) : (
            <GoalTree
              roots={filtered.roots}
              expanded={expanded}
              onToggle={toggle}
              onActivate={(id) => navigate(`/goals/${id}`)}
              today={today}
              contextIds={filtered.contextIds}
              onStatusChanged={tree.reload}
            />
          )}
        </>
      )}
    </section>
  );
}
