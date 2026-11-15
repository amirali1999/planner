/**
 * Date-only calendar math with NO JavaScript Date objects and NO timezones.
 *
 * Every date-only value in the app is a Gregorian ISO string `YYYY-MM-DD` (the API/DB format).
 * Conversions go through a Julian Day Number (integer) using the jalaali-js library's integer API
 * (g2d/d2g/j2d/d2j). Its Date-based helpers are deliberately not used: they depend on the runtime timezone.
 */
import {
  MAX_JALAALI_YEAR,
  MIN_JALAALI_YEAR,
  d2g,
  d2j,
  g2d,
  isLeapJalaaliYear,
  isValidJalaaliDate,
  j2d,
  jalaaliMonthLength,
} from 'jalaali-js';
import { currentTimeZone, dateInZone } from './timeZone';

export type IsoDate = string;

export interface JalaliDate {
  jy: number;
  jm: number;
  jd: number;
}

const mod = (a: number, b: number): number => a - Math.trunc(a / b) * b;

/** Supported Jalali years for conversion (library limits). */
export const MIN_JALALI_YEAR = MIN_JALAALI_YEAR;
export const MAX_JALALI_YEAR = MAX_JALAALI_YEAR;


export function isLeapJalaliYear(jy: number): boolean {
  return isLeapJalaaliYear(jy);
}

export function jalaliMonthLength(jy: number, jm: number): number {
  return jalaaliMonthLength(jy, jm);
}

export function isValidJalali(jy: number, jm: number, jd: number): boolean {
  return (
    Number.isInteger(jy) &&
    Number.isInteger(jm) &&
    Number.isInteger(jd) &&
    jy >= MIN_JALALI_YEAR &&
    jy <= MAX_JALALI_YEAR &&
    isValidJalaaliDate(jy, jm, jd)
  );
}

// ---- ISO date-only ----------------------------------------------------------------------------

const ISO_RE = /^(\d{4})-(\d{2})-(\d{2})$/;
const pad = (n: number, width: number): string => String(n).padStart(width, '0');

/** Strictly parses `YYYY-MM-DD`; returns null for malformed or impossible dates (e.g. 2026-02-30). */
export function parseIsoDate(value: string): { y: number; m: number; d: number } | null {
  const match = ISO_RE.exec(value);
  if (!match) return null;
  const y = Number(match[1]);
  const m = Number(match[2]);
  const d = Number(match[3]);
  if (y < 1 || m < 1 || m > 12 || d < 1) return null;
  const back = d2g(g2d(y, m, d));
  return back.gy === y && back.gm === m && back.gd === d ? { y, m, d } : null;
}

export function isIsoDate(value: string): boolean {
  return parseIsoDate(value) !== null;
}

function requireIso(value: IsoDate): { y: number; m: number; d: number } {
  const parsed = parseIsoDate(value);
  if (!parsed) throw new RangeError(`Not an ISO date: ${value}`);
  return parsed;
}

export function isoToDayNumber(value: IsoDate): number {
  const { y, m, d } = requireIso(value);
  return g2d(y, m, d);
}

export function dayNumberToIso(jdn: number): IsoDate {
  const { gy, gm, gd } = d2g(jdn);
  return `${pad(gy, 4)}-${pad(gm, 2)}-${pad(gd, 2)}`;
}

export function addDays(value: IsoDate, days: number): IsoDate {
  return dayNumberToIso(isoToDayNumber(value) + days);
}

/** Days since the most recent Saturday: Saturday = 0 … Friday = 6 (Iranian week). */
export function weekdayFromSaturday(value: IsoDate): number {
  // JDN 0 is a Monday; (jdn + 2) mod 7 maps Saturday to 0.
  return mod(mod(isoToDayNumber(value) + 2, 7) + 7, 7);
}

export function isoToJalali(value: IsoDate): JalaliDate {
  return d2j(isoToDayNumber(value));
}

export function jalaliToIso(jy: number, jm: number, jd: number): IsoDate {
  if (!isValidJalali(jy, jm, jd)) {
    throw new RangeError(`Invalid Jalali date: ${jy}/${jm}/${jd}`);
  }
  return dayNumberToIso(j2d(jy, jm, jd));
}

/**
 * The user's local calendar date as ISO, read from local Y/M/D fields.
 * Never `date.toISOString().slice(0, 10)`: that is the UTC date and is off by one around midnight
 * in non-UTC zones (e.g. 00:30 in Tehran is still "yesterday" in UTC).
 */
export function localDateToIso(date: Date): IsoDate {
  return `${pad(date.getFullYear(), 4)}-${pad(date.getMonth() + 1, 2)}-${pad(date.getDate(), 2)}`;
}

/** Today's date in the user's configured timezone (browser zone by default). */
export function todayIso(): IsoDate {
  return dateInZone(new Date(), currentTimeZone());
}
