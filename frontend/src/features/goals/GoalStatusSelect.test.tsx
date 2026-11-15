import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { DeadlineBadge } from './DeadlineBadge';
import { GoalStatusSelect } from './GoalStatusSelect';
import { STATUS_LABELS, STATUS_ORDER } from './labels';

describe('GoalStatusSelect', () => {
  it.each(STATUS_ORDER)('renders all five statuses with %s selected', (status) => {
    const html = renderToStaticMarkup(<GoalStatusSelect goalId="g" goalTitle="هدف" status={status} />);
    expect(html.match(/<option/g)).toHaveLength(5);
    expect(html).toContain(`<option value="${status}" selected="">${STATUS_LABELS[status]}</option>`);
    expect(html).toContain('وضعیت «هدف» (انتخاب شما)');
    expect(html).not.toContain('disabled');
  });

  it('is read-only when disabled (archived goal)', () => {
    expect(renderToStaticMarkup(<GoalStatusSelect goalId="g" goalTitle="x" status="doing" disabled />)).toContain('disabled=""');
  });
});

describe('DeadlineBadge', () => {
  const render = (deadline: string | null, status: Parameters<typeof DeadlineBadge>[0]['status']) =>
    renderToStaticMarkup(<DeadlineBadge deadline={deadline} status={status} today="2026-11-09" />);

  it('renders nothing without a deadline', () => {
    expect(render(null, 'doing')).toBe('');
  });

  it('shows the Jalali date and the date-only flag at the day boundary', () => {
    expect(render('2026-11-08', 'doing')).toContain('۱ روز از موعد گذشته');
    expect(render('2026-11-09', 'doing')).toContain('موعد امروز');
    expect(render('2026-11-09', 'doing')).not.toContain('گذشته');
    expect(render('2026-11-10', 'on_hold')).toContain('۱ روز تا موعد');
    expect(render('2026-12-31', 'doing')).toBe('<span class="deadline"><span>موعد: ۱۰ دی ۱۴۰۵</span></span>');
  });

  it('never flags done or dropped goals', () => {
    for (const s of ['done', 'dropped'] as const) {
      const html = render('2026-11-01', s);
      expect(html).toContain('موعد: ۱۰ آبان ۱۴۰۵');
      expect(html).not.toContain('deadline__flag');
    }
  });
});
