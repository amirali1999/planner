import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { addDays, isIsoDate, todayIso, type IsoDate } from '../../shared/date/calendar';
import { formatJalaliDate, formatNumber } from '../../shared/format';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon, type IconName } from '../../shared/ui/Icon';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { weekDays } from '../dashboard/dashboardModel';
import { focusApi } from '../focus/api';
import type { FocusItem, FocusKind } from '../focus/types';
import { goalsApi } from '../goals/api';
import { TaskDetailDialog } from '../tasks/TaskDetailDialog';
import type { Goal } from '../goals/types';
import { tasksApi } from '../tasks/api';
import { TASK_PRIORITY_LABELS, TASK_PRIORITY_ORDER } from '../tasks/labels';
import { PriorityPicker } from '../tasks/PriorityPicker';
import type { TaskPriority } from '../tasks/types';
import { dailyPlanApi, weeklyPlanApi } from './api';
import { TimeboxView } from './TimeboxView';
import { TodayPlanButton } from './TodayPlanButton';
import {
  buildWeekRows, EMPTY_FILTER, filterRows, sortRows, STATUS_LABELS, weekLabel, weekStart,
  type WeekFilter, type WeekRow, type WeekSort, type WeekStatus,
} from './weeklyModel';

const KIND_ICONS: Record<FocusKind, IconName> = { book: 'book', course: 'cap', project: 'briefcase', area: 'layers', other: 'focus' };
const SORT_LABELS: Record<WeekSort, string> = { day: 'روز / ددلاین', priority: 'اولویت', status: 'وضعیت', title: 'عنوان' };
const FILTER_STATUSES: WeekStatus[] = ['this_week', 'overdue', 'done'];

/**
 * «برنامه هفتگی» (design ui/weekly plan.png): the tasks of a Saturday–Friday week = the week's seven daily plans + tasks
 * due that week (+ overdue carry-overs in the current week). Navigate weeks, search, filter, sort, plan a task for today,
 * change priority and status in place, and add a task for a day of the week. A switch at the top (?view=timebox) shows
 * the same week as a time-box grid instead (`TimeboxView`).
 */
