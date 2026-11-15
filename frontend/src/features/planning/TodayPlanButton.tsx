import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { todayIso } from '../../shared/date/calendar';
import { Icon } from '../../shared/ui/Icon';
import { dailyPlanApi } from './api';

/** «به برنامه امروز»: one click puts the task on today's daily plan; shows ✓ once it is there. */
export function TodayPlanButton({ taskId, taskTitle, planned, disabled, onPlanned, short, alsoPlanWeek }: {
  taskId: string; taskTitle: string; planned: boolean; disabled?: boolean; onPlanned?: () => void;
  /** «+ امروز» instead of «+ به برنامه امروز» (the column header says the rest). */
  short?: boolean;
  /** Also establish the task's undated membership in the current week, atomically with today's plan. */
  alsoPlanWeek?: boolean;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (planned) {
    return (
      <span className="plan-btn plan-btn--done" title="این کار در برنامهٔ امروز هست">
        <Icon name="check" size={14} /> {short ? 'امروز' : 'در برنامه امروز'}
      </span>
    );
  }
  async function add() {
    setBusy(true);
    setError(null);
    try {
      await dailyPlanApi.addTask(todayIso(), taskId, alsoPlanWeek);
      onPlanned?.();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'اضافه نشد.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <span className="plan-btn-wrap">
      <button type="button" className="plan-btn plan-btn--today" aria-label={`افزودن «${taskTitle}» به برنامهٔ امروز`} disabled={disabled || busy}
        onClick={() => void add()}>
        <Icon name="plus" size={14} /> {short ? 'امروز' : 'به برنامه امروز'}
      </button>
      {error && <span className="field__error plan-btn__msg" role="alert">{error}</span>}
    </span>
  );
}
