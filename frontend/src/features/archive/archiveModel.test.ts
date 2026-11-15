import { describe, expect, it } from 'vitest';
import type { FocusItem } from '../focus/types';
import type { Goal } from '../goals/types';
import type { Task } from '../tasks/types';
import { archivedOnly, deletionImpact } from './archiveModel';

const g = (id: string, parentGoalId: string | null = null) => ({ id, parentGoalId, title: id, archivedAt: null }) as unknown as Goal;
const f = (id: string, goalId: string | null) => ({ id, goalId, title: id, archivedAt: null }) as unknown as FocusItem;
const t = (id: string, goalId: string | null, focusItemId: string | null) => ({ id, goalId, focusItemId, title: id, archivedAt: null }) as unknown as Task;

describe('deletionImpact', () => {
  const goals = [g('G'), g('child', 'G'), g('other')];
  const items = [f('i1', 'G'), f('i2', 'other')];
  const tasks = [t('direct', 'G', null), t('via', null, 'i1'), t('cross', 'other', 'i1'), t('keep', 'other', 'i2'), t('free', null, null)];
  it('goal: its items, tasks linked to it or its items, detached children', () => {
    expect(deletionImpact('goal', 'G', goals, items, tasks)).toEqual({ goals: 1, focusItems: 1, tasks: 3, detachedChildGoals: 1 });
  });
  it('focus item: itself and its tasks; task: itself', () => {
    expect(deletionImpact('focus', 'i2', goals, items, tasks)).toEqual({ goals: 0, focusItems: 1, tasks: 1, detachedChildGoals: 0 });
    expect(deletionImpact('task', 'free', goals, items, tasks)).toEqual({ goals: 0, focusItems: 0, tasks: 1, detachedChildGoals: 0 });
  });
});

describe('archivedOnly', () => {
  it('keeps archived entries, newest first, filtered by title', () => {
    const list = [
      { title: 'الف', archivedAt: '2026-11-01T00:00:00.000Z' },
      { title: 'ب', archivedAt: null },
      { title: 'پ کتاب', archivedAt: '2026-11-05T00:00:00.000Z' },
    ];
    expect(archivedOnly(list, '').map((x) => x.title)).toEqual(['پ کتاب', 'الف']);
    expect(archivedOnly(list, 'کتاب').map((x) => x.title)).toEqual(['پ کتاب']);
  });
});
