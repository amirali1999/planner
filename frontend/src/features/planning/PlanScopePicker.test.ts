import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { PlanScopePicker, planScopeOf } from './PlanScopePicker';

describe('focus-page planning scope', () => {
  it('defaults to no plan and recognises today, this week and next week', () => {
    expect(planScopeOf(false, false, false)).toBe('none');
    expect(planScopeOf(true, false, false)).toBe('today');
    expect(planScopeOf(false, true, false)).toBe('this_week');
    expect(planScopeOf(false, false, true)).toBe('next_week');
  });

  it('shows the nearest active scope if legacy data contains more than one', () => {
    expect(planScopeOf(true, true, true)).toBe('today');
    expect(planScopeOf(false, true, true)).toBe('this_week');
  });

  it('keeps the plan visible after completion and hides the empty trigger on completed tasks', () => {
    const task = { taskId: 'task', taskTitle: 'مطالعه', taskDone: true, plannedToday: false, plannedNextWeek: false };
    const scheduled = renderToStaticMarkup(createElement(PlanScopePicker, { ...task, plannedThisWeek: true }));
    const unscheduled = renderToStaticMarkup(createElement(PlanScopePicker, { ...task, plannedThisWeek: false }));

    expect(scheduled).toContain('این هفته');
    expect(scheduled).not.toContain('hidden=""');
    expect(unscheduled).toContain('hidden=""');
  });
});
