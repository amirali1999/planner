import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { addDays, isIsoDate, todayIso, type IsoDate } from '../../shared/date/calendar';
import { formatJalaliDateWithWeekday, formatNumber, formatPercent } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { JalaliCalendar } from '../../shared/ui/JalaliCalendar';
import { MenuButton, type MenuItem } from '../../shared/ui/MenuButton';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { focusApi } from '../focus/api';
import type { FocusItem } from '../focus/types';
import { goalsApi } from '../goals/api';
import type { Goal } from '../goals/types';
import { tasksApi } from '../tasks/api';
import { TASK_PRIORITY_LABELS, TASK_PRIORITY_ORDER } from '../tasks/labels';
import { PriorityPicker } from '../tasks/PriorityPicker';
import type { Task, TaskPriority } from '../tasks/types';
import { AddTaskToPlan } from './AddTaskToPlan';
import { dailyPlanApi } from './api';
import { KanbanBoard, type HandleProps, type KanbanColumn } from './KanbanBoard';
import {
  BOARD_COLUMNS, buildBoard, COLUMN_LABELS, columnOf, goalColor, groupByColumn, matches, moveInPlan, patchForColumn,
  type BoardColumn, type BoardEntry,
} from './todayBoardModel';

/** A move waiting for the «در انتظار» reason. */
interface PendingMove { cardId: string; to: BoardColumn; index: number; task: Task }

/**
 * «تخته امروز» (design ui/today plan.png): a day as a Kanban board. Cards are only persisted items of the day's plan;
 * earlier unfinished tasks appear after the owner adds them from the dashboard's separate remaining-tasks card.
 * Columns انجام شده · در حال انجام · در انتظار · انجام نشده; dragging a card to another column changes the task (status,
 * or the waiting reason), dragging inside a column changes the plan order (PUT /plans/daily/{date}/items/order). Search,
 * column chips with counts, day navigation (?date=),
 * quick add per column and an add dialog (new or existing task).
 */
