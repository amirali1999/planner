import { useState } from 'react';
import { Link } from 'react-router';
import { formatTimestamp } from '../../shared/format';
import { DECISION_LABELS } from './triageModel';
import { TriagePanel } from './TriagePanel';
import type { InboxItem } from './types';

const ENTITY_LINK: Record<string, (id: string) => string> = {
  task: (id) => `/tasks?highlight=${id}`,
  goal: (id) => `/goals/${id}`,
  focus_item: (id) => `/focus/${id}`,
};

/** Plain text only: React escapes it; markup in a note is shown, never executed. */
export function InboxItemRow({ item, onChanged }: { item: InboxItem; onChanged?: () => void }) {
  const [triaging, setTriaging] = useState(false);
  const decision = item.decision as keyof typeof DECISION_LABELS | null;
  return (
    <li className="card inbox-item">
      <p className="inbox-item__text" dir="auto">
        {item.rawText}
      </p>
      <p className="meta">
        ثبت: <time dateTime={item.capturedAt}>{formatTimestamp(item.capturedAt)}</time>
        {item.status === 'unprocessed' ? (
          <span className="badge badge--muted">پردازش‌نشده — هنوز به هدف، کار یا آیتم تمرکزی تبدیل نشده</span>
        ) : (
          <>
            <span className="badge">پردازش‌شده: {decision ? DECISION_LABELS[decision] : '—'}</span>
            {item.processedAt && (
              <>
                {' · '}
                <time dateTime={item.processedAt}>{formatTimestamp(item.processedAt)}</time>
              </>
            )}
            {item.createdEntityType && item.createdEntityId && ENTITY_LINK[item.createdEntityType] && (
              <>
                {' · '}
                <Link to={ENTITY_LINK[item.createdEntityType](item.createdEntityId)}>مشاهده {decision ? DECISION_LABELS[decision] : ''} ساخته‌شده</Link>
              </>
            )}
          </>
        )}
      </p>
      {item.status === 'unprocessed' && onChanged && !triaging && (
        <button type="button" className="button button--secondary" onClick={() => setTriaging(true)}>
          پردازش
        </button>
      )}
      {triaging && (
        <TriagePanel
          item={item}
          onCancel={() => setTriaging(false)}
          onFiled={() => {
            setTriaging(false);
            onChanged?.();
          }}
        />
      )}
    </li>
  );
}
