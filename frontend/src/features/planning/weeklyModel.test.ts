import { describe, expect, it } from 'vitest';
import type { Task } from '../tasks/types';
import type { DailyPlan } from './types';
import { buildWeekRows, filterRows, sortRows, weekLabel, weekStart, type WeekRow } from './weeklyModel';

const task = (id: string, extra: Partial<Task> = {}): Task => ({
  id, title: id, description: null, status: 'not_started', priority: 'low', estimatedMinutes: null, dueDate: null,
  blockedReason: null, goalId: null, focusItemId: null, focusItemGoalId: null, effectiveGoalId: null, effectiveGoalSource: null,
  warnings: [], archivedAt: null, createdAt: `2026-11-0${id.length}T00:00:00.000Z`, updatedAt: '', completedAt: null, ...extra,
});
const plan = (date: string, tasks: Task[]): DailyPlan => ({
  date, planId: 'p', exists: true, activeTaskCount: tasks.length, doneTaskCount: 0, completionPercent: null, createdAt: null, updatedAt: null,
  items: tasks.map((t, i) => ({ id: `${date}-${i}`, position: i, addedAt: '', task: t })),
});

const TODAY = '2026-11-10'; // Tuesday; week = Sat 11-07 … Fri 11-13
const START = '2026-11-07';

describe('weekly plan model', () => {
  it('finds the Saturday of a week and names weeks relative to today', () => {
    expect(weekStart('2026-11-10')).toBe(START);
    expect(weekStart('2026-11-13')).toBe(START);
    expect(weekLabel(START, TODAY)).toBe('این هفته');
    expect(weekLabel('2026-10-31', TODAY)).toBe('هفتهٔ قبل');
    expect(weekLabel('2026-11-14', TODAY)).toBe('هفتهٔ بعد');
    expect(weekLabel('2026-11-21', TODAY)).toBe('۳۰ آبان ۱۴۰۵ تا ۶ آذر ۱۴۰۵');
  });

  it('collects planned tasks, tasks due this week and overdue carry-overs, each once', () => {
    const sat = task('sat'), wed = task('wed'), twice = task('twice'), done = task('done', { status: 'done' });
    const dueThu = task('dueThu', { dueDate: '2026-11-12' });
    const late = task('late', { dueDate: '2026-11-01' });
    const lateDone = task('lateDone', { dueDate: '2026-11-01', status: 'done' });
    const nextWeek = task('next', { dueDate: '2026-11-20' });
    const plans = [plan('2026-11-07', [sat, twice]), plan('2026-11-11', [wed, twice, done]), plan('2026-11-20', [task('other')])];
    const rows = buildWeekRows(START, TODAY, plans, [sat, wed, twice, done, dueThu, late, lateDone, nextWeek]);
    const by = (id: string) => rows.find((r) => r.task.id === id)!;
    expect(rows.map((r) => r.task.id).sort()).toEqual(['done', 'dueThu', 'late', 'sat', 'twice', 'wed']);
    expect(by('sat')).toMatchObject({ status: 'overdue', due: { kind: 'day', label: 'شنبه', date: '2026-11-07' } }); // planned on a past day, not done
    expect(by('sat').plannedInWeek).toBe(true);
    expect(by('wed')).toMatchObject({ status: 'this_week', due: { kind: 'day', label: 'چهارشنبه', date: '2026-11-11' } });
    expect(by('twice')).toMatchObject({ status: 'this_week', due: { label: 'چهارشنبه (+1)' }, days: ['2026-11-07', '2026-11-11'] });
    expect(by('done').status).toBe('done');
    expect(by('dueThu')).toMatchObject({ status: 'this_week', due: { kind: 'date', label: '۲۱ آبان', date: '2026-11-12' } });
    expect(by('dueThu').plannedInWeek).toBe(false);
    expect(by('late')).toMatchObject({ status: 'overdue', due: { kind: 'date', label: '۱۰ آبان', date: '2026-11-01' } }); // 2026-11-01
  });

  it('marks other weeks correctly: past = overdue unless done, future = planned', () => {
    const t = task('t');
    expect(buildWeekRows('2026-10-31', TODAY, [plan('2026-11-01', [t])], [t])[0].status).toBe('overdue');
    expect(buildWeekRows('2026-11-14', TODAY, [plan('2026-11-15', [t])], [t])[0].status).toBe('planned');
    const d = task('d', { status: 'done' });
    expect(buildWeekRows('2026-10-31', TODAY, [plan('2026-11-01', [d])], [d])[0].status).toBe('done');
    // carry-overs only in the current week
    expect(buildWeekRows('2026-11-14', TODAY, [], [task('late', { dueDate: '2026-11-01' })])).toEqual([]);
  });

  it('prefers the live task over the copy in a plan and skips archived ones', () => {
    const old = task('x');
    const fresh = task('x', { status: 'done' });
    expect(buildWeekRows(START, TODAY, [plan('2026-11-11', [old])], [fresh])[0].status).toBe('done');
    expect(buildWeekRows(START, TODAY, [plan('2026-11-11', [task('a', { archivedAt: 'x' })])], [])).toEqual([]);
  });

  it('searches, filters and sorts', () => {
    const rows: WeekRow[] = buildWeekRows(START, TODAY, [plan('2026-11-11', [task('b', { priority: 'high', effectiveGoalId: 'g1' }),
      task('aa', { priority: 'medium' }), task('ccc', { status: 'done' })])], []);
    const names = (r: WeekRow) => (r.task.effectiveGoalId === 'g1' ? ['یادگیری AI Agent'] : []);
    expect(filterRows(rows, { query: 'agent', statuses: [], priorities: [], goalId: '' }, names).map((r) => r.task.id)).toEqual(['b']);
    expect(filterRows(rows, { query: '', statuses: ['done'], priorities: [], goalId: '' }, names).map((r) => r.task.id)).toEqual(['ccc']);
    expect(filterRows(rows, { query: '', statuses: [], priorities: ['high', 'medium'], goalId: '' }, names)).toHaveLength(2);
    expect(filterRows(rows, { query: '', statuses: [], priorities: [], goalId: 'g1' }, names).map((r) => r.task.id)).toEqual(['b']);
    expect(sortRows(rows, 'priority').map((r) => r.task.id)).toEqual(['b', 'aa', 'ccc']);
    expect(sortRows(rows, 'status').map((r) => r.task.id).at(-1)).toBe('ccc');
    expect(sortRows(rows, 'title').map((r) => r.task.id)).toEqual(['aa', 'b', 'ccc']);
  });
});

