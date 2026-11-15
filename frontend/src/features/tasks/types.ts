import type { IsoDate } from '../../shared/date/calendar';

export type TaskStatus = 'not_started' | 'doing' | 'done';
export type TaskPriority = 'low' | 'medium' | 'high';
export type ArchiveFilter = 'false' | 'true' | 'all';

/** Mirrors backend TaskResponse. dueDate is a real deadline, unrelated to daily planning. */
export interface Task {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority | null;
  estimatedMinutes: number | null;
  dueDate: IsoDate | null;
  blockedReason: string | null;
  /** Stored direct link. */
  goalId: string | null;
  focusItemId: string | null;
  /** Goal of the linked focus item (derived by the backend). */
  focusItemGoalId: string | null;
  /** Attribution used by goal task lists and Execution: direct link wins over the focus item's goal. */
  effectiveGoalId: string | null;
  effectiveGoalSource: 'direct' | 'focus_item' | null;
  warnings: { code: string; message: string; details?: Record<string, unknown> }[];
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** When the task became done (UTC instant, set by the server); null while not done. */
  completedAt: string | null;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  goalId?: string;
  focusItemId?: string;
  status: TaskStatus;
  priority?: TaskPriority;
  estimatedMinutes?: number;
  dueDate?: IsoDate;
  blockedReason?: string;
}

/** PATCH body: omitted key = unchanged, null = clear. */
export interface UpdateTaskRequest {
  title?: string;
  description?: string | null;
  goalId?: string | null;
  focusItemId?: string | null;
  status?: TaskStatus;
  /** Required when present: a priority cannot be cleared (every task has one). */
  priority?: TaskPriority;
  estimatedMinutes?: number | null;
  dueDate?: IsoDate | null;
  blockedReason?: string | null;
  archived?: boolean;
}

export interface TaskFilters {
  status?: TaskStatus;
  /** Direct goal link only (effective attribution via focus items comes with F-01). */
  goalId?: string;
  focusItemId?: string;
  archived: ArchiveFilter;
}
