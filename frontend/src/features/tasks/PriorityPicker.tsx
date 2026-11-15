import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { tasksApi } from './api';
import { TASK_PRIORITY_LABELS, TASK_PRIORITY_ORDER } from './labels';
import type { Task, TaskPriority } from './types';

/**
 * The task's priority badge; clicking it opens the three choices (پایین · متوسط · بالا) and saves the pick at once.
 * Every task has a priority (default پایین). Keyboard: arrows move, Enter/Space picks, Escape closes.
 */
export function PriorityPicker({ task, onChanged, disabled }: { task: Task; onChanged?: (task: Task) => void; disabled?: boolean }) {
  const current: TaskPriority = task.priority ?? 'low';
  const [value, setValue] = useState<TaskPriority>(current);
  const [synced, setSynced] = useState<TaskPriority>(current);
  if (synced !== current) {
    setSynced(current);
    setValue(current);
  }
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLUListElement>(null);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    menu.current?.querySelector<HTMLElement>('[aria-checked="true"]')?.focus();
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

  async function pick(next: TaskPriority) {
    if (next === value) return close();
    const previous = value;
    setValue(next);
    close();
    setSaving(true);
    setError(null);
    try {
      const saved = await tasksApi.update(task.id, { priority: next });
      onChanged?.(saved);
    } catch (e) {
      setValue(previous);
      setError(e instanceof ApiError ? e.message : 'اولویت ذخیره نشد.');
    } finally {
      setSaving(false);
    }
  }

  function onKey(e: KeyboardEvent<HTMLUListElement>) {
    const items = [...(menu.current?.querySelectorAll<HTMLElement>('[role="menuitemradio"]') ?? [])];
    const i = items.indexOf(document.activeElement as HTMLElement);
    if (e.key === 'Escape' || e.key === 'Tab') {
      e.preventDefault();
      close();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      items[(i + 1) % items.length]?.focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      items[(i - 1 + items.length) % items.length]?.focus();
    }
  }

  return (
    <span className="prio-picker">
      <button ref={button} type="button" className={`prio prio--${value} prio-picker__button`} aria-haspopup="menu" aria-expanded={open}
        aria-controls={open ? id : undefined} aria-label={`اولویت «${task.title}»: ${TASK_PRIORITY_LABELS[value]} — تغییر`}
        // not disabled while saving: a disabled button would drop the keyboard focus that was just returned to it
        disabled={disabled} aria-busy={saving} onClick={() => !saving && setOpen((v) => !v)}>
        {TASK_PRIORITY_LABELS[value]}
      </button>
      {open && (
        <ul id={id} ref={menu} role="menu" aria-label={`اولویت «${task.title}»`} className="prio-picker__menu" onKeyDown={onKey}>
          {TASK_PRIORITY_ORDER.map((p) => (
            <li role="none" key={p}>
              <button type="button" role="menuitemradio" tabIndex={-1} aria-checked={p === value} className="prio-picker__option"
                onClick={() => void pick(p)}>
                <span className={`prio prio--${p}`}>{TASK_PRIORITY_LABELS[p]}</span>
                <span className="prio-picker__mark" aria-hidden="true">{p === value ? '✓' : ''}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && <span className="field__error" role="alert">{error}</span>}
    </span>
  );
}
