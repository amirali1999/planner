import { addDays, isoToDayNumber, weekdayFromSaturday, type IsoDate } from '../../shared/date/calendar';
import type { DailyPlan } from '../planning/types';
import type { Task } from '../tasks/types';

export type CalendarView = 'month' | 'week' | 'day';

/**
 * Number of complete task lines that fit below a month cell's day heading. The small pixel tolerance absorbs browser
 * sub-pixel rounding, while the floor keeps a partially visible line out. Month cells always keep the product minimum.
 */
export function monthTaskCapacity(availableHeight: number, lineHeight: number, minimum = 5): number {
  if (!Number.isFinite(availableHeight) || !Number.isFinite(lineHeight) || lineHeight <= 0) return minimum;
  return Math.max(minimum, Math.floor((Math.max(0, availableHeight) + 0.75) / lineHeight));
}

/** One task on one calendar day: planned on that day's plan and/or due that day. */
export interface CalendarEntry {
  task: Task;
  planned: boolean;
  due: boolean;
}

/** Every date from `start` to `end` inclusive. */
export function daysBetween(start: IsoDate, end: IsoDate): IsoDate[] {
  const n = isoToDayNumber(end) - isoToDayNumber(start) + 1;
  return Array.from({ length: Math.max(0, n) }, (_, i) => addDays(start, i));
}

/** Saturday … Friday of the week containing `date`. */
export function weekOf(date: IsoDate): IsoDate[] {
  const saturday = addDays(date, -weekdayFromSaturday(date));
  return daysBetween(saturday, addDays(saturday, 6));
}

const STATUS_ORDER = { doing: 0, not_started: 1, done: 2 } as const;
const PRIORITY_ORDER = { high: 0, medium: 1, low: 2 } as const;

/**
 * Entries per day for the given days. A task appears on every day it is planned and on its due date (once per day).
 * Archived tasks are hidden (they only live on the archive page). Within a day: doing, not started, done; then priority.
 */
export function buildCalendar(days: IsoDate[], plans: DailyPlan[], tasks: Task[]): Map<IsoDate, CalendarEntry[]> {
  const byDay = new Map<IsoDate, Map<string, CalendarEntry>>(days.map((d) => [d, new Map()]));
  const put = (day: IsoDate, task: Task, kind: 'planned' | 'due') => {
    const slot = byDay.get(day);
    if (!slot || task.archivedAt) return;
    const entry = slot.get(task.id) ?? { task, planned: false, due: false };
    entry[kind] = true;
    slot.set(task.id, entry);
  };
  for (const plan of plans) for (const item of plan.items) put(plan.date, item.task, 'planned');
  for (const task of tasks) if (task.dueDate) put(task.dueDate, task, 'due');
  const result = new Map<IsoDate, CalendarEntry[]>();
  for (const [day, slot] of byDay) {
    result.set(day, [...slot.values()].sort((a, b) =>
      STATUS_ORDER[a.task.status] - STATUS_ORDER[b.task.status]
      || PRIORITY_ORDER[a.task.priority ?? 'low'] - PRIORITY_ORDER[b.task.priority ?? 'low']));
  }
  return result;
}

/** Number of distinct tasks over all days of the map. */
export function distinctTasks(calendar: Map<IsoDate, CalendarEntry[]>): number {
  const ids = new Set<string>();
  for (const entries of calendar.values()) for (const e of entries) ids.add(e.task.id);
  return ids.size;
}

/** Goal ids (in priority order) that have at least one task in the calendar, plus whether any task has no goal. */
export function legendOf(calendar: Map<IsoDate, CalendarEntry[]>, goalIdsByPriority: string[]): { goalIds: string[]; noGoal: boolean } {
  const used = new Set<string>();
  let noGoal = false;
  const active = new Set(goalIdsByPriority);
  for (const entries of calendar.values()) {
    for (const { task } of entries) {
      if (task.effectiveGoalId && active.has(task.effectiveGoalId)) used.add(task.effectiveGoalId);
      else noGoal = true;
    }
  }
  return { goalIds: goalIdsByPriority.filter((id) => used.has(id)), noGoal };
}
