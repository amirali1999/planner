import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it, vi } from 'vitest';
import { FocusTimer } from './FocusTimer';

describe('FocusTimer activity-log integration', () => {
  it('restores a persisted Pomodoro with its activity title and stop action', () => {
    const html = renderToStaticMarkup(<FocusTimer activity={{
      session: { title: 'نوشتن گزارش', startedAt: '2100-01-01T00:00:00Z', plannedMinutes: 25 },
      blocked: false,
      onStart: vi.fn(async () => undefined),
      onStop: vi.fn(async () => undefined),
      onCancel: vi.fn(async () => undefined),
    }} />);

    expect(html).toContain('نوشتن گزارش');
    expect(html).toContain('Stop and log Pomodoro');
    expect(html).toContain('Cancel Pomodoro without logging');
    expect(html).toContain('25:00');
  });
});
