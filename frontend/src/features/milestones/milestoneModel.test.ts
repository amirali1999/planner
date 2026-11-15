import { describe, expect, it } from 'vitest';
import { milestoneState } from './milestoneModel';

describe('milestone state', () => {
  const today = '2026-11-04';
  it('completed wins over dates', () => {
    expect(milestoneState({ completed: true, dueDate: '2026-10-01' }, today)).toBe('completed');
  });
  it('overdue / due today / upcoming / undated relative to the local date', () => {
    expect(milestoneState({ completed: false, dueDate: '2026-11-03' }, today)).toBe('overdue');
    expect(milestoneState({ completed: false, dueDate: '2026-11-04' }, today)).toBe('due_today');
    expect(milestoneState({ completed: false, dueDate: '2026-11-05' }, today)).toBe('upcoming');
    expect(milestoneState({ completed: false, dueDate: null }, today)).toBe('undated');
  });
});