export function TodayBoardPage() {
  const [params, setParams] = useSearchParams();
  const today = todayIso();
  const requested = params.get('date');
  const date = requested && isIsoDate(requested) ? requested : today;
  const isToday = date === today;
  usePageTitle(isToday ? 'تخته امروز' : 'تخته روز');

  const plan = useApiData((signal) => dailyPlanApi.get(date, signal), [date]);
  const goals = useApiData((signal) => goalsApi.list('all', signal), []);
  const focusItems = useApiData((signal) => focusApi.list({ archived: 'all' }, signal), []);
  const reload = plan.reload;

  const loaded = plan.data?.date === date ? buildBoard(date, plan.data) : undefined;
  /** Optimistic cards while a move is saved; dropped as soon as fresh data arrives. */
  const [local, setLocal] = useState<{ date: IsoDate; entries: BoardEntry[] } | null>(null);
  useEffect(() => setLocal(null), [plan.data]);
  const entries = local?.date === date ? local.entries : loaded;

  const [query, setQuery] = useState('');
  const [only, setOnly] = useState<BoardColumn | null>(null);
  const [saving, setSaving] = useState(false);
  const [moveError, setMoveError] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const [pendingMove, setPendingMove] = useState<PendingMove | null>(null);
  const [adding, setAdding] = useState(false);

  // archived goals / focus items are hidden everywhere except the archive page
  const goalOf = (id: string | null) => (id ? goals.data?.find((g) => g.id === id && !g.archivedAt) : undefined);
  const itemOf = (id: string | null) => (id ? focusItems.data?.find((i) => i.id === id && !i.archivedAt) : undefined);
  const names = (t: Task) => [goalOf(t.effectiveGoalId)?.title ?? '', itemOf(t.focusItemId)?.title ?? ''];

  const byColumn = entries ? groupByColumn(entries) : null;
  const shown = byColumn
    ? (Object.fromEntries(BOARD_COLUMNS.map((c) => [c, byColumn[c].filter((e) => matches(e.task, query, names(e.task)))])) as Record<BoardColumn, BoardEntry[]>)
    : null;
  const total = shown ? BOARD_COLUMNS.reduce((n, c) => n + shown[c].length, 0) : 0;
  const entryById = new Map((entries ?? []).map((e) => [e.id, e]));
  const doneCount = byColumn?.done.length ?? 0;
  const cardCount = entries?.length ?? 0;

  const goDate = (iso: IsoDate) => setParams(iso === today ? {} : { date: iso }, { replace: true });

  async function move(cardId: string, to: BoardColumn, index: number, reason?: string) {
    if (!entries || !shown || saving || plan.data?.date !== date) return;
    const entry = entryById.get(cardId);
    if (!entry) return;
    if (to === 'waiting' && columnOf(entry.task) !== 'waiting' && reason === undefined) {
      setPendingMove({ cardId, to, index, task: entry.task });
      return;
    }
    const patch = patchForColumn(entry.task, to, reason);
    // the order covers EVERY item of the day's plan (hidden ones keep their place)
    const allIds = [...plan.data.items].sort((a, b) => a.position - b.position).map((i) => i.id);
    const targets = shown[to].filter((e) => e.id !== cardId).map((e) => e.planItemId);
    const order = moveInPlan(allIds, entry.planItemId, targets, Math.min(index, targets.length));
    if (!patch && order.join() === allIds.join()) return;
    const moved: BoardEntry = { ...entry, task: patch ? ({ ...entry.task, ...patch } as Task) : entry.task };
    const byPlanId = new Map(entries.map((e) => [e.planItemId, e]));
    byPlanId.set(entry.planItemId, moved);
    setLocal({
      date,
      entries: order.flatMap((id) => (byPlanId.has(id) ? [byPlanId.get(id)!] : [])),
    });
    setSaving(true);
    setMoveError(null);
    try {
      if (patch) await tasksApi.update(entry.task.id, patch);
      await dailyPlanApi.reorder(date, order);
      setAnnouncement(`«${entry.task.title}» به ستون «${COLUMN_LABELS[to]}» رفت.`);
    } catch (e) {
      setLocal(null);
      setMoveError(e instanceof ApiError ? e.message : 'جابه‌جایی ذخیره نشد.');
    } finally {
      reload();
      setSaving(false);
      requestAnimationFrame(() => document.querySelector<HTMLElement>(`[data-kb-card] [aria-label="${CSS.escape(entry.task.title)}"] [data-kb-handle]`)?.focus({ preventScroll: true }));
    }
  }

  const visibleColumns = only ? [only] : BOARD_COLUMNS;
  const columns: KanbanColumn<BoardColumn>[] = shown
    ? visibleColumns.map((c) => ({
        id: c,
        label: COLUMN_LABELS[c],
        cardIds: shown[c].map((e) => e.id),
        header: (
          <h2 className="kb-col__head">
            <span className={`kb-dot kb-dot--${c}`} aria-hidden="true" />
            {COLUMN_LABELS[c]}
            <span className={`kb-col__count kb-col__count--${c}`}>{formatNumber(shown[c].length)}</span>
          </h2>
        ),
        footer: <ColumnAdd column={c} date={date} onAdded={reload} />,
        empty: query ? 'موردی پیدا نشد.' : 'تسکی اینجا نیست — کارت را به این ستون بکشید.',
      }))
    : [];
  const error = plan.error;

  return (
    <section className="today-board" aria-labelledby="today-heading">
      <header className="today-board__top">
        <h1 id="today-heading"><Icon name="calendar" size={32} className="dash-card__icon" /> {isToday ? 'تخته امروز' : 'تخته روز'}</h1>
        <div className="focus-page__search today-board__search">
          <label htmlFor="today-search" className="visually-hidden">جستجو در تسک‌های این روز</label>
          <Icon name="search" size={20} />
          <input id="today-search" type="search" placeholder={isToday ? 'جستجو در تسک‌های امروز…' : 'جستجو در تسک‌های این روز…'}
            value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <button type="button" className="button goals-page__add" aria-haspopup="dialog" onClick={() => setAdding(true)}>
          <Icon name="plus" size={20} /> {isToday ? 'افزودن تسک امروز' : 'افزودن تسک به این روز'}
        </button>
      </header>

      <div className="today-board__bar">
        <div className="kb-chips" role="group" aria-label="نمایش ستون‌ها">
          <button type="button" className={`kb-chip${only === null ? ' kb-chip--on' : ''}`} aria-pressed={only === null} onClick={() => setOnly(null)}>
            <Icon name="focus" size={18} /> همه <span>{formatNumber(total)}</span>
          </button>
          {BOARD_COLUMNS.map((c) => (
            <button key={c} type="button" className={`kb-chip${only === c ? ' kb-chip--on' : ''}`} aria-pressed={only === c}
              onClick={() => setOnly(only === c ? null : c)}>
              <span className={`kb-dot kb-dot--${c}`} aria-hidden="true" /> {COLUMN_LABELS[c]} <span>{formatNumber(shown?.[c].length ?? 0)}</span>
            </button>
          ))}
        </div>
        <DayNav date={date} today={today} onChange={goDate} />
      </div>

      {entries && cardCount > 0 && (
        <p className="meta today-board__progress" aria-live="polite">
          پیشرفت روز: <strong>{formatPercent((doneCount * 100) / cardCount)}</strong> — {formatNumber(doneCount)} از{' '}
          {formatNumber(cardCount)} تسک انجام شده
        </p>
      )}
      {moveError && <p className="state state--error" role="alert">{moveError}</p>}
      {error != null && <ErrorMessage error={error} onRetry={reload} />}
      {error == null && !entries && <Loading />}
      {entries && (
        <KanbanBoard
          label={`تخته ${formatJalaliDateWithWeekday(date)}`}
          columns={columns}
          disabled={saving}
          onMove={(id, to, index) => void move(id, to, index)}
          renderCard={(id, handle) => {
            const entry = entryById.get(id);
            if (!entry) return null;
            const col = shown![columnOf(entry.task)];
            return (
              <BoardCard entry={entry} handle={handle} goal={goalOf(entry.task.effectiveGoalId)} focusItem={itemOf(entry.task.focusItemId)}
                onMove={(to, index) => void move(id, to, index)} positionIn={col.findIndex((e) => e.id === id)}
                columnSize={col.length} onChanged={reload}
                onEditReason={() => setPendingMove({ cardId: id, to: 'waiting', index: shown!.waiting.findIndex((e) => e.id === id), task: entry.task })} />
            );
          }}
        />
      )}
      <p className="visually-hidden" role="status" aria-live="polite">{announcement}</p>

      <ReasonDialog
        pending={pendingMove}
        onCancel={() => setPendingMove(null)}
        onConfirm={async (reason) => {
          const p = pendingMove!;
          setPendingMove(null);
          if (columnOf(p.task) === 'waiting') {
            // only the reason changes
            try {
              await tasksApi.update(p.task.id, { blockedReason: reason });
              reload();
            } catch (e) {
              setMoveError(e instanceof ApiError ? e.message : 'ذخیره نشد.');
            }
          } else {
            await move(p.cardId, p.to, p.index, reason);
          }
        }}
      />
      <AddTodayTaskDialog open={adding} date={date} isToday={isToday} goals={(goals.data ?? []).filter((g) => !g.archivedAt)}
        items={(focusItems.data ?? []).filter((i) => !i.archivedAt)} planned={new Set((plan.data?.date === date ? plan.data.items : []).map((i) => i.task.id))}
        onClose={() => setAdding(false)} onAdded={reload} />
    </section>
  );
}

