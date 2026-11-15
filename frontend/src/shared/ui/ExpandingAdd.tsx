import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { Icon } from './Icon';

type State = 'closed' | 'open' | 'closing';
const CLOSE_MS = 260;

/**
 * Round turquoise ＋ at a card's bottom-left (inline-end in RTL). Clicking it turns it into × and smoothly opens a pill
 * to its right with the input and a ✓ button; Enter / ✓ adds, Escape / × closes (animated). Stays open after an add so
 * several items can be typed in a row.
 */
export function ExpandingAdd({ label, placeholder, inputLabel, onAdd }: {
  label: string; placeholder: string; inputLabel: string; onAdd: (title: string) => Promise<void>;
}) {
  const [state, setState] = useState<State>('closed');
  const [title, setTitle] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  function open() {
    window.clearTimeout(timer.current);
    setState('open');
    setError(null);
    // focus once the field exists; the pill keeps growing around it
    requestAnimationFrame(() => input.current?.focus({ preventScroll: true }));
  }
  function close() {
    setState('closing');
    setError(null);
    timer.current = window.setTimeout(() => {
      setState('closed');
      setTitle('');
    }, CLOSE_MS);
    toggle.current?.focus();
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim() || saving) return;
    setSaving(true);
    setError(null);
    try {
      await onAdd(title.trim());
      setTitle('');
      input.current?.focus();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'ذخیره نشد.');
    } finally {
      setSaving(false);
    }
  }

  const isOpen = state === 'open';
  return (
    <div className={`xadd xadd--${state}`} onKeyDown={(e) => e.key === 'Escape' && isOpen && close()}>
      {state !== 'closed' && (
        <form className="xadd__form" onSubmit={submit}>
          <label className="visually-hidden" htmlFor={`xadd-${inputLabel}`}>{inputLabel}</label>
          <input ref={input} id={`xadd-${inputLabel}`} className="xadd__input" value={title} maxLength={200} placeholder={placeholder}
            onChange={(e) => setTitle(e.target.value)} disabled={!isOpen} />
          <button type="submit" className="xadd__ok" aria-label="افزودن" title="افزودن" disabled={!isOpen || saving || !title.trim()}>
            <Icon name="check" size={18} />
          </button>
        </form>
      )}
      <button ref={toggle} type="button" className="dash-fab xadd__toggle" aria-expanded={isOpen}
        aria-label={isOpen ? 'بستن' : label} title={isOpen ? 'بستن' : label} onClick={() => (isOpen ? close() : open())}>
        <Icon name="plus" size={24} />
      </button>
      {error && <p className="xadd__error" role="alert">{error}</p>}
    </div>
  );
}
