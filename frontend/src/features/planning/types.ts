import type { IsoDate } from '../../shared/date/calendar';
import type { Task } from '../tasks/types';

export interface DailyPlanItem {
  id: string;
  position: number;
  addedAt: string;
  /** The live Task record (same as in /tasks), not a copy. */
  task: Task;
}

/** GET /plans/daily/{date}/carry-over — a task planned on an earlier day and not on this day's plan. */
export interface CarriedTask {
  /** Latest earlier day the task was planned on. */
  plannedOn: IsoDate;
  task: Task;
}

/** GET /plans/weekly/{weekStart}/items — a task put on a Saturday–Friday week without a day. */
export interface WeeklyPlanItem {
  id: string;
  /** The Saturday of the week. */
  weekStart: IsoDate;
  addedAt: string;
  /** The live task (may be done or archived). */
  task: Task;
}

/** GET /plans/daily?date=… — a missing plan comes back with exists=false and no items. */
export interface DailyPlan {
  date: IsoDate;
  planId: string | null;
  exists: boolean;
  items: DailyPlanItem[];
  activeTaskCount: number;
  doneTaskCount: number;
  /** null when there is no plan or no non-archived task on it. */
  completionPercent: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}
