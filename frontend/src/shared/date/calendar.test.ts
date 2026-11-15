import { describe, expect, it } from 'vitest';
import {
  addDays,
  dayNumberToIso,
  isIsoDate,
  isLeapJalaliYear,
  isoToDayNumber,
  isoToJalali,
  isValidJalali,
  jalaliMonthLength,
  jalaliToIso,
  localDateToIso,
  weekdayFromSaturday,
} from './calendar';
import { periodRange } from './periods';

describe('known Jalali ↔ Gregorian pairs', () => {
  const pairs: Array<[string, [number, number, number]]> = [
    ['2020-03-20', [1399, 1, 1]],
    ['2021-03-21', [1400, 1, 1]],
    ['2024-03-20', [1403, 1, 1]],
    ['2025-03-20', [1403, 12, 30]], // 1403 is leap: Esfand has 30 days
    ['2025-03-21', [1404, 1, 1]],
    ['2026-03-21', [1405, 1, 1]],
    ['2026-09-23', [1405, 7, 1]], // docs example quarter start (1 Mehr)
    ['2026-12-21', [1405, 9, 30]], // docs example quarter end (30 Azar)
    ['2026-11-03', [1405, 8, 12]],
    ['1979-02-11', [1357, 11, 22]],
  ];
  it.each(pairs)('%s = %j', (iso, [jy, jm, jd]) => {
    expect(isoToJalali(iso)).toEqual({ jy, jm, jd });
    expect(jalaliToIso(jy, jm, jd)).toBe(iso);
  });
});

describe('date-only round trip', () => {
  it('every day 1990-01-01..2040-12-31 survives ISO → Jalali → ISO and day-number round trips', () => {
    const first = isoToDayNumber('1990-01-01');
    const last = isoToDayNumber('2040-12-31');
    let previous = isoToJalali(dayNumberToIso(first - 1));
    for (let jdn = first; jdn <= last; jdn += 1) {
      const iso = dayNumberToIso(jdn);
      expect(isoToDayNumber(iso)).toBe(jdn);
      const j = isoToJalali(iso);
      expect(isValidJalali(j.jy, j.jm, j.jd)).toBe(true);
      expect(jalaliToIso(j.jy, j.jm, j.jd)).toBe(iso);
      // consecutive days are consecutive Jalali days (no skips or repeats)
      const nextOfPrevious =
        previous.jd < jalaliMonthLength(previous.jy, previous.jm)
          ? { ...previous, jd: previous.jd + 1 }
          : previous.jm < 12
            ? { jy: previous.jy, jm: previous.jm + 1, jd: 1 }
            : { jy: previous.jy + 1, jm: 1, jd: 1 };
      expect(j).toEqual(nextOfPrevious);
      previous = j;
    }
  });
});

describe('Jalali validation', () => {
  it('knows month lengths and leap Esfand', () => {
    expect(jalaliMonthLength(1405, 1)).toBe(31);
    expect(jalaliMonthLength(1405, 7)).toBe(30);
    expect(isLeapJalaliYear(1403)).toBe(true);
    expect(isLeapJalaliYear(1404)).toBe(false);
    expect(isValidJalali(1403, 12, 30)).toBe(true);
    expect(isValidJalali(1404, 12, 30)).toBe(false);
    expect(isValidJalali(1405, 7, 31)).toBe(false);
    expect(isValidJalali(1405, 13, 1)).toBe(false);
    expect(isValidJalali(1405, 1, 0)).toBe(false);
    expect(() => jalaliToIso(1404, 12, 30)).toThrow(RangeError);
  });
});

describe('ISO parsing is strict', () => {
  it.each(['2026-02-30', '2026-13-01', '2026-9-23', '2026-09-23T00:00:00Z', '1405/07/01', '', '0000-01-01'])(
    'rejects %s',
    (value) => expect(isIsoDate(value)).toBe(false),
  );
  it('accepts real dates including Feb 29 of leap years', () => {
    expect(isIsoDate('2028-02-29')).toBe(true);
    expect(isIsoDate('2026-02-28')).toBe(true);
  });
  it('adds days across month and year boundaries', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2028-03-01', -1)).toBe('2028-02-29');
  });
});

describe('local calendar date never shifts through UTC', () => {
  it('runs under Asia/Tehran (set in test setup)', () => {
    expect(Intl.DateTimeFormat().resolvedOptions().timeZone).toBe('Asia/Tehran');
  });
  it('00:30 local on 2026-11-03 is 2026-11-03, although toISOString() says the 2nd', () => {
    const justAfterMidnight = new Date(2026, 10, 3, 0, 30);
    expect(justAfterMidnight.toISOString().slice(0, 10)).toBe('2026-11-02'); // the bug we avoid
    expect(localDateToIso(justAfterMidnight)).toBe('2026-11-03');
  });
  it('23:59 local is still the same local day', () => {
    expect(localDateToIso(new Date(2026, 2, 20, 23, 59))).toBe('2026-03-20');
  });
});

describe('Saturday-based weeks and Jalali periods', () => {
  it('weekday index: Saturday 0 … Friday 6', () => {
    expect(weekdayFromSaturday('2026-10-31')).toBe(0); // Saturday
    expect(weekdayFromSaturday('2026-11-03')).toBe(3); // Tuesday
    expect(weekdayFromSaturday('2026-11-06')).toBe(6); // Friday
  });

  it('weekday agrees with UTC Date for many days', () => {
    for (let jdn = isoToDayNumber('2025-01-01'); jdn < isoToDayNumber('2027-01-01'); jdn += 1) {
      const iso = dayNumberToIso(jdn);
      const [y, m, d] = iso.split('-').map(Number);
      const utcDay = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // Sunday 0
      expect(weekdayFromSaturday(iso)).toBe((utcDay + 1) % 7);
    }
  });

  it('vector 10: a week starting on the last Saturday of Aban ends on Friday in Azar, 7 consecutive dates', () => {
    expect(isoToJalali('2026-11-21')).toEqual({ jy: 1405, jm: 8, jd: 30 });
    const week = periodRange('weekly', '2026-11-24');
    expect(week).toEqual({ start: '2026-11-21', end: '2026-11-27' });
    expect(isoToJalali(week.end)).toEqual({ jy: 1405, jm: 9, jd: 6 });
    const days = Array.from({ length: 7 }, (_, i) => addDays(week.start, i));
    expect(new Set(days).size).toBe(7);
    expect(weekdayFromSaturday(days[6])).toBe(6);
  });

  it('monthly, quarterly and annual follow Jalali boundaries', () => {
    expect(periodRange('monthly', '2026-11-03')).toEqual({ start: '2026-10-23', end: '2026-11-21' });
    expect(periodRange('quarterly', '2026-11-03')).toEqual({ start: '2026-09-23', end: '2026-12-21' });
    expect(periodRange('annual', '2026-11-03')).toEqual({ start: '2026-03-21', end: '2027-03-20' });
    expect(periodRange('monthly', '2025-03-15')).toEqual({ start: '2025-02-19', end: '2025-03-20' }); // leap Esfand 1403
  });
});
