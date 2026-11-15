import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../shared/api/client';
import type { IsoDate } from '../../shared/date/calendar';
import { formatNumber } from '../../shared/format';
import { ErrorMessage } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { ProgressRing } from '../../shared/ui/ProgressRing';
import type { GoalProgress } from '../dashboard/types';
import { focusApi } from '../focus/api';
import type { FocusItem } from '../focus/types';
import { goalsApi } from './api';
import type { MoveControls } from './GoalBoard';
import { deadlineFlag, deadlineState } from './deadline';
import { FocusSortableList } from './FocusSortableList';
import { doneCount, dueLine, PERIOD_ICONS } from './goalBoardModel';
import { PERIOD_LABELS, STATUS_LABELS, STATUS_ORDER } from './labels';
import type { Goal, GoalStatus } from './types';

interface GoalBoardCardProps {
  goal: Goal;
  progress: GoalProgress | undefined;
  progressError: boolean;
  focusItems: FocusItem[];
  today: IsoDate;
  /** Something changed on the server: reload goals, progress and focus items. */
  onChanged: () => void;
  /** Board position (priority) and keyboard/touch alternatives to drag and drop. */
  controls: MoveControls;
}

/**
 * One goal of the board (design ui/goal.png): icon, title, ⋮ actions, Outcome ring, due date and the goal's focus
 * items as a checklist. Fixed height; the checklist scrolls inside the card.
 */
export function GoalBoardCard({ goal, progress, progressError, focusItems, today, onChanged, controls }: GoalBoardCardProps) {
  const archived = Boolean(goal.archivedAt);
  const due = dueLine(goal, today);
  const flag = due.isDeadline ? deadlineFlag(deadlineState(goal.deadline, goal.status, today)) : null;
  const headingId = useId();
  return (
    <article className={`goal-card${archived ? ' goal-card--archived' : ''}`} aria-labelledby={headingId}>
      <header className="goal-card__header">
        <span className="goal-card__icon">
          <Icon name={PERIOD_ICONS[goal.periodType]} size={28} />
        </span>
        <h2 id={headingId} className="goal-card__title">
          <Link to={`/goals/${goal.id}`}>{goal.title}</Link>
        </h2>
        <GoalMenu goal={goal} onChanged={onChanged} controls={controls} />
      </header>

      <div className="goal-card__summary">
        <ProgressRing value={progress?.outcomeProgress ?? null} size={84} stroke={7} showValue
          label={`پیشرفت نتیجهٔ «${goal.title}»`} />
        <div className="goal-card__facts">
          <p className={`goal-card__due${flag && flag.includes('گذشته') ? ' goal-card__due--late' : ''}`}>
            <Icon name="calendar" size={18} />
            <span>{due.text}</span>
          </p>
          {flag && <p className="goal-card__flag">{flag}</p>}
          <p className="goal-card__meta">
            {PERIOD_LABELS[goal.periodType]} · {STATUS_LABELS[goal.status]}
            {archived && ' · بایگانی‌شده'}
          </p>
          {archived ? (
            <p className="goal-card__meta">بدون اولویت (بایگانی‌شده)</p>
          ) : (
            <p className="goal-card__priority">{`اولویت ${formatNumber(controls.reorderable ? controls.rank : goal.priority ?? controls.rank)}`}</p>
          )}
          {progressError && <p className="goal-card__meta">پیشرفت بارگیری نشد</p>}
        </div>
      </div>

      <FocusChecklist goal={goal} items={focusItems} archived={archived} onChanged={onChanged} />
    </article>
  );
}

// -------------------------------------------------------------------------------------------- focus items checklist