function DayNav({ date, today, onChange }: { date: IsoDate; today: IsoDate; onChange: (d: IsoDate) => void }) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const outside = (e: MouseEvent) => !box.current?.contains(e.target as Node) && setOpen(false);
    document.addEventListener('mousedown', outside);
    return () => document.removeEventListener('mousedown', outside);
  }, [open]);
  return (
    <div className="day-pick" ref={box} role="group" aria-label="انتخاب روز">
      <button type="button" className="icon-button" aria-label="روز قبل" onClick={() => onChange(addDays(date, -1))}>
        <Icon name="chevron-right" size={22} />
      </button>
      <button type="button" className="day-pick__label" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <Icon name="calendar" size={20} />
        <time dateTime={date}>{formatJalaliDateWithWeekday(date)}</time>
      </button>
      <button type="button" className="icon-button" aria-label="روز بعد" onClick={() => onChange(addDays(date, 1))}>
        <Icon name="chevron-left" size={22} />
      </button>
      {date !== today && <button type="button" className="button button--secondary day-pick__today" onClick={() => onChange(today)}>امروز</button>}
      {open && (
        <div className="weekly-pop__panel day-pick__panel">
          <JalaliCalendar label="انتخاب روز" selected={date} autoFocus onEscape={() => setOpen(false)}
            onSelect={(d) => { onChange(d); setOpen(false); }} />
        </div>
      )}
    </div>
  );
}

