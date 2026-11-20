import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { addDays, todayIso } from '../../shared/date/calendar';
import { Icon } from '../../shared/ui/Icon';
import { dailyPlanApi, weeklyPlanApi } from './api';
import { weekStart } from './weeklyModel';

const SCHEDULED_SCOPES = ['today', 'this_week', 'next_week'] as const;
type PlanScope = 'none' | typeof SCHEDULED_SCOPES[number];
const SCOPE_LABELS: Record<PlanScope, string> = {
  none: 'زمان‌بندی',
  today: 'امروز',
  this_week: 'این هفته',
  next_week: 'هفته بعد',
};

/** The most specific single state shown by the focus-page planning selector. */
export function planScopeOf(plannedToday: boolean, plannedThisWeek: boolean, plannedNextWeek: boolean): PlanScope {
  if (plannedToday) return 'today';
  if (plannedThisWeek) return 'this_week';
  if (plannedNextWeek) return 'next_week';
  return 'none';
}

export function PlanScopePicker({ taskId, taskTitle, taskDone, plannedToday, plannedThisWeek, plannedNextWeek, disabled, focusTaskCheckbox, onChanged }: {
  taskId: string; taskTitle: string; taskDone: boolean; plannedToday: boolean; plannedThisWeek: boolean; plannedNextWeek: boolean;
  disabled?: boolean; focusTaskCheckbox?: () => void; onChanged?: () => void;
}) {
  const current = planScopeOf(plannedToday, plannedThisWeek, plannedNextWeek);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const picker = useRef<HTMLSpanElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const options = useRef<HTMLSpanElement>(null);
  const restoreFocus = useRef(false);
  const id = useId();

  useLayoutEffect(() => {
    if (open) {
      const control = options.current?.querySelector<HTMLButtonElement>('[aria-pressed="true"]')
        ?? options.current?.querySelector<HTMLButtonElement>('button');
      control?.focus({ preventScroll: true });
    } else if (restoreFocus.current) {
      restoreFocus.current = false;
      button.current?.focus({ preventScroll: true });
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!picker.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [open]);

  const close = () => {
    restoreFocus.current = true;
    setOpen(false);
  };

  async function pick(next: PlanScope) {
    if (next === current) return close();
    close();
    setBusy(true);
    setError(null);
    const today = todayIso();
    const thisWeek = weekStart(today);
    const nextWeek = addDays(thisWeek, 7);
    const plannedCurrent = plannedToday || plannedThisWeek;
    try {
      if (next === 'none') {
        if (plannedCurrent) await weeklyPlanApi.removeTask(thisWeek, taskId);
        if (plannedNextWeek) await weeklyPlanApi.removeTask(nextWeek, taskId);
        if (taskDone && document.activeElement === button.current) focusTaskCheckbox?.();
      } else if (next === 'today') {
        await dailyPlanApi.addTask(today, taskId, true);
        if (plannedNextWeek) await weeklyPlanApi.removeTask(nextWeek, taskId);
      } else if (next === 'this_week') {
        if (plannedToday) {
          // Remove the daily assignment, then restore undated membership in the same week.
          await weeklyPlanApi.removeTask(thisWeek, taskId);
          await weeklyPlanApi.addTask(thisWeek, taskId);
        } else if (!plannedThisWeek) {
          await weeklyPlanApi.addTask(thisWeek, taskId);
        }
        if (plannedNextWeek) await weeklyPlanApi.removeTask(nextWeek, taskId);
      } else {
        // Save the destination before removing the current plan.
        if (!plannedNextWeek) await weeklyPlanApi.addTask(nextWeek, taskId);
        if (plannedCurrent) await weeklyPlanApi.removeTask(thisWeek, taskId);
      }
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'برنامه‌ریزی ذخیره نشد.');
    } finally {
      // A move can partially succeed, so reload the persisted plan after errors too.
      onChanged?.();
      setBusy(false);
    }
  }

  return (
    <span className="plan-scope" ref={picker} onBlur={(event) => {
      if (open && event.relatedTarget && !event.currentTarget.contains(event.relatedTarget)) setOpen(false);
    }} onKeyDown={(event) => {
      if (open && event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        close();
      }
    }}>
      <button ref={button} type="button" className={`plan-scope__button${current === 'none' ? ' plan-scope__button--empty' : ''}`}
        hidden={open || (taskDone && current === 'none')} aria-expanded={open} aria-controls={open ? id : undefined}
        aria-label={`زمان‌بندی «${taskTitle}»${current === 'none' ? '' : `، ${SCOPE_LABELS[current]}`}`}
        disabled={disabled} aria-disabled={busy || undefined} aria-busy={busy} onClick={() => { if (!busy) setOpen(true); }}>
        <Icon name={current === 'none' ? 'plus' : 'calendar'} size={18} />
        <span>{SCOPE_LABELS[current]}</span>
        {current !== 'none' && <Icon name="chevron-down" size={16} />}
      </button>
      {open && (
        <span id={id} ref={options} className="plan-scope__actions" role="group" aria-label={`زمان‌بندی «${taskTitle}»`}>
          <span className="plan-scope__options">
            {SCHEDULED_SCOPES.map((scope) => (
              <button type="button" key={scope} className="plan-scope__option" aria-pressed={scope === current}
                disabled={disabled} onClick={() => void pick(scope)}>
                {scope === current && <Icon name="check" size={16} />}
                <span>{SCOPE_LABELS[scope]}</span>
              </button>
            ))}
          </span>
          {current !== 'none' && <button type="button" className="plan-scope__remove" disabled={disabled}
            onClick={() => void pick('none')}>حذف از برنامه</button>}
        </span>
      )}
      {error && <span className="field__error plan-scope__error" role="alert">{error}</span>}
    </span>
  );
}