describe('tasks put on the week without a day', () => {
  const item = (t: Task, weekStart = START) => ({ id: `w-${t.id}`, weekStart, addedAt: '', task: t });
  it('shows them once with «بدون روز», after dated rows, and ignores archived tasks and other weeks', () => {
    const free = task('free'), dated = task('dated'), gone = task('gone', { archivedAt: '2026-11-09T00:00:00Z' });
    const both = task('both');
    const rows = buildWeekRows(START, TODAY, [plan('2026-11-11', [dated, both])], [free, dated, both],
      [item(free), item(both), item(gone), item(task('later'), '2026-11-14')]);
    expect(rows.map((r) => r.task.id).sort()).toEqual(['both', 'dated', 'free']);
    const f = rows.find((r) => r.task.id === 'free')!;
    expect(f.due).toMatchObject({ kind: 'week', label: 'بدون روز', date: null });
    expect(f.status).toBe('this_week');
    expect(f.plannedInWeek).toBe(true);
    expect(rows.find((r) => r.task.id === 'both')!.due.kind).toBe('day'); // a planned day wins
    expect(sortRows(rows, 'day').at(-1)!.task.id).toBe('free');
  });

  it('uses the live task and its deadline', () => {
    const t = task('t', { dueDate: '2026-11-08' });
    const rows = buildWeekRows(START, TODAY, [], [{ ...t, status: 'done' }], [item(t)]);
    expect(rows[0].status).toBe('done');
    expect(rows[0].due.kind).toBe('date');
  });
});
