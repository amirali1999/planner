import type { FocusItem } from '../focus/types';
import type { Goal } from '../goals/types';
import type { Task } from '../tasks/types';
import type { DeletionResult } from './types';

export type ArchiveKind = 'goal' | 'focus' | 'task';

/** Newest archived first. */
export function archivedOnly<T extends { archivedAt: string | null; title: string }>(list: T[], query: string): T[] {
  const q = norm(query);
  return list
    .filter((x) => x.archivedAt && (!q || norm(x.title).includes(q)))
    .sort((a, b) => (b.archivedAt ?? '').localeCompare(a.archivedAt ?? '') || a.title.localeCompare(b.title, 'fa'));
}

const norm = (s: string) => s.replace(/[‌\s]+/g, ' ').trim().toLocaleLowerCase('fa');

/**
 * What a permanent delete will remove — the same rules as the backend: a goal takes its focus items, every task
 * linked to the goal or to those items, and detaches its child goals; a focus item takes its tasks.
 */
export function deletionImpact(kind: ArchiveKind, id: string, goals: Goal[], items: FocusItem[], tasks: Task[]): DeletionResult {
  if (kind === 'task') return { goals: 0, focusItems: 0, tasks: 1, detachedChildGoals: 0 };
  if (kind === 'focus') return { goals: 0, focusItems: 1, tasks: tasks.filter((t) => t.focusItemId === id).length, detachedChildGoals: 0 };
  const itemIds = new Set(items.filter((i) => i.goalId === id).map((i) => i.id));
  return {
    goals: 1,
    focusItems: itemIds.size,
    tasks: tasks.filter((t) => t.goalId === id || (t.focusItemId != null && itemIds.has(t.focusItemId))).length,
    detachedChildGoals: goals.filter((g) => g.parentGoalId === id).length,
  };
}