export function WeeklyPlanPage() {
  usePageTitle('برنامه هفتگی');
  const today = todayIso();
  const [params, setParams] = useSearchParams();
  const requested = params.get('week');
  const start = weekStart(requested && isIsoDate(requested) ? requested : today);
  const view: 'list' | 'timebox' = params.get('view') === 'timebox' ? 'timebox' : 'list';
  const [addBlock, setAddBlock] = useState(0);
  const days = weekDays(start);
  const inWeek = days.some((d) => d.date === today);

  const plans = useApiData((signal) => Promise.all(days.map((d) => dailyPlanApi.get(d.date, signal))), [start]);
  const todayPlan = useApiData((signal) => (inWeek ? Promise.resolve(null) : dailyPlanApi.get(today, signal)), [today, inWeek]);
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'false' }, signal), []);
  const weekItems = useApiData((signal) => weeklyPlanApi.items(start, signal), [start]);
  const goals = useApiData((signal) => goalsApi.list('all', signal), []);
  const items = useApiData((signal) => focusApi.list({ archived: 'all' }, signal), []);
  const reload = () => {
    plans.reload();
    weekItems.reload();
    todayPlan.reload();
    tasks.reload();
  };

  const [filter, setFilter] = useState<WeekFilter>(EMPTY_FILTER);
  const [sort, setSort] = useState<WeekSort>('day');
  const [adding, setAdding] = useState(false);
  /** Task shown in the details pop-up (by id: the live record is looked up after every reload). */
  const [openTaskId, setOpenTaskId] = useState<string | null>(null);

  // archived goals / focus items are hidden everywhere except the archive page
  const goalOf = (id: string | null) => (id ? goals.data?.find((g) => g.id === id && !g.archivedAt) : undefined);
  const itemOf = (id: string | null) => (id ? items.data?.find((i) => i.id === id && !i.archivedAt) : undefined);
  const names = (r: WeekRow) => [goalOf(r.task.effectiveGoalId)?.title ?? '', itemOf(r.task.focusItemId)?.title ?? ''];
  const plannedToday = new Set(
    ((inWeek ? plans.data?.find((p) => p.date === today) : todayPlan.data) ?? { items: [] }).items.map((i) => i.task.id),
  );
  const allRows = plans.data && tasks.data && weekItems.data ? buildWeekRows(start, today, plans.data, tasks.data, weekItems.data) : null;
  const rows = allRows ? sortRows(filterRows(allRows, filter, names), sort) : null;
  const filtered = filter.statuses.length + filter.priorities.length > 0 || filter.goalId !== '';

  const paramsFor = (s: IsoDate, v: typeof view) => ({ ...(s !== weekStart(today) && { week: s }), ...(v === 'timebox' && { view: v }) });
  const goWeek = (s: IsoDate) => setParams(paramsFor(s, view), { replace: true });
  const setView = (v: typeof view) => setParams(paramsFor(start, v), { replace: true });
  const weekTasks = allRows ? sortRows(allRows, 'day').filter((r) => r.status !== 'done').map((r) => r.task) : [];
  const error = plans.error ?? tasks.error ?? weekItems.error;

  return (
    <section className="weekly" aria-labelledby="weekly-heading">
      <header className="weekly__title">
        <div>
          <h1 id="weekly-heading"><Icon name="calendar" size={30} className="dash-card__icon" /> برنامه هفتگی</h1>
          <p className="meta">{view === 'timebox' ? 'زمان‌بندی دقیق کارهای هفته' : 'برنامه‌ریزی و مدیریت تسک‌های این هفته'}</p>
        </div>
        <div className="week-nav" role="group" aria-label="انتخاب هفته">
          <button type="button" className="icon-button" aria-label="هفتهٔ قبل" onClick={() => goWeek(addDays(start, -7))}>
            <Icon name="chevron-right" size={20} />
          </button>
          <span className="week-nav__label" aria-live="polite">
            <Icon name="calendar" size={18} />
            <span>{weekLabel(start, today)}</span>
            <span className="week-nav__range">{`${formatJalaliDate(start)} – ${formatJalaliDate(addDays(start, 6))}`}</span>
          </span>
          <button type="button" className="icon-button" aria-label="هفتهٔ بعد" onClick={() => goWeek(addDays(start, 7))}>
            <Icon name="chevron-left" size={20} />
          </button>
          {!inWeek && (
            <button type="button" className="button button--secondary week-nav__today" onClick={() => goWeek(weekStart(today))}>این هفته</button>
          )}
        </div>
        {view === 'timebox' ? (
          <>
            <span className="weekly__spacer" />
            <button type="button" className="button weekly__add" aria-haspopup="dialog" aria-label="افزودن بلاک زمانی" title="افزودن بلاک زمانی"
              onClick={() => setAddBlock((n) => n + 1)}>
              <Icon name="plus" size={24} />
            </button>
          </>
        ) : (<>
        <div className="focus-page__search weekly__search">
          <label htmlFor="weekly-search" className="visually-hidden">جستجو در تسک‌های هفتگی</label>
          <Icon name="search" size={20} />
          <input id="weekly-search" type="search" placeholder="جستجو در تسک‌های هفتگی…" value={filter.query}
            onChange={(e) => setFilter({ ...filter, query: e.target.value })} />
        </div>
        <Popover label={`فیلتر${filtered ? ' (فعال)' : ''}`} icon="filter" active={filtered}>
          <FilterPanel filter={filter} onChange={setFilter} goals={(goals.data ?? []).filter((g) => !g.archivedAt)} />
        </Popover>
        <Popover label="مرتب‌سازی" title={`مرتب‌سازی: ${SORT_LABELS[sort]}`} icon="sort" menu>
          {(close) => (
            <ul role="menu" className="pop-menu" aria-label="مرتب‌سازی">
              {(Object.keys(SORT_LABELS) as WeekSort[]).map((s) => (
                <li role="none" key={s}>
                  <button type="button" role="menuitemradio" aria-checked={s === sort} className="prio-picker__option"
                    onClick={() => { setSort(s); close(); }}>
                    {SORT_LABELS[s]}<span className="prio-picker__mark" aria-hidden="true">{s === sort ? '✓' : ''}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Popover>
        <button type="button" className="button weekly__add" aria-haspopup="dialog" aria-label="افزودن تسک هفتگی" title="افزودن تسک هفتگی"
          onClick={() => setAdding(true)}>
          <Icon name="plus" size={24} />
        </button>
        </>)}
        <div className="view-switch" role="group" aria-label="نوع نمایش">
          <button type="button" className={`view-switch__btn${view === 'list' ? ' view-switch__btn--on' : ''}`} aria-pressed={view === 'list'}
            onClick={() => setView('list')}>
            <Icon name="list" size={18} /> فهرست
          </button>
          <button type="button" className={`view-switch__btn${view === 'timebox' ? ' view-switch__btn--on' : ''}`} aria-pressed={view === 'timebox'}
            onClick={() => setView('timebox')}>
            <Icon name="calendar" size={18} /> تایم‌باکس
          </button>
        </div>
      </header>

      <AddWeeklyTaskDialog open={adding} days={days} today={today} goals={(goals.data ?? []).filter((g) => !g.archivedAt)}
        items={(items.data ?? []).filter((i) => !i.archivedAt)} onClose={() => setAdding(false)}
        onCreated={() => { setAdding(false); reload(); }} />

      <TaskDetailDialog
        task={openTaskId ? tasks.data?.find((t) => t.id === openTaskId) ?? allRows?.find((r) => r.task.id === openTaskId)?.task ?? null : null}
        plannedDays={allRows?.find((r) => r.task.id === openTaskId)?.days ?? []}
        onWeekWithoutDay={allRows?.find((r) => r.task.id === openTaskId)?.due.kind === 'week'}
        goals={goals.data ?? []} focusItems={items.data ?? []} onClose={() => setOpenTaskId(null)} onChanged={reload} />

      {error != null && <ErrorMessage error={error} onRetry={reload} />}
      {!rows && error == null && <Loading />}
      {rows && view === 'timebox' && (
        <TimeboxView start={start} days={days} today={today} weekTasks={weekTasks} allTasks={tasks.data ?? []} goalOf={goalOf} addRequest={addBlock} onOpenTask={setOpenTaskId} />
      )}
      {rows && view === 'list' && (
        <div className="weekly-table" role="region" aria-label={`تسک‌های ${weekLabel(start, today)}`} tabIndex={0}>
          <table>
            <thead>
              <tr>
                <Th icon="file">عنوان تسک</Th>
                <Th icon="target">هدف مرتبط</Th>
                <Th icon="layers">آیتم تمرکز مرتبط</Th>
                <Th icon="flag">اولویت</Th>
                <Th icon="calendar">ددلاین</Th>
                <Th icon="today">افزودن به برنامه امروز</Th>
                <Th icon="status">وضعیت</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <Row key={r.task.id} row={r} goal={goalOf(r.task.effectiveGoalId)} item={itemOf(r.task.focusItemId)}
                  weekStart={start} days={days} plannedToday={plannedToday.has(r.task.id)} onChanged={reload} onOpen={() => setOpenTaskId(r.task.id)} />
              ))}
            </tbody>
          </table>
          {rows.length === 0 && (
            allRows && allRows.length > 0
              ? <EmptyState title="تسکی با این جستجو یا فیلتر پیدا نشد." />
              : <EmptyState title="برای این هفته تسکی برنامه‌ریزی نشده است.">
                  <p>با «افزودن تسک هفتگی» یا از صفحهٔ <Link to="/focus">آیتم‌های تمرکز</Link> (دکمهٔ «به برنامه هفتگی») تسک اضافه کنید.</p>
                </EmptyState>
          )}
          {rows.length > 0 && <p className="meta weekly__count">{`${formatNumber(rows.length)} تسک`}</p>}
        </div>
      )}
    </section>
  );
}

function Th({ icon, children }: { icon: IconName; children: ReactNode }) {
  return (
    <th scope="col">
      <span className="weekly-th"><Icon name={icon} size={20} />{children}</span>
    </th>
  );
}

function Row({ row, goal, item, weekStart: shownWeek, days, plannedToday, onChanged, onOpen }: {
  row: WeekRow; goal: Goal | undefined; item: FocusItem | undefined; days: { date: IsoDate; label: string }[];
  weekStart: IsoDate; plannedToday: boolean; onChanged: () => void; onOpen: () => void;
}) {
  const t = row.task;
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savingDay, setSavingDay] = useState(false);
  const [dayError, setDayError] = useState<string | null>(null);
  async function removeFromWeek() {
    setSaving(true);
    setError(null);
    try {
      await weeklyPlanApi.removeTask(shownWeek, t.id);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'از برنامهٔ هفته خارج نشد.');
    } finally {
      setSaving(false);
    }
  }
  async function changeDay(next: IsoDate) {
    if (next === row.due.date || savingDay) return;
    setSavingDay(true);
    setDayError(null);
    try {
      if (row.due.kind === 'day') await dailyPlanApi.move(row.due.date, t.id, next);
      else if (row.due.kind === 'week') await dailyPlanApi.addTask(next, t.id);
      else await tasksApi.update(t.id, { dueDate: next });
      onChanged();
    } catch (e) {
      setDayError(e instanceof ApiError ? e.message : 'روز تغییر نکرد.');
    } finally {
      setSavingDay(false);
    }
  }
  const currentOutsideWeek = row.due.date != null && !days.some((d) => d.date === row.due.date);
  return (
    <tr className={`weekly-row weekly-row--${row.status}`}>
      <th scope="row" className="weekly-cell weekly-cell--title">
        <span className="weekly-title">
          <Icon name="file" size={20} />
          <button type="button" className="weekly-title__open" aria-haspopup="dialog" onClick={onOpen}>{t.title}</button>
        </span>
      </th>
      <td className="weekly-cell">
        {goal ? <Link to={`/goals/${goal.id}`} className="weekly-ref"><Icon name="target" size={18} />{goal.title}</Link> : <span className="ft-muted">—</span>}
      </td>
      <td className="weekly-cell">
        {item ? <Link to={`/focus/${item.id}`} className="weekly-ref"><Icon name={KIND_ICONS[item.kind]} size={18} />{item.title}</Link> : <span className="ft-muted">—</span>}
      </td>
      <td className="weekly-cell weekly-cell--center"><PriorityPicker task={t} onChanged={onChanged} /></td>
      <td className={`weekly-cell weekly-cell--center${row.status === 'overdue' && row.due.kind === 'date' ? ' ft-col--late' : ''}`}
        title={row.due.kind === 'day' ? 'روز برنامه‌ریزی‌شده در این هفته' : row.due.kind === 'date' ? 'ددلاین تسک' : row.due.kind === 'week' ? 'در برنامهٔ این هفته، بدون روز مشخص' : undefined}>
        <span className="weekly-day-picker">
          <select value={row.due.date ?? ''} disabled={savingDay} aria-label={`تغییر روز «${t.title}»`}
            title="تغییر روز در همین هفته" onChange={(e) => void changeDay(e.target.value as IsoDate)}>
            {row.due.date == null && <option value="" disabled>{row.due.label}</option>}
            {currentOutsideWeek && <option value={row.due.date!}>{row.due.label}</option>}
            {days.map((d) => <option key={d.date} value={d.date}>{d.date === row.due.date ? row.due.label : d.label}</option>)}
          </select>
          {savingDay && <span className="weekly-day-picker__saving" aria-live="polite">در حال ذخیره…</span>}
          {dayError && <span className="field__error" role="alert">{dayError}</span>}
        </span>
      </td>
      <td className="weekly-cell weekly-cell--center">
        <TodayPlanButton taskId={t.id} taskTitle={t.title} planned={plannedToday} disabled={t.status === 'done'} onPlanned={onChanged} short />
      </td>
      <td className="weekly-cell weekly-cell--center">
        {row.plannedInWeek ? (
          <button type="button" className={`status-chip status-chip--${row.status}`} disabled={saving} onClick={() => void removeFromWeek()}
            aria-label={`خارج کردن «${t.title}» از برنامهٔ این هفته`}
            title="کلیک: خارج کردن از برنامهٔ این هفته">
            {STATUS_LABELS[row.status]}
          </button>
        ) : (
          <span className={`status-chip status-chip--${row.status}`}
            title="این تسک فقط به‌دلیل ددلاین در این جدول دیده می‌شود">{STATUS_LABELS[row.status]}</span>
        )}
        {error && <span className="field__error" role="alert">{error}</span>}
      </td>
    </tr>
  );
}

