import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { formatNumber } from '../../shared/format';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import { inboxApi } from './api';
import { RAW_TEXT_MAX, checkCapture, codePointLength } from './inboxModel';

const MESSAGES = {
  required: 'متن یادداشت نمی‌تواند خالی باشد.',
  too_long: 'متن یادداشت حداکثر ۴۰۰۰ نویسه می‌تواند باشد.',
} as const;

interface Props {
  onCaptured?: () => void;
  autoFocus?: boolean;
  /** Shows a link to /inbox in the confirmation (header panel). */
  showInboxLink?: boolean;
}

/**
 * One field, nothing else: no goal, deadline or priority. Ctrl/⌘+Enter submits; Enter adds a new line.
 * The text is sent as typed and never interpreted.
 */
export function QuickCaptureForm({ onCaptured, autoFocus, showInboxLink }: Props) {
  const id = useId();
  const ref = useRef<HTMLTextAreaElement>(null);
  const [text, setText] = useState('');
  const [error, setError] = useState<'required' | 'too_long' | null>(null);
  const [serverError, setServerError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const length = codePointLength(text.trim());

  useEffect(() => {
    if (autoFocus) ref.current?.focus();
  }, [autoFocus]);

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (busy) return;
    const check = checkCapture(text);
    setSaved(false);
    setServerError(null);
    if (!check.ok) {
      setError(check.code);
      return;
    }
    setError(null);
    setBusy(true);
    try {
      await inboxApi.capture(check.text);
      setText('');
      setSaved(true);
      onCaptured?.();
      ref.current?.focus();
    } catch (e) {
      if (e instanceof ApiError && e.fieldCodes().rawText) setError(e.fieldCodes().rawText === 'too_long' ? 'too_long' : 'required');
      else setServerError(e);
    } finally {
      setBusy(false);
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      void submit();
    }
  }

  return (
    <form className="quick-capture" onSubmit={submit} noValidate aria-label="ثبت سریع در صندوق ورودی">
      <label htmlFor={`${id}-text`} className="field__label">
        چه چیزی در ذهن دارید؟
      </label>
      <textarea
        id={`${id}-text`}
        ref={ref}
        dir="auto"
        rows={3}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          setSaved(false);
        }}
        onKeyDown={onKeyDown}
        aria-invalid={error ? true : undefined}
        aria-describedby={`${id}-hint${error ? ` ${id}-error` : ''}`}
        enterKeyHint="send"
        autoComplete="off"
      />
      <p className="field__hint" id={`${id}-hint`}>
        <span className={length > RAW_TEXT_MAX ? 'field__error' : undefined}>
          {formatNumber(length)} / {formatNumber(RAW_TEXT_MAX)}
        </span>{' '}
        · فقط متن؛ هدف، موعد یا اولویت لازم نیست. Ctrl/⌘ + Enter برای ثبت.
      </p>
      {error && (
        <p className="field__error" id={`${id}-error`} role="alert">
          {MESSAGES[error]}
        </p>
      )}
      {serverError != null && <ErrorMessage error={serverError} />}
      <div className="form__actions">
        <button type="submit" className="button" disabled={busy}>
          {busy ? 'در حال ثبت…' : 'ثبت'}
        </button>
        <span className="meta" role="status" aria-live="polite">
          {saved && (
            <>
              ثبت شد.
              {showInboxLink && (
                <>
                  {' '}
                  <Link to="/inbox">مشاهده صندوق ورودی</Link>
                </>
              )}
            </>
          )}
        </span>
      </div>
    </form>
  );
}