function FocusChecklist({ goal, items, archived, onChanged }: { goal: Goal; items: FocusItem[]; archived: boolean; onChanged: () => void }) {
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const inputId = useId();
  async function add(event: FormEvent) {
    event.preventDefault();
    if (!title.trim() || saving) return;
    setSaving(true);
    setError(null);
    try {
      // quick add: kind "other" (سایر) — the kind can be changed on the focus item's own page
      await focusApi.create({ title: title.trim(), kind: 'other', status: 'not_started', goalId: goal.id });
      setTitle('');
      setAdding(false);
      onChanged();
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }
  return (
    <section className="goal-card__focus" aria-label={`آیتم‌های تمرکز «${goal.title}»`}>
      <h3 className="goal-card__section-title">
        <Icon name="layers" size={22} />
        <span>آیتم‌های تمرکز</span>
        {items.length > 0 && (
          <span className="goal-card__count">{`${formatNumber(doneCount(items))} از ${formatNumber(items.length)}`}</span>
        )}
      </h3>
      <div className="goal-card__list" tabIndex={0} role="group" aria-label={`فهرست آیتم‌های تمرکز «${goal.title}»`}>
        {items.length === 0 ? (
          <p className="dash-empty">هنوز آیتم تمرکزی به این هدف وصل نیست.</p>
        ) : (
          <FocusSortableList goalId={goal.id} items={items} onChanged={onChanged}
            renderRow={(item, handle) => <FocusRow item={item} onChanged={onChanged} handle={handle} />} />
        )}
      </div>
      {adding ? (
        <form className="dash-add-form" onSubmit={add}>
          <label htmlFor={inputId} className="visually-hidden">عنوان آیتم تمرکز</label>
          <input id={inputId} autoFocus value={title} maxLength={200} placeholder="عنوان آیتم تمرکز…"
            onChange={(e) => setTitle(e.target.value)} onKeyDown={(e) => e.key === 'Escape' && setAdding(false)} />
          <button type="submit" className="button" disabled={saving || !title.trim()}>{saving ? '…' : 'افزودن'}</button>
          <button type="button" className="button button--secondary" onClick={() => setAdding(false)}>انصراف</button>
          {error != null && <ErrorMessage error={error} />}
        </form>
      ) : (
        <button type="button" className="dash-add" disabled={archived} onClick={() => setAdding(true)}
          title={archived ? 'هدف بایگانی‌شده آیتم جدید نمی‌پذیرد' : undefined}>
          <Icon name="plus" size={22} />
          افزودن آیتم تمرکز
        </button>
      )}
    </section>
  );
}

function FocusRow({ item, onChanged, handle }: { item: FocusItem; onChanged: () => void; handle: ReactNode }) {
  const [done, setDone] = useState(item.status === 'done');
  const [synced, setSynced] = useState(item.status);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (synced !== item.status) {
    setSynced(item.status);
    setDone(item.status === 'done');
  }
  const id = useId();
  async function toggle() {
    const next = !done;
    setDone(next);
    setSaving(true);
    setError(null);
    try {
      await focusApi.update(item.id, { status: next ? 'done' : 'not_started' });
      onChanged();
    } catch (e) {
      setDone(!next);
      setError(e instanceof ApiError ? e.message : 'ذخیره نشد.');
    } finally {
      setSaving(false);
    }
  }
  return (
    <div className={`check-row focus-row${done ? ' check-row--done' : ''}`}>
      <input id={id} type="checkbox" className="check" checked={done} disabled={saving} onChange={toggle} />
      <label htmlFor={id} className="check-row__title">{item.title}</label>
      {handle}
      {error && <span className="field__error" role="alert">{error}</span>}
    </div>
  );
}

// -------------------------------------------------------------------------------------------- ⋮ menu

/** Accessible menu button (WAI-ARIA menu pattern): details link, manual status, archive/restore. */
function GoalMenu({ goal, onChanged, controls }: { goal: Goal; onChanged: () => void; controls: MoveControls }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLUListElement>(null);
  const menuId = useId();
  const archived = Boolean(goal.archivedAt);

  useEffect(() => {
    if (!open) return;
    menuRef.current?.querySelector<HTMLElement>('[role^="menuitem"]')?.focus();
    const onDoc = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node) && !buttonRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  function close() {
    setOpen(false);
    buttonRef.current?.focus();
  }

  function onKey(e: KeyboardEvent<HTMLUListElement>) {
    const items = [...(menuRef.current?.querySelectorAll<HTMLElement>('[role^="menuitem"]:not([aria-disabled="true"])') ?? [])];
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

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await action();
      close();
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'انجام نشد.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="goal-menu">
      <button ref={buttonRef} type="button" className="icon-button goal-menu__button" aria-haspopup="menu" aria-expanded={open}
        aria-controls={open ? menuId : undefined} aria-label={`گزینه‌های «${goal.title}»`} onClick={() => setOpen((v) => !v)}>
        <Icon name="more" size={22} />
      </button>
      {open && (
        <ul id={menuId} ref={menuRef} role="menu" className="goal-menu__list" aria-label={`گزینه‌های «${goal.title}»`} onKeyDown={onKey}>
          <li role="none">
            <Link role="menuitem" tabIndex={-1} to={`/goals/${goal.id}`} className="goal-menu__item">جزئیات و ویرایش</Link>
          </li>
          {controls.reorderable && (
            <>
              <li role="none">
                <button type="button" role="menuitem" tabIndex={-1} className="goal-menu__item" aria-disabled={!controls.moveEarlier}
                  disabled={!controls.moveEarlier} onClick={() => { controls.moveEarlier?.(); close(); }}>
                  → انتقال به راست (اولویت بالاتر)
                </button>
              </li>
              <li role="none">
                <button type="button" role="menuitem" tabIndex={-1} className="goal-menu__item" aria-disabled={!controls.moveLater}
                  disabled={!controls.moveLater} onClick={() => { controls.moveLater?.(); close(); }}>
                  ← انتقال به چپ (اولویت پایین‌تر)
                </button>
              </li>
            </>
          )}
          {!archived && (
            <li role="none" className="goal-menu__group" aria-hidden="false">
              <span className="goal-menu__label" id={`${menuId}-status`}>وضعیت (انتخاب شما)</span>
            </li>
          )}
          {!archived &&
            STATUS_ORDER.map((s: GoalStatus) => (
              <li role="none" key={s}>
                <button type="button" role="menuitemradio" tabIndex={-1} aria-checked={goal.status === s} className="goal-menu__item"
                  disabled={busy} onClick={() => (goal.status === s ? close() : run(() => goalsApi.update(goal.id, { status: s })))}>
                  <span className="goal-menu__mark" aria-hidden="true">{goal.status === s ? '●' : ''}</span>
                  {STATUS_LABELS[s]}
                </button>
              </li>
            ))}
          <li role="none">
            <button type="button" role="menuitem" tabIndex={-1} className="goal-menu__item goal-menu__item--danger" disabled={busy}
              onClick={() => {
                if (archived) void run(() => goalsApi.unarchive(goal.id));
                else if (window.confirm(`هدف «${goal.title}» بایگانی شود؟ زیرهدف‌ها، نتایج کلیدی و کارهایش حذف یا بایگانی نمی‌شوند.`)) {
                  void run(() => goalsApi.archive(goal.id));
                }
              }}>
              <Icon name="archive" size={18} />
              {archived ? 'بازگردانی از بایگانی' : 'بایگانی'}
            </button>
          </li>
          {error && (
            <li role="none" className="field__error" aria-live="assertive">
              {error}
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
