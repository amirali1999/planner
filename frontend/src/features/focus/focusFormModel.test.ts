import { describe, expect, it } from 'vitest';
import { EMPTY_FOCUS_FORM, formFromItem, toCreateRequest, toUpdateRequest, validateFocusForm } from './focusFormModel';
import type { FocusItem } from './types';

const item: FocusItem = {
  id: 'f1', title: 'کتاب', kind: 'book', description: 'd', goalId: 'g1', status: 'doing', position: 1,
  archivedAt: null, createdAt: '', updatedAt: '',
};

describe('focus form model', () => {
  it('standalone create omits goalId (no goal is ever implied)', () => {
    expect(toCreateRequest({ ...EMPTY_FOCUS_FORM, title: ' پروژه ' })).toEqual({ title: 'پروژه', kind: 'project', status: 'not_started' });
    expect(toCreateRequest({ ...EMPTY_FOCUS_FORM, title: 'x', goalId: 'g1' }).goalId).toBe('g1');
  });
  it('requires a title', () => {
    expect(validateFocusForm(EMPTY_FOCUS_FORM)).toEqual({ title: 'required' });
  });
  it('round trip is an empty patch; unlinking sends null', () => {
    expect(toUpdateRequest(item, formFromItem(item))).toEqual({});
    expect(toUpdateRequest(item, { ...formFromItem(item), goalId: '', description: '', kind: 'course' })).toEqual({
      goalId: null,
      description: null,
      kind: 'course',
    });
  });
});
