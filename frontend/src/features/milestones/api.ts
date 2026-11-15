import { apiRequest } from '../../shared/api/client';
import type { IsoDate } from '../../shared/date/calendar';
import type { Milestone, MilestoneStatusFilter, UpdateMilestoneRequest } from './types';

const enc = encodeURIComponent;

export const milestonesApi = {
  /** {@code today} is the user's local date (configured timezone); needed for overdue/upcoming. */
  list: (goalId: string, status: MilestoneStatusFilter, today: IsoDate, includeArchived: boolean, signal?: AbortSignal) => {
    const params = new URLSearchParams({ status, date: today, archived: includeArchived ? 'all' : 'false' });
    return apiRequest<Milestone[]>(`/goals/${enc(goalId)}/milestones?${params.toString()}`, { signal });
  },
  create: (goalId: string, body: { title: string; description?: string; dueDate?: IsoDate }) =>
    apiRequest<Milestone>(`/goals/${enc(goalId)}/milestones`, { method: 'POST', body }),
  update: (milestoneId: string, body: UpdateMilestoneRequest) =>
    apiRequest<Milestone>(`/milestones/${enc(milestoneId)}`, { method: 'PATCH', body }),
};
