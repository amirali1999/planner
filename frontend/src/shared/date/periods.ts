import { addDays, isoToJalali, jalaliMonthLength, jalaliToIso, weekdayFromSaturday, type IsoDate } from './calendar';
import { JALALI_MONTHS, formatJalaliDate, formatJalaliRange, toPersianDigits } from '../format';

export type PeriodType = 'annual' | 'quarterly' | 'monthly' | 'weekly';

export interface DateRange {
  start: IsoDate;
  end: IsoDate;
}

/**
 * Inclusive Jalali period that contains {@code reference}:
 * weekly = Saturday..Friday (may span two Jalali months), monthly = Jalali month,
 * quarterly = Farvardin–Khordad / Tir–Shahrivar / Mehr–Azar / Dey–Esfand, annual = Jalali year.
 */
export function periodRange(type: PeriodType, reference: IsoDate): DateRange {
  if (type === 'weekly') {
    const start = addDays(reference, -weekdayFromSaturday(reference));
    return { start, end: addDays(start, 6) };
  }
  const { jy, jm } = isoToJalali(reference);
  const [firstMonth, lastMonth] =
    type === 'monthly' ? [jm, jm] : type === 'quarterly' ? [jm - ((jm - 1) % 3), jm - ((jm - 1) % 3) + 2] : [1, 12];
  return {
    start: jalaliToIso(jy, firstMonth, 1),
    end: jalaliToIso(jy, lastMonth, jalaliMonthLength(jy, lastMonth)),
  };
}

/** 1..4 */
export function quarterOfMonth(jm: number): number {
  return Math.floor((jm - 1) / 3) + 1;
}

/** Standard period from a picker selection: index is the quarter (1..4) or month (1..12); ignored for annual. */
export function periodForSelection(type: Exclude<PeriodType, 'weekly'>, jy: number, index: number): DateRange {
  const firstMonth = type === 'annual' ? 1 : type === 'quarterly' ? (index - 1) * 3 + 1 : index;
  return periodRange(type, jalaliToIso(jy, firstMonth, 1));
}

/** True when start..end is exactly the standard Jalali period of this type (not a custom range). */
export function isStandardPeriod(type: PeriodType, start: IsoDate, end: IsoDate): boolean {
  const range = periodRange(type, start);
  return range.start === start && range.end === end;
}

const QUARTER_NAMES = ['اول', 'دوم', 'سوم', 'چهارم'];

export function quarterLabel(q: number): string {
  const first = (q - 1) * 3;
  return `فصل ${QUARTER_NAMES[q - 1]} (${JALALI_MONTHS[first]} تا ${JALALI_MONTHS[first + 2]})`;
}

/** Human label: standard periods by name, custom ranges as "x تا y". */
export function describePeriod(type: PeriodType, start: IsoDate, end: IsoDate): string {
  if (!isStandardPeriod(type, start, end)) return formatJalaliRange(start, end);
  const { jy, jm } = isoToJalali(start);
  switch (type) {
    case 'annual':
      return `سال ${toPersianDigits(jy)}`;
    case 'quarterly':
      return `${quarterLabel(quarterOfMonth(jm))} ${toPersianDigits(jy)}`;
    case 'monthly':
      return `${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}`;
    case 'weekly':
      return `هفته ${formatJalaliDate(start)} تا ${formatJalaliDate(end)}`;
  }
}
