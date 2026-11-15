/** A captured note awaiting triage. Before processing it has no decision and no linked Goal/Task/Focus Item. */
export interface InboxItem {
  id: string;
  rawText: string;
  status: 'unprocessed' | 'processed';
  decision: string | null;
  createdEntityType: string | null;
  createdEntityId: string | null;
  processedAt: string | null;
  capturedAt: string;
  createdAt: string;
  updatedAt: string;
}

/** Inbox views: the queue, converted items, and the filed (never erased) reference/someday/discard items. */
export type InboxView = 'unprocessed' | 'converted' | 'reference' | 'someday' | 'discard' | 'all';

export type InboxDecision = 'task' | 'goal' | 'focus_item' | 'reference' | 'someday' | 'discard';

export interface ProcessResponse {
  inboxItemId: string;
  status: 'processed';
  decision: InboxDecision;
  createdEntityType: 'task' | 'goal' | 'focus_item' | null;
  createdEntityId: string | null;
  processedAt: string;
}