function BoardCard({ entry, handle, goal, focusItem, onMove, positionIn, columnSize, onChanged, onEditReason }: {
  entry: BoardEntry; handle: HandleProps | null; goal: Goal | undefined; focusItem: FocusItem | undefined;
  onMove: (to: BoardColumn, index: number) => void; positionIn: number; columnSize: number; onChanged: () => void; onEditReason: () => void;
}) {
  const t = entry.task;
  const col = columnOf(t);
  const menu: MenuItem[] = [
    ...BOARD_COLUMNS.filter((c) => c !== col).map((c) => ({ label: `انتقال به «${COLUMN_LABELS[c]}»`, onSelect: () => onMove(c, 0) })),
    { label: 'بالاتر', disabled: positionIn <= 0, onSelect: () => onMove(col, positionIn - 1) },
    { label: 'پایین‌تر', disabled: positionIn >= columnSize - 1, onSelect: () => onMove(col, positionIn + 1) },
    ...(col === 'waiting' ? [{ label: 'ویرایش دلیل انتظار', onSelect: onEditReason }] : []),
    { label: 'باز کردن در کارها', to: `/tasks?highlight=${t.id}` },
  ];
  return (
    <article className={`kb-card kb-card--${col}`} aria-label={t.title}>
      <h3 className="kb-card__title"><Link to={`/tasks?highlight=${t.id}`}>{t.title}</Link></h3>
      {(goal || focusItem) && (
        <p className="kb-card__meta">
          {(goal || focusItem) && <span className="kb-goal-dot" style={{ background: goal ? goalColor(goal.id) : 'var(--muted)' }} aria-hidden="true" />}
          {goal && <span>{goal.title}</span>}
          {goal && focusItem && <span aria-hidden="true">•</span>}
          {focusItem && <span>{focusItem.title}</span>}
        </p>
      )}
      {col === 'waiting' && t.blockedReason && <p className="kb-card__reason"><Icon name="pause" size={14} /> {t.blockedReason}</p>}
      <div className="kb-card__menu">{handle && <MenuButton label={`گزینه‌های «${t.title}»`} items={menu} />}</div>
      <div className="kb-card__prio">
        {handle ? <PriorityPicker task={t} onChanged={onChanged} /> : <span className={`prio prio--${t.priority ?? 'low'}`}>{TASK_PRIORITY_LABELS[t.priority ?? 'low']}</span>}
      </div>
      {handle ? (
        <button type="button" className="kb-card__grip" {...handle}
          aria-label={`جابه‌جایی «${t.title}» — ستون ${COLUMN_LABELS[col]}، جایگاه ${formatNumber(positionIn + 1)} از ${formatNumber(columnSize)}؛ بالا/پایین در ستون، چپ/راست بین ستون‌ها`}
          title="بکشید، یا با کلیدهای جهت‌نما جابه‌جا کنید">
          <Icon name="grip" size={18} />
        </button>
      ) : (
        <span className="kb-card__grip"><Icon name="grip" size={18} /></span>
      )}
    </article>
  );
}

