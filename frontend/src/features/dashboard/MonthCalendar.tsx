import { Link } from 'react-router';
import { useLayoutEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { addDays, isoToJalali, jalaliToIso, todayIso, type IsoDate } from '../../shared/date/calendar';
import { addJalaliMonths, monthGrid } from '../../shared/date/monthGrid';
import { periodRange } from '../../shared/date/periods';
import { JALALI_MONTHS, WEEKDAYS, formatJalaliDate, formatJalaliDateWithWeekday, formatNumber, toPersianDigits } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { useApiData } from '../../shared/ui/useApiData';
import { goalsApi } from '../goals/api';
import { dailyPlanApi } from '../planning/api';
import { tasksApi } from '../tasks/api';
import { TASK_PRIORITY_LABELS as PRIORITY_LABELS, TASK_STATUS_LABELS as STATUS_LABELS } from '../tasks/labels';
import { goalColor } from '../planning/todayBoardModel';
import { CalendarGhost, useCalendarDrag } from './calendarDrag';
import { ApiError } from '../../shared/api/client';
import {
  buildCalendar, daysBetween, distinctTasks, legendOf, monthTaskCapacity, weekOf, type CalendarEntry, type CalendarView,
} from './calendarModel';

const VIEWS: { id: CalendarView; label: string }[] = [
  { id: 'month', label: 'ماه' },
  { id: 'week', label: 'هفته' },
  { id: 'day', label: 'روز' },
];
/** At least this many tasks per month cell; more when the cells are taller (measured). */
const MONTH_CELL_MIN = 5;

/**
 * Month / week / day calendar of the dashboard's second slide. A task shows on every day it is planned and on its due
 * date; the dot colour is its (effective, active) goal. Read-only: clicking a day opens the day view.
 */
/** `heading` is the page title (h1) shown at the start of the toolbar, so the calendar gets the whole height. */
export function MonthCalendar({ heading }: { heading?: string }) {
  const today = todayIso();
  const [view, setView] = useState<CalendarView>('month');
  const [anchor, setAnchor] = useState<IsoDate>(today);

  const { jy, jm } = isoToJalali(anchor);
  const month = periodRange('monthly', anchor);
  const days = view === 'month' ? daysBetween(month.start, month.end) : view === 'week' ? weekOf(anchor) : [anchor];
  const key = `${days[0]}|${days[days.length - 1]}`;

  const plans = useApiData((signal) => Promise.all(days.map((d) => dailyPlanApi.get(d, signal))), [key]);
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'false' }, signal), []);
  const goals = useApiData((signal) => goalsApi.search('archived=false&sort=priority', signal), []);

  const goalIds = goals.data?.items.map((g) => g.id) ?? [];
  const goalTitle = new Map(goals.data?.items.map((g) => [g.id, g.title]) ?? []);
  const calendar = plans.status !== 'loading' && plans.data && tasks.data ? buildCalendar(days, plans.data, tasks.data) : null;
  const legend = calendar ? legendOf(calendar, goalIds) : null;
  const [moving, setMoving] = useState(false);
  const [moveError, setMoveError] = useState<string | null>(null);
  /**
   * Moves a task to another day: a planned task moves on the daily plans (one call), a deadline-only task gets that
   * day as its new deadline. A task that is both keeps its deadline (only the plan moves).
   */
  async function moveEntry(entry: CalendarEntry, from: IsoDate, to: IsoDate) {
    if (from === to || moving) return;
    setMoving(true);
    setMoveError(null);
    try {
      if (entry.planned) await dailyPlanApi.move(from, entry.task.id, to);
      else await tasksApi.update(entry.task.id, { dueDate: to });
      plans.reload();
      tasks.reload();
    } catch (e) {
      setMoveError(e instanceof ApiError ? e.message : 'جابه‌جا نشد.');
    } finally {
      setMoving(false);
    }
  }
  // How many complete task lines fit in each month row right now. Rows can have different heights, so one global
  // capacity leaves usable space behind in taller rows.
  const monthRef = useRef<HTMLDivElement>(null);
  const [rowFits, setRowFits] = useState<number[]>([]);
  useLayoutEffect(() => {
    const el = monthRef.current;
    if (!el) return;
    const measure = () => {
      const fallbackLine = Number.parseFloat(getComputedStyle(el).getPropertyValue('--cal-line')) || 16;
      const fits = [...el.querySelectorAll<HTMLElement>('.cal__row')].map((row) => {
        const button = row.querySelector<HTMLElement>('.cal__daybtn');
        const head = button?.querySelector<HTMLElement>('.cal__dayhead');
        if (!button || !head) return MONTH_CELL_MIN;
        const style = getComputedStyle(button);
        const padding = Number.parseFloat(style.paddingTop) + Number.parseFloat(style.paddingBottom);
        const gap = Number.parseFloat(style.rowGap || style.gap) || 0;
        const line = row.querySelector<HTMLElement>('.cal-entry')?.getBoundingClientRect().height || fallbackLine;
        const available = button.clientHeight - padding - head.getBoundingClientRect().height - gap;
        return monthTaskCapacity(available, line, MONTH_CELL_MIN);
      });
      setRowFits((current) => current.length === fits.length && current.every((fit, i) => fit === fits[i]) ? current : fits);
    };
    measure();
    if (typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    el.querySelectorAll<HTMLElement>('.cal__row').forEach((row) => ro.observe(row));
    return () => ro.disconnect();
  }, [view, calendar !== null]);
  const dnd = useCalendarDrag((entry, from, to) => void moveEntry(entry, from, to));
  const dropClass = (d: IsoDate) => (dnd.drag ? (dnd.drag.target === d ? (d === dnd.drag.from ? ' cal--drop-self' : ' cal--drop') : '') : '');
  /** Same colour as on the today board; tasks without an active goal get the neutral dot. */
  const toneOf = (e: CalendarEntry): string | null => (e.task.effectiveGoalId && goalTitle.has(e.task.effectiveGoalId) ? goalColor(e.task.effectiveGoalId) : null);
  const goalOf = (e: CalendarEntry) => (e.task.effectiveGoalId ? goalTitle.get(e.task.effectiveGoalId) ?? null : null);

  function step(dir: -1 | 1) {
    setAnchor((a) => (view === 'month' ? addJalaliMonths(a, dir) : addDays(a, dir * (view === 'week' ? 7 : 1))));
  }
  function openDay(d: IsoDate) {
    setAnchor(d);
    setView('day');
  }

  const title = view === 'month'
    ? `${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}`
    : view === 'week' ? `${formatJalaliDate(days[0])} تا ${formatJalaliDate(days[6])}` : formatJalaliDateWithWeekday(anchor);
  const countLabel = view === 'month' ? 'در این ماه' : view === 'week' ? 'در این هفته' : 'در این روز';
  const error = plans.status === 'error' ? plans.error : tasks.status === 'error' ? tasks.error : null;

  return (
    <div className={`cal dash-card${dnd.drag ? ' cal--dragging' : ''}${moving ? ' cal--busy' : ''}`}>
      <CalendarGhost drag={dnd.drag} />
      {moveError && <p className="field__error cal__move-error" role="alert">{moveError}</p>}
      <div className="cal__toolbar">
        {heading && (
          <div className="cal__heading">
            <h1 id="calendar-heading">{heading}</h1>
          </div>
        )}
        <div className="cal__nav">
          <button type="button" className="icon-button cal__arrow" onClick={() => step(-1)} aria-label={`${VIEWS.find((v) => v.id === view)!.label} قبل`}>
            <Icon name="chevron-right" size={22} />
          </button>
          <h2 className="cal__title" aria-live="polite">{title}</h2>
          <button type="button" className="icon-button cal__arrow" onClick={() => step(1)} aria-label={`${VIEWS.find((v) => v.id === view)!.label} بعد`}>
            <Icon name="chevron-left" size={22} />
          </button>
          {!days.includes(today) && (
            <button type="button" className="link-button cal__today-link" onClick={() => setAnchor(today)}>امروز</button>
          )}
        </div>
        <div className="cal__meta">
          <p className="cal__count">
            <Icon name="calendar" size={20} />
            {calendar ? `${formatNumber(distinctTasks(calendar))} کار ${countLabel}` : '…'}
          </p>
          {legend && (legend.goalIds.length > 0 || legend.noGoal) && (
            <ul className="cal__legend" aria-label="راهنمای رنگ‌ها (هدف)">
              {legend.goalIds.map((id) => (
                <li key={id}><Dot color={goalColor(id)} />{goalTitle.get(id)}</li>
              ))}
              {legend.noGoal && <li><Dot color={null} />بدون هدف</li>}
            </ul>
          )}
        </div>
        <div className="cal__views">
          <div className="view-switch" role="group" aria-label="نمای تقویم">
            {VIEWS.map((v) => (
              <button key={v.id} type="button" className={`view-switch__btn${view === v.id ? ' view-switch__btn--on' : ''}`}
                aria-pressed={view === v.id} onClick={() => setView(v.id)}>
                {v.label}
              </button>
            ))}
          </div>
          <span className="cal__icon" aria-hidden="true"><Icon name="calendar" size={26} /></span>
        </div>
      </div>

      {error ? (
        <ErrorMessage error={error} onRetry={() => { plans.reload(); tasks.reload(); }} />
      ) : !calendar ? (
        <Loading />
      ) : view === 'month' ? (
        <div ref={monthRef} className="cal__month" role="grid" aria-label={title}>
          <div className="cal__weekdays" role="row">
            {WEEKDAYS.map((w) => (
              <div key={w} role="columnheader" className="cal__weekday" aria-label={w}>
                <span className="cal__wd-full" aria-hidden="true">{w}</span>
                <span className="cal__wd-short" aria-hidden="true">{w[0]}</span>
              </div>
            ))}
          </div>
          {fillGrid(monthGrid(jy, jm), jy, jm).map((row, rowIndex) => {
            const cellFit = rowFits[rowIndex] ?? MONTH_CELL_MIN;
            return (
              <div key={row[0].date} className="cal__row" role="row">
                {row.map(({ date, outside }) => {
                  const entries = outside ? [] : calendar.get(date) ?? [];
                  const { jd } = isoToJalali(date);
                  return (
                    <div key={date} role="gridcell" data-cal-day={outside ? undefined : date}
                      className={`cal__cell${outside ? ' cal__cell--outside' : ''}${date === today ? ' cal__cell--today' : ''}${outside ? '' : dropClass(date)}`}>
                      {outside ? (
                        <span className="cal__daynum">{toPersianDigits(jd)}</span>
                      ) : (
                        <button type="button" className="cal__daybtn" onClick={() => !dnd.justDropped() && openDay(date)}
                          aria-label={`${formatJalaliDateWithWeekday(date)}، ${entries.length === 0 ? 'بدون کار' : `${formatNumber(entries.length)} کار`}`}
                          aria-current={date === today ? 'date' : undefined}>
                          <span className="cal__dayhead">
                            <span className="cal__daynum">{toPersianDigits(jd)}</span>
                            {entries.length > cellFit && (
                              <span className="cal__more" aria-hidden="true">+{formatNumber(entries.length - cellFit)} کار دیگر</span>
                            )}
                          </span>
                          <span className="cal__items" aria-hidden="true">
                            {entries.slice(0, cellFit).map((e) => <EntryLine key={e.task.id} entry={e} tone={toneOf(e)} dragging={dnd.drag?.entry.task.id === e.task.id && dnd.drag.from === date}
                              onPointerDown={(ev) => dnd.press(ev, e, date)} />)}
                          </span>
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      ) : view === 'week' ? (
        <div className="cal__week">
          {days.map((d, i) => {
            const entries = calendar.get(d) ?? [];
            return (
              <section key={d} data-cal-day={d} className={`cal__wday${d === today ? ' cal__wday--today' : ''}${dropClass(d)}`} aria-label={formatJalaliDateWithWeekday(d)}>
                <button type="button" className="cal__wday-head" onClick={() => !dnd.justDropped() && openDay(d)}>
                  <span>{WEEKDAYS[i]}</span>
                  <span className="cal__daynum">{toPersianDigits(isoToJalali(d).jd)}</span>
                </button>
                {entries.length === 0 ? <p className="cal__empty">—</p> : (
                  <ul className="cal__list">
                    {entries.map((e) => <li key={e.task.id}><EntryLine entry={e} tone={toneOf(e)} dragging={dnd.drag?.entry.task.id === e.task.id && dnd.drag.from === d}
                      onPointerDown={(ev) => dnd.press(ev, e, d)} /></li>)}
                  </ul>
                )}
              </section>
            );
          })}
        </div>
      ) : (
        <DayList date={anchor} entries={calendar.get(anchor) ?? []} toneOf={toneOf} goalOf={goalOf} busy={moving}
          onMove={(e, to) => void moveEntry(e, anchor, to)} />
      )}

    </div>
  );
}

/** Replaces the empty cells of a month grid with the neighbouring months' dates (shown dimmed, without tasks). */
function fillGrid(rows: (IsoDate | null)[][], jy: number, jm: number): { date: IsoDate; outside: boolean }[][] {
  const first = jalaliToIso(jy, jm, 1);
  const flat = rows.flat();
  const lead = flat.findIndex((d) => d !== null);
  const filled = flat.map((d, i) => (d ? { date: d, outside: false } : { date: addDays(first, i - lead), outside: true }));
  const out: { date: IsoDate; outside: boolean }[][] = [];
  for (let i = 0; i < filled.length; i += 7) out.push(filled.slice(i, i + 7));
  return out;
}

function Dot({ color }: { color: string | null }) {
  return <span className="cal-dot" style={color ? { background: color } : undefined} aria-hidden="true" />;
}

function EntryLine({ entry, tone, onPointerDown, dragging }: {
  entry: CalendarEntry; tone: string | null; onPointerDown?: (e: ReactPointerEvent) => void; dragging?: boolean;
}) {
  const done = entry.task.status === 'done';
  return (
    <span className={`cal-entry cal-entry--draggable${done ? ' cal-entry--done' : ''}${dragging ? ' cal-entry--dragging' : ''}`}
      title={`${entry.task.title} — برای جابه‌جایی به روز دیگر بکشید`} onPointerDown={onPointerDown}>
      <Dot color={tone} />
      <span className="cal-entry__title">{entry.task.title}</span>
      {entry.due && <span className="cal-entry__due" title="ددلاین همین روز">⚑</span>}
    </span>
  );
}

function DayList({ date, entries, toneOf, goalOf, onMove, busy }: {
  date: IsoDate; entries: CalendarEntry[]; toneOf: (e: CalendarEntry) => string | null; goalOf: (e: CalendarEntry) => string | null;
  onMove: (e: CalendarEntry, to: IsoDate) => void; busy: boolean;
}) {
  return (
    <div className="cal__day">
      {date === todayIso() && <p className="cal__day-today"><span className="cal-chip cal-chip--today">امروز</span></p>}
      {entries.length === 0 ? (
        <p className="dash-empty">برای این روز کاری برنامه‌ریزی نشده و ددلاینی هم نیست.</p>
      ) : (
        <ul className="cal__daylist">
          {entries.map((e) => {
            const goal = goalOf(e);
            return (
              <li key={e.task.id} className={`cal-dayrow${e.task.status === 'done' ? ' cal-dayrow--done' : ''}`}>
                <Dot color={toneOf(e)} />
                <span className="cal-dayrow__title">{e.task.title}</span>
                <span className="cal-dayrow__meta">
                  {goal && <span className="cal-chip">{goal}</span>}
                  <span className={`cal-chip cal-chip--${e.task.status !== 'done' && e.task.blockedReason ? 'waiting' : e.task.status}`}>
                    {e.task.status !== 'done' && e.task.blockedReason ? 'در انتظار' : STATUS_LABELS[e.task.status]}
                  </span>
                  {e.task.priority && <span className={`prio prio--${e.task.priority}`}>{PRIORITY_LABELS[e.task.priority]}</span>}
                  {e.planned && <span className="cal-chip">در برنامهٔ روز</span>}
                  {e.due && <span className="cal-chip cal-chip--due">ددلاین</span>}
                </span>
                {/* keyboard alternative to drag-and-drop */}
                <span className="cal-dayrow__move" role="group" aria-label={`جابه‌جایی «${e.task.title}»`}>
                  <button type="button" className="link-button" disabled={busy} onClick={() => onMove(e, addDays(date, -1))}>روز قبل</button>
                  <button type="button" className="link-button" disabled={busy} onClick={() => onMove(e, addDays(date, 1))}>روز بعد</button>
                </span>
              </li>
            );
          })}
        </ul>
      )}
      <Link className="dash-card__link cal__open" to={`/today?date=${date}`}>
        باز کردن تختهٔ این روز <Icon name="chevron-left" size={16} />
      </Link>
    </div>
  );
}
