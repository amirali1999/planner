import type { Goal } from '../goals/types';
import type { Task } from '../tasks/types';
import type { FocusItem } from './types';

export interface FocusNode {
  item: FocusItem;
  /** Active tasks linked to this focus item (creation order). */
  tasks: Task[];
  /** done ÷ active tasks × 100 (2 decimals); null without tasks — never 0 by default. */
  progress: number | null;
}

export interface GoalNode {
  goal: Goal;
  focus: FocusNode[];
  /** Active tasks whose EFFECTIVE goal is this goal (direct link or through one of its focus items), each once. */
  taskCount: number;
  /** Active tasks linked to the goal directly, without any focus item (shown as their own group). */
  directTasks: Task[];
}

export interface FocusTree {
  goals: GoalNode[];
  /** Focus items without a goal. */
  unlinked: FocusNode[];
}

function percent(done: number, total: number): number | null {
  return total === 0 ? null : Math.round((done / total) * 10000) / 100;
}

function focusNode(item: FocusItem, tasks: Task[]): FocusNode {
  const own = tasks.filter((t) => t.focusItemId === item.id && !t.archivedAt);
  return { item, tasks: own, progress: percent(own.filter((t) => t.status === 'done').length, own.length) };
}

const byPosition = (a: FocusItem, b: FocusItem) =>
  (a.position ?? Infinity) - (b.position ?? Infinity) || a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id);

const byCreation = (a: FocusItem, b: FocusItem) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id);

/** Goals in the given order (priority), each with its active focus items (manual order) and their tasks. */
export function buildFocusTree(goals: Goal[], items: FocusItem[], tasks: Task[]): FocusTree {
  const active = items.filter((i) => !i.archivedAt);
  const goalIds = new Set(goals.map((g) => g.id));
  return {
    goals: goals.map((goal) => ({
      goal,
      focus: active.filter((i) => i.goalId === goal.id).sort(byPosition).map((i) => focusNode(i, tasks)),
      taskCount: tasks.filter((t) => !t.archivedAt && t.effectiveGoalId === goal.id).length,
      directTasks: tasks.filter((t) => !t.archivedAt && t.goalId === goal.id && !t.focusItemId),
    })),
    // items of a goal that is not in the list (e.g. archived goal) are shown with the unlinked ones
    // positions are per goal, so items without a (listed) goal are simply in creation order
    unlinked: active.filter((i) => !i.goalId || !goalIds.has(i.goalId)).sort(byCreation).map((i) => focusNode(i, tasks)),
  };
}

const norm = (s: string) => s.replace(/[‌\s]+/g, ' ').trim().toLocaleLowerCase('fa');

/**
 * Search over goal, focus item and task titles. A goal stays when it or anything below it matches; a focus item stays
 * when it or one of its tasks matches (all its tasks are kept when the item itself matches).
 */
export function filterFocusTree(tree: FocusTree, query: string): FocusTree {
  const q = norm(query);
  if (!q) return tree;
  const hit = (s: string) => norm(s).includes(q);
  const filterFocus = (nodes: FocusNode[]) =>
    nodes
      .map((n) => (hit(n.item.title) ? n : { ...n, tasks: n.tasks.filter((t) => hit(t.title)) }))
      .filter((n) => hit(n.item.title) || n.tasks.length > 0);
  return {
    goals: tree.goals
      .map((g) => (hit(g.goal.title) ? g : { ...g, focus: filterFocus(g.focus), directTasks: g.directTasks.filter((t) => hit(t.title)) }))
      .filter((g) => hit(g.goal.title) || g.focus.length > 0 || g.directTasks.length > 0),
    unlinked: filterFocus(tree.unlinked),
  };
}
