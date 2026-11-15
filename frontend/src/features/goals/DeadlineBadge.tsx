import type { IsoDate } from '../../shared/date/calendar';
import { deadlineFlag, deadlineState, deadlineText } from './deadline';
import type { GoalStatus } from './types';

/** Jalali deadline plus an overdue / due-today / due-soon flag for open goals (date-only comparison). */
export function DeadlineBadge({ deadline, status, today }: { deadline: IsoDate | null; status: GoalStatus; today: IsoDate }) {
  const state = deadlineState(deadline, status, today);
  if (!deadline || !state) return null;
  const flag = deadlineFlag(state);
  return (
    <span className="deadline">
      <span>{deadlineText(deadline)}</span>
      {flag && <span className={`badge deadline__flag deadline__flag--${state.kind}`}>{flag}</span>}
    </span>
  );
}
