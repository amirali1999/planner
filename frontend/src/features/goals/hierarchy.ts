import type { Goal, PeriodType } from './types';

const RANK: Record<PeriodType, number> = { annual: 4, quarterly: 3, monthly: 2, weekly: 1 };

/** Mirrors the backend rule for offering choices only; the backend remains the authority (409 on violation). */
export function isCoarser(parent: PeriodType, child: PeriodType): boolean {
  return RANK[parent] > RANK[child];
}

/** Ids of the goal and every goal below it, from a flat list (cycle-safe). */
export function selfAndDescendantIds(goalId: string, goals: Goal[]): Set<string> {
  const result = new Set<string>([goalId]);
  let added = true;
  while (added) {
    added = false;
    for (const g of goals) {
      if (g.parentGoalId && result.has(g.parentGoalId) && !result.has(g.id)) {
        result.add(g.id);
        added = true;
      }
    }
  }
  return result;
}

/**
 * Parent choices for a goal: active, strictly coarser period, and not the goal itself or one of its descendants.
 * Skipped levels are allowed (annual → monthly).
 */
export function eligibleParents(goal: Pick<Goal, 'id' | 'periodType'>, goals: Goal[]): Goal[] {
  const excluded = selfAndDescendantIds(goal.id, goals);
  return goals.filter((g) => !g.archivedAt && !excluded.has(g.id) && isCoarser(g.periodType, goal.periodType));
}
