import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { MonthCalendar } from './MonthCalendar';
import { FocusTimer } from './FocusTimer';
import { ExpandingAdd } from '../../shared/ui/ExpandingAdd';
import { HabitsCard } from '../habits/HabitsCard';
import { ApiError } from '../../shared/api/client';
import { addDays, isIsoDate, isoToJalali, todayIso, type IsoDate } from '../../shared/date/calendar';
import { formatJalaliDate, formatJalaliDateWithWeekday, formatNumber, formatPercent, toPersianDigits } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon, type IconName } from '../../shared/ui/Icon';
import { ProgressRing } from '../../shared/ui/ProgressRing';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { focusApi } from '../focus/api';
import { buildFocusTree } from '../focus/focusTreeModel';
import { goalsApi } from '../goals/api';
import { PERIOD_LABELS } from '../goals/labels';
import { dailyPlanApi } from '../planning/api';
import type { CarriedTask, DailyPlan, DailyPlanItem } from '../planning/types';
import { tasksApi } from '../tasks/api';
import { TASK_PRIORITY_LABELS } from '../tasks/labels';
import { PriorityPicker } from '../tasks/PriorityPicker';
import { buildBoard, goalColor } from '../planning/todayBoardModel';
import type { Task } from '../tasks/types';
import { progressApi } from './api';
import {
  attentionItems, dayOutcome, orderGoals, remainingTasks, sortDaily, weekDays, weekSummary, type AttentionItem, type AttentionKind } from './dashboardModel';
import type { GoalCard } from './types';

/** Dashboard (design: ui/…png). All numbers come from the API; Outcome and Execution are never combined. */
const SLIDES = [
  { id: 'overview', label: 'نمای کلی' },
  { id: 'calendar', label: 'تقویم ماه' },
] as const;

/** Dashboard slideshow: slide 1 = overview cards, slide 2 = month calendar (`?slide=calendar`). */
export function DashboardPage() {
  const [params, setParams] = useSearchParams();
  const index = Math.max(0, SLIDES.findIndex((s) => s.id === params.get('slide')));
  usePageTitle(index === 1 ? 'تقویم ماه' : 'داشبورد');
  // the slides are stacked vertically: the next one comes in from below, the previous one from above
  const last = useRef(index);
  const from = index > last.current ? 'below' : index < last.current ? 'above' : 'none';
  useEffect(() => { last.current = index; }, [index]);
  function go(i: number) {
    const next = new URLSearchParams(params);
    if (i === 0) next.delete('slide');
    else next.set('slide', SLIDES[i].id);
    setParams(next, { replace: true });
  }
  function onDotsKey(e: KeyboardEvent<HTMLDivElement>) {
    // vertical dots: ↓ = next slide, ↑ = previous
    const delta = e.key === 'ArrowDown' ? 1 : e.key === 'ArrowUp' ? -1 : 0;
    if (!delta) return;
    e.preventDefault();
    const i = (index + delta + SLIDES.length) % SLIDES.length;
    go(i);
    document.getElementById(`dash-dot-${SLIDES[i].id}`)?.focus();
  }
  return (
    <div className="dash dash--slides">
      <div key={SLIDES[index].id} id={`dash-slide-${SLIDES[index].id}`} className={`dash__slide dash__slide--from-${from}`} role="tabpanel"
        aria-labelledby={`dash-dot-${SLIDES[index].id}`}>
        {index === 1 ? <CalendarSlide /> : <OverviewSlide />}
      </div>
      <div className="dash-dots" role="tablist" aria-label="صفحه‌های داشبورد" aria-orientation="vertical" onKeyDown={onDotsKey}
        onWheel={(e) => { const i = index + (e.deltaY > 0 ? 1 : -1); if (i >= 0 && i < SLIDES.length) go(i); }}>
        {SLIDES.map((s, i) => (
          <button key={s.id} id={`dash-dot-${s.id}`} type="button" role="tab" aria-selected={i === index}
            aria-controls={`dash-slide-${s.id}`} tabIndex={i === index ? 0 : -1} title={s.label} aria-label={s.label}
            className={`dash-dot${i === index ? ' dash-dot--on' : ''}`} onClick={() => go(i)} />
        ))}
      </div>
    </div>
  );
}

function CalendarSlide() {
  return (
    <section className="dash dash--calendar" aria-labelledby="calendar-heading">
      <MonthCalendar heading="تقویم ماه" />
    </section>
  );
}

