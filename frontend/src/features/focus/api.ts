import { apiRequest } from '../../shared/api/client';
import type { DeletionResult } from '../archive/types';
import type { CreateFocusItemRequest, FocusFilters, FocusItem, FocusItemNote, UpdateFocusItemRequest } from './types';

const enc = encodeURIComponent;

export const focusApi = {
  list: (filters: FocusFilters, signal?: AbortSignal) => {
    const params = new URLSearchParams({ archived: filters.archived });
    if (filters.goalId) params.set('goalId', filters.goalId);
    if (filters.kind) params.set('kind', filters.kind);
    if (filters.status) params.set('status', filters.status);
    return apiRequest<FocusItem[]>(`/focus-items?${params.toString()}`, { signal });
  },
  get: (id: string, signal?: AbortSignal) => apiRequest<FocusItem>(`/focus-items/${enc(id)}`, { signal }),
  create: (body: CreateFocusItemRequest) => apiRequest<FocusItem>('/focus-items', { method: 'POST', body }),
  /** New order of ALL active focus items of one goal (top first). Items can't move to another goal this way. */
  reorder: (goalId: string, focusItemIds: string[]) =>
    apiRequest<FocusItem[]>(`/goals/${enc(goalId)}/focus-items/order`, { method: 'PUT', body: { focusItemIds } }),
  update: (id: string, body: UpdateFocusItemRequest) => apiRequest<FocusItem>(`/focus-items/${enc(id)}`, { method: 'PATCH', body }),
  /** Permanent; only an archived item. Its tasks are deleted too. */
  remove: (id: string) => apiRequest<DeletionResult>(`/focus-items/${enc(id)}`, { method: 'DELETE' }),
};

/** Notes of a focus item (plain text, not tasks). Archived item → 409 focus_item_archived on writes. */
export const focusNotesApi = {
  list: (focusItemId: string, signal?: AbortSignal) =>
    apiRequest<FocusItemNote[]>(`/focus-items/${enc(focusItemId)}/notes`, { signal }),
  add: (focusItemId: string, body: string) =>
    apiRequest<FocusItemNote>(`/focus-items/${enc(focusItemId)}/notes`, { method: 'POST', body: { body } }),
  update: (noteId: string, body: string) =>
    apiRequest<FocusItemNote>(`/focus-item-notes/${enc(noteId)}`, { method: 'PUT', body: { body } }),
  remove: (noteId: string) => apiRequest<void>(`/focus-item-notes/${enc(noteId)}`, { method: 'DELETE' }),
  /** Only items that have notes are listed. */
  counts: (signal?: AbortSignal) => apiRequest<{ focusItemId: string; count: number }[]>('/focus-item-notes/counts', { signal }),
};
