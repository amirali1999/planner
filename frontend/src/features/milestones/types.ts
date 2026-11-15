import type { IsoDate } from '../../shared/date/calendar';

/** A Goal's delivery checkpoint. Not a Key Result: completing it changes neither Outcome nor Execution. */
export interface Milestone {
  id: string;
  goalId: string;
  title: string;
  description: string | null;
  dueDate: IsoDate | null;
  completed: boolean;
  completedAt: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type MilestoneStatusFilter = 'all' | 'open' | 'overdue' | 'upcoming' | 'completed';

export interface UpdateMilestoneRequest {
  title?: string;
  description?: string | null;
  dueDate?: IsoDate | null;
  completed?: boolean;
  archived?: boolean;
}
