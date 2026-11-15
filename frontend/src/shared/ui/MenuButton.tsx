import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../api/client';
import { Icon } from './Icon';

export interface MenuItem {
  label: string;
  /** Navigates (rendered as a link). */
  to?: string;
  /** Runs an action; the menu shows a message if it throws. */
  onSelect?: () => Promise<unknown> | void;
  danger?: boolean;
  disabled?: boolean;
  /** Asked with window.confirm before running. */
  confirm?: string;
}

/** "⋮" menu button (WAI-ARIA menu pattern: arrows, Escape, focus return, outside click closes). */
export function MenuButton({ label, items, icon }: { label: string; items: MenuItem[]; icon?: ReactNode }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLUListElement>(null);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    menu.current?.querySelector<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])')?.focus();
    const outside = (e: MouseEvent) => {
      if (!menu.current?.contains(e.target as Node) && !button.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', outside);
    return () => document.removeEventListener('mousedown', outside);
  }, [open]);

  const close = () => {
    setOpen(false);
    button.current?.focus();
  };

  function onKey(e: KeyboardEvent<HTMLUListElement>) {
    const all = [...(menu.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])') ?? [])];
    const i = all.indexOf(document.activeElement as HTMLElement);
    if (e.key === 'Escape' || e.key === 'Tab') {
      e.preventDefault();
      close();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      all[(i + 1) % all.length]?.focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      all[(i - 1 + all.length) % all.length]?.focus();
    }
  }

  async function run(item: MenuItem) {
    if (item.confirm && !window.confirm(item.confirm)) return;
    setBusy(true);
    setError(null);
    try {
      await item.onSelect?.();
      close();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'انجام نشد.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="goal-menu">
      <button ref={button} type="button" className="icon-button goal-menu__button" aria-haspopup="menu" aria-expanded={open}
        aria-controls={open ? id : undefined} aria-label={label} title={label} onClick={() => setOpen((v) => !v)}>
        {icon ?? <Icon name="more" size={22} />}
      </button>
      {open && (
        <ul id={id} ref={menu} role="menu" aria-label={label} className="goal-menu__list" onKeyDown={onKey}>
          {items.map((item) => (
            <li role="none" key={item.label}>
              {item.to ? (
                <Link role="menuitem" tabIndex={-1} to={item.to} className="goal-menu__item">{item.label}</Link>
              ) : (
                <button type="button" role="menuitem" tabIndex={-1} aria-disabled={item.disabled || busy}
                  disabled={item.disabled || busy} className={`goal-menu__item${item.danger ? ' goal-menu__item--danger' : ''}`}
                  onClick={() => void run(item)}>
                  {item.label}
                </button>
              )}
            </li>
          ))}
          {error && <li role="none" className="field__error" aria-live="assertive">{error}</li>}
        </ul>
      )}
    </div>
  );
}
