import { apiRequest } from '../../shared/api/client';
import type { IsoDate } from '../../shared/date/calendar';
import type { TimeBlock, TimeBlockInput } from './timeboxModel';
import type { CarriedTask, DailyPlan, DailyPlanItem, WeeklyPlanItem } from './types';

export const dailyPlanApi = {
  /** Pure read; never creates a plan. */
  get: (date: IsoDate, signal?: AbortSignal) =>
    apiRequest<DailyPlan>(`/plans/daily?date=${encodeURIComponent(date)}`, { signal }),
  /** Adds an existing task; the backend creates the day's plan if needed. 409 duplicate_plan_task if present. */
  addTask: (date: IsoDate, taskId: string, addToWeek = false) =>
    apiRequest<DailyPlanItem>(`/plans/daily/${encodeURIComponent(date)}/items${addToWeek ? '?addToWeek=true' : ''}`, { method: 'POST', body: { taskId } }),
  /**
   * Read-only: unfinished tasks planned on an earlier day (not on this date's plan), plus — with the date's local-day
   * window (UTC instants) — the ones finished inside it.
   */
  carryOver: (date: IsoDate, window: { from: string; to: string } | null, signal?: AbortSignal) =>
    apiRequest<CarriedTask[]>(`/plans/daily/${encodeURIComponent(date)}/carry-over${window
      ? `?completedFrom=${encodeURIComponent(window.from)}&completedTo=${encodeURIComponent(window.to)}` : ''}`, { signal }),
  /** Moves a planned task to another date in one step (calendar drag-and-drop). 404 when it is not on `date`'s plan. */
  move: (date: IsoDate, taskId: string, toDate: IsoDate) =>
    apiRequest<DailyPlanItem>(`/plans/daily/${encodeURIComponent(date)}/tasks/${encodeURIComponent(taskId)}/move`, { method: 'POST', body: { toDate } }),
  /** New order of ALL items of the date's plan (top first). 404 when the date has no plan. */
  reorder: (date: IsoDate, itemIds: string[]) =>
    apiRequest<DailyPlan>(`/plans/daily/${encodeURIComponent(date)}/items/order`, { method: 'PUT', body: { itemIds } }),
};

/** Tasks put on a week without a day (`weekStart` = the Saturday). */
export const weeklyPlanApi = {
  items: (weekStart: IsoDate, signal?: AbortSignal) =>
    apiRequest<WeeklyPlanItem[]>(`/plans/weekly/${encodeURIComponent(weekStart)}/items`, { signal }),
  /** 409 duplicate_plan_task when the task is already on that week, task_archived for an archived task. */
  addTask: (weekStart: IsoDate, taskId: string) =>
    apiRequest<WeeklyPlanItem>(`/plans/weekly/${encodeURIComponent(weekStart)}/items`, { method: 'POST', body: { taskId } }),
  /** Removes all dated and undated planning entries for this task in the week; task fields stay unchanged. */
  removeTask: (weekStart: IsoDate, taskId: string) =>
    apiRequest<void>(`/plans/weekly/${encodeURIComponent(weekStart)}/tasks/${encodeURIComponent(taskId)}`, { method: 'DELETE' }),
};

/** Weekly time-box (planning aid only: a block never changes its task). */
export const timeBlocksApi = {
  /** Read-only; from..to inclusive, at most 42 days. */
  list: (from: IsoDate, to: IsoDate, signal?: AbortSignal) =>
    apiRequest<TimeBlock[]>(`/time-blocks?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, { signal }),
  /** 409 time_block_overlap (another block of that day) or task_archived. */
  create: (input: TimeBlockInput) => apiRequest<TimeBlock>('/time-blocks', { method: 'POST', body: input }),
  update: (id: string, patch: Partial<TimeBlockInput>) =>
    apiRequest<TimeBlock>(`/time-blocks/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  remove: (id: string) => apiRequest<void>(`/time-blocks/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
