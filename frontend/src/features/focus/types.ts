import type { Note } from '../../shared/api/notes';

export type FocusKind = 'project' | 'book' | 'course' | 'area' | 'other';
export type FocusStatus = 'not_started' | 'doing' | 'done';
export type FocusArchiveFilter = 'false' | 'true' | 'all';

/** Project / book / course / area. The goal link is optional and single (many-to-many is not part of MVP v0). */
export interface FocusItem {
  id: string;
  title: string;
  kind: FocusKind;
  description: string | null;
  goalId: string | null;
  status: FocusStatus;
  /** Manual position inside its goal (1 = top); null without goal. */
  position: number | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFocusItemRequest {
  title: string;
  kind: FocusKind;
  description?: string;
  goalId?: string;
  status: FocusStatus;
}

/** PATCH: omitted = unchanged, null = clear; `archived` archives/restores. */
export interface UpdateFocusItemRequest {
  title?: string;
  kind?: FocusKind;
  description?: string | null;
  goalId?: string | null;
  status?: FocusStatus;
  archived?: boolean;
}

export interface FocusFilters {
  archived: FocusArchiveFilter;
  goalId?: string;
  kind?: FocusKind;
  status?: FocusStatus;
}

/** GET /focus-items/{id}/notes — a free-text note of a focus item (not a task). */
export interface FocusItemNote extends Note {
  focusItemId: string;
}
