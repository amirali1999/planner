import { useId, useState } from 'react';
import { useNavigate } from 'react-router';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import { useApiData } from '../../shared/ui/useApiData';
import { focusApi } from '../focus/api';
import { EMPTY_FOCUS_FORM, toCreateRequest as toFocusRequest } from '../focus/focusFormModel';
import { FocusItemForm } from '../focus/FocusItemForm';
import { goalsApi } from '../goals/api';
import { GoalCreateForm } from '../goals/GoalCreateForm';
import { TaskForm } from '../tasks/TaskForm';
import { EMPTY_TASK_FORM, toCreateRequest as toTaskRequest } from '../tasks/taskFormModel';
import { inboxApi } from './api';
import { DECISION_HINTS, DECISION_LABELS, prefillFromNote, unprefixErrors } from './triageModel';
import type { InboxDecision, InboxItem } from './types';

const ORDER: InboxDecision[] = ['task', 'goal', 'focus_item', 'reference', 'someday', 'discard'];

/**
 * Explicit triage of one note. Conversions reuse the normal create forms; the backend creates the entity and marks the
 * note processed in one transaction. Discard needs a confirmation and keeps the text.
 */
export function TriagePanel({ item, onFiled, onCancel }: { item: InboxItem; onFiled: () => void; onCancel: () => void }) {
  const id = useId();
  const navigate = useNavigate();
  const [decision, setDecision] = useState<InboxDecision | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const goals = useApiData((signal) => goalsApi.list('all', signal), []);
  const focus = useApiData((signal) => focusApi.list({ archived: 'all' }, signal), []);
  const prefill = prefillFromNote(item.rawText);

  async function file(d: 'reference' | 'someday' | 'discard') {
    setBusy(true);
    setError(null);
    try {
      await inboxApi.process(item.id, { decision: d });
      onFiled();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="triage card" role="group" aria-label={`پردازش: ${prefill.title}`}>
      <fieldset className="triage__choices">
        <legend>این مورد چیست؟</legend>
        {ORDER.map((d) => (
          <label key={d} className="triage__choice">
            <input type="radio" name={`${id}-decision`} value={d} checked={decision === d} onChange={() => setDecision(d)} />
            <span>
              <strong>{DECISION_LABELS[d]}</strong> <span className="meta">— {DECISION_HINTS[d]}</span>
            </span>
          </label>
        ))}
      </fieldset>

      {decision === 'task' && (
        <TaskForm
          heading="تبدیل به کار"
          initial={{ ...EMPTY_TASK_FORM, title: prefill.title, description: prefill.description }}
          goals={goals.data ?? []}
          focusItems={focus.data ?? []}
          submitLabel="ایجاد کار و پردازش مورد"
          onSubmit={async (v) => {
            const res = await inboxApi.process(item.id, { decision: 'task', task: toTaskRequest(v) }).catch((e) => {
              throw unprefixErrors(e, 'task');
            });
            navigate(`/tasks?highlight=${res.createdEntityId}`);
          }}
        />
      )}
      {decision === 'goal' && (
        <GoalCreateForm
          heading="تبدیل به هدف"
          initialTitle={prefill.title}
          initialDescription={prefill.description}
          create={async (body) => {
            const res = await inboxApi.process(item.id, { decision: 'goal', goal: body }).catch((e) => {
              throw unprefixErrors(e, 'goal');
            });
            return { id: res.createdEntityId! };
          }}
          onCreated={(goalId) => navigate(`/goals/${goalId}`)}
        />
      )}
      {decision === 'focus_item' && (
        <FocusItemForm
          heading="تبدیل به آیتم تمرکز"
          initial={{ ...EMPTY_FOCUS_FORM, title: prefill.title, description: prefill.description }}
          goals={goals.data ?? []}
          submitLabel="ایجاد آیتم و پردازش مورد"
          onSubmit={async (v) => {
            const res = await inboxApi.process(item.id, { decision: 'focus_item', focusItem: toFocusRequest(v) }).catch((e) => {
              throw unprefixErrors(e, 'focusItem');
            });
            navigate(`/focus/${res.createdEntityId}`);
          }}
        />
      )}
      {(decision === 'reference' || decision === 'someday') && (
        <div className="form__actions">
          <button type="button" className="button" disabled={busy} onClick={() => file(decision)}>
            {busy ? 'در حال ثبت…' : `ثبت به‌عنوان «${DECISION_LABELS[decision]}»`}
          </button>
        </div>
      )}
      {decision === 'discard' && (
        <div className="state state--notice" role="alertdialog" aria-labelledby={`${id}-discard-title`}>
          <p id={`${id}-discard-title`} className="state__title">
            این مورد از صف حذف شود؟
          </p>
          <p>چیزی پاک نمی‌شود؛ متن در نمای «حذف‌شده‌ها» قابل مشاهده می‌ماند. این کار قابل برگشت به صف نیست.</p>
          <div className="form__actions">
            <button type="button" className="button" disabled={busy} onClick={() => file('discard')}>
              {busy ? 'در حال حذف…' : 'بله، از صف حذف شود'}
            </button>
            <button type="button" className="button button--secondary" onClick={() => setDecision(null)}>
              انصراف
            </button>
          </div>
        </div>
      )}
      {error != null && <ErrorMessage error={error} />}
      <div className="form__actions">
        <button type="button" className="button button--link" onClick={onCancel}>
          بستن پردازش
        </button>
      </div>
    </div>
  );
}
