import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { goalsApi } from './api';
import { STATUS_LABELS, STATUS_ORDER } from './labels';
import type { Goal, GoalStatus } from './types';

interface GoalStatusSelectProps {
  goalId: string;
  goalTitle: string;
  status: GoalStatus;
  /** Archived goals are read-only. */
  disabled?: boolean;
  onChanged?: (goal: Goal) => void;
}

/**
 * Inline manual status change (PATCH status only). Status is the user's own judgement; it never follows progress
 * numbers and changing it never changes them. Optimistic, reverted with a message on failure.
 */
export function GoalStatusSelect({ goalId, goalTitle, status, disabled, onChanged }: GoalStatusSelectProps) {
  const [value, setValue] = useState<GoalStatus>(status);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [synced, setSynced] = useState(status);
  if (synced !== status) {
    // parent reloaded with a newer status
    setSynced(status);
    setValue(status);
  }

  async function change(next: GoalStatus) {
    const previous = value;
    setValue(next);
    setSaving(true);
    setError(null);
    try {
      const saved = await goalsApi.update(goalId, { status: next });
      setValue(saved.status);
      onChanged?.(saved);
    } catch (e) {
      setValue(previous);
      setError(e instanceof ApiError ? e.message : 'تغییر وضعیت ذخیره نشد.');
    } finally {
      setSaving(false);
    }
  }

  const id = `goal-status-${goalId}`;
  return (
    <span className="status-select" onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
      <label htmlFor={id} className="visually-hidden">{`وضعیت «${goalTitle}» (انتخاب شما)`}</label>
      <select
        id={id}
        className={`status-select__input badge--status-${value}`}
        value={value}
        disabled={disabled || saving}
        aria-busy={saving}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(e) => change(e.target.value as GoalStatus)}
      >
        {STATUS_ORDER.map((s) => (
          <option key={s} value={s}>
            {STATUS_LABELS[s]}
          </option>
        ))}
      </select>
      {error && (
        <span id={`${id}-error`} role="alert" className="field__error">
          {error}
        </span>
      )}
    </span>
  );
}
