import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { JalaliCalendar } from './JalaliCalendar';
import { PeriodPicker } from './PeriodPicker';

describe('JalaliCalendar markup', () => {
  const html = renderToStaticMarkup(<JalaliCalendar label="تقویم" selected="2026-11-04" onSelect={() => {}} />);

  it('has Saturday-first weekday headers with Friday marked as weekend', () => {
    const headers = [...html.matchAll(/<th[^>]*abbr="([^"]+)"[^>]*>/g)].map((m) => m[1]);
    expect(headers).toEqual(['شنبه', 'یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه']);
    expect(html).toMatch(/<th[^>]*abbr="جمعه"[^>]*class="calendar__weekend"|<th[^>]*class="calendar__weekend"[^>]*abbr="جمعه"/);
  });

  it('renders Aban 1405 with 30 day buttons, one roving tab stop on the selected day', () => {
    expect(html).toContain('aria-label="آبان ۱۴۰۵"');
    expect(html.match(/data-iso="/g)).toHaveLength(30);
    expect(html.match(/tabindex="0"/g)).toHaveLength(1);
    expect(html).toMatch(/data-iso="2026-11-04"[^>]*tabindex="0"/);
    expect(html).toContain('aria-label="چهارشنبه ۱۳ آبان ۱۴۰۵"');
  });
});

describe('PeriodPicker', () => {
  it('weekly standard mode lists the seven Saturday–Friday dates of a boundary week', () => {
    const html = renderToStaticMarkup(<PeriodPicker periodType="weekly" start="2026-11-21" end="2026-11-27" onChange={() => {}} />);
    const listed = [...html.matchAll(/\((2026-\d\d-\d\d)\)<\/span><\/li>/g)].map((m) => m[1]);
    expect(listed).toEqual(['2026-11-21', '2026-11-22', '2026-11-23', '2026-11-24', '2026-11-25', '2026-11-26', '2026-11-27']);
    expect(html).toContain('هفته ۳۰ آبان ۱۴۰۵ تا ۶ آذر ۱۴۰۵');
  });

  it('a non-standard range opens in custom mode so existing goals stay editable', () => {
    const html = renderToStaticMarkup(<PeriodPicker periodType="monthly" start="2026-10-25" end="2026-11-21" onChange={() => {}} />);
    expect(html).toMatch(/<input type="radio"[^>]*checked=""[^>]*\/> بازه دلخواه/);
    expect(html).toContain('۳ آبان ۱۴۰۵ تا ۳۰ آبان ۱۴۰۵');
  });

  it('quarterly standard mode shows year and quarter selects', () => {
    const html = renderToStaticMarkup(<PeriodPicker periodType="quarterly" start="2026-09-23" end="2026-12-21" onChange={() => {}} />);
    expect(html).toContain('فصل سوم (مهر تا آذر) ۱۴۰۵');
    expect(html).toMatch(/<option value="3" selected="">فصل سوم/);
  });
});
