import { describe, expect, it } from 'vitest';
import { addDays, isLeapJalaliYear, isValidJalali, isoToJalali, jalaliMonthLength, jalaliToIso, weekdayFromSaturday } from './calendar';
import { addJalaliMonths, monthGrid, moveByKey } from './monthGrid';
import { describePeriod, isStandardPeriod, periodForSelection, periodRange, quarterOfMonth } from './periods';
import { dateInZone, isValidTimeZone, timeInZone, zonedWallTimeToUtc } from './timeZone';

describe('leap Esfand', () => {
  it.each([1399, 1403, 1408])('%i is leap: Esfand has 30 days and 30 Esfand is valid', (jy) => {
    expect(isLeapJalaliYear(jy)).toBe(true);
    expect(jalaliMonthLength(jy, 12)).toBe(30);
    expect(isValidJalali(jy, 12, 30)).toBe(true);
    // 30 Esfand is the day before Nowruz
    expect(addDays(jalaliToIso(jy, 12, 30), 1)).toBe(jalaliToIso(jy + 1, 1, 1));
  });
  it.each([1400, 1401, 1402, 1404, 1405])('%i is not leap: 30 Esfand is invalid', (jy) => {
    expect(isLeapJalaliYear(jy)).toBe(false);
    expect(isValidJalali(jy, 12, 30)).toBe(false);
    expect(() => jalaliToIso(jy, 12, 30)).toThrow(RangeError);
    expect(addDays(jalaliToIso(jy, 12, 29), 1)).toBe(jalaliToIso(jy + 1, 1, 1));
  });
});

describe('year, quarter and month boundaries (1405)', () => {
  it('year 1405 = 2026-03-21 .. 2027-03-20', () => {
    expect(periodForSelection('annual', 1405, 0)).toEqual({ start: '2026-03-21', end: '2027-03-20' });
  });
  it('four quarters are contiguous and cover the year', () => {
    const quarters = [1, 2, 3, 4].map((q) => periodForSelection('quarterly', 1405, q));
    expect(quarters).toEqual([
      { start: '2026-03-21', end: '2026-06-21' }, // Farvardin–Khordad (31-day months)
      { start: '2026-06-22', end: '2026-09-22' }, // Tir–Shahrivar
      { start: '2026-09-23', end: '2026-12-21' }, // Mehr–Azar (30-day months)
      { start: '2026-12-22', end: '2027-03-20' }, // Dey–Esfand (Esfand 29 days in 1405)
    ]);
    for (let i = 1; i < 4; i++) expect(addDays(quarters[i - 1].end, 1)).toBe(quarters[i].start);
  });
  it('month transitions: 31 Shahrivar → 1 Mehr, 30 Esfand 1403 → 1 Farvardin 1404', () => {
    expect(isoToJalali('2026-09-22')).toEqual({ jy: 1405, jm: 6, jd: 31 });
    expect(isoToJalali('2026-09-23')).toEqual({ jy: 1405, jm: 7, jd: 1 });
    expect(periodForSelection('monthly', 1403, 12)).toEqual({ start: '2025-02-19', end: '2025-03-20' });
    expect(periodForSelection('monthly', 1404, 1).start).toBe('2025-03-21');
  });
  it('quarter of month', () => {
    expect([1, 3, 4, 6, 7, 9, 10, 12].map(quarterOfMonth)).toEqual([1, 1, 2, 2, 3, 3, 4, 4]);
  });
  it('standard vs custom ranges', () => {
    expect(isStandardPeriod('quarterly', '2026-09-23', '2026-12-21')).toBe(true);
    expect(isStandardPeriod('quarterly', '2026-09-23', '2026-12-20')).toBe(false);
    expect(isStandardPeriod('weekly', '2026-11-21', '2026-11-27')).toBe(true);
    expect(isStandardPeriod('weekly', '2026-11-22', '2026-11-28')).toBe(false); // starts Sunday
  });
  it('labels', () => {
    expect(describePeriod('annual', '2026-03-21', '2027-03-20')).toBe('سال ۱۴۰۵');
    expect(describePeriod('quarterly', '2026-09-23', '2026-12-21')).toBe('فصل سوم (مهر تا آذر) ۱۴۰۵');
    expect(describePeriod('monthly', '2026-10-23', '2026-11-21')).toBe('آبان ۱۴۰۵');
    expect(describePeriod('weekly', '2026-11-21', '2026-11-27')).toBe('هفته ۳۰ آبان ۱۴۰۵ تا ۶ آذر ۱۴۰۵');
    expect(describePeriod('monthly', '2026-10-25', '2026-11-21')).toBe('۳ آبان ۱۴۰۵ تا ۳۰ آبان ۱۴۰۵');
  });
});

