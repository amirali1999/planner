import { describe, expect, it } from 'vitest';
import type { DailyPlan } from '../planning/types';
import type { Task } from '../tasks/types';
import type { GoalCard } from './types';
import { attentionItems, weekSummary } from './dashboardModel';

const task = (id: string, over: Partial<Task> = {}): Task => ({
  id, title: id, description: null, status: 'not_started', priority: 'low', estimatedMinutes: null, dueDate: null,
  blockedReason: null, goalId: null, focusItemId: null, focusItemGoalId: null, effectiveGoalId: null, effectiveGoalSource: null,
  warnings: [], archivedAt: null, createdAt: '', updatedAt: '', completedAt: null, ...over,
});
const plan = (date: string, tasks: Task[]): DailyPlan => ({
  date, planId: 'p', exists: true, activeTaskCount: 0, doneTaskCount: 0, completionPercent: null, createdAt: null, updatedAt: null,
  items: tasks.map((t, i) => ({ id: `${date}-${i}`, position: i, addedAt: '', task: t })),
});
const goal = (id: string, outcome: number | null, over: Partial<GoalCard> = {}): GoalCard => ({
  goalId: id, title: id, status: 'doing', periodType: 'monthly', periodStart: '2026-11-01', periodEnd: '2026-11-30',
  outcomeProgress: outcome, outcomeSource: outcome == null ? 'not_measured' : 'own_key_results', keyResultsCount: 1,
  contributingChildCount: 0, measuredAt: null, executionProgress: null, activeTaskCount: 0, doneTaskCount: 0, ...over,
});

describe('weekSummary', () => {
  it('counts per day, distinct tasks over the week, and sums estimates without archived tasks', () => {
    const a = task('a', { status: 'done', estimatedMinutes: 30 });
    const b = task('b', { estimatedMinutes: 90 });
    const gone = task('x', { archivedAt: '2026-11-01T00:00:00Z', estimatedMinutes: 600 });
    const s = weekSummary([plan('2026-11-07', [a, b, gone]), plan('2026-11-08', [a]), plan('2026-11-09', [])]);
    expect(s.days).toEqual([
      { date: '2026-11-07', total: 2, done: 1 }, { date: '2026-11-08', total: 1, done: 1 }, { date: '2026-11-09', total: 0, done: 0 },
    ]);
    expect([s.total, s.done, s.minutes]).toEqual([2, 1, 120]);
  });
  it('reports no planned time (null, not 0) when no task has an estimate', () => {
    expect(weekSummary([plan('2026-11-07', [task('a')])]).minutes).toBeNull();
    expect(weekSummary([]).minutes).toBeNull();
  });
});

describe('attentionItems', () => {
  const today = '2026-11-12';
  it('lists overdue (most late first), due today and due tomorrow — only unfinished, active tasks', () => {
    const items = attentionItems([
      task('later', { dueDate: '2026-11-20' }),
      task('tomorrow', { dueDate: '2026-11-13' }),
      task('today', { dueDate: '2026-11-12' }),
      task('late2', { dueDate: '2026-11-10' }),
      task('late5', { dueDate: '2026-11-07' }),
      task('doneLate', { dueDate: '2026-11-01', status: 'done' }),
      task('archivedLate', { dueDate: '2026-11-01', archivedAt: '2026-11-02T00:00:00Z' }),
    ], [], today);
    expect(items.map((i) => [i.kind, i.id, i.amount])).toEqual([
      ['overdue', 'late5', 5], ['overdue', 'late2', 2], ['due_today', 'today', 0], ['due_tomorrow', 'tomorrow', 1],
    ]);
  });
  it('flags a measured goal trailing its elapsed period by more than the margin; never an unmeasured or finished one', () => {
    // 12 of 30 days elapsed = 40%
    const items = attentionItems([], [goal('behind', 10), goal('ok', 30), goal('unmeasured', null), goal('done', 0, { status: 'done' })], today);
    expect(items).toEqual([{ kind: 'behind', id: 'behind', title: 'behind', amount: 30 }]);
  });
});
