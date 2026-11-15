import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { isoToJalali, jalaliToIso, todayIso, type IsoDate } from '../date/calendar';
import { addJalaliMonths, monthGrid, moveByKey } from '../date/monthGrid';
import { JALALI_MONTHS, WEEKDAYS, formatJalaliDateWithWeekday, toPersianDigits } from '../format';

interface JalaliCalendarProps {
  /** Selected single day (highlighted). */
  selected?: IsoDate | null;
  /** Optional inclusive range to highlight (e.g. the selected week). */
  rangeStart?: IsoDate | null;
  rangeEnd?: IsoDate | null;
  onSelect: (iso: IsoDate) => void;
  label: string;
  /** Focus the active day on mount (when opened as a popover). */
  autoFocus?: boolean;
  onEscape?: () => void;
}

const WEEKDAY_SHORT = ['ش', 'ی', 'د', 'س', 'چ', 'پ', 'ج'];

/**
 * Saturday-first Jalali month grid (RTL). Pure presentation over ISO dates: no Date objects, no timezone math.
 * Keyboard: arrows (RTL), Home/End = Saturday/Friday, PageUp/PageDown = month, Enter/Space = select.
 */
export function JalaliCalendar({ selected, rangeStart, rangeEnd, onSelect, label, autoFocus, onEscape }: JalaliCalendarProps) {
  const id = useId();
  const today = todayIso();
  const [focused, setFocused] = useState<IsoDate>(selected ?? rangeStart ?? today);
  const [moveFocus, setMoveFocus] = useState(Boolean(autoFocus));
  const { jy, jm } = isoToJalali(focused);
  const gridRef = useRef<HTMLTableElement>(null);

  useEffect(() => {
    if (!moveFocus) return;
    gridRef.current?.querySelector<HTMLButtonElement>(`button[data-iso="${focused}"]`)?.focus();
    setMoveFocus(false);
  }, [focused, moveFocus]);

  const goTo = (iso: IsoDate, takeFocus = true) => {
    setFocused(iso);
    setMoveFocus(takeFocus);
  };

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, iso: IsoDate) {
    if (event.key === 'Escape' && onEscape) {
      event.preventDefault();
      onEscape();
      return;
    }
    const next = moveByKey(iso, event.key);
    if (next) {
      event.preventDefault();
      goTo(next);
    }
  }

  const years = Array.from({ length: 21 }, (_, i) => jy - 10 + i);
  const inRange = (iso: IsoDate) => rangeStart != null && rangeEnd != null && iso >= rangeStart && iso <= rangeEnd;

  return (
    <div className="calendar" role="group" aria-label={label}>
      <div className="calendar__header">
        <button type="button" className="button button--secondary calendar__nav" onClick={() => goTo(addJalaliMonths(focused, -1), false)} aria-label="ماه قبل">
          →
        </button>
        <label className="visually-hidden" htmlFor={`${id}-month`}>
          ماه
        </label>
        <select
          id={`${id}-month`}
          value={jm}
          onChange={(e) => goTo(jalaliToIso(jy, Number(e.target.value), 1), false)}
        >
          {JALALI_MONTHS.map((name, i) => (
            <option key={name} value={i + 1}>
              {name}
            </option>
          ))}
        </select>
        <label className="visually-hidden" htmlFor={`${id}-year`}>
          سال
        </label>
        <select
          id={`${id}-year`}
          value={jy}
          onChange={(e) => goTo(jalaliToIso(Number(e.target.value), jm, 1), false)}
        >
          {years.map((y) => (
            <option key={y} value={y}>
              {toPersianDigits(y)}
            </option>
          ))}
        </select>
        <button type="button" className="button button--secondary calendar__nav" onClick={() => goTo(addJalaliMonths(focused, 1), false)} aria-label="ماه بعد">
          ←
        </button>
      </div>
      <table className="calendar__grid" role="grid" ref={gridRef} aria-label={`${JALALI_MONTHS[jm - 1]} ${toPersianDigits(jy)}`}>
        <thead>
          <tr>
            {WEEKDAY_SHORT.map((d, i) => (
              <th key={d} scope="col" abbr={WEEKDAYS[i]} className={i === 6 ? 'calendar__weekend' : undefined}>
                {d}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {monthGrid(jy, jm).map((row, r) => (
            <tr key={r}>
              {row.map((iso, c) =>
                iso == null ? (
                  <td key={c} className={c === 6 ? 'calendar__weekend' : undefined} />
                ) : (
                  <td key={iso} className={c === 6 ? 'calendar__weekend' : undefined}>
                    <button
                      type="button"
                      data-iso={iso}
                      tabIndex={iso === focused ? 0 : -1}
                      className={[
                        'calendar__day',
                        iso === selected ? 'calendar__day--selected' : '',
                        inRange(iso) ? 'calendar__day--in-range' : '',
                        iso === today ? 'calendar__day--today' : '',
                      ].join(' ')}
                      aria-pressed={iso === selected || inRange(iso)}
                      aria-current={iso === today ? 'date' : undefined}
                      aria-label={formatJalaliDateWithWeekday(iso)}
                      onClick={() => onSelect(iso)}
                      onKeyDown={(e) => onKeyDown(e, iso)}
                      onFocus={() => setFocused(iso)}
                    >
                      {toPersianDigits(isoToJalali(iso).jd)}
                    </button>
                  </td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
