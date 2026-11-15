import { isoToJalali, weekdayFromSaturday, type IsoDate } from './date/calendar';
import { currentTimeZone } from './date/timeZone';

export const JALALI_MONTHS = [
  'فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور',
  'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند',
] as const;

/** Iranian week order: index = days since Saturday. */
export const WEEKDAYS = ['شنبه', 'یکشنبه', 'دوشنبه', 'سه‌شنبه', 'چهارشنبه', 'پنجشنبه', 'جمعه'] as const;

const PERSIAN_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const ARABIC_DIGITS = '٠١٢٣٤٥٦٧٨٩';

export function toPersianDigits(value: string | number): string {
  return String(value).replace(/\d/g, (d) => PERSIAN_DIGITS[Number(d)]);
}

/** Accepts Persian/Arabic digits and separators typed by the user and returns ASCII. */
export function normalizeDigits(value: string): string {
  return value
    .replace(/[۰-۹]/g, (d) => String(PERSIAN_DIGITS.indexOf(d)))
    .replace(/[٠-٩]/g, (d) => String(ARABIC_DIGITS.indexOf(d)))
    .replace(/[٫,]/g, '.')
    .replace(/[−–]/g, '-')
    .trim();
}

/** e.g. "۱ مهر ۱۴۰۵". Pure calendar math: no Date, no timezone. */
export function formatJalaliDate(iso: IsoDate): string {
  const { jy, jm, jd } = isoToJalali(iso);
  return `${toPersianDigits(jd)} ${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}`;
}

/** e.g. "سه‌شنبه ۱۲ آبان ۱۴۰۵". */
export function formatJalaliDateWithWeekday(iso: IsoDate): string {
  return `${WEEKDAYS[weekdayFromSaturday(iso)]} ${formatJalaliDate(iso)}`;
}

export function formatJalaliRange(start: IsoDate, end: IsoDate): string {
  return `${formatJalaliDate(start)} تا ${formatJalaliDate(end)}`;
}

const NUMBER_FORMAT = new Intl.NumberFormat('fa-IR', { maximumFractionDigits: 6, useGrouping: true });
const PERCENT_NUMBER = new Intl.NumberFormat('fa-IR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });

export function formatNumber(value: number): string {
  return NUMBER_FORMAT.format(value);
}

/** Score in 0..100 (already rounded by the backend) → "۵۰٪". */
export function formatPercent(value: number): string {
  return `${PERCENT_NUMBER.format(value)}٪`;
}

/** UTC timestamp → Jalali date+time in the user's configured timezone (browser zone by default). */
export function formatTimestamp(utc: string, timeZone: string = currentTimeZone()): string {
  return new Intl.DateTimeFormat('fa-IR-u-ca-persian', {
    timeZone,
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(utc));
}

const DECIMAL_RE = /^-?\d+(\.\d+)?$/;

export type DecimalParse = { ok: true; value: number } | { ok: false; reason: 'empty' | 'invalid' | 'precision' };

/**
 * Parses a user-typed decimal for the API. Limits match the backend (≤6 fractional, ≤15 integer digits)
 * and keep ≤15 significant digits so the JSON number is exactly the typed decimal.
 */
export function parseDecimal(raw: string): DecimalParse {
  const text = normalizeDigits(raw);
  if (text === '') return { ok: false, reason: 'empty' };
  if (!DECIMAL_RE.test(text)) return { ok: false, reason: 'invalid' };
  const [intPart, fracPart = ''] = text.replace('-', '').split('.');
  const significant = (intPart.replace(/^0+/, '') + fracPart).replace(/^0+/, '').replace(/0+$/, '');
  if (fracPart.length > 6 || intPart.replace(/^0+/, '').length > 15 || significant.length > 15) {
    return { ok: false, reason: 'precision' };
  }
  return { ok: true, value: Number(text) };
}
