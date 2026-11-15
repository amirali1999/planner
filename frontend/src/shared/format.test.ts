import { describe, expect, it } from 'vitest';
import { formatJalaliDate, formatJalaliDateWithWeekday, formatPercent, normalizeDigits, parseDecimal, toPersianDigits } from './format';

describe('format', () => {
  it('formats ISO date-only as Jalali without shifting', () => {
    expect(formatJalaliDate('2026-09-23')).toBe('۱ مهر ۱۴۰۵');
    expect(formatJalaliDate('2026-03-21')).toBe('۱ فروردین ۱۴۰۵');
  });

  it('adds the Persian weekday without timezone math', () => {
    expect(formatJalaliDateWithWeekday('2026-11-03')).toBe('سه‌شنبه ۱۲ آبان ۱۴۰۵');
    expect(formatJalaliDateWithWeekday('2026-10-31')).toBe('شنبه ۹ آبان ۱۴۰۵');
    expect(formatJalaliDateWithWeekday('2026-11-06')).toBe('جمعه ۱۵ آبان ۱۴۰۵');
  });

  it('normalizes Persian digits and decimal separator', () => {
    expect(normalizeDigits('۱۲٫۵')).toBe('12.5');
    expect(normalizeDigits(' −۳ ')).toBe('-3');
    expect(toPersianDigits(1405)).toBe('۱۴۰۵');
  });

  it('parses decimals within backend limits', () => {
    expect(parseDecimal('۸۰')).toEqual({ ok: true, value: 80 });
    expect(parseDecimal('-0.5')).toEqual({ ok: true, value: -0.5 });
    expect(parseDecimal('0.333333')).toEqual({ ok: true, value: 0.333333 });
    expect(parseDecimal('')).toEqual({ ok: false, reason: 'empty' });
    expect(parseDecimal('abc')).toEqual({ ok: false, reason: 'invalid' });
    expect(parseDecimal('1e5')).toEqual({ ok: false, reason: 'invalid' });
    expect(parseDecimal('0.1234567')).toEqual({ ok: false, reason: 'precision' });
    expect(parseDecimal('1234567890123456')).toEqual({ ok: false, reason: 'precision' });
  });

  it('formats percent with Persian digits', () => {
    expect(formatPercent(50)).toBe('۵۰٪');
    expect(formatPercent(66.67)).toBe('۶۶٫۶۷٪');
  });
});
