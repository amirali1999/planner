import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { todayIso } from '../../shared/date/calendar';
import { formatNumber } from '../../shared/format';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { progressApi } from '../dashboard/api';
import type { GoalProgress } from '../dashboard/types';
import { goalsApi } from './api';
import { focusApi } from '../focus/api';
import { GoalBoard } from './GoalBoard';
import { GoalBoardCard } from './GoalBoardCard';
import { focusItemsByGoal } from './goalBoardModel';
import { GoalCreateForm } from './GoalCreateForm';
import {
  DEFAULT_FILTERS,
  filtersFromSearch,
  filtersToSearch,
  goalApiQuery,
  canReorder,
  type DueFilter,
  type GoalListFilters,
  type GoalSort,
} from './goalQuery';
import { PERIOD_LABELS, PERIOD_ORDER, STATUS_LABELS, STATUS_ORDER } from './labels';
import type { GoalArchiveFilter, GoalStatus, PeriodType } from './types';

const DUE_LABELS: Record<DueFilter, string> = {
  any: 'همه',
  overdue: 'گذشته از موعد',
  week: 'موعد در ۷ روز آینده',
  month: 'موعد در ۳۰ روز آینده',
};


export function GoalsPage() {
  usePageTitle('اهداف');
  const [search, setSearch] = useSearchParams();
  const filters = filtersFromSearch(search);
  const today = todayIso();
  // the board shows every matching goal in one scrollable row (no paging)
  const query = goalApiQuery(filters, today, null);
  const goals = useApiData((signal) => goalsApi.search(query, signal), [query]);
  const ids = goals.data?.items.map((g) => g.id) ?? [];
  const idKey = ids.join(',');
  const progress = useApiData<GoalProgress[]>(
    (signal) => (ids.length ? progressApi.goals(ids, signal) : Promise.resolve([])),
    [idKey],
  );
  const progressOf = (id: string) => progress.data?.find((p) => p.goalId === id);
  // all active focus items of the user in ONE request, grouped per goal on the client
  const focusItems = useApiData((signal) => focusApi.list({ archived: 'false' }, signal), []);
  const [createOpen, setCreateOpen] = useState(false);
  const [revealId, setRevealId] = useState<string | null>(null);

  const update = (patch: Partial<GoalListFilters>) =>
    setSearch(filtersToSearch({ ...filters, page: 1, ...patch }), { replace: true });
  const toggleStatus = (s: GoalStatus, on: boolean) =>
    update({ statuses: on ? [...filters.statuses, s] : filters.statuses.filter((x) => x !== s) });

  const total = goals.data?.totalCount ?? 0;
  const reorderable = canReorder(filters);

  const filtered =
    filters.statuses.length > 0 || filters.periodTypes.length > 0 || filters.due !== 'any' || filters.archived !== 'false';

  const [showFilters, setShowFilters] = useState(filtered);
  const focusIndex = focusItemsByGoal(focusItems.data ?? []);
  const reloadBoard = () => {
    goals.reload();
    progress.reload();
    focusItems.reload();
  };

  return (
    <section className="goals-page" aria-labelledby="goals-heading">
      <div className="goals-page__header">
        <h1 id="goals-heading" className="goals-page__title">
          <Icon name="target" size={30} className="dash-card__icon" />
          اهداف
        </h1>
        <div className="goals-page__actions">
          <Link to="/goals/tree" className="button button--secondary goals-page__tool">
            <Icon name="tree" size={18} /> نمای درختی
          </Link>
          <button type="button" className={`button button--secondary goals-page__tool${filtered ? ' goals-page__tool--active' : ''}`}
            aria-expanded={showFilters} aria-controls="goal-filters" onClick={() => setShowFilters((v) => !v)}>
            <Icon name="filter" size={18} /> فیلترها{filtered ? ' (فعال)' : ''}
          </button>
          <button type="button" className="button goals-page__add" aria-expanded={createOpen} onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={20} /> افزودن هدف
          </button>
        </div>
      </div>


      {showFilters && (
        <form id="goal-filters" className="filters goals-page__filters" role="search" aria-label="فیلتر اهداف" onSubmit={(e) => e.preventDefault()}>
          <fieldset className="field field--compact filters__statuses">
            <legend>وضعیت</legend>
            {STATUS_ORDER.map((s) => (
              <label key={s} className="checkbox">
                <input type="checkbox" checked={filters.statuses.includes(s)} onChange={(e) => toggleStatus(s, e.target.checked)} />
                {STATUS_LABELS[s]}
              </label>
            ))}
          </fieldset>
          <div className="field field--compact">
            <label htmlFor="goal-filter-period">نوع دوره</label>
            <select id="goal-filter-period" value={filters.periodTypes[0] ?? ''}
              onChange={(e) => update({ periodTypes: e.target.value ? [e.target.value as PeriodType] : [] })}>
              <option value="">همه</option>
              {PERIOD_ORDER.map((p) => (
                <option key={p} value={p}>{PERIOD_LABELS[p]}</option>
              ))}
            </select>
          </div>
          <div className="field field--compact">
            <label htmlFor="goal-filter-due">موعد</label>
            <select id="goal-filter-due" value={filters.due} onChange={(e) => update({ due: e.target.value as DueFilter })}>
              {(Object.keys(DUE_LABELS) as DueFilter[]).map((d) => (
                <option key={d} value={d}>{DUE_LABELS[d]}</option>
              ))}
            </select>
          </div>
          <div className="field field--compact">
            <label htmlFor="goal-filter-archived">فیلتر بایگانی</label>
            <select id="goal-filter-archived" value={filters.archived} onChange={(e) => update({ archived: e.target.value as GoalArchiveFilter })}>
              <option value="false">اهداف فعال</option>
              <option value="true">بایگانی‌شده‌ها</option>
              <option value="all">همه</option>
            </select>
          </div>
          <div className="field field--compact">
            <label htmlFor="goal-sort">ترتیب</label>
            <select id="goal-sort" value={filters.sort} onChange={(e) => update({ sort: e.target.value as GoalSort })}>
              <option value="priority">اولویت (ترتیب شما)</option>
              <option value="period">شروع دوره</option>
              <option value="deadline">نزدیک‌ترین موعد</option>
            </select>
          </div>
          {filtered && (
            <div className="form__actions tree__actions">
              <button type="button" className="button button--secondary" onClick={() => setSearch(filtersToSearch(DEFAULT_FILTERS), { replace: true })}>
                حذف فیلترها
              </button>
            </div>
          )}
          <p className="meta goals-page__hint">
            حلقه «پیشرفت نتیجه» (از نتایج کلیدی) است و «اجرا» جدا نوشته شده؛ وضعیت را خودتان از منوی ⋮ تعیین می‌کنید و پیشرفت آن را
            تغییر نمی‌دهد. «گذشته از موعد» فقط اهداف شروع‌نشده، در حال انجام و متوقف را نشان می‌دهد.
          </p>
        </form>
      )}

      {goals.status === 'loading' && !goals.data && <Loading />}
      {goals.status === 'error' && <ErrorMessage error={goals.error} onRetry={goals.reload} />}
      {focusItems.status === 'error' && <ErrorMessage error={focusItems.error} onRetry={focusItems.reload} />}
      {goals.data && goals.data.items.length === 0 && !createOpen &&
        (!filtered ? (
          <EmptyState title="هدف فعالی ندارید.">
            <p>با «افزودن هدف» یک هدف سالانه، فصلی، ماهانه یا هفتگی بسازید. اهداف بایگانی‌شده با «فیلترها» دیده می‌شوند.</p>
          </EmptyState>
        ) : (
          <EmptyState title="هدفی با این فیلترها پیدا نشد." />
        ))}
      {goals.data && (goals.data.items.length > 0 || createOpen) && (
        <>
          <p className="meta" aria-live="polite">{`${formatNumber(total)} هدف`}</p>
          <GoalBoard
            goals={goals.data.items}
            reorderable={reorderable}
            revealId={revealId}
            trailing={createOpen ? (
              <GoalCreateForm variant="card" heading="هدف جدید" onCancel={() => setCreateOpen(false)}
                onCreated={(id) => {
                  setCreateOpen(false);
                  setRevealId(id);
                  reloadBoard();
                }} />
            ) : undefined}
            onReorder={async (ids) => {
              await goalsApi.reorder(ids);
              goals.reload();
            }}
            renderCard={(goal, controls) => (
              <GoalBoardCard goal={goal} progress={progressOf(goal.id)} progressError={progress.status === 'error'}
                focusItems={focusIndex.get(goal.id) ?? []} today={today} onChanged={reloadBoard} controls={controls} />
            )}
          />
          {!reorderable && goals.data.items.length > 1 && (
            <p className="meta">برای جابه‌جا کردن کارت‌ها (تغییر اولویت)، فیلترها را بردارید و ترتیب را روی «اولویت» بگذارید.</p>
          )}
        </>
      )}
    </section>
  );
}

