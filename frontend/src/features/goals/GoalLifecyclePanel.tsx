import { useState } from 'react';
import { formatNumber } from '../../shared/format';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import { goalsApi } from './api';
import type { Goal } from './types';

interface Props {
  goal: Goal;
  activeChildCount: number;
  activeTaskCount: number | undefined;
  keyResultCount: number | undefined;
  onChanged: (goal: Goal) => void;
}

/** Archive with an explicit confirmation that states what is NOT archived; restore for archived goals. */
export function GoalLifecyclePanel({ goal, activeChildCount, activeTaskCount, keyResultCount, onChanged }: Props) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function run(action: () => Promise<Goal>) {
    setBusy(true);
    setError(null);
    try {
      onChanged(await action());
      setConfirming(false);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  if (goal.archivedAt) {
    return (
      <div className="state state--notice" role="status">
        <p>این هدف بایگانی شده است و فقط قابل مشاهده است. نتایج کلیدی، کارها و زیرهدف‌های آن دست‌نخورده باقی مانده‌اند.</p>
        <button type="button" className="button button--secondary" disabled={busy} onClick={() => run(() => goalsApi.unarchive(goal.id))}>
          خروج از بایگانی
        </button>
        {error != null && <ErrorMessage error={error} />}
      </div>
    );
  }

  if (!confirming) {
    return (
      <button type="button" className="button button--secondary" onClick={() => setConfirming(true)}>
        بایگانی هدف
      </button>
    );
  }

  return (
    <div className="state state--notice" role="alertdialog" aria-labelledby="archive-confirm-title" aria-describedby="archive-confirm-body">
      <p id="archive-confirm-title" className="state__title">
        بایگانی «{goal.title}»؟
      </p>
      <div id="archive-confirm-body">
        <p>هدف از فهرست اهداف فعال و داشبورد خارج می‌شود اما چیزی حذف نمی‌شود:</p>
        <ul>
          <li>{activeChildCount > 0 ? `${formatNumber(activeChildCount)} زیرهدف فعال بایگانی نمی‌شوند و پیوندشان به این هدف می‌ماند.` : 'زیرهدف فعالی ندارد.'}</li>
          {activeTaskCount != null && <li>{`${formatNumber(activeTaskCount)} کار مرتبط بدون تغییر وضعیت باقی می‌مانند.`}</li>}
          {keyResultCount != null && <li>{`${formatNumber(keyResultCount)} نتیجه کلیدی و تاریخچه اندازه‌گیری‌ها حفظ می‌شوند.`}</li>}
        </ul>
        <p>هر زمان بخواهید می‌توانید آن را از بایگانی خارج کنید.</p>
      </div>
      <div className="form__actions">
        <button type="button" className="button" disabled={busy} onClick={() => run(() => goalsApi.archive(goal.id))}>
          {busy ? 'در حال بایگانی…' : 'بله، بایگانی شود'}
        </button>
        <button type="button" className="button button--secondary" onClick={() => setConfirming(false)}>
          انصراف
        </button>
      </div>
      {error != null && <ErrorMessage error={error} />}
    </div>
  );
}
