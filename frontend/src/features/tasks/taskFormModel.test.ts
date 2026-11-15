import { describe, expect, it } from 'vitest';
import { EMPTY_TASK_FORM, formFromTask, parseTaskForm, toCreateRequest, toUpdateRequest } from './taskFormModel';
import type { Task } from './types';

const task: Task = {
  id: 't1',
  title: 'مطالعه',
  description: null,
  status: 'doing',
  priority: 'low',
  estimatedMinutes: 30,
  dueDate: '2026-11-10',
  blockedReason: 'منتظر داده',
  goalId: 'g1',
  focusItemId: null,
  focusItemGoalId: null,
  effectiveGoalId: 'g1',
  effectiveGoalSource: 'direct',
  warnings: [],
  archivedAt: null,
  createdAt: '2026-11-03T12:00:00Z',
  updatedAt: '2026-11-03T12:00:00Z', completedAt: null,
};

describe('task form', () => {
  it('requires a title and validates minutes', () => {
    const result = parseTaskForm({ ...EMPTY_TASK_FORM, estimatedMinutes: '-5' });
    expect(result).toEqual({ ok: false, errors: { title: 'required', estimatedMinutes: 'invalid_minutes' } });
  });

  it('keeps 0 minutes distinct from empty and passes the picked ISO due date through', () => {
    const zero = parseTaskForm({ ...EMPTY_TASK_FORM, title: 't', estimatedMinutes: '۰', dueDate: '2026-11-10' });
    expect(zero.ok && zero.value.estimatedMinutes).toBe(0);
    expect(zero.ok && zero.value.dueDate).toBe('2026-11-10');
    const empty = parseTaskForm({ ...EMPTY_TASK_FORM, title: 't' });
    expect(empty.ok && empty.value.estimatedMinutes).toBeNull();
    expect(empty.ok && toCreateRequest(empty.value)).toEqual({ title: 't', status: 'not_started', priority: 'low' }); // default priority
  });

  it('round-trips an existing task without changes → empty PATCH', () => {
    const parsed = parseTaskForm(formFromTask(task));
    expect(parsed.ok && toUpdateRequest(task, parsed.value)).toEqual({});
  });

  it('focus item link is sent on create and set/cleared on update', () => {
    const created = parseTaskForm({ ...EMPTY_TASK_FORM, title: 't', focusItemId: 'f1' });
    expect(created.ok && toCreateRequest(created.value)).toEqual({ title: 't', status: 'not_started', priority: 'low', focusItemId: 'f1' });
    const linked = parseTaskForm({ ...formFromTask(task), focusItemId: 'f2' });
    expect(linked.ok && toUpdateRequest(task, linked.value)).toEqual({ focusItemId: 'f2' });
    const unlinked = parseTaskForm(formFromTask({ ...task, focusItemId: 'f2' }));
    expect(unlinked.ok && toUpdateRequest({ ...task, focusItemId: 'f2' }, { ...unlinked.value, focusItemId: null })).toEqual({
      focusItemId: null,
    });
  });

  it('PATCH contains only changes, with null for cleared fields', () => {
    const form = { ...formFromTask(task), status: 'done' as const, goalId: '', blockedReason: '  ', estimatedMinutes: '' };
    const parsed = parseTaskForm(form);
    expect(parsed.ok && toUpdateRequest(task, parsed.value)).toEqual({
      status: 'done',
      goalId: null,
      blockedReason: null,
      estimatedMinutes: null,
    });
  });
});