/** «+ افزودن تسک» at the bottom of a column: creates a task in that column and puts it on the day's plan. */
function ColumnAdd({ column, date, onAdded }: { column: BoardColumn; date: IsoDate; onAdded: () => void }) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const id = `kb-add-${column}`;
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim()) return setError('عنوان را بنویسید.');
    if (column === 'waiting' && !reason.trim()) return setError('دلیل انتظار را بنویسید.');
    setSaving(true);
    setError(null);
    try {
      const task = await tasksApi.create({
        title: title.trim(), status: column === 'waiting' ? 'not_started' : column, priority: 'low',
        ...(column === 'waiting' && { blockedReason: reason.trim() }),
      });
      await dailyPlanApi.addTask(date, task.id);
      setTitle('');
      setReason('');
      onAdded();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'اضافه نشد.');
    } finally {
      setSaving(false);
    }
  }
  if (!open) {
    return (
      <div className="kb-col__foot">
        <button type="button" className="kb-add" onClick={() => setOpen(true)}><Icon name="plus" size={18} /> افزودن تسک</button>
      </div>
    );
  }
  return (
    <form className="kb-col__foot kb-add-form" onSubmit={submit} onKeyDown={(e) => e.key === 'Escape' && setOpen(false)} noValidate>
      <label htmlFor={id} className="visually-hidden">{`عنوان تسک جدید در «${COLUMN_LABELS[column]}»`}</label>
      <input id={id} autoFocus placeholder="عنوان تسک…" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
      {column === 'waiting' && (
        <>
          <label htmlFor={`${id}-reason`} className="visually-hidden">دلیل انتظار</label>
          <input id={`${id}-reason`} placeholder="منتظر چه چیزی؟" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
        </>
      )}
      {error && <p className="field__error" role="alert">{error}</p>}
      <div className="kb-add-form__actions">
        <button type="submit" className="button" disabled={saving}>{saving ? '…' : 'افزودن'}</button>
        <button type="button" className="button button--secondary" onClick={() => { setOpen(false); setError(null); }}>انصراف</button>
      </div>
    </form>
  );
}

/** Asks why the task is waiting (moving a card into «در انتظار», or editing the reason). */
function ReasonDialog({ pending, onCancel, onConfirm }: { pending: PendingMove | null; onCancel: () => void; onConfirm: (reason: string) => void | Promise<void> }) {
  const ref = useRef<HTMLDialogElement>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState(false);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (pending && !d.open) {
      setReason(pending.task.blockedReason ?? '');
      setError(false);
      d.showModal();
    }
    if (!pending && d.open) d.close();
  }, [pending]);
  return (
    <dialog ref={ref} className="modal" aria-labelledby="reason-heading" onClose={() => pending && onCancel()}
      onClick={(e) => e.target === e.currentTarget && onCancel()}>
      {pending && (
        <form className="card form modal__body" noValidate onSubmit={(e) => {
          e.preventDefault();
          if (!reason.trim()) return setError(true);
          void onConfirm(reason.trim());
        }}>
          <button type="button" className="icon-button modal__close" aria-label="بستن" onClick={onCancel}>✕</button>
          <h2 id="reason-heading">در انتظار چه چیزی؟</h2>
          <p className="meta">«{pending.task.title}» به ستون «در انتظار» می‌رود. وضعیت تسک عوض نمی‌شود؛ فقط دلیل انتظار ثبت می‌شود.</p>
          <div className="field">
            <label htmlFor="reason-input">دلیل انتظار <span aria-hidden="true">*</span></label>
            <input id="reason-input" autoFocus value={reason} maxLength={500} aria-invalid={error || undefined}
              aria-describedby={error ? 'reason-error' : undefined} onChange={(e) => { setReason(e.target.value); setError(false); }} />
            {error && <p id="reason-error" className="field__error">دلیل انتظار را بنویسید.</p>}
          </div>
          <div className="form__actions">
            <button type="submit" className="button">ثبت</button>
            <button type="button" className="button button--secondary" onClick={onCancel}>انصراف</button>
          </div>
        </form>
      )}
    </dialog>
  );
}

