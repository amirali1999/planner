import type { FieldErrors } from '../../shared/ui/fieldErrors';
import type { CreateFocusItemRequest, FocusItem, FocusKind, FocusStatus, UpdateFocusItemRequest } from './types';

export interface FocusFormValues {
  title: string;
  kind: FocusKind;
  description: string;
  goalId: string;
  status: FocusStatus;
}

export const EMPTY_FOCUS_FORM: FocusFormValues = { title: '', kind: 'project', description: '', goalId: '', status: 'not_started' };

export function formFromItem(item: FocusItem): FocusFormValues {
  return { title: item.title, kind: item.kind, description: item.description ?? '', goalId: item.goalId ?? '', status: item.status };
}

export function validateFocusForm(v: FocusFormValues): FieldErrors {
  return v.title.trim() ? {} : { title: 'required' };
}

/** Optional fields are omitted when empty; no goal is ever created implicitly. */
export function toCreateRequest(v: FocusFormValues): CreateFocusItemRequest {
  return {
    title: v.title.trim(),
    kind: v.kind,
    status: v.status,
    ...(v.description.trim() && { description: v.description.trim() }),
    ...(v.goalId && { goalId: v.goalId }),
  };
}

/** Only changed fields; a cleared description or goal link is sent as null. */
export function toUpdateRequest(item: FocusItem, v: FocusFormValues): UpdateFocusItemRequest {
  const patch: UpdateFocusItemRequest = {};
  if (v.title.trim() !== item.title) patch.title = v.title.trim();
  if (v.kind !== item.kind) patch.kind = v.kind;
  if ((v.description.trim() || null) !== item.description) patch.description = v.description.trim() || null;
  if ((v.goalId || null) !== item.goalId) patch.goalId = v.goalId || null;
  if (v.status !== item.status) patch.status = v.status;
  return patch;
}
