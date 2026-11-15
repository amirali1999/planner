import { addDays, isoToDayNumber, weekdayFromSaturday, type IsoDate } from '../../shared/date/calendar';
import { periodRange } from '../../shared/date/periods';
import { WEEKDAYS } from '../../shared/format';
import { currentTimeZone, dateInZone } from '../../shared/date/timeZone';
import type { CarriedTask, DailyPlan } from '../planning/types';
import type { Task } from '../tasks/types';
import type { GoalCard } from './types';

/** The Saturday–Friday week containing `today` (date-only arithmetic, no time zones). */
export function weekDays(today: IsoDate): { date: IsoDate; label: string }[] {
  const saturday = addDays(today, -weekdayFromSaturday(today));
  return WEEKDAYS.map((label, i) => ({ date: addDays(saturday, i), label }));
}

/** Every date of the Jalali month containing `today`. */
export function monthDays(today: IsoDate): IsoDate[] {
  const { start, end } = periodRange('monthly', today);
  const n = isoToDayNumber(end) - isoToDayNumber(start) + 1;
  return Array.from({ length: n }, (_, i) => addDays(start, i));
}

/** Distinct tasks planned on any of the given days (a task planned on two days counts once). */
/** The plan without items of archived tasks (they are only shown on the archive page). */
export function withoutArchived(plan: DailyPlan): DailyPlan {
  return plan.items.some((i) => i.task.archivedAt) ? { ...plan, items: plan.items.filter((i) => !i.task.archivedAt) } : plan;
}

export function distinctPlannedTasks(plans: DailyPlan[]): number {
  return new Set(plans.flatMap((p) => p.items.map((i) => i.task.id))).size;
}

export interface OutcomeSummary {
  /** Unweighted mean of the goals' Outcome; null when no goal has a measured Outcome (never 0 by default). */
  mean: number | null;
  measured: number;
  total: number;
}

/**
 * Summary of the OUTCOME of the given goals (Key-Result based). Execution is never mixed in; goals without a measured
 * Outcome are left out of the mean, not counted as 0.
 */
export function outcomeSummary(goals: GoalCard[]): OutcomeSummary {
  const values = goals.map((g) => g.outcomeProgress).filter((v): v is number => v != null);
  const mean = values.length ? Math.round((values.reduce((a, b) => a + b, 0) / values.length) * 100) / 100 : null;
  return { mean, measured: values.length, total: goals.length };
}

const PERIOD_RANK: Record<GoalCard['periodType'], number> = { annual: 0, quarterly: 1, monthly: 2, weekly: 3 };

/**
 * Every goal of the dashboard card: coarse periods first (سالانه → فصلی → ماهانه → هفتگی), then the owner's manual
 * goal priority (`priorityIds`, first = highest; unknown ids keep the API order after the known ones).
 */
export function orderGoals(goals: GoalCard[], priorityIds: string[] = []): GoalCard[] {
  const rank = new Map(priorityIds.map((id, i) => [id, i]));
  const at = (g: GoalCard) => rank.get(g.goalId) ?? Number.MAX_SAFE_INTEGER;
  return goals
    .map((g, i) => ({ g, i }))
    .sort((a, b) => PERIOD_RANK[a.g.periodType] - PERIOD_RANK[b.g.periodType] || at(a.g) - at(b.g) || a.i - b.i)
    .map((x) => x.g);
}

export function goalsOfType(goals: GoalCard[], type: GoalCard['periodType']): GoalCard[] {
  return goals.filter((g) => g.periodType === type);
}

/** Focus timer (Pomodoro-like), pure so it can be tested: remaining seconds at `now`. */
export interface TimerState {
  durationSec: number;
  /** epoch ms when the current run started; null while paused */
  startedAt: number | null;
  /** seconds already elapsed in earlier runs */
  elapsedSec: number;
}

export const FOCUS_SECONDS = 25 * 60;

export function remaining(t: TimerState, now: number): number {
  const running = t.startedAt == null ? 0 : Math.floor((now - t.startedAt) / 1000);
  return Math.max(0, t.durationSec - t.elapsedSec - running);
}

export function startTimer(t: TimerState, now: number): TimerState {
  return t.startedAt != null || remaining(t, now) === 0 ? t : { ...t, startedAt: now };
}

export function pauseTimer(t: TimerState, now: number): TimerState {
  if (t.startedAt == null) return t;
  return { ...t, startedAt: null, elapsedSec: t.durationSec - remaining(t, now) };
}

export function resetTimer(durationSec = FOCUS_SECONDS): TimerState {
  return { durationSec, startedAt: null, elapsedSec: 0 };
}

