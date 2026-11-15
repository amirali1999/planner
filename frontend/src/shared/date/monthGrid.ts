import { addDays, isoToJalali, jalaliMonthLength, jalaliToIso, weekdayFromSaturday, type IsoDate } from './calendar';

/** Rows of 7 cells, Saturday first … Friday last; null outside the month. */
export function monthGrid(jy: number, jm: number): (IsoDate | null)[][] {
  const first = jalaliToIso(jy, jm, 1);
  const length = jalaliMonthLength(jy, jm);
  const cells: (IsoDate | null)[] = Array.from({ length: weekdayFromSaturday(first) }, () => null);
  for (let day = 0; day < length; day += 1) cells.push(addDays(first, day));
  while (cells.length % 7 !== 0) cells.push(null);
  const rows: (IsoDate | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) rows.push(cells.slice(i, i + 7));
  return rows;
}

/** Same Jalali day {@code months} later/earlier, clamped to the target month's length (e.g. 31 Shahrivar → 30 Mehr). */
export function addJalaliMonths(iso: IsoDate, months: number): IsoDate {
  const { jy, jm, jd } = isoToJalali(iso);
  const index = jy * 12 + (jm - 1) + months;
  const ty = Math.floor(index / 12);
  const tm = (index % 12) + 1;
  return jalaliToIso(ty, tm, Math.min(jd, jalaliMonthLength(ty, tm)));
}

/**
 * Keyboard movement inside an RTL Saturday-first grid: visually right is the previous day.
 * Returns null for keys the grid does not handle.
 */
export function moveByKey(iso: IsoDate, key: string): IsoDate | null {
  switch (key) {
    case 'ArrowRight':
      return addDays(iso, -1);
    case 'ArrowLeft':
      return addDays(iso, 1);
    case 'ArrowUp':
      return addDays(iso, -7);
    case 'ArrowDown':
      return addDays(iso, 7);
    case 'Home':
      return addDays(iso, -weekdayFromSaturday(iso));
    case 'End':
      return addDays(iso, 6 - weekdayFromSaturday(iso));
    case 'PageUp':
      return addJalaliMonths(iso, -1);
    case 'PageDown':
      return addJalaliMonths(iso, 1);
    default:
      return null;
  }
}
