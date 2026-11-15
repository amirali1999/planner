import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { todayIso } from '../../shared/date/calendar';
import { Icon } from '../../shared/ui/Icon';
import { weeklyPlanApi } from './api';
import { weekStart } from './weeklyModel';

/**
 * «به برنامه هفتگی»: one click puts the task on the current Saturday–Friday week without choosing a day
 * (POST /plans/weekly/{weekStart}/items); shows ✓ once it is there. A day can still be planned on the weekly page.
 */
export function WeekPlanButton({ taskId, taskTitle, planned, disabled, onPlanned }: {
  taskId: string; taskTitle: string; planned: boolean; disabled?: boolean; onPlanned?: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (planned) {
    return (
      <span className="plan-btn plan-btn--done" title="این کار در برنامهٔ این هفته هست">
        <Icon name="check" size={14} /> در برنامه هفتگی
      </span>
    );
  }
  async function add() {
    setBusy(true);
    setError(null);
    try {
      await weeklyPlanApi.addTask(weekStart(todayIso()), taskId);
      onPlanned?.();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'اضافه نشد.');
    } finally {
      setBusy(false);
    }
  }
  return (
    <span className="plan-btn-wrap">
      <button type="button" className="plan-btn" aria-label={`افزودن «${taskTitle}» به برنامهٔ این هفته (بدون تعیین روز)`}
        disabled={disabled || busy} onClick={() => void add()}>
        <Icon name="plus" size={14} /> به برنامه هفتگی
      </button>
      {error && <span className="field__error plan-btn__msg" role="alert">{error}</span>}
    </span>
  );
}
