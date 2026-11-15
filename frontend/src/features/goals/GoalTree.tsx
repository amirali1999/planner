import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { describePeriod } from '../../shared/date/periods';
import type { IsoDate } from '../../shared/date/calendar';
import { formatPercent } from '../../shared/format';
import { DeadlineBadge } from './DeadlineBadge';
import { deadlineFlag, deadlineState } from './deadline';
import { GoalStatusSelect } from './GoalStatusSelect';
import { PERIOD_LABELS, STATUS_LABELS } from './labels';
import { treeKeyAction, visibleItems } from './treeNav';
import type { GoalTreeNode } from './types';

interface GoalTreeProps {
  roots: GoalTreeNode[];
  expanded: ReadonlySet<string>;
  onToggle: (id: string, expand: boolean) => void;
  onActivate: (id: string) => void;
  /** User's local date for deadline flags. */
  today: IsoDate;
  /** Goals shown only as context for a filtered descendant (muted, no status editor). */
  contextIds?: ReadonlySet<string>;
  /** When given, active goals get an inline status select. */
  onStatusChanged?: () => void;
}

function metric(label: string, value: number | null) {
  return `${label}: ${value == null ? 'سنجیده نشده' : formatPercent(value)}`;
}

/**
 * Accessible RTL treeview (WAI-ARIA pattern) with one roving tab stop. Each item shows its two measurements
 * separately; nothing is combined.
 */
export function GoalTree({ roots, expanded, onToggle, onActivate, today, contextIds, onStatusChanged }: GoalTreeProps) {
  const items = visibleItems(roots, expanded);
  const [focusedId, setFocusedId] = useState<string | null>(items[0]?.node.id ?? null);
  const [moveFocus, setMoveFocus] = useState(false);
  const treeRef = useRef<HTMLUListElement>(null);
  const current = items.some((i) => i.node.id === focusedId) ? focusedId : (items[0]?.node.id ?? null);

  useEffect(() => {
    if (!moveFocus || !current) return;
    treeRef.current?.querySelector<HTMLElement>(`[data-goal-id="${current}"]`)?.focus();
    setMoveFocus(false);
  }, [current, moveFocus]);

  function onKeyDown(event: KeyboardEvent<HTMLLIElement>, id: string) {
    if (event.target !== event.currentTarget) return; // nested item handles its own keys
    const action = treeKeyAction(items, id, event.key, expanded);
    if (action.type === 'none') return;
    event.preventDefault();
    if (action.type === 'focus') {
      setFocusedId(action.id);
      setMoveFocus(true);
    } else if (action.type === 'expand' || action.type === 'collapse') {
      onToggle(action.id, action.type === 'expand');
    } else {
      onActivate(action.id);
    }
  }

  const render = (nodes: GoalTreeNode[], level: number) =>
    nodes.map((node, i) => {
      const hasChildren = node.children.length > 0;
      const isOpen = hasChildren && expanded.has(node.id);
      const isContext = contextIds?.has(node.id) ?? false;
      const flag = deadlineFlag(deadlineState(node.deadline, node.status, today));
      return (
        <li
          key={node.id}
          role="treeitem"
          data-goal-id={node.id}
          aria-level={level}
          aria-setsize={nodes.length}
          aria-posinset={i + 1}
          aria-expanded={hasChildren ? isOpen : undefined}
          aria-selected={node.id === current}
          aria-label={[
            node.title,
            PERIOD_LABELS[node.periodType],
            `وضعیت: ${STATUS_LABELS[node.status]}`,
            metric('نتیجه', node.outcomeProgress),
            metric('اجرا', node.executionProgress),
            ...(flag ? [flag] : []),
            ...(isContext ? ['خارج از فیلتر'] : []),
          ].join('، ')}
          tabIndex={node.id === current ? 0 : -1}
          className={isContext ? 'tree__item tree__item--context' : 'tree__item'}
          onKeyDown={(e) => onKeyDown(e, node.id)}
          onFocus={(e) => {
            if (e.target === e.currentTarget) setFocusedId(node.id);
          }}
        >
          <div className="tree__row" onClick={() => onActivate(node.id)}>
            {hasChildren ? (
              <span
                className="tree__toggle"
                aria-hidden="true"
                onClick={(e) => {
                  e.stopPropagation();
                  onToggle(node.id, !isOpen);
                }}
              >
                {isOpen ? '▾' : '◂'}
              </span>
            ) : (
              <span className="tree__toggle tree__toggle--leaf" aria-hidden="true">
                •
              </span>
            )}
            <span className="tree__title">{node.title}</span>
            <span className="badge">{PERIOD_LABELS[node.periodType]}</span>
            {onStatusChanged && !node.archivedAt && !isContext ? (
              <GoalStatusSelect goalId={node.id} goalTitle={node.title} status={node.status} onChanged={onStatusChanged} />
            ) : (
              <span className={`badge badge--status-${node.status}`}>{STATUS_LABELS[node.status]}</span>
            )}
            {isContext && <span className="badge badge--muted">خارج از فیلتر</span>}
            {node.archivedAt && <span className="badge badge--muted">بایگانی‌شده</span>}
            {node.detachedFromParent && <span className="badge badge--muted">والد بایگانی‌شده</span>}
            <span className="tree__meta">
              {describePeriod(node.periodType, node.periodStart, node.periodEnd)} · {metric('نتیجه', node.outcomeProgress)} ·{' '}
              {metric('اجرا', node.executionProgress)}
              {node.deadline && (
                <>
                  {' · '}
                  <DeadlineBadge deadline={node.deadline} status={node.status} today={today} />
                </>
              )}
            </span>
          </div>
          {isOpen && (
            <ul role="group" className="tree__group">
              {render(node.children, level + 1)}
            </ul>
          )}
        </li>
      );
    });

  return (
    <ul role="tree" aria-label="درخت اهداف" className="tree" ref={treeRef}>
      {render(roots, 1)}
    </ul>
  );
}
