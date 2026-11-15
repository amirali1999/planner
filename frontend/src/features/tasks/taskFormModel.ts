import { normalizeDigits } from '../../shared/format';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import type { IsoDate } from '../../shared/date/calendar';
import type { CreateTaskRequest, Task, TaskPriority, TaskStatus, UpdateTaskRequest } from './types';

/** Editable form state; strings as typed, converted on submit. */
export interface TaskFormValues {
  title: string;
  description: string;
  goalId: string;
  focusItemId: string;
  status: TaskStatus;
  /** Every task has a priority; default low. */
  priority: TaskPriority;
  estimatedMinutes: string;
  /** Gregorian ISO date-only (picked in Jalali), or null. */
  dueDate: IsoDate | null;
  blockedReason: string;
}

export const EMPTY_TASK_FORM: TaskFormValues = {
  title: '',
  description: '',
  goalId: '',
  focusItemId: '',
  status: 'not_started',
  priority: 'low',
  estimatedMinutes: '',
  dueDate: null,
  blockedReason: '',
};

export function formFromTask(task: Task): TaskFormValues {
  return {
    title: task.title,
    description: task.description ?? '',
    goalId: task.goalId ?? '',
    focusItemId: task.focusItemId ?? '',
    status: task.status,
    priority: task.priority ?? 'low',
    estimatedMinutes: task.estimatedMinutes == null ? '' : String(task.estimatedMinutes),
    dueDate: task.dueDate,
    blockedReason: task.blockedReason ?? '',
  };
}

/** Normalized values ready for the API; null = empty/not set. */
export interface ParsedTaskForm {
  title: string;
  description: string | null;
  goalId: string | null;
  focusItemId: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  estimatedMinutes: number | null;
  dueDate: string | null;
  blockedReason: string | null;
}

export type TaskFormResult = { ok: true; value: ParsedTaskForm } | { ok: false; errors: FieldErrors };

const orNull = (s: string): string | null => (s.trim() === '' ? null : s.trim());

export function parseTaskForm(values: TaskFormValues): TaskFormResult {
  const errors: FieldErrors = {};
  if (!values.title.trim()) errors.title = 'required';

  let estimatedMinutes: number | null = null;
  const estimateText = normalizeDigits(values.estimatedMinutes);
  if (estimateText !== '') {
    if (!/^\d+$/.test(estimateText)) errors.estimatedMinutes = 'invalid_minutes';
    else if (Number(estimateText) > 525_600) errors.estimatedMinutes = 'too_large';
    else estimatedMinutes = Number(estimateText);
  }

  if (Object.keys(errors).length > 0) return { ok: false, errors };
  return {
    ok: true,
    value: {
      title: values.title.trim(),
      description: orNull(values.description),
      goalId: values.goalId || null,
      focusItemId: values.focusItemId || null,
      status: values.status,
      priority: values.priority,
      estimatedMinutes,
      dueDate: values.dueDate,
      blockedReason: orNull(values.blockedReason),
    },
  };
}

export function toCreateRequest(v: ParsedTaskForm): CreateTaskRequest {
  return {
    title: v.title,
    status: v.status,
    ...(v.description != null && { description: v.description }),
    ...(v.goalId != null && { goalId: v.goalId }),
    ...(v.focusItemId != null && { focusItemId: v.focusItemId }),
    priority: v.priority,
    ...(v.estimatedMinutes != null && { estimatedMinutes: v.estimatedMinutes }),
    ...(v.dueDate != null && { dueDate: v.dueDate }),
    ...(v.blockedReason != null && { blockedReason: v.blockedReason }),
  };
}

/** Only changed fields; a cleared optional field is sent as null. */
export function toUpdateRequest(original: Task, v: ParsedTaskForm): UpdateTaskRequest {
  const patch: UpdateTaskRequest = {};
  if (v.title !== original.title) patch.title = v.title;
  if (v.description !== original.description) patch.description = v.description;
  if (v.goalId !== original.goalId) patch.goalId = v.goalId;
  if (v.focusItemId !== original.focusItemId) patch.focusItemId = v.focusItemId;
  if (v.status !== original.status) patch.status = v.status;
  if (v.priority !== (original.priority ?? 'low')) patch.priority = v.priority;
  if (v.estimatedMinutes !== original.estimatedMinutes) patch.estimatedMinutes = v.estimatedMinutes;
  if (v.dueDate !== original.dueDate) patch.dueDate = v.dueDate;
  if (v.blockedReason !== original.blockedReason) patch.blockedReason = v.blockedReason;
  return patch;
}
