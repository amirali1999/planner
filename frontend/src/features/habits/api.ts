import { apiRequest } from '../../shared/api/client';
import type { IsoDate } from '../../shared/date/calendar';
import type { Habit } from './types';

const enc = encodeURIComponent;

export const habitsApi = {
  /** Read-only; from..to inclusive (≤ 62 days). Active habits, or archived ones with archived=true. */
  list: (from: IsoDate, to: IsoDate, signal?: AbortSignal, archived = false) =>
    apiRequest<Habit[]>(`/habits?from=${enc(from)}&to=${enc(to)}${archived ? '&archived=true' : ''}`, { signal }),
  create: (title: string) => apiRequest<Habit>('/habits', { method: 'POST', body: { title } }),
  update: (id: string, patch: { title?: string; archived?: boolean }) =>
    apiRequest<Habit>(`/habits/${enc(id)}`, { method: 'PATCH', body: patch }),
  /** Idempotent tick / untick of one local day. 409 habit_archived. */
  setDone: (id: string, date: IsoDate, done: boolean) =>
    apiRequest<void>(`/habits/${enc(id)}/checks/${enc(date)}`, { method: done ? 'PUT' : 'DELETE' }),
};
