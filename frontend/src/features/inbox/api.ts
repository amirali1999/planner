import { apiRequest } from '../../shared/api/client';
import type { CreateFocusItemRequest } from '../focus/types';
import type { CreateGoalRequest } from '../goals/types';
import type { CreateTaskRequest } from '../tasks/types';
import type { InboxDecision, InboxItem, InboxView, ProcessResponse } from './types';

const VIEW_QUERY: Record<InboxView, string> = {
  unprocessed: 'status=unprocessed',
  converted: 'status=processed&decision=task,goal,focus_item',
  reference: 'status=processed&decision=reference',
  someday: 'status=processed&decision=someday',
  discard: 'status=processed&decision=discard',
  all: 'status=all',
};

export type ProcessBody =
  | { decision: 'task'; task: CreateTaskRequest }
  | { decision: 'goal'; goal: CreateGoalRequest }
  | { decision: 'focus_item'; focusItem: CreateFocusItemRequest }
  | { decision: Exclude<InboxDecision, 'task' | 'goal' | 'focus_item'> };

export const inboxApi = {
  list: (view: InboxView, signal?: AbortSignal) => apiRequest<InboxItem[]>(`/inbox-items?${VIEW_QUERY[view]}`, { signal }),
  /** One transaction: creates the entity (if any) and marks the item processed. 409 if already processed. */
  process: (id: string, body: ProcessBody) =>
    apiRequest<ProcessResponse>(`/inbox-items/${encodeURIComponent(id)}/process`, { method: 'POST', body }),
  /** Raw text only — nothing else is sent or inferred. */
  capture: (rawText: string) => apiRequest<InboxItem>('/inbox-items', { method: 'POST', body: { rawText } }),
};
