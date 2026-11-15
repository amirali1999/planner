import type { FocusKind, FocusStatus } from './types';

export const KIND_LABELS: Record<FocusKind, string> = {
  project: 'پروژه',
  book: 'کتاب',
  course: 'دوره',
  area: 'حوزه',
  other: 'سایر',
};
export const KIND_ORDER: FocusKind[] = ['project', 'book', 'course', 'area', 'other'];

export const FOCUS_STATUS_LABELS: Record<FocusStatus, string> = {
  not_started: 'شروع‌نشده',
  doing: 'در حال انجام',
  done: 'انجام‌شده',
};
export const FOCUS_STATUS_ORDER: FocusStatus[] = ['not_started', 'doing', 'done'];
