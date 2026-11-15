import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { ProgressPair } from './ProgressPair';
import type { ProgressValues } from './types';

const render = (p: ProgressValues) => renderToStaticMarkup(<ProgressPair progress={p} />);

describe('ProgressPair', () => {
  it('shows backend Outcome and Execution as two separate numbers, never blended', () => {
    const html = render({
      outcomeProgress: 50,
      outcomeSource: 'own_key_results',
      keyResultsCount: 1,
      contributingChildCount: 0,
      measuredAt: '2026-11-03T12:00:00Z',
      executionProgress: 75,
      activeTaskCount: 4,
      doneTaskCount: 3,
    });
    expect(html).toContain('۵۰٪');
    expect(html).toContain('۷۵٪');
    expect(html).toContain('۳ از ۴ کار انجام شده');
    expect(html).not.toContain('۶۲٫۵');
    expect(html).not.toContain('62.5');
    expect(html.match(/<meter/g)).toHaveLength(2);
  });

  it('renders missing measurements as «سنجیده نشده», not 0%', () => {
    const html = render({
      outcomeProgress: null,
      outcomeSource: 'not_measured',
      keyResultsCount: 0,
      contributingChildCount: 0,
      measuredAt: null,
      executionProgress: null,
      activeTaskCount: 0,
      doneTaskCount: 0,
    });
    expect(html.match(/سنجیده نشده/g)).toHaveLength(2);
    expect(html).not.toContain('۰٪');
    expect(html).not.toContain('<meter');
  });

  it('keeps a measured 0% (baseline-only KR) distinct from not measured', () => {
    const html = render({
      outcomeProgress: 0,
      outcomeSource: 'own_key_results',
      keyResultsCount: 1,
      contributingChildCount: 0,
      measuredAt: null,
      executionProgress: null,
      activeTaskCount: 0,
      doneTaskCount: 0,
    });
    expect(html).toContain('۰٪');
    expect(html).toContain('فقط مقدار پایه ثبت شده');
    expect(html.match(/سنجیده نشده/g)).toHaveLength(1);
  });

  it('labels a child rollup as a plain mean of sub-goals, still separate from Execution', () => {
    const html = render({
      outcomeProgress: 60,
      outcomeSource: 'child_rollup',
      keyResultsCount: 0,
      contributingChildCount: 2,
      measuredAt: null,
      executionProgress: 50,
      activeTaskCount: 2,
      doneTaskCount: 1,
    });
    expect(html).toContain('۶۰٪');
    expect(html).toContain('میانگین ساده پیشرفت نتیجه ۲ زیرهدف فعال');
    expect(html).toContain('۵۰٪');
    expect(html).not.toContain('۵۵');
    expect(html.match(/<meter/g)).toHaveLength(2);
  });
});