/**
 * Overview (design: owner screenshot). Right: this week + «نیازمند توجه»; middle: the day's tasks + habits; left: focus
 * timer + goals / focus items as rings. All numbers come from the API; Outcome and Execution are never combined.
 */
function OverviewSlide() {
  const [params] = useSearchParams();
  const requested = params.get('date');
  const date = requested && isIsoDate(requested) ? requested : todayIso();
  const week = weekDays(date);
  const dash = useApiData((signal) => progressApi.today(date, signal), [date]);
  const weekPlans = useApiData((signal) => Promise.all(week.map((d) => dailyPlanApi.get(d.date, signal))), [date]);
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'false' }, signal), []);
  const [version, setVersion] = useState(0);
  const reloadAll = () => {
    dash.reload();
    weekPlans.reload();
    tasks.reload();
    setVersion((v) => v + 1);
  };

  return (
    <section className="dash dash--overview" aria-labelledby="dashboard-heading">
      <h1 id="dashboard-heading" className="visually-hidden">داشبورد</h1>
      {dash.status === 'error' && <ErrorMessage error={dash.error} onRetry={dash.reload} />}
      <div className="dash__cols">
        <div className="dash__col">
          <WeekCard date={date} week={week} plans={weekPlans.data} error={weekPlans.status === 'error' ? weekPlans.error : null}
            onRetry={weekPlans.reload} onChanged={reloadAll} />
          <RemainingTasksCard version={version} onChanged={reloadAll} />
          <AttentionCard date={date} tasks={tasks.data} goals={dash.data?.goals} error={tasks.status === 'error' ? tasks.error : null}
            onRetry={tasks.reload} />
        </div>
        <div className="dash__col">
          <DailyTasksCard initialDate={date} version={version} onChanged={reloadAll} />
          <HabitsCard week={week} />
        </div>
        <div className="dash__col">
          <FocusTimer />
          <GoalsAndFocusCard goals={dash.data?.goals ?? []} loading={!dash.data} />
        </div>
      </div>
    </section>
  );
}

// ---------------------------------------------------------------------------------------------------- shared bits

function CardHeader({ id, title, icon, action }: { id: string; title: string; icon: IconName; action?: ReactNode }) {
  return (
    <div className="dash-card__header">
      <h2 id={id} className="dash-card__title">
        <Icon name={icon} size={24} className="dash-card__icon" />
        {title}
      </h2>
      {action}
    </div>
  );
}

/** Scrollable middle part of a card (header/footer stay); focusable so keyboard users can scroll it. */
function CardBody({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="dash-card__body" tabIndex={0} role="group" aria-label={label}>
      {children}
    </div>
  );
}


// ---------------------------------------------------------------------------------------------------- week + remaining + attention

const DAY_CLOSE_MS = 280;

/**
 * «برنامه این هفته»: one box per day (done ÷ planned bar). Clicking a day opens it inside the card (animated like the
 * ＋ pill): its not-done and done tasks, tickable. The same day, × or Escape closes it; another day switches in place.
 */
