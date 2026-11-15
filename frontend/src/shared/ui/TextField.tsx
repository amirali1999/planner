import { useId, type InputHTMLAttributes } from 'react';
import { fieldMessage } from './fieldErrors';

interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> {
  label: string;
  value: string;
  onChange: (value: string) => void;
  errorCode?: string;
  hint?: string;
  multiline?: boolean;
}

/** Labelled input with accessible hint/error wiring. */
export function TextField({ label, value, onChange, errorCode, hint, multiline, required, ...rest }: TextFieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const error = fieldMessage(errorCode);
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined;
  const common = {
    id,
    value,
    required,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': describedBy,
  };
  return (
    <div className="field">
      <label htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {multiline ? (
        <textarea {...common} rows={3} onChange={(e) => onChange(e.target.value)} />
      ) : (
        <input {...common} {...rest} onChange={(e) => onChange(e.target.value)} />
      )}
      {hint && (
        <p className="field__hint" id={hintId}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field__error" id={errorId}>
          {error}
        </p>
      )}
    </div>
  );
}