/** «افزودن تسک امروز»: a new task (in a chosen column) or an existing one, put on the day's plan. */
function AddTodayTaskDialog({ open, date, isToday, goals, items, planned, onClose, onAdded }: {
  open: boolean; date: IsoDate; isToday: boolean; goals: Goal[]; items: FocusItem[]; planned: Set<string>; onClose: () => void; onAdded: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [title, setTitle] = useState('');
  const [column, setColumn] = useState<BoardColumn>('not_started');
  const [reason, setReason] = useState('');
  const [priority, setPriority] = useState<TaskPriority>('low');
  const [goalId, setGoalId] = useState('');
  const [focusItemId, setFocusItemId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [fieldError, setFieldError] = useState<'title' | 'reason' | null>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) {
      setTitle(''); setColumn('not_started'); setReason(''); setPriority('low'); setGoalId(''); setFocusItemId(''); setError(null); setFieldError(null);
      d.showModal();
    }
    if (!open && d.open) d.close();
  }, [open]);
  const itemChoices = goalId ? items.filter((i) => i.goalId === goalId) : items;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim()) return setFieldError('title');
    if (column === 'waiting' && !reason.trim()) return setFieldError('reason');
    setSaving(true);
    setError(null);
    try {
      const task = await tasksApi.create({
        title: title.trim(), status: column === 'waiting' ? 'not_started' : column, priority,
        ...(column === 'waiting' && { blockedReason: reason.trim() }),
        ...(goalId && { goalId }), ...(focusItemId && { focusItemId }),
      });
      await dailyPlanApi.addTask(date, task.id);
      onAdded();
      onClose();
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }
  return (
    <dialog ref={ref} className="modal" aria-labelledby="today-add-heading" onClose={onClose} onClick={(e) => e.target === e.currentTarget && onClose()}>
      {open && (
        <div className="modal__body">
          <button type="button" className="icon-button modal__close" aria-label="بستن" onClick={onClose}>✕</button>
          <form className="card form" onSubmit={submit} noValidate>
            <h2 id="today-add-heading">{isToday ? 'افزودن تسک امروز' : 'افزودن تسک به این روز'}</h2>
            <div className="field">
              <label htmlFor="ta-title">عنوان تسک <span aria-hidden="true">*</span></label>
              <input id="ta-title" autoFocus value={title} maxLength={200} aria-invalid={fieldError === 'title' || undefined}
                aria-describedby={fieldError === 'title' ? 'ta-title-error' : undefined} onChange={(e) => { setTitle(e.target.value); setFieldError(null); }} />
              {fieldError === 'title' && <p id="ta-title-error" className="field__error">این فیلد الزامی است.</p>}
            </div>
            <div className="form__row">
              <div className="field">
                <label htmlFor="ta-column">ستون</label>
                <select id="ta-column" value={column} onChange={(e) => setColumn(e.target.value as BoardColumn)}>
                  {[...BOARD_COLUMNS].reverse().map((c) => <option key={c} value={c}>{COLUMN_LABELS[c]}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor="ta-priority">اولویت</label>
                <select id="ta-priority" value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
                  {TASK_PRIORITY_ORDER.map((p) => <option key={p} value={p}>{TASK_PRIORITY_LABELS[p]}</option>)}
                </select>
              </div>
            </div>
            {column === 'waiting' && (
              <div className="field">
                <label htmlFor="ta-reason">دلیل انتظار <span aria-hidden="true">*</span></label>
                <input id="ta-reason" value={reason} maxLength={500} aria-invalid={fieldError === 'reason' || undefined}
                  aria-describedby={fieldError === 'reason' ? 'ta-reason-error' : undefined} onChange={(e) => { setReason(e.target.value); setFieldError(null); }} />
                {fieldError === 'reason' && <p id="ta-reason-error" className="field__error">دلیل انتظار را بنویسید.</p>}
              </div>
            )}
            <div className="form__row">
              <div className="field">
                <label htmlFor="ta-goal">هدف مرتبط (اختیاری)</label>
                <select id="ta-goal" value={goalId} onChange={(e) => { setGoalId(e.target.value); setFocusItemId(''); }}>
                  <option value="">بدون هدف</option>
                  {goals.map((g) => <option key={g.id} value={g.id}>{g.title}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor="ta-focus">آیتم تمرکز (اختیاری)</label>
                <select id="ta-focus" value={focusItemId} onChange={(e) => setFocusItemId(e.target.value)}>
                  <option value="">بدون آیتم</option>
                  {itemChoices.map((i) => <option key={i.id} value={i.id}>{i.title}</option>)}
                </select>
              </div>
            </div>
            {error != null && <ErrorMessage error={error} />}
            <div className="form__actions">
              <button type="submit" className="button" disabled={saving}>{saving ? 'در حال ذخیره…' : 'ساخت و افزودن'}</button>
              <button type="button" className="button button--secondary" onClick={onClose}>انصراف</button>
            </div>
          </form>
          <section className="card today-add__existing" aria-label="افزودن تسک موجود">
            <h3>یا یک تسک موجود</h3>
            <AddTaskToPlan key={date} date={date} plannedTaskIds={planned} onAdded={onAdded} />
          </section>
        </div>
      )}
    </dialog>
  );
}