function WeekCard({ date, week, plans, error, onRetry, onChanged }: {
  date: IsoDate; week: { date: IsoDate; label: string }[]; plans: DailyPlan[] | undefined; error: unknown; onRetry: () => void;
  onChanged: () => void;
}) {
  const summary = plans ? weekSummary(plans) : null;
  const today = todayIso();
  const [open, setOpen] = useState<IsoDate | null>(null);
  const [shown, setShown] = useState<IsoDate | null>(null); // stays set while the panel animates closed
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);
  function pick(d: IsoDate) {
    window.clearTimeout(timer.current);
    if (open === d) return close();
    setOpen(d);
    setShown(d);
  }
  function close() {
    setOpen(null);
    timer.current = window.setTimeout(() => setShown(null), DAY_CLOSE_MS);
  }
  const label = (d: IsoDate) => week.find((w) => w.date === d)?.label ?? '';
  const items = shown ? plans?.find((p) => p.date === shown)?.items.filter((i) => !i.task.archivedAt) ?? [] : [];
  // judged for THAT day: a task finished on a later day was not done on this day (it moved on)
  const notDone = shown ? sortDaily(items.filter((i) => !dayOutcome(i.task, shown).done)) : [];
  const done = shown ? sortDaily(items.filter((i) => dayOutcome(i.task, shown).done)) : [];
  const laterNote = (task: Task) => {
    const o = dayOutcome(task, shown!);
    if (o.doneLater) return { locked: `انجام‌شده در ${formatJalaliDate(o.doneLater)}` };
    return shown! < today ? { note: 'منتقل شد' } : {};
  };
  return (
    <section className={`dash-card dash-card--week${open ? ' dash-card--week-open' : ''}`} aria-labelledby="week-card"
      onKeyDown={(e) => e.key === 'Escape' && open && close()}>
      <CardHeader id="week-card" title="برنامه این هفته" icon="calendar"
        action={<span className="dash-card__range">{`${toPersianDigits(isoToJalali(week[0].date).jd)} تا ${formatJalaliDate(week[6].date)}`}</span>} />
      {error != null && <ErrorMessage error={error} onRetry={onRetry} />}
      {!summary && !error && <Loading />}
      {summary && (
        <>
          <ul className="week-pills" aria-label="روزهای هفته">
            {week.map((d) => {
              const day = summary.days.find((x) => x.date === d.date) ?? { total: 0, done: 0 };
              const pct = day.total ? (day.done / day.total) * 100 : 0;
              return (
                <li key={d.date}>
                  <button type="button" onClick={() => pick(d.date)} aria-expanded={open === d.date} aria-controls="week-day-panel"
                    title={open === d.date ? 'بستن' : `نمایش کارهای ${d.label}`}
                    className={`week-pill${d.date === today ? ' week-pill--today' : ''}${d.date === date && d.date !== today ? ' week-pill--selected' : ''}${open === d.date ? ' week-pill--open' : ''}`}
                    aria-label={`${d.label} ${formatJalaliDate(d.date)}: ${day.total ? `${formatNumber(day.done)} از ${formatNumber(day.total)} کار انجام شده` : 'بدون کار'}`}
                    aria-current={d.date === today ? 'date' : undefined}>
                    <span className="week-pill__name" aria-hidden="true">{d.label.charAt(0)}</span>
                    <span className="week-pill__num" aria-hidden="true">{toPersianDigits(isoToJalali(d.date).jd)}</span>
                    <span className="week-pill__bar" aria-hidden="true">
                      {day.total > 0 && <span style={{ inlineSize: `${Math.max(pct, 8)}%` }} className={pct === 0 ? 'week-pill__bar--zero' : undefined} />}
                    </span>
                    <Icon name="chevron-down" size={14} className="week-pill__chev" />
                  </button>
                </li>
              );
            })}
          </ul>
          <p className={`week-hint${open ? ' week-hint--hidden' : ''}`} aria-hidden={open ? true : undefined}>
            <Icon name="list" size={16} /> روی هر روز بزنید تا کارهای انجام‌شده و انجام‌نشده‌اش را ببینید
          </p>
          <div id="week-day-panel" className={`week-day${open ? ' week-day--open' : ''}`} role="region"
            aria-label={shown ? `کارهای ${label(shown)} ${formatJalaliDate(shown)}` : undefined} inert={!open}>
            <div className="week-day__inner">
              {shown && (
                <div key={shown} className="week-day__content">
                  <div className="week-day__head">
                    <strong>{`${label(shown)} ${formatJalaliDate(shown)}`}</strong>
                    <Link className="dash-card__link" to={`/today?date=${shown}`}>تخته روز <Icon name="chevron-left" size={16} /></Link>
                    <button type="button" className="icon-button week-day__close" aria-label="بستن" onClick={close}>✕</button>
                  </div>
                  {items.length === 0 ? (
                    <p className="dash-empty">برای این روز کاری برنامه‌ریزی نشده است.</p>
                  ) : (
                    <div className="week-day__lists">
                      <h3 className="week-day__group">
                        {`انجام نشده (${formatNumber(notDone.length)})`}
                        {notDone.length === 0 && <span className="week-day__none">همه انجام شده‌اند</span>}
                      </h3>
                      {notDone.length > 0 && (
                        <ul className="check-list">{notDone.map((i) => <TaskCheckRow key={i.id} item={i} onChanged={onChanged} scope="week" {...laterNote(i.task)} />)}</ul>
                      )}
                      <h3 className="week-day__group week-day__group--done">
                        {`انجام شده (${formatNumber(done.length)})`}
                        {done.length === 0 && <span className="week-day__none">هنوز کاری انجام نشده</span>}
                      </h3>
                      {done.length > 0 && (
                        <ul className="check-list">{done.map((i) => <TaskCheckRow key={i.id} item={i} onChanged={onChanged} scope="week" />)}</ul>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </section>
  );
}

/** Tasks left unfinished on earlier days, intentionally separate from «کارهای روزانه». */
function RemainingTasksCard({ version, onChanged }: { version: number; onChanged: () => void }) {
  const today = todayIso();
  // No completion window: this action card asks only for unfinished carry-over tasks.
  const carry = useApiData((signal) => dailyPlanApi.carryOver(today, null, signal), [today, version]);
  const goals = useApiData((signal) => goalsApi.list('false', signal), []);
  const focusItems = useApiData((signal) => focusApi.list({ archived: 'false' }, signal), []);
  const links: TaskLinks = {
    goal: (id) => (id ? goals.data?.find((goal) => goal.id === id) : undefined),
    item: (id) => (id ? focusItems.data?.find((item) => item.id === id) : undefined),
  };
  const items = carry.data ? remainingTasks(carry.data, today) : null;
  return (
    <section className="dash-card dash-card--remaining" aria-labelledby="remaining-tasks-card">
      <CardHeader id="remaining-tasks-card" title="کارهای باقی‌مانده" icon="reset"
        action={items && items.length > 0 ? <span className="dash-card__count">{formatNumber(items.length)}</span> : undefined} />
      <CardBody label="کارهای باقی‌مانده از روزهای قبل">
        {carry.status === 'error' && <ErrorMessage error={carry.error} onRetry={carry.reload} />}
        {!carry.data && carry.status !== 'error' && <Loading />}
        {items && items.length === 0 && <p className="dash-empty">کاری از روزهای قبل باقی نمانده است.</p>}
        {items && items.length > 0 && (
          <ul className="remaining-list">
            {items.map((entry) => <RemainingTaskRow key={entry.task.id} entry={entry} today={today} links={links} onChanged={onChanged} />)}
          </ul>
        )}
      </CardBody>
    </section>
  );
}

function RemainingTaskRow({ entry, today, links, onChanged }: {
  entry: CarriedTask; today: IsoDate; links: TaskLinks; onChanged: () => void;
}) {
  const priority = entry.task.priority ?? 'low';
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function addToday() {
    setSaving(true);
    setError(null);
    try {
      await dailyPlanApi.addTask(today, entry.task.id);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'به برنامه امروز اضافه نشد.');
    } finally {
      setSaving(false);
    }
  }
  return (
    <li className="remaining-item">
      <button type="button" className="button button--secondary remaining-item__add" disabled={saving} onClick={addToday}
        aria-label={`افزودن «${entry.task.title}» به برنامه امروز`}>
        {saving ? '…' : <><span aria-hidden="true">+</span> امروز</>}
      </button>
      <span className="remaining-item__body">
        <Link className="remaining-item__title" to={`/tasks?highlight=${entry.task.id}`}>{entry.task.title}</Link>
        <span className="remaining-item__details">
          <TaskMeta task={entry.task} links={links} />
          <span className="remaining-item__meta">
            <span className={`prio prio--${priority}`}>{TASK_PRIORITY_LABELS[priority]}</span>
          </span>
        </span>
        {error && <span className="field__error" role="alert">{error}</span>}
      </span>
    </li>
  );
}

const ATTENTION: Record<AttentionKind, { icon: IconName; tone: string }> = {
  overdue: { icon: 'alert', tone: 'danger' },
  due_today: { icon: 'clock', tone: 'warn' },
  due_tomorrow: { icon: 'clock', tone: 'warn' },
  behind: { icon: 'trend-down', tone: 'muted' },
};

function attentionText(a: AttentionItem): string {
  switch (a.kind) {
    case 'overdue': return `${formatNumber(a.amount)} روز عقب‌افتاده`;
    case 'due_today': return 'سررسید امروز';
    case 'due_tomorrow': return 'سررسید فردا';
    case 'behind': return `پیشرفت کمتر از برنامه (${formatNumber(a.amount)}٪ عقب)`;
  }
}

/** «نیازمند توجه»: overdue / due today / due tomorrow tasks and measured goals behind schedule. */
function AttentionCard({ date, tasks, goals, error, onRetry }: {
  date: IsoDate; tasks: Task[] | undefined; goals: GoalCard[] | undefined; error: unknown; onRetry: () => void;
}) {
  const items = tasks && goals ? attentionItems(tasks, goals, date) : null;
  return (
    <section className="dash-card dash-card--attention" aria-labelledby="attention-card">
      <CardHeader id="attention-card" title="نیازمند توجه" icon="bell" />
      <CardBody label="موارد نیازمند توجه">
        {error != null && <ErrorMessage error={error} onRetry={onRetry} />}
        {!items && !error && <Loading />}
        {items && items.length === 0 && <p className="dash-empty">موردی نیست؛ کار عقب‌افتاده یا سررسید نزدیکی ندارید.</p>}
        {items && items.length > 0 && (
          <ul className="attention">
            {items.map((a) => (
              <li key={`${a.kind}-${a.id}`} className="attention__item">
                <span className="attention__body">
                  <Link className="attention__title" to={a.kind === 'behind' ? `/goals/${a.id}` : `/weekly?week=${date}`}>{a.title}</Link>
                  <span className={`attention__note attention__note--${ATTENTION[a.kind].tone}`}>{attentionText(a)}</span>
                </span>
                <span className={`attention__icon attention__note--${ATTENTION[a.kind].tone}`}><Icon name={ATTENTION[a.kind].icon} size={26} /></span>
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </section>
  );
}

// ---------------------------------------------------------------------------------------------------- daily tasks

/** Goal / focus-item names for the meta line of a task row (active ones only; archived are hidden on working pages). */
interface TaskLinks { goal: (id: string | null) => { id: string; title: string } | undefined; item: (id: string | null) => { id: string; title: string } | undefined }

/**
 * One tickable task. `note` is a small tag after the title (e.g. «مانده از …»); `locked` shows the task as NOT done on this
 * day although it was finished later (week panel) — read-only, with the reason.
 */
function TaskCheckRow({ item, onChanged, scope = 'daily', links, note, locked }: {
  item: Pick<DailyPlanItem, 'id' | 'task'>; onChanged: () => void; scope?: string; links?: TaskLinks; note?: string; locked?: string;
}) {
  const task = item.task;
  const [done, setDone] = useState(task.status === 'done');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [synced, setSynced] = useState(task.status);
  if (synced !== task.status) {
    setSynced(task.status);
    setDone(task.status === 'done');
  }
  async function toggle() {
    const next = !done;
    setDone(next);
    setSaving(true);
    setError(null);
    try {
      await tasksApi.update(task.id, { status: next ? 'done' : 'not_started' });
      onChanged();
    } catch (e) {
      setDone(!next);
      setError(e instanceof ApiError ? e.message : 'ذخیره نشد.');
    } finally {
      setSaving(false);
    }
  }
  const id = `${scope}-task-${item.id}`; // the same task can be in the week panel and the daily card
  return (
    <li className={`check-row${done && !locked ? ' check-row--done' : ''}`}>
      <input id={id} type="checkbox" className="check" checked={done && !locked} disabled={saving || Boolean(locked)} onChange={toggle}
        title={locked} />
      {links ? (
        <span className="task-line">
          <label htmlFor={id} className="check-row__title">{task.title}</label>
          <TaskMeta task={task} links={links} />
        </span>
      ) : (
        <label htmlFor={id} className="check-row__title">{task.title}</label>
      )}
      {note && <span className="kb-carry day-note day-note--carry">{note}</span>}
      {locked && <span className="day-note day-note--later">{locked}</span>}
      {links && <PriorityPicker task={task} onChanged={onChanged} />}
      {error && <span className="field__error" role="alert">{error}</span>}
    </li>
  );
}

/** «هدف · آیتم تمرکز» under a task title; the goal dot has the same colour as on تخته امروز. */
function TaskMeta({ task, links }: { task: Task; links: TaskLinks }) {
  const goal = links.goal(task.effectiveGoalId);
  const focus = links.item(task.focusItemId);
  if (!goal && !focus) return <span className="task-meta task-meta--none">بدون هدف</span>;
  return (
    <span className="task-meta">
      {goal && (
        <Link to={`/goals/${goal.id}`} className="task-meta__goal" title={`هدف: ${goal.title}`}>
          <span className="kb-goal-dot" style={{ background: goalColor(goal.id) }} aria-hidden="true" />
          <span className="visually-hidden">هدف: </span>{goal.title}
        </Link>
      )}
      {goal && focus && <span aria-hidden="true" className="task-meta__sep">·</span>}
      {focus && (
        <Link to={`/focus/${focus.id}`} className="task-meta__focus" title={`آیتم تمرکز: ${focus.title}`}>
          <Icon name="layers" size={13} /><span className="visually-hidden">آیتم تمرکز: </span>{focus.title}
        </Link>
      )}
    </span>
  );
}

/** «کارهای روزانه»: the plan of one day, switchable with ‹ › (the dashboard is not left). */
function DailyTasksCard({ initialDate, version, onChanged }: { initialDate: IsoDate; version: number; onChanged: () => void }) {
  const [day, setDay] = useState<IsoDate>(initialDate);
  useEffect(() => setDay(initialDate), [initialDate]);
  const today = todayIso();
  const plan = useApiData((signal) => dailyPlanApi.get(day, signal), [day, version]);
  // Only this day's own plan lives here; earlier unfinished tasks have a separate dashboard card.
  const items = plan.data?.date === day ? sortDaily(buildBoard(day, plan.data)) : [];
  const isToday = day === today;
  const goals = useApiData((signal) => goalsApi.list('false', signal), []);
  const focusItems = useApiData((signal) => focusApi.list({ archived: 'false' }, signal), []);
  const links: TaskLinks = {
    goal: (id) => (id ? goals.data?.find((g) => g.id === id) : undefined),
    item: (id) => (id ? focusItems.data?.find((f) => f.id === id) : undefined),
  };
  return (
    <section className="dash-card dash-card--today" aria-labelledby="daily-tasks">
      <CardHeader id="daily-tasks" title="کارهای روزانه" icon="list" action={
        <div className="day-switch" role="group" aria-label="انتخاب روز">
          <button type="button" className="icon-button" aria-label="روز قبل" onClick={() => setDay(addDays(day, -1))}>
            <Icon name="chevron-right" size={18} />
          </button>
          <button type="button" className="day-switch__label" title={isToday ? undefined : 'برگشت به امروز'}
            onClick={() => setDay(todayIso())} aria-label={`${formatJalaliDateWithWeekday(day)}${isToday ? ' (امروز)' : '، برگشت به امروز'}`}>
            {formatJalaliDateWithWeekday(day)}
          </button>
          <button type="button" className="icon-button" aria-label="روز بعد" onClick={() => setDay(addDays(day, 1))}>
            <Icon name="chevron-left" size={18} />
          </button>
        </div>
      } />
      <CardBody label="فهرست کارهای روز">
        {plan.status === 'error' && <ErrorMessage error={plan.error} onRetry={plan.reload} />}
        {!plan.data && plan.status !== 'error' && <Loading />}
        {plan.data && items.length === 0 && <p className="dash-empty">برای این روز کاری برنامه‌ریزی نشده است.</p>}
        {items.length > 0 && (
          <ul className="check-list">
            {items.map((e) => <TaskCheckRow key={e.id} item={e} onChanged={onChanged} links={links} />)}
          </ul>
        )}
      </CardBody>
      <ExpandingAdd label={isToday ? 'افزودن کار جدید برای امروز' : 'افزودن کار جدید برای این روز'} inputLabel="عنوان کار جدید"
        placeholder="عنوان کار…" onAdd={async (t) => {
          const task = await tasksApi.create({ title: t, status: 'not_started' });
          await dailyPlanApi.addTask(day, task.id);
          onChanged();
        }} />
    </section>
  );
}

// ---------------------------------------------------------------------------------------------------- timer + goals


type GoalsTab = 'goals' | 'focus';

/** «اهداف» / «آیتم‌های تمرکز» as a grid of rings (goal ring = Outcome only; Execution is written under it, never mixed in). */
function GoalsAndFocusCard({ goals, loading }: { goals: GoalCard[]; loading: boolean }) {
  const [tab, setTab] = useState<GoalsTab>('goals');
  const tabs: { id: GoalsTab; label: string; icon: IconName }[] = [
    { id: 'goals', label: 'اهداف', icon: 'target' },
    { id: 'focus', label: 'آیتم‌های تمرکز', icon: 'layers' },
  ];
  function onKey(e: KeyboardEvent<HTMLButtonElement>) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return;
    e.preventDefault();
    const next: GoalsTab = e.key === 'Home' ? 'goals' : e.key === 'End' ? 'focus' : tab === 'goals' ? 'focus' : 'goals';
    setTab(next);
    requestAnimationFrame(() => document.getElementById(`dash-tab-${next}`)?.focus());
  }
  return (
    <section className="dash-card dash-card--goals dash-card--glow" aria-label="اهداف و آیتم‌های تمرکز">
      <div className="seg" role="tablist" aria-label="نمایش">
        {tabs.map((t) => (
          <button key={t.id} id={`dash-tab-${t.id}`} type="button" role="tab" aria-selected={tab === t.id} aria-controls="dash-goals-panel"
            tabIndex={tab === t.id ? 0 : -1} className={`seg__btn${tab === t.id ? ' seg__btn--on' : ''}`} onClick={() => setTab(t.id)} onKeyDown={onKey}>
            <Icon name={t.icon} size={20} />
            {t.label}
          </button>
        ))}
      </div>
      <div id="dash-goals-panel" role="tabpanel" aria-labelledby={`dash-tab-${tab}`} className="dash-card__body" tabIndex={0}>
        {tab === 'goals' ? <GoalRings goals={goals} loading={loading} /> : <FocusRings />}
      </div>
    </section>
  );
}

function RingTile({ to, title, value, label, sub }: { to: string; title: string; value: number | null; label: string; sub: string }) {
  return (
    <li className="ring-tile">
      <Link to={to} className="ring-tile__link" title={`${title} — ${sub}`}>
        <ProgressRing value={value} size={74} stroke={6} showValue label={label} tone="teal" />
        <span className="ring-tile__title">{title}</span>
        <span className="ring-tile__sub">{sub}</span>
      </Link>
    </li>
  );
}

function GoalRings({ goals, loading }: { goals: GoalCard[]; loading: boolean }) {
  // only the order comes from here; while it loads (or if it fails) the API order is kept
  const priority = useApiData((signal) => goalsApi.search('archived=false&sort=priority', signal), []);
  if (loading) return <Loading />;
  if (goals.length === 0) return <p className="dash-empty">هدف فعالی برای این تاریخ ندارید. <Link to="/goals">ساخت هدف</Link></p>;
  return (
    <ul className="ring-grid">
      {orderGoals(goals, priority.data?.items.map((g) => g.id)).map((g) => (
        <RingTile key={g.goalId} to={`/goals/${g.goalId}`} title={g.title} value={g.outcomeProgress} label={`پیشرفت نتیجهٔ «${g.title}»`}
          sub={`${PERIOD_LABELS[g.periodType]} · اجرا ${g.executionProgress == null ? '—' : formatPercent(g.executionProgress)}`} />
      ))}
    </ul>
  );
}

/** Loaded only when the tab is opened. Same order as the focus items page (goal priority, then manual order). */
function FocusRings() {
  const goals = useApiData((signal) => goalsApi.search('archived=false&sort=priority', signal), []);
  const items = useApiData((signal) => focusApi.list({ archived: 'false' }, signal), []);
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'false' }, signal), []);
  const error = goals.error ?? items.error ?? tasks.error;
  if (error != null) return <ErrorMessage error={error} onRetry={() => { goals.reload(); items.reload(); tasks.reload(); }} />;
  if (!goals.data || !items.data || !tasks.data) return <Loading />;
  const tree = buildFocusTree(goals.data.items, items.data, tasks.data);
  const nodes = [...tree.goals.flatMap((g) => g.focus), ...tree.unlinked];
  if (nodes.length === 0) return <p className="dash-empty">آیتم تمرکز فعالی ندارید. <Link to="/focus">ساخت آیتم تمرکز</Link></p>;
  return (
    <ul className="ring-grid">
      {nodes.map((node) => {
        const done = node.tasks.filter((t) => t.status === 'done').length;
        return (
          <RingTile key={node.item.id} to={`/focus/${node.item.id}`} title={node.item.title} value={node.progress}
            label={`پیشرفت «${node.item.title}»`}
            sub={node.tasks.length === 0 ? 'تسکی ندارد' : `${formatNumber(done)} از ${formatNumber(node.tasks.length)} تسک`} />
        );
      })}
    </ul>
  );
}

