import { ApiError } from '../../shared/api/client';
import type { InboxDecision } from './types';

export const DECISION_LABELS: Record<InboxDecision, string> = {
  task: 'کار',
  goal: 'هدف',
  focus_item: 'آیتم تمرکز',
  reference: 'مرجع',
  someday: 'شاید بعداً',
  discard: 'حذف از صف',
};

export const DECISION_HINTS: Record<InboxDecision, string> = {
  task: 'یک اقدام مشخص که باید انجام شود.',
  goal: 'یک نتیجه که می‌خواهید در یک دوره به آن برسید.',
  focus_item: 'پروژه، کتاب، دوره یا حوزه‌ای که روی آن کار می‌کنید.',
  reference: 'اطلاعاتی برای نگه داشتن؛ اقدامی ندارد.',
  someday: 'شاید در آینده؛ فعلاً نه.',
  discard: 'لازم نیست؛ متن در بخش «حذف‌شده‌ها» باقی می‌ماند.',
};

/** Prefill for a conversion: first line as title (≤200 chars); the whole note as description when it adds anything. */
export function prefillFromNote(rawText: string): { title: string; description: string } {
  const firstLine = rawText.split('\n')[0].trim();
  const title = [...firstLine].slice(0, 200).join('');
  return { title, description: rawText.trim() === title ? '' : rawText.trim() };
}

/** Server errors on nested payloads are keyed "task.title"; forms know "title". */
export function unprefixErrors(error: unknown, prefix: string): unknown {
  if (!(error instanceof ApiError)) return error;
  const details: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(error.details)) details[k.startsWith(prefix + '.') ? k.slice(prefix.length + 1) : k] = v;
  return new ApiError(error.status, error.code, error.message, details);
}
