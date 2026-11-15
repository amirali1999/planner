import { describe, expect, it } from 'vitest';
import type { Task } from '../tasks/types';
import type { DailyPlan, DailyPlanItem } from './types';
import { buildBoard, columnOf, goalColor, groupByColumn, localDayOf, matches, moveInPlan, patchForColumn } from './todayBoardModel';

const task = (id: string, over: Partial<Task> = {}): Task => ({
  id, title: id, description: null, status: 'not_started', priority: 'low', estimatedMinutes: null, dueDate: null,
  blockedReason: null, goalId: null, focusItemId: null, focusItemGoalId: null, effectiveGoalId: null, effectiveGoalSource: null,
  warnings: [], archivedAt: null, createdAt: '2026-11-10T08:00:00Z', updatedAt: '2026-11-10T08:00:00Z', completedAt: null, ...over,
});
const item = (id: string, position: number, over: Partial<Task> = {}): DailyPlanItem => ({ id: `i-${id}`, position, addedAt: '', task: task(id, over) });

describe('columnOf', () => {
  it('maps the three statuses and treats a blocked, unfinished task as waiting', () => {
    expect(columnOf(task('a'))).toBe('not_started');
    expect(columnOf(task('a', { status: 'doing' }))).toBe('doing');
    expect(columnOf(task('a', { status: 'doing', blockedReason: 'منتظر تیم' }))).toBe('waiting');
    expect(columnOf(task('a', { status: 'done', blockedReason: 'قدیمی' }))).toBe('done');
  });
});

const TZ = 'Asia/Tehran'; // UTC+03:30
const planOf = (date: string, items: DailyPlanItem[]): DailyPlan => ({
  date, planId: 'p', exists: true, items, activeTaskCount: 0, doneTaskCount: 0, completionPercent: null, createdAt: null, updatedAt: null,
});
const ids = (b: { task: Task }[]) => b.map((e) => e.task.id);

describe('buildBoard / groupByColumn', () => {
  it('keeps plan order inside each column and hides archived tasks', () => {
    const board = buildBoard('2026-11-10', planOf('2026-11-10', [item('b', 2), item('a', 0),
      item('x', 1, { archivedAt: '2026-11-01T00:00:00Z' }), item('d', 3, { status: 'done' })]), TZ);
    const g = groupByColumn(board);
    expect(ids(g.not_started)).toEqual(['a', 'b']);
    expect(ids(g.done)).toEqual(['d']); // done without a completion time keeps showing
    expect(g.waiting).toEqual([]);
  });

  it('shows a done task only on the local day it was finished', () => {
    // 2026-11-09T21:00Z is 00:30 on 2026-11-10 in Tehran
    const late = item('late', 0, { status: 'done', completedAt: '2026-11-09T21:00:00.000Z' });
    const early = item('early', 1, { status: 'done', completedAt: '2026-11-09T20:00:00.000Z' });
    expect(ids(buildBoard('2026-11-10', planOf('2026-11-10', [late, early]), TZ))).toEqual(['late']);
    expect(ids(buildBoard('2026-11-09', planOf('2026-11-09', [late, early]), TZ))).toEqual(['early']);
  });

  it('contains only persisted items of the selected day and is empty without a plan', () => {
    const board = buildBoard('2026-11-10', planOf('2026-11-10', [item('planned', 0), item('waiting', 1, { blockedReason: 'منتظر' })]), TZ);
    expect(ids(board)).toEqual(['planned', 'waiting']);
    expect(board.every((entry) => entry.planItemId.startsWith('i-'))).toBe(true);
    expect(ids(groupByColumn(board).waiting)).toEqual(['waiting']);
    expect(buildBoard('2026-11-10', undefined, TZ)).toEqual([]);
  });

  it('maps a completion instant to its local day', () => {
    expect(localDayOf('2026-11-09T20:30:00.000Z', TZ)).toBe('2026-11-10');
    expect(localDayOf('2026-11-09T20:29:59.000Z', TZ)).toBe('2026-11-09');
  });
});

describe('moveInPlan', () => {
  const all = ['a', 'b', 'c', 'd', 'e'];
  it('reorders inside a column', () => {
    expect(moveInPlan(all, 'c', ['a', 'c', 'e'], 0)).toEqual(['c', 'a', 'b', 'd', 'e']);
    expect(moveInPlan(all, 'a', ['a', 'c', 'e'], 2)).toEqual(['b', 'c', 'd', 'e', 'a']);
  });
  it('drops into another column before the target card or after its last card', () => {
    expect(moveInPlan(all, 'e', ['b', 'd'], 1)).toEqual(['a', 'b', 'c', 'e', 'd']);
    expect(moveInPlan(all, 'a', ['b', 'd'], 2)).toEqual(['b', 'c', 'd', 'a', 'e']);
  });
  it('appends to an empty column and keeps items that are not shown', () => {
    expect(moveInPlan(all, 'b', [], 0)).toEqual(['a', 'c', 'd', 'e', 'b']);
    expect(moveInPlan(all, 'b', ['d'], 9)).toEqual(['a', 'c', 'd', 'b', 'e']);
  });
});

describe('patchForColumn', () => {
  it('changes status and clears the waiting reason when leaving «در انتظار»', () => {
    expect(patchForColumn(task('a'), 'doing')).toEqual({ status: 'doing' });
    expect(patchForColumn(task('a', { blockedReason: 'x', status: 'doing' }), 'done')).toEqual({ status: 'done', blockedReason: null });
    expect(patchForColumn(task('a', { blockedReason: 'x' }), 'not_started')).toEqual({ status: 'not_started', blockedReason: null });
    expect(patchForColumn(task('a', { status: 'doing' }), 'doing')).toBeNull();
  });
  it('needs a reason to wait; a done task starts again', () => {
    expect(() => patchForColumn(task('a'), 'waiting', '  ')).toThrow();
    expect(patchForColumn(task('a', { status: 'doing' }), 'waiting', ' منتظر پاسخ ')).toEqual({ blockedReason: 'منتظر پاسخ' });
    expect(patchForColumn(task('a', { status: 'done' }), 'waiting', 'r')).toEqual({ blockedReason: 'r', status: 'not_started' });
  });
});

describe('matches / goalColor', () => {
  it('searches title, reason and supplied names; colours are stable', () => {
    const i = task('x', { title: 'توسعه فلو', blockedReason: 'منتظر تیم' });
    expect(matches(i, 'فلو', [])).toBe(true);
    expect(matches(i, 'تیم', [])).toBe(true);
    expect(matches(i, 'n8n', ['AI Agent', 'N8N'])).toBe(true);
    expect(matches(i, 'کتاب', ['AI Agent'])).toBe(false);
    expect(goalColor('g1')).toBe(goalColor('g1'));
  });
});
