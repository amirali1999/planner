import { isoToDayNumber, type IsoDate } from '../../shared/date/calendar';
import { formatJalaliDate, formatNumber } from '../../shared/format';
import type { GoalStatus } from './types';

/** Days before the deadline from which an open goal is "due soon". */
export const DUE_SOON_DAYS = 7;

/** Statuses for which a passed deadline still matters (same set as the backend's `overdueOn` filter). */
export const OPEN_STATUSES: readonly GoalStatus[] = ['not_started', 'doing', 'on_hold'];

export type DeadlineState =
  | { kind: 'overdue'; days: number }
  | { kind: 'due_today'; days: 0 }
  | { kind: 'due_soon'; days: number }
  | { kind: 'upcoming'; days: number }
  /** done/dropped: the deadline is shown, but nothing is due any more. */
  | { kind: 'closed'; days: number };

/**
 * Deadline state from date-only strings: whole calendar days between `today` (the user's local date, see
 * `todayIso()`) and the deadline. No `Date` objects, so no timezone can shift the result.
 */
export function deadlineState(deadline: IsoDate | null, status: GoalStatus, today: IsoDate): DeadlineState | null {
  if (!deadline) return null;
  const days = isoToDayNumber(deadline) - isoToDayNumber(today);
  if (!OPEN_STATUSES.includes(status)) return { kind: 'closed', days };
  if (days < 0) return { kind: 'overdue', days: -days };
  if (days === 0) return { kind: 'due_today', days: 0 };
  if (days <= DUE_SOON_DAYS) return { kind: 'due_soon', days };
  return { kind: 'upcoming', days };
}

/** Short Persian label; null when there is nothing to flag (no deadline, far away or closed). */
export function deadlineFlag(state: DeadlineState | null): string | null {
  if (!state) return null;
  switch (state.kind) {
    case 'overdue':
      return `${formatNumber(state.days)} روز از موعد گذشته`;
    case 'due_today':
      return 'موعد امروز';
    case 'due_soon':
      return `${formatNumber(state.days)} روز تا موعد`;
    default:
      return null;
  }
}

export function deadlineText(deadline: IsoDate): string {
  return `موعد: ${formatJalaliDate(deadline)}`;
}
