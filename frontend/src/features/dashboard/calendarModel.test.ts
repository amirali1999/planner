import { describe, expect, it } from 'vitest';
import type { DailyPlan, DailyPlanItem } from '../planning/types';
import type { Task } from '../tasks/types';
import { buildCalendar, daysBetween, distinctTasks, legendOf, monthTaskCapacity, weekOf } from './calendarModel';

const task = (id: string, over: Partial<Task> = {}): Task => ({
  id, title: id, description: null, status: 'not_started', priority: 'low', estimatedMinutes: null, dueDate: null,
  blockedReason: null, goalId: null, focusItemId: null, focusItemGoalId: null, effectiveGoalId: null, effectiveGoalSource: null,
  warnings: [], archivedAt: null, createdAt: '2026-11-10T08:00:00Z', updatedAt: '2026-11-10T08:00:00Z', completedAt: null, ...over,
});
const item = (t: Task): DailyPlanItem => ({ id: `i-${t.id}`, position: 1, addedAt: '', task: t });
const plan = (date: string, tasks: Task[]): DailyPlan => ({
  date, planId: 'p', exists: true, items: tasks.map(item), activeTaskCount: 0, doneTaskCount: 0, completionPercent: null, createdAt: null, updatedAt: null,
});

describe('daysBetween / weekOf', () => {
  it('lists inclusive ranges and Saturday–Friday weeks', () => {
    expect(daysBetween('2026-11-10', '2026-11-12')).toEqual(['2026-11-10', '2026-11-11', '2026-11-12']);
    expect(daysBetween('2026-11-12', '2026-11-10')).toEqual([]);
    const w = weekOf('2026-11-12'); // Thursday
    expect(w[0]).toBe('2026-11-07'); // Saturday
    expect(w[6]).toBe('2026-11-13'); // Friday
  });
});

describe('monthTaskCapacity', () => {
  it('uses every complete line of available height and keeps the five-task minimum', () => {
    expect(monthTaskCapacity(63, 16)).toBe(5);
    expect(monthTaskCapacity(95, 16)).toBe(5);
    expect(monthTaskCapacity(96, 16)).toBe(6);
    expect(monthTaskCapacity(128, 16)).toBe(8);
  });

  it('tolerates sub-pixel layout rounding without admitting a visibly partial line', () => {
    expect(monthTaskCapacity(127.4, 16)).toBe(8);
    expect(monthTaskCapacity(126.9, 16)).toBe(7);
    expect(monthTaskCapacity(Number.NaN, 16)).toBe(5);
    expect(monthTaskCapacity(100, 0)).toBe(5);
  });
});

describe('buildCalendar', () => {
  const days = daysBetween('2026-11-10', '2026-11-12');
  it('puts a task on each planned day and its due day once, and ignores days outside the range', () => {
    const a = task('a', { dueDate: '2026-11-11' });
    const b = task('b', { dueDate: '2026-12-01' });
    const cal = buildCalendar(days, [plan('2026-11-10', [a]), plan('2026-11-11', [a])], [a, b]);
    expect(cal.get('2026-11-10')!.map((e) => [e.task.id, e.planned, e.due])).toEqual([['a', true, false]]);
    expect(cal.get('2026-11-11')!.map((e) => [e.task.id, e.planned, e.due])).toEqual([['a', true, true]]);
    expect(cal.get('2026-11-12')).toEqual([]);
    expect(distinctTasks(cal)).toBe(1);
  });
  it('hides archived tasks and orders doing, not started, done then by priority', () => {
    const tasks = [
      task('done', { status: 'done', dueDate: '2026-11-10' }),
      task('low', { dueDate: '2026-11-10' }),
      task('high', { priority: 'high', dueDate: '2026-11-10' }),
      task('doing', { status: 'doing', dueDate: '2026-11-10' }),
      task('gone', { archivedAt: '2026-11-01T00:00:00Z', dueDate: '2026-11-10' }),
    ];
    expect(buildCalendar(days, [], tasks).get('2026-11-10')!.map((e) => e.task.id)).toEqual(['doing', 'high', 'low', 'done']);
  });
  it('builds the legend from active goals in priority order and flags tasks without one', () => {
    const tasks = [
      task('x', { effectiveGoalId: 'g2', dueDate: '2026-11-10' }),
      task('y', { effectiveGoalId: 'archived-goal', dueDate: '2026-11-10' }),
    ];
    const cal = buildCalendar(days, [], tasks);
    expect(legendOf(cal, ['g1', 'g2'])).toEqual({ goalIds: ['g2'], noGoal: true });
    expect(legendOf(buildCalendar(days, [], []), ['g1'])).toEqual({ goalIds: [], noGoal: false });
  });
});
