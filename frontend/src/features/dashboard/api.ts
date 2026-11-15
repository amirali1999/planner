import { apiRequest } from '../../shared/api/client';
import type { IsoDate } from '../../shared/date/calendar';
import type { DashboardToday, GoalProgress } from './types';

export const progressApi = {
  goal: (goalId: string, signal?: AbortSignal) =>
    apiRequest<GoalProgress>(`/goals/${encodeURIComponent(goalId)}/progress`, { signal }),
  /** Progress of up to 200 goals in one request (same values as `goal`). */
  goals: (goalIds: string[], signal?: AbortSignal) =>
    apiRequest<GoalProgress[]>(`/goals/progress?ids=${goalIds.map(encodeURIComponent).join(',')}`, { signal }),
  /** Read-only; the date is the device's local calendar date. */
  today: (date: IsoDate, signal?: AbortSignal) =>
    apiRequest<DashboardToday>(`/dashboard/today?date=${encodeURIComponent(date)}`, { signal }),
};
