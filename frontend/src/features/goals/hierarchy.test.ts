import { describe, expect, it } from 'vitest';
import { eligibleParents, isCoarser, selfAndDescendantIds } from './hierarchy';
import type { Goal, PeriodType } from './types';

function goal(id: string, periodType: PeriodType, parentGoalId: string | null = null, archived = false): Goal {
  return {
    id, title: id, description: null, status: 'doing', periodType, periodStart: '2026-03-21', periodEnd: '2027-03-20',
    deadline: null, parentGoalId, archivedAt: archived ? '2026-11-03T12:00:00Z' : null, createdAt: '', updatedAt: '', priority: archived ? null : 1,
  };
}

describe('goal hierarchy helpers', () => {
  it('orders granularity annual > quarterly > monthly > weekly', () => {
    expect(isCoarser('annual', 'monthly')).toBe(true);
    expect(isCoarser('monthly', 'monthly')).toBe(false);
    expect(isCoarser('weekly', 'annual')).toBe(false);
  });

  it('finds descendants at any depth', () => {
    const goals = [goal('a', 'annual'), goal('q', 'quarterly', 'a'), goal('m', 'monthly', 'q'), goal('w', 'weekly', 'm'), goal('x', 'monthly')];
    expect([...selfAndDescendantIds('a', goals)].sort()).toEqual(['a', 'm', 'q', 'w']);
  });

  it('offers only active, coarser, non-descendant parents (skipping levels allowed)', () => {
    const goals = [
      goal('a', 'annual'), goal('a2', 'annual', null, true), goal('q', 'quarterly', 'a'), goal('m', 'monthly', 'q'), goal('m2', 'monthly'),
    ];
    expect(eligibleParents(goals[3], goals).map((g) => g.id)).toEqual(['a', 'q']); // monthly: annual (skip) or quarterly
    expect(eligibleParents(goals[0], goals)).toEqual([]); // annual has nothing coarser
    expect(eligibleParents(goal('w', 'weekly', 'm'), [...goals, goal('w', 'weekly', 'm')]).map((g) => g.id)).toEqual(['a', 'q', 'm', 'm2']);
    expect(eligibleParents(goals[2], goals).map((g) => g.id)).toEqual(['a']); // archived a2 excluded
  });
});
