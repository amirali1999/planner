import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { tasksApi } from './api';
import { TASK_STATUS_ORDER } from './labels';
import type { Task, TaskStatus } from './types';

const STATUS_LABELS: Record<TaskStatus, string> = {
  not_started: 'انجام‌نشده',
  doing: 'در حال انجام',
  done: 'انجام‌شده',
};

/** Compact three-state task status menu. Selecting a state only updates the task; planning membership is untouched. */
export function TaskStatusPicker({ task, onChanged, disabled }: {
  task: Task; onChanged?: (task: Task) => void; disabled?: boolean;
}) {
  const current = task.status;
  const [value, setValue] = useState<TaskStatus>(current);
  const [synced, setSynced] = useState<TaskStatus>(current);
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
    const outside = (event: MouseEvent) => {
      if (!menu.current?.contains(event.target as Node) && !button.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', outside);
    return () => document.removeEventListener('mousedown', outside);
  }, [open]);

  const close = () => {
    setOpen(false);
    button.current?.focus();
  };

  async function pick(next: TaskStatus) {
    if (next === value) return close();
    const previous = value;
    setValue(next);
    close();
    setSaving(true);
    setError(null);
    try {
      const saved = await tasksApi.update(task.id, { status: next });
      onChanged?.(saved);
    } catch (cause) {
      setValue(previous);
      setError(cause instanceof ApiError ? cause.message : 'وضعیت ذخیره نشد.');
    } finally {
      setSaving(false);
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLUListElement>) {
    const options = [...(menu.current?.querySelectorAll<HTMLElement>('[role="menuitemradio"]') ?? [])];
    const index = options.indexOf(document.activeElement as HTMLElement);
    if (event.key === 'Escape' || event.key === 'Tab') {
      event.preventDefault();
      close();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      options[(index + 1) % options.length]?.focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      options[(index - 1 + options.length) % options.length]?.focus();
    }
  }

  return (
    <span className="task-status-picker">
      <button ref={button} type="button" className={`status-chip status-chip--${value}`}
        aria-haspopup="menu" aria-expanded={open} aria-controls={open ? id : undefined}
        aria-label={`وضعیت «${task.title}»: ${STATUS_LABELS[value]} — تغییر`}
        disabled={disabled} aria-busy={saving} onClick={() => !saving && setOpen((shown) => !shown)}>
        {STATUS_LABELS[value]}
      </button>
      {open && (
        <ul id={id} ref={menu} role="menu" aria-label={`وضعیت «${task.title}»`}
          className="prio-picker__menu task-status-picker__menu" onKeyDown={onKeyDown}>
          {TASK_STATUS_ORDER.map((status) => (
            <li role="none" key={status}>
              <button type="button" role="menuitemradio" tabIndex={-1} aria-checked={status === value}
                className="prio-picker__option" onClick={() => void pick(status)}>
                <span>{STATUS_LABELS[status]}</span>
                <span className="prio-picker__mark" aria-hidden="true">{status === value ? '✓' : ''}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && <span className="field__error" role="alert">{error}</span>}
    </span>
  );
}