/** Toolbar button with a floating panel (filter) or menu (sort). Escape / outside click closes. */
function Popover({ label, title, icon, active, menu, children }: {
  label: string; title?: string; icon: IconName; active?: boolean; menu?: boolean; children: ReactNode | ((close: () => void) => ReactNode);
}) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const id = useId();
  useEffect(() => {
    if (!open) return;
    box.current?.querySelector<HTMLElement>('[role="menuitemradio"][aria-checked="true"], input, select, button:not(.weekly-tool)')?.focus();
    const outside = (e: MouseEvent) => !box.current?.contains(e.target as Node) && setOpen(false);
    document.addEventListener('mousedown', outside);
    return () => document.removeEventListener('mousedown', outside);
  }, [open]);
  const close = () => {
    setOpen(false);
    button.current?.focus();
  };
  return (
    <div className="weekly-pop" ref={box} onKeyDown={(e) => e.key === 'Escape' && open && close()}>
      <button ref={button} type="button" className={`button button--secondary goals-page__tool weekly-tool${active ? ' goals-page__tool--active' : ''}`}
        aria-haspopup={menu ? 'menu' : 'dialog'} aria-expanded={open} title={title} aria-label={title} aria-controls={open ? id : undefined} onClick={() => setOpen((v) => !v)}>
        <Icon name={icon} size={18} /> {label}
      </button>
      {open && <div id={id} className="weekly-pop__panel">{typeof children === 'function' ? children(close) : children}</div>}
    </div>
  );
}

