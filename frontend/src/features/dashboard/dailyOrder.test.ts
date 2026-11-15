import { describe, expect, it } from 'vitest';
import type { CarriedTask, DailyPlan } from '../planning/types';
import type { Task } from '../tasks/types';
import { dayOutcome, remainingTasks, sortDaily, weekSummary } from './dashboardModel';

const TZ = 'Asia/Tehran'; // UTC+03:30
const task = (id: string, over: Partial<Task> = {}): Task => ({
  id, title: id, description: null, status: 'not_started', priority: 'low', estimatedMinutes: null, dueDate: null,
  blockedReason: null, goalId: null, focusItemId: null, focusItemGoalId: null, effectiveGoalId: null, effectiveGoalSource: null,
  warnings: [], archivedAt: null, createdAt: '', updatedAt: '', completedAt: null, ...over,
});

describe('sortDaily', () => {
  it('puts unfinished tasks first by priority (high → low, plan order kept for ties) and finished ones last', () => {
    const list = [
      task('done-high', { status: 'done', priority: 'high' }), task('low1'), task('med', { priority: 'medium' }),
      task('high', { priority: 'high' }), task('doing-low', { status: 'doing' }), task('low2'), task('done-low', { status: 'done' }),
    ].map((t) => ({ task: t }));
    expect(sortDaily(list).map((e) => e.task.id)).toEqual(['high', 'med', 'low1', 'doing-low', 'low2', 'done-high', 'done-low']);
  });
});

describe('remainingTasks', () => {
  it('keeps only unfinished active tasks from earlier days and orders them by priority', () => {
    const carried: CarriedTask[] = [
      { plannedOn: '2026-11-12', task: task('low') },
      { plannedOn: '2026-11-13', task: task('high', { priority: 'high' }) },
      { plannedOn: '2026-11-13', task: task('done', { status: 'done' }) },
      { plannedOn: '2026-11-13', task: task('archived', { archivedAt: '2026-11-14T00:00:00Z' }) },
      { plannedOn: '2026-11-14', task: task('today') },
    ];
    expect(remainingTasks(carried, '2026-11-14').map((entry) => entry.task.id)).toEqual(['high', 'low']);
  });
});

describe('dayOutcome', () => {
  it('done that day or earlier counts as done; finished on a later local day does not', () => {
    // 2026-11-10 22:00 UTC = 11 Nov 01:30 in Tehran
    const late = task('a', { status: 'done', completedAt: '2026-11-10T22:00:00Z' });
    expect(dayOutcome(late, '2026-11-10', TZ)).toEqual({ done: false, doneLater: '2026-11-11' });
    expect(dayOutcome(late, '2026-11-11', TZ)).toEqual({ done: true, doneLater: null });
    expect(dayOutcome(task('b'), '2026-11-10', TZ)).toEqual({ done: false, doneLater: null });
    expect(dayOutcome(task('c', { status: 'done' }), '2026-11-10', TZ)).toEqual({ done: true, doneLater: null });
  });
  it('week bars count a task as done only on the day it was finished (or later planned days)', () => {
    const t = task('a', { status: 'done', completedAt: '2026-11-11T08:00:00Z' });
    const plan = (date: string): DailyPlan => ({ date, planId: 'p', exists: true, activeTaskCount: 0, doneTaskCount: 0,
      completionPercent: null, createdAt: null, updatedAt: null, items: [{ id: date, position: 1, addedAt: '', task: t }] });
    expect(weekSummary([plan('2026-11-10'), plan('2026-11-11')], TZ).days.map((d) => d.done)).toEqual([0, 1]);
  });
});
