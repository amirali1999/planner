import { useId, useState } from 'react';
import { addDays, isoToJalali, todayIso, type IsoDate } from '../date/calendar';
import { describePeriod, isStandardPeriod, periodForSelection, periodRange, quarterLabel, quarterOfMonth, type DateRange, type PeriodType } from '../date/periods';
import { JALALI_MONTHS, formatJalaliDateWithWeekday, toPersianDigits } from '../format';
import { fieldMessage } from './fieldErrors';
import { JalaliCalendar } from './JalaliCalendar';
import { JalaliDateField } from './JalaliDateField';

interface PeriodPickerProps {
  periodType: PeriodType;
  start: IsoDate;
  end: IsoDate;
  onChange: (range: DateRange) => void;
  startError?: string;
  endError?: string;
}

/**
 * Picks a goal period. Standard mode selects a Jalali year / quarter / month / Saturday–Friday week; custom mode
 * allows any start/end (existing goals with non-standard dates stay editable). Values are Gregorian ISO.
 */
export function PeriodPicker({ periodType, start, end, onChange, startError, endError }: PeriodPickerProps) {
  const id = useId();
  const [custom, setCustom] = useState(() => !isStandardPeriod(periodType, start, end));
  const { jy, jm } = isoToJalali(start);
  const thisYear = isoToJalali(todayIso()).jy;
  const years = Array.from(new Set([...Array.from({ length: 11 }, (_, i) => thisYear - 5 + i), jy])).sort((a, b) => a - b);
  const weekDays = periodType === 'weekly' && !custom ? Array.from({ length: 7 }, (_, i) => addDays(start, i)) : [];

  const switchMode = (toCustom: boolean) => {
    setCustom(toCustom);
    if (!toCustom) onChange(periodRange(periodType, start)); // snap to the standard period containing the start
  };

  return (
    <fieldset className="period-picker">
      <legend>بازه دوره *</legend>
      <div className="period-picker__modes" role="radiogroup" aria-label="نوع انتخاب بازه">
        <label>
          <input type="radio" name={`${id}-mode`} checked={!custom} onChange={() => switchMode(false)} /> دوره استاندارد
        </label>
        <label>
          <input type="radio" name={`${id}-mode`} checked={custom} onChange={() => switchMode(true)} /> بازه دلخواه
        </label>
      </div>

      {!custom && periodType !== 'weekly' && (
        <div className="form__row">
          <div className="field field--compact">
            <label htmlFor={`${id}-year`}>سال</label>
            <select id={`${id}-year`} value={jy} onChange={(e) => onChange(periodForSelection(periodType, Number(e.target.value), periodType === 'quarterly' ? quarterOfMonth(jm) : jm))}>
              {years.map((y) => (
                <option key={y} value={y}>
                  {toPersianDigits(y)}
                </option>
              ))}
            </select>
          </div>
          {periodType === 'quarterly' && (
            <div className="field field--compact">
              <label htmlFor={`${id}-quarter`}>فصل</label>
              <select id={`${id}-quarter`} value={quarterOfMonth(jm)} onChange={(e) => onChange(periodForSelection('quarterly', jy, Number(e.target.value)))}>
                {[1, 2, 3, 4].map((q) => (
                  <option key={q} value={q}>
                    {quarterLabel(q)}
                  </option>
                ))}
              </select>
            </div>
          )}
          {periodType === 'monthly' && (
            <div className="field field--compact">
              <label htmlFor={`${id}-month`}>ماه</label>
              <select id={`${id}-month`} value={jm} onChange={(e) => onChange(periodForSelection('monthly', jy, Number(e.target.value)))}>
                {JALALI_MONTHS.map((name, i) => (
                  <option key={name} value={i + 1}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      )}

      {!custom && periodType === 'weekly' && (
        <>
          <p className="field__hint">یک روز را انتخاب کنید؛ هفته شنبه تا جمعه آن انتخاب می‌شود.</p>
          <JalaliCalendar
            label="انتخاب هفته"
            selected={null}
            rangeStart={start}
            rangeEnd={end}
            onSelect={(iso) => onChange(periodRange('weekly', iso))}
          />
          <ol className="week-days" aria-label="روزهای هفته انتخاب‌شده">
            {weekDays.map((d) => (
              <li key={d}>
                {formatJalaliDateWithWeekday(d)} <span dir="ltr">({d})</span>
              </li>
            ))}
          </ol>
        </>
      )}

      {custom && (
        <div className="form__row">
          <JalaliDateField label="شروع" value={start} required errorCode={startError} onChange={(v) => v && onChange({ start: v, end })} />
          <JalaliDateField label="پایان" value={end} required errorCode={endError} onChange={(v) => v && onChange({ start, end: v })} />
        </div>
      )}

      <p className="period-picker__summary" aria-live="polite">
        {describePeriod(periodType, start, end)} <span dir="ltr">({start} … {end})</span>
      </p>
      {!custom && (startError || endError) && <p className="field__error">{fieldMessage(startError ?? endError)}</p>}
    </fieldset>
  );
}
