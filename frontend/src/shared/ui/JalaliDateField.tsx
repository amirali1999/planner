import { useId, useRef, useState } from 'react';
import type { IsoDate } from '../date/calendar';
import { formatJalaliDateWithWeekday } from '../format';
import { fieldMessage } from './fieldErrors';
import { JalaliCalendar } from './JalaliCalendar';

interface JalaliDateFieldProps {
  label: string;
  /** Gregorian ISO date-only, or null when empty. */
  value: IsoDate | null;
  onChange: (value: IsoDate | null) => void;
  required?: boolean;
  /** Allow clearing (optional fields such as deadline/due date). */
  clearable?: boolean;
  errorCode?: string;
  hint?: string;
}

/** Date-only field: shows the Jalali date (and the ISO value sent to the API) and opens a Jalali calendar. */
export function JalaliDateField({ label, value, onChange, required, clearable, errorCode, hint }: JalaliDateFieldProps) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const toggleRef = useRef<HTMLButtonElement>(null);
  const error = fieldMessage(errorCode);
  const close = () => {
    setOpen(false);
    toggleRef.current?.focus();
  };

  return (
    <div className="field date-field" aria-invalid={error ? true : undefined}>
      <span className="field__label" id={`${id}-label`}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </span>
      <div className="date-field__control">
        <button
          ref={toggleRef}
          type="button"
          className="button button--secondary date-field__toggle"
          aria-expanded={open}
          aria-controls={`${id}-calendar`}
          aria-labelledby={`${id}-label ${id}-value`}
          aria-describedby={[hint ? `${id}-hint` : '', error ? `${id}-error` : ''].join(' ').trim() || undefined}
          onClick={() => setOpen((v) => !v)}
        >
          <span id={`${id}-value`}>
            {value ? (
              <>
                {formatJalaliDateWithWeekday(value)} <span dir="ltr">({value})</span>
              </>
            ) : (
              'انتخاب تاریخ'
            )}
          </span>
        </button>
        {clearable && value && (
          <button type="button" className="button button--link" onClick={() => onChange(null)}>
            پاک کردن
          </button>
        )}
      </div>
      {open && (
        <div id={`${id}-calendar`}>
          <JalaliCalendar
            label={label}
            selected={value}
            autoFocus
            onEscape={close}
            onSelect={(iso) => {
              onChange(iso);
              close();
            }}
          />
        </div>
      )}
      {hint && (
        <p className="field__hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field__error" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  );
}
