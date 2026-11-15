import { Link } from 'react-router';
import type { FocusItem } from '../focus/types';
import type { Goal } from '../goals/types';
import type { Task } from './types';

interface Props {
  task: Task;
  goals: Goal[];
  focusItems: FocusItem[];
}

/**
 * The task's EFFECTIVE goal as computed by the backend (direct link wins over the focus item's goal), where it comes
 * from, the linked focus item, and a non-blocking warning when the direct goal and the focus item's goal disagree.
 */
export function TaskAttribution({ task, goals, focusItems }: Props) {
  const goal = task.effectiveGoalId ? goals.find((g) => g.id === task.effectiveGoalId) : undefined;
  const focus = task.focusItemId ? focusItems.find((f) => f.id === task.focusItemId) : undefined;
  const conflict = task.warnings.some((w) => w.code === 'goal_differs_from_focus_item');
  const focusGoal = conflict && task.focusItemGoalId ? goals.find((g) => g.id === task.focusItemGoalId) : undefined;
  return (
    <>
      {task.effectiveGoalId ? (
        <>
          هدف:{' '}
          {goal ? <Link to={`/goals/${goal.id}`}>{goal.title}</Link> : '—'}
          {goal?.archivedAt && <span className="badge badge--muted">بایگانی‌شده</span>}
          {task.effectiveGoalSource === 'focus_item' && <span className="meta"> (از طریق آیتم تمرکز)</span>}
        </>
      ) : (
        <span className="badge badge--muted">بدون هدف</span>
      )}
      {task.focusItemId && (
        <>
          {' · '}تمرکز: {focus ? <Link to={`/focus/${focus.id}`}>{focus.title}</Link> : '—'}
        </>
      )}
      {conflict && (
        <span className="badge badge--warning" title="هدف مستقیم ملاک است">
          هدف مستقیم با هدف آیتم تمرکز{focusGoal ? ` («${focusGoal.title}»)` : ''} فرق دارد؛ هدف مستقیم ملاک است
        </span>
      )}
    </>
  );
}
