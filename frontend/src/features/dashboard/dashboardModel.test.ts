import { describe, expect, it } from 'vitest';
import type { DailyPlan } from '../planning/types';
import type { GoalCard } from './types';
import {
  clock, distinctPlannedTasks, FOCUS_SECONDS, monthDays, orderGoals, outcomeSummary, pauseTimer, remaining, resetTimer, startTimer, weekDays, withoutArchived,
} from './dashboardModel';

const goal = (id: string, outcome: number | null, periodType: GoalCard['periodType'] = 'monthly'): GoalCard => ({
  goalId: id, title: id, status: 'doing', periodType, periodStart: '2026-10-23', periodEnd: '2026-11-21',
  outcomeProgress: outcome, outcomeSource: outcome == null ? 'not_measured' : 'own_key_results', keyResultsCount: 1,
  contributingChildCount: 0, measuredAt: null, executionProgress: 90, activeTaskCount: 1, doneTaskCount: 1,
});
const plan = (date: string, taskIds: string[]): DailyPlan => ({
  date, planId: 'p', exists: true, activeTaskCount: taskIds.length, doneTaskCount: 0, completionPercent: 0,
  createdAt: null, updatedAt: null,
  items: taskIds.map((id, i) => ({ id: `${date}-${i}`, position: i, addedAt: '', task: { id } as never })),
});

describe('dashboard model', () => {
  it('builds the Saturday–Friday week around today', () => {
    const w = weekDays('2026-11-10'); // Tuesday
    expect(w.map((d) => d.date)).toEqual(['2026-11-07', '2026-11-08', '2026-11-09', '2026-11-10', '2026-11-11', '2026-11-12', '2026-11-13']);
    expect(w[0].label).toBe('شنبه');
    expect(w[6].label).toBe('جمعه');
    expect(weekDays('2026-11-07')[0].date).toBe('2026-11-07'); // Saturday itself
    expect(weekDays('2026-11-13')[0].date).toBe('2026-11-07'); // Friday
  });

  it('lists the days of the Jalali month (Aban 1405 has 30 days, Esfand 1404 has 29)', () => {
    const aban = monthDays('2026-11-09');
    expect(aban[0]).toBe('2026-10-23');
    expect(aban).toHaveLength(30);
    expect(monthDays('2026-03-01')).toHaveLength(29);
  });

  it('counts distinct planned tasks across days', () => {
    expect(distinctPlannedTasks([plan('2026-11-08', ['a', 'b']), plan('2026-11-09', ['b', 'c'])])).toBe(3);
    expect(distinctPlannedTasks([])).toBe(0);
  });

  it('averages Outcome only, skipping unmeasured goals, never using Execution', () => {
    expect(outcomeSummary([goal('a', 40), goal('b', 80), goal('c', null)])).toEqual({ mean: 60, measured: 2, total: 3 });
    expect(outcomeSummary([goal('a', null)])).toEqual({ mean: null, measured: 0, total: 1 });
    expect(outcomeSummary([])).toEqual({ mean: null, measured: 0, total: 0 });
    expect(outcomeSummary([goal('a', 0)]).mean).toBe(0); // a measured 0 is a value
  });

  it('runs, pauses and finishes the focus timer', () => {
    let t = resetTimer();
    expect(remaining(t, 0)).toBe(FOCUS_SECONDS);
    t = startTimer(t, 1_000);
    expect(remaining(t, 61_000)).toBe(FOCUS_SECONDS - 60);
    t = pauseTimer(t, 61_000);
    expect(remaining(t, 999_000)).toBe(FOCUS_SECONDS - 60); // paused: time does not run
    t = startTimer(t, 1_000_000);
    expect(remaining(t, 1_000_000 + FOCUS_SECONDS * 1000)).toBe(0); // never negative
    expect(clock(FOCUS_SECONDS)).toBe('25:00');
    expect(clock(59)).toBe('00:59');
  });
});

describe('withoutArchived', () => {
  it('drops archived tasks from a plan and leaves clean plans untouched', () => {
    const item = (id: string, archivedAt: string | null) => ({ id, position: 0, addedAt: '', task: { id, archivedAt } }) as never;
    const plan = { date: '2026-11-10', items: [item('a', null), item('b', '2026-11-09T00:00:00.000Z')] } as never as DailyPlan;
    expect(withoutArchived(plan).items.map((i) => i.id)).toEqual(['a']);
    const clean = { ...plan, items: [plan.items[0]] };
    expect(withoutArchived(clean)).toBe(clean);
  });
});

describe('orderGoals', () => {
  it('shows every period, coarse first, then the manual goal priority', () => {
    const goals = [goal('m1', 10), goal('q1', null, 'quarterly'), goal('a1', 5, 'annual'), goal('a2', null, 'annual'), goal('w1', 1, 'weekly')];
    expect(orderGoals(goals, ['a2', 'q1', 'a1']).map((g) => g.goalId)).toEqual(['a2', 'a1', 'q1', 'm1', 'w1']);
  });

  it('keeps the API order when the priority is unknown', () => {
    const goals = [goal('a1', 1, 'annual'), goal('a2', 2, 'annual'), goal('a3', 3, 'annual')];
    expect(orderGoals(goals).map((g) => g.goalId)).toEqual(['a1', 'a2', 'a3']);
    expect(orderGoals(goals, ['a3']).map((g) => g.goalId)).toEqual(['a3', 'a1', 'a2']);
  });
});
