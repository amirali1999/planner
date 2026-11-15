import { addDays, isoToDayNumber, weekdayFromSaturday, type IsoDate } from '../../shared/date/calendar';
import { formatJalaliDate, WEEKDAYS } from '../../shared/format';
import { weekDays } from '../dashboard/dashboardModel';
import type { Task, TaskPriority } from '../tasks/types';
import type { DailyPlan, WeeklyPlanItem } from './types';

/**
 * Weekly plan = the seven daily plans of a Saturday–Friday week, plus the tasks put on the week without a day
 * (GET /plans/weekly/{weekStart}/items), plus tasks whose deadline (dueDate) falls in it, plus — for the current week —
 * unfinished tasks whose deadline already passed.
 */
export type WeekStatus = 'done' | 'overdue' | 'this_week' | 'planned';

export interface WeekRow {
  task: Task;
  /** True only when the task is actually planned in this week (not merely visible because of its due date). */
  plannedInWeek: boolean;
  /** Days of this week the task is planned on (ascending). */
  days: IsoDate[];
  /** «ددلاین» column: the planned weekday, else the task's due date, else «بدون روز» (on the week without a day) or nothing. */
  due:
    | { kind: 'day' | 'date'; label: string; sortKey: number; date: IsoDate }
    | { kind: 'week' | 'none'; label: string; sortKey: number; date: null };
  status: WeekStatus;
}

export const STATUS_LABELS: Record<WeekStatus, string> = {
  done: 'انجام‌شده',
  overdue: 'عقب‌افتاده',
  this_week: 'این هفته',
  planned: 'برنامه‌ریزی‌شده',
};

const PRIORITY_RANK: Record<TaskPriority, number> = { high: 0, medium: 1, low: 2 };
const STATUS_RANK: Record<WeekStatus, number> = { overdue: 0, this_week: 1, planned: 1, done: 2 };

/** Saturday of the week containing `date`. */
export function weekStart(date: IsoDate): IsoDate {
  return weekDays(date)[0].date;
}

/** "۱۶ تا ۲۲ آبان ۱۴۰۵" style range of the week. */
export function weekLabel(start: IsoDate, today: IsoDate): string {
  const current = weekStart(today);
  if (start === current) return 'این هفته';
  if (start === addDays(current, -7)) return 'هفتهٔ قبل';
  if (start === addDays(current, 7)) return 'هفتهٔ بعد';
  return `${formatJalaliDate(start)} تا ${formatJalaliDate(addDays(start, 6))}`;
}

export function buildWeekRows(start: IsoDate, today: IsoDate, plans: DailyPlan[], tasks: Task[], weekly: WeeklyPlanItem[] = []): WeekRow[] {
  const end = addDays(start, 6);
  const current = weekStart(today) === start;
  const past = isoToDayNumber(end) < isoToDayNumber(today);
  const days = new Map<string, IsoDate[]>();
  const planned = new Map<string, Task>();
  for (const plan of plans) {
    if (plan.date < start || plan.date > end) continue;
    for (const item of plan.items) {
      if (item.task.archivedAt) continue;
      days.set(item.task.id, [...(days.get(item.task.id) ?? []), plan.date].sort());
      planned.set(item.task.id, item.task);
    }
  }
  const onWeek = new Set<string>();
  for (const item of weekly) {
    if (item.weekStart !== start || item.task.archivedAt) continue;
    onWeek.add(item.task.id);
    if (!planned.has(item.task.id)) planned.set(item.task.id, item.task);
  }
  // the live task list is newer than the plan copies (e.g. right after ticking a box)
  const live = new Map(tasks.map((t) => [t.id, t]));
  const rows = new Map<string, Task>();
  for (const [id, t] of planned) rows.set(id, live.get(id) ?? t);
  for (const t of tasks) {
    if (t.archivedAt || rows.has(t.id) || !t.dueDate) continue;
    const inWeek = t.dueDate >= start && t.dueDate <= end;
    const carriedOver = current && t.status !== 'done' && t.dueDate < start;
    if (inWeek || carriedOver) rows.set(t.id, t);
  }
  return [...rows.values()].map((task) => {
    const taskDays = days.get(task.id) ?? [];
    const upcoming = taskDays.find((d) => d >= today);
    const shownDay = upcoming ?? taskDays[0];
    const due: WeekRow['due'] = shownDay
      ? { kind: 'day', label: WEEKDAYS[weekdayFromSaturday(shownDay)] + (taskDays.length > 1 ? ` (+${taskDays.length - 1})` : ''), sortKey: isoToDayNumber(shownDay), date: shownDay }
      : task.dueDate
        ? { kind: 'date', label: shortDate(task.dueDate), sortKey: isoToDayNumber(task.dueDate), date: task.dueDate }
        : onWeek.has(task.id)
          ? { kind: 'week', label: 'بدون روز', sortKey: Number.MAX_SAFE_INTEGER - 1, date: null }
          : { kind: 'none', label: '—', sortKey: Number.MAX_SAFE_INTEGER, date: null };
    let status: WeekStatus;
    if (task.status === 'done') status = 'done';
    else if (past) status = 'overdue';
    else if ((task.dueDate && task.dueDate < today) || (taskDays.length > 0 && !upcoming)) status = 'overdue';
    else status = current ? 'this_week' : 'planned';
    return { task, plannedInWeek: taskDays.length > 0 || onWeek.has(task.id), days: taskDays, due, status };
  });
}

function shortDate(iso: IsoDate): string {
  return formatJalaliDate(iso).replace(/ \S+$/, ''); // "۱۴ آبان" (no year)
}

export type WeekSort = 'day' | 'priority' | 'status' | 'title';

export interface WeekFilter {
  query: string;
  statuses: WeekStatus[];
  priorities: TaskPriority[];
  goalId: string;
}

export const EMPTY_FILTER: WeekFilter = { query: '', statuses: [], priorities: [], goalId: '' };

const norm = (s: string) => s.replace(/[‌\s]+/g, ' ').trim().toLocaleLowerCase('fa');

/** Search covers the task, its goal and its focus item titles (names supplied by the caller). */
export function filterRows(rows: WeekRow[], f: WeekFilter, names: (row: WeekRow) => string[]): WeekRow[] {
  const q = norm(f.query);
  return rows.filter((r) =>
    (!q || [r.task.title, ...names(r)].some((s) => norm(s).includes(q))) &&
    (f.statuses.length === 0 || f.statuses.includes(r.status === 'planned' ? 'this_week' : r.status)) &&
    (f.priorities.length === 0 || f.priorities.includes(r.task.priority ?? 'low')) &&
    (!f.goalId || r.task.effectiveGoalId === f.goalId));
}

export function sortRows(rows: WeekRow[], by: WeekSort): WeekRow[] {
  const prio = (r: WeekRow) => PRIORITY_RANK[r.task.priority ?? 'low'];
  const cmp: Record<WeekSort, (a: WeekRow, b: WeekRow) => number> = {
    day: (a, b) => a.due.sortKey - b.due.sortKey || prio(a) - prio(b),
    priority: (a, b) => prio(a) - prio(b) || a.due.sortKey - b.due.sortKey,
    status: (a, b) => STATUS_RANK[a.status] - STATUS_RANK[b.status] || a.due.sortKey - b.due.sortKey,
    title: (a, b) => a.task.title.localeCompare(b.task.title, 'fa'),
  };
  return [...rows].sort((a, b) => cmp[by](a, b) || a.task.createdAt.localeCompare(b.task.createdAt) || a.task.id.localeCompare(b.task.id));
}
