import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { TASK_PRIORITY_LABELS, TASK_PRIORITY_ORDER } from './labels';
import { PriorityPicker } from './PriorityPicker';
import type { Task } from './types';

const task = (priority: Task['priority']): Task => ({
  id: 't', title: 'کار', description: null, status: 'not_started', priority, estimatedMinutes: null, dueDate: null, blockedReason: null,
  goalId: null, focusItemId: null, focusItemGoalId: null, effectiveGoalId: null, effectiveGoalSource: null, warnings: [],
  archivedAt: null, createdAt: '', updatedAt: '', completedAt: null,
});

describe('PriorityPicker', () => {
  it('shows the current priority as a menu button', () => {
    const html = renderToStaticMarkup(<PriorityPicker task={task('high')} />);
    expect(html).toContain('aria-haspopup="menu"');
    expect(html).toContain('prio--high');
    expect(html).toContain('>بالا<');
  });

  it('treats a missing priority as the default «پایین»', () => {
    expect(renderToStaticMarkup(<PriorityPicker task={task(null)} />)).toContain('prio--low');
  });

  it('offers exactly three priorities, low → high', () => {
    expect(TASK_PRIORITY_ORDER).toEqual(['low', 'medium', 'high']);
    expect(TASK_PRIORITY_ORDER.map((p) => TASK_PRIORITY_LABELS[p])).toEqual(['پایین', 'متوسط', 'بالا']);
  });
});