describe('Saturday–Friday weeks', () => {
  it('boundary week spanning Aban/Azar has seven consecutive ISO dates, Saturday to Friday', () => {
    const week = periodRange('weekly', '2026-11-25');
    const days = Array.from({ length: 7 }, (_, i) => addDays(week.start, i));
    expect(days).toEqual(['2026-11-21', '2026-11-22', '2026-11-23', '2026-11-24', '2026-11-25', '2026-11-26', '2026-11-27']);
    expect(days.map(weekdayFromSaturday)).toEqual([0, 1, 2, 3, 4, 5, 6]);
    expect(days.map((d) => isoToJalali(d).jm)).toEqual([8, 9, 9, 9, 9, 9, 9]);
  });
  it('a week spanning the Jalali new year', () => {
    const week = periodRange('weekly', '2027-03-21'); // 1 Farvardin 1406
    expect(isoToJalali(week.start).jy).toBe(1405);
    expect(isoToJalali(week.end).jy).toBe(1406);
    expect(weekdayFromSaturday(week.start)).toBe(0);
  });
});

describe('month grid and keyboard movement', () => {
  it('Aban 1405 grid starts on Saturday columns; 1 Aban is a Friday', () => {
    const grid = monthGrid(1405, 8);
    expect(grid.every((row) => row.length === 7)).toBe(true);
    expect(grid[0]).toEqual([null, null, null, null, null, null, '2026-10-23']);
    expect(grid.flat().filter(Boolean)).toHaveLength(30);
    expect(grid[1][0]).toBe('2026-10-24'); // Saturday
    expect(grid.flat().filter(Boolean).at(-1)).toBe('2026-11-21');
  });
  it('leap Esfand grid has 30 days, non-leap 29', () => {
    expect(monthGrid(1403, 12).flat().filter(Boolean)).toHaveLength(30);
    expect(monthGrid(1404, 12).flat().filter(Boolean)).toHaveLength(29);
  });
  it('RTL arrows: right = previous day, left = next day; Home/End = Saturday/Friday', () => {
    expect(moveByKey('2026-11-04', 'ArrowRight')).toBe('2026-11-03');
    expect(moveByKey('2026-11-04', 'ArrowLeft')).toBe('2026-11-05');
    expect(moveByKey('2026-11-04', 'ArrowUp')).toBe('2026-10-28');
    expect(moveByKey('2026-11-04', 'ArrowDown')).toBe('2026-11-11');
    expect(moveByKey('2026-11-04', 'Home')).toBe('2026-10-31');
    expect(moveByKey('2026-11-04', 'End')).toBe('2026-11-06');
    expect(moveByKey('2026-11-04', 'Tab')).toBeNull();
  });
  it('PageUp/PageDown keep the Jalali day, clamped to month length', () => {
    expect(addJalaliMonths(jalaliToIso(1405, 6, 31), 1)).toBe(jalaliToIso(1405, 7, 30));
    expect(addJalaliMonths(jalaliToIso(1405, 12, 15), 1)).toBe(jalaliToIso(1406, 1, 15));
    expect(addJalaliMonths(jalaliToIso(1404, 1, 30), -1)).toBe(jalaliToIso(1403, 12, 30)); // leap
    expect(addJalaliMonths(jalaliToIso(1405, 1, 30), -1)).toBe(jalaliToIso(1404, 12, 29)); // non-leap
  });
});

describe('Jalali → Gregorian → Jalali round trip over many years', () => {
  it('every valid Jalali date 1380..1420 round-trips', () => {
    for (let jy = 1380; jy <= 1420; jy++) {
      for (let jm = 1; jm <= 12; jm++) {
        for (let jd = 1; jd <= jalaliMonthLength(jy, jm); jd++) {
          expect(isoToJalali(jalaliToIso(jy, jm, jd))).toEqual({ jy, jm, jd });
        }
      }
    }
  });
});

describe('configured timezone decides "today" without UTC dates', () => {
  const instant = new Date('2026-11-03T21:00:00Z');
  it('the same instant is a different calendar date in different zones', () => {
    expect(dateInZone(instant, 'Asia/Tehran')).toBe('2026-11-04'); // 00:30
    expect(dateInZone(instant, 'UTC')).toBe('2026-11-03');
    expect(dateInZone(instant, 'America/Los_Angeles')).toBe('2026-11-03');
    expect(timeInZone(instant, 'Asia/Tehran')).toBe('00:30');
  });
  it('wall time in a zone → UTC instant, incl. DST zones', () => {
    expect(zonedWallTimeToUtc('2026-11-04', 0, 30, 'Asia/Tehran').toISOString()).toBe('2026-11-03T21:00:00.000Z');
    expect(zonedWallTimeToUtc('2026-07-01', 12, 0, 'Europe/Berlin').toISOString()).toBe('2026-07-01T10:00:00.000Z'); // CEST
    expect(zonedWallTimeToUtc('2026-12-01', 12, 0, 'Europe/Berlin').toISOString()).toBe('2026-12-01T11:00:00.000Z'); // CET
    expect(zonedWallTimeToUtc('2026-11-04', 9, 0, 'UTC').toISOString()).toBe('2026-11-04T09:00:00.000Z');
  });
  it('validates zone names', () => {
    expect(isValidTimeZone('Asia/Tehran')).toBe(true);
    expect(isValidTimeZone('Mars/Olympus')).toBe(false);
  });
});
