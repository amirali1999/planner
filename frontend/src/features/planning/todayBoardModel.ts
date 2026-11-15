import type { IsoDate } from '../../shared/date/calendar';
import { currentTimeZone, dateInZone } from '../../shared/date/timeZone';
import type { Task, UpdateTaskRequest } from '../tasks/types';
import type { DailyPlan } from './types';

/**
 * Kanban columns of the daily plan (design ui/today plan.png). «در انتظار» is NOT a fourth task status: it is a task
 * that is not done and has a blockedReason (DOMAIN_RULES: 3 statuses + optional blockedReason).
 */
export type BoardColumn = 'done' | 'doing' | 'waiting' | 'not_started';

/** Reading order (RTL: the first column is the right-most), as in the design. */
export const BOARD_COLUMNS: BoardColumn[] = ['done', 'doing', 'waiting', 'not_started'];

export const COLUMN_LABELS: Record<BoardColumn, string> = {
  done: 'انجام شده',
  doing: 'در حال انجام',
  waiting: 'در انتظار',
  not_started: 'انجام نشده',
};

export function columnOf(task: Task): BoardColumn {
  if (task.status === 'done') return 'done';
  if (task.blockedReason) return 'waiting';
  return task.status;
}

/**
 * One card of «تخته امروز». Every card is a persisted item of that day's plan. Earlier unfinished tasks enter the
 * board only after the owner explicitly adds them from the dashboard's «کارهای باقی‌مانده» card.
 */
export interface BoardEntry {
  id: string;
  task: Task;
  planItemId: string;
}

/** Local calendar day (configured time zone) of a UTC instant. */
export function localDayOf(instant: string, zone = currentTimeZone()): IsoDate {
  return dateInZone(new Date(instant), zone);
}

/** A done task is shown only on the day it was finished (a done task without a completion time keeps showing). */
function shownOn(task: Task, date: IsoDate, zone: string): boolean {
  if (task.archivedAt) return false;
  return task.status !== 'done' || !task.completedAt || localDayOf(task.completedAt, zone) === date;
}

/**
 * Cards of the board of `date`: only that day's persisted plan, in plan order. Archived tasks and tasks finished on
 * another day are hidden. There is no implicit carry-over on the execution board.
 */
export function buildBoard(date: IsoDate, plan: DailyPlan | undefined, zone = currentTimeZone()): BoardEntry[] {
  const planned = [...(plan?.items ?? [])].sort((a, b) => a.position - b.position);
  return planned
    .filter((i) => shownOn(i.task, date, zone))
    .map((i) => ({ id: i.id, task: i.task, planItemId: i.id }));
}

/** Cards per column, each in board order. */
export function groupByColumn(entries: BoardEntry[]): Record<BoardColumn, BoardEntry[]> {
  const out: Record<BoardColumn, BoardEntry[]> = { done: [], doing: [], waiting: [], not_started: [] };
  for (const e of entries) out[columnOf(e.task)].push(e);
  return out;
}

const norm = (s: string) => s.replace(/[‌\s]+/g, ' ').trim().toLocaleLowerCase('fa');

/** Search over the task title and the names the caller supplies (goal, focus item, waiting reason). */
export function matches(task: Task, query: string, names: string[]): boolean {
  const q = norm(query);
  return !q || [task.title, task.blockedReason ?? '', ...names].some((s) => norm(s).includes(q));
}

/**
 * New order of ALL plan item ids after moving `movedId` to `index` among `targetIds` (the cards currently shown in the
 * destination column, the moved card excluded). Items not on the board (archived, filtered out) keep their place.
 */
export function moveInPlan(allIds: string[], movedId: string, targetIds: string[], index: number): string[] {
  const rest = allIds.filter((id) => id !== movedId);
  const others = targetIds.filter((id) => id !== movedId);
  const clamped = Math.max(0, Math.min(index, others.length));
  if (others.length === 0) return [...rest, movedId];
  if (clamped < others.length) {
    const at = rest.indexOf(others[clamped]);
    return [...rest.slice(0, at), movedId, ...rest.slice(at)];
  }
  const after = rest.indexOf(others[others.length - 1]);
  return [...rest.slice(0, after + 1), movedId, ...rest.slice(after + 1)];
}

/**
 * PATCH that puts a task into `to` (null when it is already there). Moving into «در انتظار» needs a reason; a done task
 * moved there starts again (not_started). Leaving «در انتظار» clears the reason.
 */
export function patchForColumn(task: Task, to: BoardColumn, reason?: string): UpdateTaskRequest | null {
  const from = columnOf(task);
  if (from === to) return null;
  if (to === 'waiting') {
    const r = reason?.trim();
    if (!r) throw new Error('reason required');
    return { blockedReason: r, ...(task.status === 'done' && { status: 'not_started' as const }) };
  }
  return { status: to, ...(task.blockedReason != null && { blockedReason: null }) };
}

/** Stable colour of a goal's dot (hash of its id over a small palette). */
const DOT_COLORS = ['#3b82f6', '#a855f7', '#22c55e', '#84cc16', '#ec4899', '#14b8a6', '#ef4444', '#eab308'];
export function goalColor(goalId: string): string {
  let h = 0;
  for (const c of goalId) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return DOT_COLORS[h % DOT_COLORS.length];
}
