import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { GoalTree } from './GoalTree';
import type { GoalTreeNode } from './types';

function node(id: string, extra: Partial<GoalTreeNode> = {}, children: GoalTreeNode[] = []): GoalTreeNode {
  return {
    id, title: id, status: 'doing', periodType: 'annual', periodStart: '2026-03-21', periodEnd: '2027-03-20', deadline: null,
    parentGoalId: null, detachedFromParent: false, archivedAt: null, outcomeProgress: null, outcomeSource: 'not_measured',
    keyResultsCount: 0, contributingChildCount: 0, executionProgress: null, activeTaskCount: 0, doneTaskCount: 0, children, ...extra,
  };
}

describe('GoalTree markup', () => {
  const roots = [
    node('a', { outcomeProgress: 50, executionProgress: 75 }, [node('m', { periodType: 'monthly', periodStart: '2026-10-23', periodEnd: '2026-11-21' })]),
    node('s', { periodType: 'monthly', periodStart: '2026-10-23', periodEnd: '2026-11-21' }),
  ];
  const html = renderToStaticMarkup(<GoalTree roots={roots} expanded={new Set(['a'])} onToggle={() => {}} onActivate={() => {}} today="2026-11-09" />);

  it('uses tree roles with levels, positions and expansion state', () => {
    expect(html).toContain('role="tree"');
    expect(html).toContain('role="group"');
    expect(html.match(/role="treeitem"/g)).toHaveLength(3);
    expect(html).toMatch(/data-goal-id="a"[^>]*aria-level="1"[^>]*aria-setsize="2"[^>]*aria-posinset="1"[^>]*aria-expanded="true"/);
    expect(html).toMatch(/data-goal-id="m"[^>]*aria-level="2"/);
    expect(html).not.toMatch(/data-goal-id="s"[^>]*aria-expanded/); // leaf: no aria-expanded
  });

  it('has exactly one tab stop (roving tabindex)', () => {
    expect(html.match(/tabindex="0"/g)).toHaveLength(1);
  });

  it('shows Outcome and Execution separately, never combined', () => {
    expect(html).toContain('نتیجه: ۵۰٪');
    expect(html).toContain('اجرا: ۷۵٪');
    expect(html).toContain('نتیجه: سنجیده نشده');
    expect(html).not.toContain('۶۲٫۵');
  });

  it('keeps measured progress and the chosen status apart; Outcome 100 does not imply done', () => {
    const out = renderToStaticMarkup(
      <GoalTree roots={[node('x', { outcomeProgress: 100, executionProgress: 100 })]} expanded={new Set()} onToggle={() => {}} onActivate={() => {}} today="2026-11-09" />,
    );
    expect(out).toContain('وضعیت: در حال انجام');
    expect(out).toContain('نتیجه: ۱۰۰٪');
    expect(out).not.toContain('انجام‌شده');
  });
});

describe('GoalTree status, deadline and filter context', () => {
  const roots = [
    node('late', { deadline: '2026-11-08' }, [node('hold', { status: 'on_hold', periodType: 'monthly', deadline: '2026-11-09' })]),
    node('done-late', { status: 'done', deadline: '2026-11-01' }),
    node('arch', { archivedAt: '2026-11-01T10:00:00.000Z' }),
  ];
  const html = renderToStaticMarkup(
    <GoalTree
      roots={roots}
      expanded={new Set(['late'])}
      onToggle={() => {}}
      onActivate={() => {}}
      today="2026-11-09"
      contextIds={new Set(['late'])}
      onStatusChanged={() => {}}
    />,
  );

  it('flags overdue / due today only for open goals, with Jalali dates', () => {
    expect(html).toContain('موعد: ۱۸ آبان ۱۴۰۵');
    expect(html).toContain('۱ روز از موعد گذشته');
    expect(html).toContain('موعد امروز');
    expect(html).toContain('موعد: ۱۰ آبان ۱۴۰۵'); // done goal: date shown …
    expect(html.match(/deadline__flag--overdue/g)).toHaveLength(1); // … but not flagged
  });

  it('offers inline status editing only for active, matching goals', () => {
    expect(html).toContain('id="goal-status-hold"');
    expect(html).toContain('id="goal-status-done-late"');
    expect(html).not.toContain('id="goal-status-late"'); // context ancestor
    expect(html).not.toContain('id="goal-status-arch"'); // archived = read-only
    expect(html).toMatch(/<label for="goal-status-hold"[^>]*>وضعیت «hold» \(انتخاب شما\)<\/label>/);
    expect(html).toContain('tree__item--context');
    expect(html).toContain('خارج از فیلتر');
  });
});