function FilterPanel({ filter, onChange, goals }: { filter: WeekFilter; onChange: (f: WeekFilter) => void; goals: Goal[] }) {
  const toggle = <T,>(list: T[], v: T, on: boolean) => (on ? [...list, v] : list.filter((x) => x !== v));
  return (
    <div className="weekly-filter" role="group" aria-label="فیلتر تسک‌ها">
      <fieldset className="filters__statuses">
        <legend>وضعیت</legend>
        {FILTER_STATUSES.map((s) => (
          <label key={s} className="checkbox">
            <input type="checkbox" checked={filter.statuses.includes(s)} onChange={(e) => onChange({ ...filter, statuses: toggle(filter.statuses, s, e.target.checked) })} />
            {STATUS_LABELS[s]}
          </label>
        ))}
      </fieldset>
      <fieldset className="filters__statuses">
        <legend>اولویت</legend>
        {TASK_PRIORITY_ORDER.map((p) => (
          <label key={p} className="checkbox">
            <input type="checkbox" checked={filter.priorities.includes(p)} onChange={(e) => onChange({ ...filter, priorities: toggle(filter.priorities, p, e.target.checked) })} />
            {TASK_PRIORITY_LABELS[p]}
          </label>
        ))}
      </fieldset>
      <div className="field">
        <label htmlFor="weekly-filter-goal">هدف</label>
        <select id="weekly-filter-goal" value={filter.goalId} onChange={(e) => onChange({ ...filter, goalId: e.target.value })}>
          <option value="">همه</option>
          {goals.map((g) => <option key={g.id} value={g.id}>{g.title}</option>)}
        </select>
      </div>
      <button type="button" className="button button--secondary" onClick={() => onChange({ ...EMPTY_FILTER, query: filter.query })}>حذف فیلترها</button>
    </div>
  );
}

