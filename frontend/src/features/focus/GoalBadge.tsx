import { Link } from 'react-router';
import type { Goal } from '../goals/types';

/** The linked goal, clearly marked when archived; "بدون هدف" for standalone items. */
export function GoalBadge({ goalId, goals }: { goalId: string | null; goals: Goal[] }) {
  if (!goalId) return <span className="badge badge--muted">بدون هدف</span>;
  const goal = goals.find((g) => g.id === goalId);
  if (!goal) return <span className="meta">هدف: —</span>;
  return (
    <span>
      هدف: <Link to={`/goals/${goal.id}`}>{goal.title}</Link>
      {goal.archivedAt && <span className="badge badge--muted">هدف بایگانی‌شده</span>}
    </span>
  );
}
