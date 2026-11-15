import type { IsoDate } from '../../shared/date/calendar';
import type { Milestone } from './types';

export type MilestoneState = 'completed' | 'overdue' | 'due_today' | 'upcoming' | 'undated';

/** Display state relative to the user's local date (ISO strings compare chronologically). */
export function milestoneState(m: Pick<Milestone, 'completed' | 'dueDate'>, today: IsoDate): MilestoneState {
  if (m.completed) return 'completed';
  if (!m.dueDate) return 'undated';
  if (m.dueDate < today) return 'overdue';
  return m.dueDate === today ? 'due_today' : 'upcoming';
}

export const STATE_LABELS: Record<MilestoneState, string> = {
  completed: 'تکمیل‌شده',
  overdue: 'سررسید گذشته',
  due_today: 'سررسید امروز',
  upcoming: 'پیش‌رو',
  undated: 'بدون موعد',
};

export const FILTER_LABELS = {
  all: 'همه',
  open: 'باز',
  overdue: 'سررسید گذشته',
  upcoming: 'پیش‌رو',
  completed: 'تکمیل‌شده',
} as const;