/** «افزودن تسک هفتگی»: creates the task and puts it on the chosen day's plan of the shown week. */
function AddWeeklyTaskDialog({ open, days, today, goals, items, onClose, onCreated }: {
  open: boolean; days: { date: IsoDate; label: string }[]; today: IsoDate; goals: Goal[]; items: FocusItem[];
  onClose: () => void; onCreated: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const defaultDay = days.some((d) => d.date === today) ? today : days[0].date;
  const [title, setTitle] = useState('');
  const [day, setDay] = useState<IsoDate>(defaultDay);
  const [priority, setPriority] = useState<TaskPriority>('low');
  const [goalId, setGoalId] = useState('');
  const [focusItemId, setFocusItemId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [titleError, setTitleError] = useState(false);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) {
      setTitle(''); setDay(defaultDay); setPriority('low'); setGoalId(''); setFocusItemId(''); setError(null); setTitleError(false);
      d.showModal();
    }
    if (!open && d.open) d.close();
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps
  const itemChoices = goalId ? items.filter((i) => i.goalId === goalId) : items;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim()) return setTitleError(true);
    setSaving(true);
    setError(null);
    try {
      const task = await tasksApi.create({
        title: title.trim(), status: 'not_started', priority,
        ...(goalId && { goalId }), ...(focusItemId && { focusItemId }),
      });
      await dailyPlanApi.addTask(day, task.id);
      onCreated();
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }
  return (
    <dialog ref={ref} className="modal" aria-labelledby="weekly-add-heading" onClose={onClose} onClick={(e) => e.target === e.currentTarget && onClose()}>
      {open && (
        <form className="card form modal__body" onSubmit={submit} noValidate>
          <button type="button" className="icon-button modal__close" aria-label="بستن" onClick={onClose}>✕</button>
          <h2 id="weekly-add-heading">افزودن تسک هفتگی</h2>
          <div className="field">
            <label htmlFor="wa-title">عنوان تسک <span aria-hidden="true">*</span></label>
            <input id="wa-title" autoFocus value={title} maxLength={200} aria-invalid={titleError || undefined}
              aria-describedby={titleError ? 'wa-title-error' : undefined} onChange={(e) => { setTitle(e.target.value); setTitleError(false); }} />
            {titleError && <p id="wa-title-error" className="field__error">این فیلد الزامی است.</p>}
          </div>
          <div className="form__row">
            <div className="field">
              <label htmlFor="wa-day">روز</label>
              <select id="wa-day" value={day} onChange={(e) => setDay(e.target.value)}>
                {days.map((d) => <option key={d.date} value={d.date}>{`${d.label} ${formatJalaliDate(d.date)}${d.date === today ? ' (امروز)' : ''}`}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="wa-priority">اولویت</label>
              <select id="wa-priority" value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
                {TASK_PRIORITY_ORDER.map((p) => <option key={p} value={p}>{TASK_PRIORITY_LABELS[p]}</option>)}
              </select>
            </div>
          </div>
          <div className="form__row">
            <div className="field">
              <label htmlFor="wa-goal">هدف مرتبط (اختیاری)</label>
              <select id="wa-goal" value={goalId} onChange={(e) => { setGoalId(e.target.value); setFocusItemId(''); }}>
                <option value="">بدون هدف</option>
                {goals.map((g) => <option key={g.id} value={g.id}>{g.title}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="wa-focus">آیتم تمرکز (اختیاری)</label>
              <select id="wa-focus" value={focusItemId} onChange={(e) => setFocusItemId(e.target.value)}>
                <option value="">بدون آیتم</option>
                {itemChoices.map((i) => <option key={i.id} value={i.id}>{i.title}</option>)}
              </select>
            </div>
          </div>
          {error != null && <ErrorMessage error={error} />}
          <div className="form__actions">
            <button type="submit" className="button" disabled={saving}>{saving ? 'در حال ذخیره…' : 'افزودن به این هفته'}</button>
            <button type="button" className="button button--secondary" onClick={onClose}>انصراف</button>
          </div>
        </form>
      )}
    </dialog>
  );
}
