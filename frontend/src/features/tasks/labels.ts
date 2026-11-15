import type { TaskPriority, TaskStatus } from './types';

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  not_started: 'شروع‌نشده',
  doing: 'در حال انجام',
  done: 'انجام‌شده',
};

export const TASK_PRIORITY_LABELS: Record<TaskPriority, string> = {
  low: 'پایین',
  medium: 'متوسط',
  high: 'بالا',
};

export const TASK_STATUS_ORDER: TaskStatus[] = ['not_started', 'doing', 'done'];
/** Order of the choices, as the owner lists them: low, medium, high (low is the default). */
export const TASK_PRIORITY_ORDER: TaskPriority[] = ['low', 'medium', 'high'];
