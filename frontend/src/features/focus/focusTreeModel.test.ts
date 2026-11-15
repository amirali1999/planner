import { describe, expect, it } from 'vitest';
import type { Goal } from '../goals/types';
import type { Task } from '../tasks/types';
import { buildFocusTree, filterFocusTree } from './focusTreeModel';
import type { FocusItem } from './types';

const goal = (id: string, title = id): Goal => ({
  id, title, description: null, status: 'doing', periodType: 'monthly', periodStart: '2026-10-23', periodEnd: '2026-11-21',
  deadline: null, parentGoalId: null, archivedAt: null, createdAt: '', updatedAt: '', priority: 1,
});
const item = (id: string, goalId: string | null, position: number | null, extra: Partial<FocusItem> = {}): FocusItem => ({
  id, title: id, kind: 'other', description: null, goalId, status: 'not_started', position, archivedAt: null,
  createdAt: '2026-11-01T00:00:00.000Z', updatedAt: '', ...extra,
});
const task = (id: string, extra: Partial<Task> = {}): Task => ({
  id, title: id, description: null, status: 'not_started', priority: null, estimatedMinutes: null, dueDate: null,
  blockedReason: null, goalId: null, focusItemId: null, focusItemGoalId: null, effectiveGoalId: null, effectiveGoalSource: null,
  warnings: [], archivedAt: null, createdAt: '', updatedAt: '', completedAt: null, ...extra,
});

describe('focus tree', () => {
  const goals = [goal('g1', 'مطالعه ۱۰ کتاب'), goal('g2', 'پروژه طراحی')];
  const items = [item('f2', 'g1', 2, { title: 'کتاب‌های تخصصی' }), item('f1', 'g1', 1, { title: 'کتاب‌های پایه' }),
    item('free', null, null), item('old', 'g1', 3, { archivedAt: '2026-11-02T00:00:00.000Z' }), item('lost', 'gone', 1, { createdAt: '2026-11-03T00:00:00.000Z' })];
  const tasks = [
    task('t1', { title: 'فصل ۳ معماری', focusItemId: 'f1', status: 'done', effectiveGoalId: 'g1' }),
    task('t2', { title: 'خلاصه‌نویسی', focusItemId: 'f1', effectiveGoalId: 'g1' }),
    task('t3', { focusItemId: 'f1', effectiveGoalId: 'g1', archivedAt: '2026-11-02T00:00:00.000Z' }),
    task('t4', { goalId: 'g1', effectiveGoalId: 'g1' }), // direct goal link, no focus item
    task('t5', { focusItemId: 'f2', goalId: 'g2', effectiveGoalId: 'g2' }), // direct goal wins over the focus item's goal
  ];
  const tree = buildFocusTree(goals, items, tasks);

  it('nests focus items in manual order under their goal, without archived ones', () => {
    expect(tree.goals.map((g) => g.goal.id)).toEqual(['g1', 'g2']);
    expect(tree.goals[0].focus.map((f) => f.item.id)).toEqual(['f1', 'f2']);
    expect(tree.goals[1].focus).toEqual([]);
    expect(tree.unlinked.map((f) => f.item.id)).toEqual(['free', 'lost']);
  });

  it('counts tasks by effective goal (each once) and computes focus item progress from its active tasks', () => {
    expect(tree.goals[0].taskCount).toBe(3); // t1, t2, t4 — t3 archived, t5 counts for g2
    expect(tree.goals[1].taskCount).toBe(1);
    expect(tree.goals[0].directTasks.map((t) => t.id)).toEqual(['t4']); // linked to the goal without a focus item
    expect(tree.goals[1].directTasks).toEqual([]); // t5 has a focus item, so it is listed under that item
    const f1 = tree.goals[0].focus[0];
    expect(f1.tasks.map((t) => t.id)).toEqual(['t1', 't2']);
    expect(f1.progress).toBe(50);
    expect(tree.unlinked[0].progress).toBeNull(); // no tasks: not measured, not 0%
  });

  it('searches goals, focus items and tasks, keeping the path to each hit', () => {
    expect(filterFocusTree(tree, '').goals).toHaveLength(2);
    const byTask = filterFocusTree(tree, 'معماری');
    expect(byTask.goals.map((g) => g.goal.id)).toEqual(['g1']);
    expect(byTask.goals[0].focus.map((f) => f.item.id)).toEqual(['f1']);
    expect(byTask.goals[0].focus[0].tasks.map((t) => t.id)).toEqual(['t1']);
    const byItem = filterFocusTree(tree, 'پایه');
    expect(byItem.goals[0].focus[0].tasks).toHaveLength(2); // a matching item keeps all its tasks
    expect(filterFocusTree(tree, 'طراحی').goals.map((g) => g.goal.id)).toEqual(['g2']);
    expect(filterFocusTree(tree, 'کتاب‌های‌پایه').goals).toHaveLength(1); // ZWNJ/space-insensitive
    expect(filterFocusTree(tree, 'zzz').goals).toEqual([]);
    const direct = filterFocusTree(tree, 't4');
    expect(direct.goals.map((g) => g.goal.id)).toEqual(['g1']);
    expect(direct.goals[0].directTasks.map((t) => t.id)).toEqual(['t4']);
    expect(direct.goals[0].focus).toEqual([]);
  });
});