/** mm:ss with Latin digits (formatted to Persian by the caller). */
export function clock(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

// ---------------------------------------------------------------------------------------------------- week summary

export interface WeekSummary {
  /** Per day: tasks on that day's plan and how many of them are done. */
  days: { date: IsoDate; total: number; done: number }[];
  /** Distinct tasks over the week (a task planned on two days counts once). */
  total: number;
  done: number;
  /** Sum of the distinct tasks' estimates; null when none has an estimate (never a fabricated 0). */
  minutes: number | null;
}

/** «برنامه این هفته»: archived tasks are left out (they only live on the archive page). */
export function weekSummary(plans: DailyPlan[], zone = currentTimeZone()): WeekSummary {
  const tasks = new Map<string, DailyPlan['items'][number]['task']>();
  const days = plans.map((p) => {
    const items = p.items.filter((i) => !i.task.archivedAt);
    for (const i of items) tasks.set(i.task.id, i.task);
    // a task finished on a later day was NOT done on this day (it was carried over)
    return { date: p.date, total: items.length, done: items.filter((i) => dayOutcome(i.task, p.date, zone).done).length };
  });
  const all = [...tasks.values()];
  const estimated = all.filter((t) => t.estimatedMinutes != null);
  return {
    days,
    total: all.length,
    done: all.filter((t) => t.status === 'done').length,
    minutes: estimated.length === 0 ? null : estimated.reduce((s, t) => s + (t.estimatedMinutes ?? 0), 0),
  };
}

// ---------------------------------------------------------------------------------------------------- attention

export type AttentionKind = 'overdue' | 'due_today' | 'due_tomorrow' | 'behind';

export interface AttentionItem {
  kind: AttentionKind;
  id: string;
  title: string;
  /** overdue: days late; behind: percentage points behind the elapsed share of the period. */
  amount: number;
}

/** A measured goal is «behind» when its Outcome trails the elapsed share of its period by more than this. */
export const BEHIND_MARGIN = 20;

/**
 * «نیازمند توجه»: unfinished active tasks that are overdue / due today / due tomorrow, and active goals whose MEASURED
 * Outcome trails the elapsed part of their period (unmeasured goals are never flagged). Most urgent first.
 */
export function attentionItems(tasks: { id: string; title: string; status: string; dueDate: IsoDate | null; archivedAt: string | null }[],
  goals: GoalCard[], today: IsoDate): AttentionItem[] {
  const t0 = isoToDayNumber(today);
  const out: AttentionItem[] = [];
  for (const t of tasks) {
    if (t.archivedAt || t.status === 'done' || !t.dueDate) continue;
    const diff = isoToDayNumber(t.dueDate) - t0;
    if (diff < 0) out.push({ kind: 'overdue', id: t.id, title: t.title, amount: -diff });
    else if (diff === 0) out.push({ kind: 'due_today', id: t.id, title: t.title, amount: 0 });
    else if (diff === 1) out.push({ kind: 'due_tomorrow', id: t.id, title: t.title, amount: 1 });
  }
  for (const g of goals) {
    if (g.outcomeProgress == null || g.status === 'done' || g.status === 'dropped') continue;
    const length = isoToDayNumber(g.periodEnd) - isoToDayNumber(g.periodStart) + 1;
    const elapsed = Math.min(100, Math.max(0, ((t0 - isoToDayNumber(g.periodStart) + 1) / length) * 100));
    const gap = elapsed - g.outcomeProgress;
    if (gap > BEHIND_MARGIN) out.push({ kind: 'behind', id: g.goalId, title: g.title, amount: Math.round(gap) });
  }
  const rank: Record<AttentionKind, number> = { overdue: 0, due_today: 1, due_tomorrow: 2, behind: 3 };
  return out.sort((a, b) => rank[a.kind] - rank[b.kind] || b.amount - a.amount);
}

// ---------------------------------------------------------------------------------------------------- daily list

/** A task's result on one planned day: done that day (or earlier), done on a later day, or still not done. */
export interface DayOutcome {
  done: boolean;
  /** The local day it was finished, when that is after the planned day. */
  doneLater: IsoDate | null;
}

export function dayOutcome(task: Pick<Task, 'status' | 'completedAt'>, day: IsoDate, zone = currentTimeZone()): DayOutcome {
  if (task.status !== 'done') return { done: false, doneLater: null };
  if (!task.completedAt) return { done: true, doneLater: null }; // done before completion times were recorded
  const finished = dateInZone(new Date(task.completedAt), zone);
  return finished <= day ? { done: true, doneLater: null } : { done: false, doneLater: finished };
}

const PRIORITY_RANK = { high: 0, medium: 1, low: 2 } as const;

/**
 * Order of the daily list: unfinished tasks first, high → medium → low priority (ties keep their plan order), then the
 * finished ones at the bottom (in plan order).
 */
export function sortDaily<T extends { task: Pick<Task, 'status' | 'priority'> }>(entries: T[]): T[] {
  const indexed = entries.map((e, i) => ({ e, i }));
  const rank = (t: Pick<Task, 'status' | 'priority'>) => (t.status === 'done' ? 3 : PRIORITY_RANK[t.priority ?? 'low']);
  return indexed.sort((a, b) => rank(a.e.task) - rank(b.e.task) || a.i - b.i).map((x) => x.e);
}

/**
 * Unfinished tasks from dates before `today`, for the dashboard's separate «کارهای باقی‌مانده» card. Completed and
 * archived tasks never belong in this action list; the carry-over endpoint can include completed-today rows when a
 * completion window is requested by another screen.
 */
export function remainingTasks(carried: CarriedTask[], today: IsoDate): CarriedTask[] {
  return sortDaily(carried.filter((entry) => entry.plannedOn < today && entry.task.status !== 'done' && !entry.task.archivedAt));
}
