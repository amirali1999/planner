import { useEffect, useId, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { addDays, type IsoDate } from '../../shared/date/calendar';
import { currentTimeZone, dateInZone, timeInZone } from '../../shared/date/timeZone';
import { formatJalaliDate, formatNumber } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { MenuButton } from '../../shared/ui/MenuButton';
import { useApiData } from '../../shared/ui/useApiData';
import type { Goal } from '../goals/types';
import type { Task } from '../tasks/types';
import { timeBlocksApi } from './api';
import {
  BLOCK_COLORS, blockTitle, clamp, COLOR_LABELS, colorForGoal, DAY_MINUTES, durationLabel, GRID_END, GRID_START, minutesByTask, moved,
  overlapping, resized, snap, SNAP, timeLabel, timeOptions, visibleBlocks, type BlockColor, type TimeBlock, type TimeBlockInput,
} from './timeboxModel';
import { goalColor } from './todayBoardModel';

const HOUR_PX = 56;
const PX_PER_MIN = HOUR_PX / 60;
const THRESHOLD = 4;
const EDGE = 48;

interface Span { date: IsoDate; startMinute: number; endMinute: number }

/** A pointer gesture on the grid: moving/resizing a block, or dropping a task from the side panel. */
interface Gesture {
  kind: 'move' | 'resize' | 'task';
  pointerId: number;
  x0: number;
  y0: number;
  block?: TimeBlock;
  task?: Task;
  /** Pointer passed the threshold (otherwise it is a click). */
  active: boolean;
  x: number;
  y: number;
  preview: Span | null;
}

/** What the block dialog edits. */
type DialogState =
  | { mode: 'create'; span: Span; taskId: string | null }
  | { mode: 'edit'; block: TimeBlock }
  | null;

interface Props {
  start: IsoDate;
  days: { date: IsoDate; label: string }[];
  today: IsoDate;
  /** Unfinished tasks of the week (side panel), in the weekly page's order. */
  weekTasks: Task[];
  /** Every active task (dialog choices). */
  allTasks: Task[];
  goalOf: (id: string | null) => Goal | undefined;
  /** Opens the task details pop-up. */
  onOpenTask: (taskId: string) => void;
  /** «افزودن بلاک زمانی» in the page toolbar was pressed (incrementing counter). */
  addRequest: number;
}

/**
 * «تایم‌باکس» view of the weekly plan: a Saturday–Friday × 06:00–24:00 grid of time blocks (GET/POST/PATCH/DELETE
 * /time-blocks) and the week's tasks beside it. Click an empty slot or «افزودن بلاک زمانی» to add a block; drag a task
 * from the panel (⠿) onto the grid; drag a block to move it (also to another day), drag its lower edge to resize.
 * Keyboard on a focused block: ↑/↓ 15 minutes, ←/→ day, Shift+↑/↓ length, Enter edit, Delete remove; ⋮ menu too.
 * Blocks of a day never overlap; a block never changes its task.
 */
export function TimeboxView({ start, days, today, weekTasks, allTasks, goalOf, addRequest, onOpenTask }: Props) {
  const end = addDays(start, 6);
  const blocks = useApiData((signal) => timeBlocksApi.list(start, end, signal), [start]);
  const [local, setLocal] = useState<TimeBlock[] | null>(null);
  useEffect(() => setLocal(null), [blocks.data]);
  const all = local ?? blocks.data;
  const shown = all ? visibleBlocks(all) : null;
  const planned = shown ? minutesByTask(shown) : new Map<string, number>();

  const [dialog, setDialog] = useState<DialogState>(null);
  const [error, setError] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const [saving, setSaving] = useState(false);
  const [gesture, setGestureState] = useState<Gesture | null>(null);
  /** Always the latest gesture: pointer events can arrive before React re-renders. */
  const gestureRef = useRef<Gesture | null>(null);
  const setGesture = (g: Gesture | null) => {
    gestureRef.current = g;
    setGestureState(g);
  };
  const wrap = useRef<HTMLDivElement>(null);
  const hintId = useId();

  // «افزودن بلاک زمانی» from the toolbar: the next free hour of today (or Saturday) from 09:00
  const lastAdd = useRef(addRequest);
  useEffect(() => {
    if (addRequest === lastAdd.current) return;
    lastAdd.current = addRequest;
    const date = days.some((d) => d.date === today) ? today : start;
    let s = 9 * 60;
    while (s < GRID_END - 60 && shown && overlapping(shown, date, s, s + 60)) s += 30;
    setDialog({ mode: 'create', span: { date, startMinute: s, endMinute: s + 60 }, taskId: null });
  }, [addRequest]); // eslint-disable-line react-hooks/exhaustive-deps

  // first view: scroll to 08:00, or to the earliest block of the week if that is earlier
  const scrolledFor = useRef<string | null>(null);
  useLayoutEffect(() => {
    if (!shown || !wrap.current || scrolledFor.current === start) return;
    scrolledFor.current = start;
    const earliest = Math.min(8 * 60, ...shown.map((b) => b.startMinute));
    wrap.current.scrollTop = Math.max(0, (earliest - GRID_START - 30) * PX_PER_MIN);
  });

  // current time line (today's column), refreshed every minute
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(t);
  }, []);
  const zone = currentTimeZone();
  const nowDate = dateInZone(now, zone);
  const [nh, nm] = timeInZone(now, zone).split(':').map(Number);
  const nowMinute = nh * 60 + nm;

  async function save(op: () => Promise<unknown>, optimistic: TimeBlock[] | null, done: string) {
    if (optimistic) setLocal(optimistic);
    setSaving(true);
    setError(null);
    try {
      await op();
      setAnnouncement(done);
    } catch (e) {
      setLocal(null);
      setError(e instanceof ApiError ? e.message : 'ذخیره نشد.');
    } finally {
      setSaving(false);
      blocks.reload();
    }
  }

  function moveBlock(b: TimeBlock, span: Span) {
    if (!all || (span.date === b.date && span.startMinute === b.startMinute && span.endMinute === b.endMinute)) return;
    if (shown && overlapping(shown, span.date, span.startMinute, span.endMinute, b.id)) {
      setError('این بازه با بلاک دیگری در همان روز هم‌پوشانی دارد.');
      return;
    }
    void save(() => timeBlocksApi.update(b.id, span), all.map((x) => (x.id === b.id ? { ...x, ...span } : x)),
      `«${blockTitle(b)}»: ${dayName(span.date)} ${timeLabel(span.startMinute)} تا ${timeLabel(span.endMinute)}`);
  }

  function createFromTask(task: Task, span: Span) {
    if (shown && overlapping(shown, span.date, span.startMinute, span.endMinute)) {
      setError('این بازه با بلاک دیگری در همان روز هم‌پوشانی دارد.');
      return;
    }
    const input: TimeBlockInput = { ...span, taskId: task.id, color: colorForGoal(task.effectiveGoalId) };
    const temp: TimeBlock = { id: `new:${task.id}`, title: null, taskId: task.id, task, color: input.color!, createdAt: '', updatedAt: '', ...span };
    void save(() => timeBlocksApi.create(input), [...(all ?? []), temp], `«${task.title}» زمان‌بندی شد.`);
  }

  function removeBlock(b: TimeBlock) {
    void save(() => timeBlocksApi.remove(b.id), (all ?? []).filter((x) => x.id !== b.id), `«${blockTitle(b)}» حذف شد.`);
  }

  const dayName = (date: IsoDate) => days.find((d) => d.date === date)?.label ?? formatJalaliDate(date);

  // ------------------------------------------------------------------------------------------- pointer gestures
  function dayAt(x: number): HTMLElement | null {
    const cols = [...(wrap.current?.querySelectorAll<HTMLElement>('[data-tb-day]') ?? [])];
    let best: HTMLElement | null = null;
    let bestDist = Infinity;
    for (const c of cols) {
      const r = c.getBoundingClientRect();
      const d = x < r.left ? r.left - x : x > r.right ? x - r.right : 0;
      if (d < bestDist) { best = c; bestDist = d; }
    }
    return best;
  }
  const minuteAt = (y: number, col: HTMLElement) => GRID_START + (y - col.getBoundingClientRect().top) / PX_PER_MIN;
  const overGrid = (x: number, y: number) => {
    const r = wrap.current?.getBoundingClientRect();
    return !!r && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
  };

  function previewFor(g: Gesture, x: number, y: number): Span | null {
    if (g.kind === 'task') {
      if (!overGrid(x, y)) return null;
      const col = dayAt(x);
      if (!col) return null;
      const s = clamp(snap(minuteAt(y, col) - 30), 0, DAY_MINUTES - 60);
      return { date: col.dataset.tbDay!, startMinute: s, endMinute: s + 60 };
    }
    const b = g.block!;
    const dy = (y - g.y0) / PX_PER_MIN;
    if (g.kind === 'resize') return { date: b.date, startMinute: b.startMinute, endMinute: resized(b.startMinute, snap(b.endMinute + dy)) };
    const col = dayAt(x);
    return { date: col?.dataset.tbDay ?? b.date, ...moved(b.startMinute, b.endMinute, snap(b.startMinute + dy)) };
  }

  useEffect(() => {
    const onMove = (e: PointerEvent) => {
      const g = gestureRef.current;
      if (!g || g.pointerId !== e.pointerId) return;
      const active = g.active || Math.hypot(e.clientX - g.x0, e.clientY - g.y0) >= THRESHOLD;
      if (!active) return;
      e.preventDefault();
      setGesture({ ...g, active, x: e.clientX, y: e.clientY, preview: previewFor(g, e.clientX, e.clientY) });
    };
    const onUp = (e: PointerEvent) => {
      const g = gestureRef.current;
      if (!g || g.pointerId !== e.pointerId) return;
      setGesture(null);
      if (e.type !== 'pointerup') return;
      if (!g.active) {
        if (g.block) setDialog({ mode: 'edit', block: g.block }); // a click opens the block
        return;
      }
      if (!g.preview) return;
      if (g.kind === 'task') createFromTask(g.task!, g.preview);
      else moveBlock(g.block!, g.preview);
    };
    const onKey = (e: globalThis.KeyboardEvent) => e.key === 'Escape' && gestureRef.current && setGesture(null);
    window.addEventListener('pointermove', onMove, { passive: false });
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onUp);
      window.removeEventListener('keydown', onKey);
    };
  }); // re-bound every render so the handlers see current data

  // auto-scroll the grid while dragging near its edges
  const dragging = gesture?.active ?? false;
  useEffect(() => {
    if (!dragging) return;
    let frame = 0;
    const tick = () => {
      const g = gestureRef.current;
      const w = wrap.current;
      if (g && w) {
        const r = w.getBoundingClientRect();
        let dy = 0;
        let dx = 0;
        if (g.y < r.top + EDGE && g.y > r.top - EDGE) dy = -Math.ceil(((r.top + EDGE - g.y) / EDGE) * 14);
        else if (g.y > r.bottom - EDGE && g.y < r.bottom + EDGE) dy = Math.ceil(((g.y - (r.bottom - EDGE)) / EDGE) * 14);
        if (g.x < r.left + EDGE && g.x > r.left) dx = -12;
        else if (g.x > r.right - EDGE && g.x < r.right) dx = 12;
        if (dy || dx) {
          w.scrollBy({ top: dy, left: dx });
          const preview = previewFor(g, g.x, g.y);
          setGesture({ ...g, preview });
        }
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [dragging]); // eslint-disable-line react-hooks/exhaustive-deps

  function startBlockGesture(e: ReactPointerEvent<HTMLElement>, block: TimeBlock, kind: 'move' | 'resize') {
    if (saving || e.button !== 0 || block.id.startsWith('new:')) return;
    if (kind === 'move' && (e.target as HTMLElement).closest('button, a, [role="menu"]')) return;
    e.preventDefault();
    e.stopPropagation();
    setGesture({ kind, pointerId: e.pointerId, x0: e.clientX, y0: e.clientY, x: e.clientX, y: e.clientY, block, active: false, preview: null });
  }

  function startTaskGesture(e: ReactPointerEvent<HTMLElement>, task: Task) {
    if (saving || e.button !== 0) return;
    e.preventDefault();
    setGesture({ kind: 'task', pointerId: e.pointerId, x0: e.clientX, y0: e.clientY, x: e.clientX, y: e.clientY, task, active: false, preview: null });
  }

  /** Click on an empty part of a day: a new block from the 15 minutes under the pointer. */
  function onDayClick(e: React.MouseEvent<HTMLElement>, date: IsoDate) {
    if (e.target !== e.currentTarget || saving) return;
    const s = clamp(Math.floor(minuteAt(e.clientY, e.currentTarget) / SNAP) * SNAP, 0, DAY_MINUTES - SNAP);
    let endMinute = Math.min(s + 60, DAY_MINUTES);
    const next = shown?.filter((b) => b.date === date && b.startMinute >= s + SNAP).sort((a, b) => a.startMinute - b.startMinute)[0];
    if (next) endMinute = Math.min(endMinute, next.startMinute);
    setDialog({ mode: 'create', span: { date, startMinute: s, endMinute }, taskId: null });
  }

  function onBlockKey(e: KeyboardEvent<HTMLElement>, b: TimeBlock) {
    if (e.target !== e.currentTarget || saving) return;
    const rtl = getComputedStyle(e.currentTarget).direction === 'rtl';
    const di = days.findIndex((d) => d.date === b.date);
    let span: Span | null = null;
    if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
      const step = e.key === 'ArrowUp' ? -SNAP : SNAP;
      span = e.shiftKey
        ? { date: b.date, startMinute: b.startMinute, endMinute: resized(b.startMinute, b.endMinute + step) }
        : { date: b.date, ...moved(b.startMinute, b.endMinute, b.startMinute + step) };
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
      const next = days[di + ((e.key === 'ArrowLeft') === rtl ? 1 : -1)];
      if (next) span = { date: next.date, startMinute: b.startMinute, endMinute: b.endMinute };
    } else if (e.key === 'Enter') {
      e.preventDefault();
      setDialog({ mode: 'edit', block: b });
      return;
    } else if (e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault();
      if (window.confirm(`بلاک «${blockTitle(b)}» حذف شود؟`)) removeBlock(b);
      return;
    } else {
      return;
    }
    e.preventDefault();
    if (span) moveBlock(b, span);
    requestAnimationFrame(() => document.querySelector<HTMLElement>(`[data-tb-block="${b.id}"]`)?.focus({ preventScroll: false }));
  }

  // ------------------------------------------------------------------------------------------------------ render
  const hours: number[] = [];
  for (let m = GRID_START; m < GRID_END; m += 60) hours.push(m);
  const gridHeight = (GRID_END - GRID_START) * PX_PER_MIN;
  const dragId = gesture?.active && gesture.block ? gesture.block.id : null;
  const preview = gesture?.active ? gesture.preview : null;
  const previewClash = preview && shown ? overlapping(shown, preview.date, preview.startMinute, preview.endMinute, gesture?.block?.id) : null;
  const top = (m: number) => (clamp(m, GRID_START, GRID_END) - GRID_START) * PX_PER_MIN;
  const total = shown?.reduce((n, b) => n + b.endMinute - b.startMinute, 0) ?? 0;

  return (
    <div className={`timebox${gesture?.active ? ' timebox--dragging' : ''}`}>
      <section className="tb-main" aria-label="جدول زمانی هفته">
        {error && <p className="state state--error tb-error" role="alert">{error} <button type="button" className="link-button" onClick={() => setError(null)}>بستن</button></p>}
        {blocks.status === 'error' && <ErrorMessage error={blocks.error} onRetry={blocks.reload} />}
        {!shown && blocks.status !== 'error' && <Loading />}
        {shown && (
          <div ref={wrap} className="tb-wrap" tabIndex={-1}>
            <div className="tb-grid" style={{ ['--tb-hour' as string]: `${HOUR_PX}px` }}>
              <div className="tb-head" role="row">
                <span className="tb-corner" aria-hidden="true" />
                {days.map((d) => (
                  <span key={d.date} className={`tb-dayhead${d.date === today ? ' tb-dayhead--today' : ''}`} role="columnheader">
                    <strong>{d.label}</strong>
                    <span>{formatJalaliDate(d.date).replace(/ \S+$/, '')}</span>
                  </span>
                ))}
              </div>
              <div className="tb-body" style={{ blockSize: gridHeight }}>
                <div className="tb-times" aria-hidden="true">
                  {hours.map((m) => <span key={m} style={{ insetBlockStart: top(m) }}>{timeLabel(m)}</span>)}
                </div>
                {days.map((d) => {
                  const dayBlocks = shown.filter((b) => b.date === d.date);
                  return (
                    <div key={d.date} className={`tb-day${d.date === today ? ' tb-day--today' : ''}`} data-tb-day={d.date}
                      onClick={(e) => onDayClick(e, d.date)} role="group"
                      aria-label={`${d.label} ${formatJalaliDate(d.date)}، ${formatNumber(dayBlocks.length)} بلاک — برای افزودن، روی ساعت خالی کلیک کنید`}>
                      {d.date === nowDate && nowMinute >= GRID_START && nowMinute < GRID_END && (
                        <span className="tb-now" style={{ insetBlockStart: top(nowMinute) }} aria-hidden="true" />
                      )}
                      {dayBlocks.map((b) => (
                        <Block key={b.id} block={b} goal={goalOf(b.task?.effectiveGoalId ?? null)} hintId={hintId}
                          style={{ insetBlockStart: top(b.startMinute), blockSize: Math.max(top(b.endMinute) - top(b.startMinute), 18) }}
                          dragging={b.id === dragId}
                          onPointerDown={(e) => startBlockGesture(e, b, 'move')}
                          onResizeDown={(e) => startBlockGesture(e, b, 'resize')}
                          onKeyDown={(e) => onBlockKey(e, b)}
                          onEdit={() => setDialog({ mode: 'edit', block: b })}
                          onOpenTask={b.taskId ? () => onOpenTask(b.taskId!) : undefined}
                          onRemove={() => removeBlock(b)} />
                      ))}
                      {preview && preview.date === d.date && (
                        <span className={`tb-preview${previewClash ? ' tb-preview--clash' : ''}`} aria-hidden="true"
                          style={{ insetBlockStart: top(preview.startMinute), blockSize: top(preview.endMinute) - top(preview.startMinute) }}>
                          {timeLabel(preview.startMinute)} – {timeLabel(preview.endMinute)}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}
        <p className="meta tb-summary" aria-live="polite">
          {shown && `${formatNumber(shown.length)} بلاک · ${durationLabel(total)} زمان‌بندی‌شده در این هفته`}
        </p>
        <p id={hintId} className="visually-hidden">
          بلاک: کلیدهای بالا و پایین ۱۵ دقیقه جابه‌جا می‌کنند، چپ و راست روز را عوض می‌کنند، شیفت با بالا و پایین طول را تغییر می‌دهد، اینتر ویرایش و دیلیت حذف.
        </p>
      </section>

      <aside className="tb-side" aria-labelledby="tb-side-title">
        <h2 id="tb-side-title" className="tb-side__title"><Icon name="list" size={22} /> تسک‌های این هفته</h2>
        <p className="meta tb-side__hint">تسک را با ⠿ روی جدول بکشید، یا «زمان‌بندی» را بزنید.</p>
        {weekTasks.length === 0 ? (
          <p className="dash-empty">تسک انجام‌نشده‌ای برای این هفته نیست.</p>
        ) : (
          <ul className="tb-tasks">
            {weekTasks.map((t) => {
              const goal = goalOf(t.effectiveGoalId);
              const mins = planned.get(t.id) ?? 0;
              return (
                <li key={t.id} className="tb-task">
                  <button type="button" className="tb-task__grip" aria-label={`کشیدن «${t.title}» روی جدول`} title="روی جدول بکشید"
                    onPointerDown={(e) => startTaskGesture(e, t)}>
                    <Icon name="grip" size={18} />
                  </button>
                  <span className="tb-task__body">
                    <button type="button" className="tb-task__title tb-task__open" aria-haspopup="dialog" onClick={() => onOpenTask(t.id)}>{t.title}</button>
                    <span className="tb-task__meta">
                      {goal && <><span className="kb-goal-dot" style={{ background: goalColor(goal.id) }} aria-hidden="true" /> {goal.title}</>}
                      {mins > 0 && <span className="tb-task__mins">{durationLabel(mins)}</span>}
                    </span>
                  </span>
                  <button type="button" className="tb-task__plan" onClick={() => {
                    const date = days.some((d) => d.date === today) ? today : start;
                    let s = 9 * 60;
                    while (s < GRID_END - 60 && shown && overlapping(shown, date, s, s + 60)) s += 30;
                    setDialog({ mode: 'create', span: { date, startMinute: s, endMinute: s + 60 }, taskId: t.id });
                  }}>
                    <Icon name="plus" size={14} /> زمان‌بندی
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </aside>

      {gesture?.active && gesture.kind === 'task' && gesture.task && (
        <div className="tb-ghost" aria-hidden="true" style={{ left: gesture.x + 12, top: gesture.y + 8 }}>{gesture.task.title}</div>
      )}
      <p className="visually-hidden" role="status" aria-live="polite">{announcement}</p>

      <BlockDialog state={dialog} days={days} tasks={allTasks} blocks={shown ?? []} goalOf={goalOf} onClose={() => setDialog(null)}
        onSaved={(msg) => { setDialog(null); setAnnouncement(msg); blocks.reload(); }} />
    </div>
  );
}

function Block({ block, goal, style, dragging, hintId, onPointerDown, onResizeDown, onKeyDown, onEdit, onOpenTask, onRemove }: {
  block: TimeBlock; goal: Goal | undefined; style: React.CSSProperties; dragging: boolean; hintId: string;
  onPointerDown: (e: ReactPointerEvent<HTMLElement>) => void; onResizeDown: (e: ReactPointerEvent<HTMLElement>) => void;
  onKeyDown: (e: KeyboardEvent<HTMLElement>) => void; onEdit: () => void; onOpenTask?: () => void; onRemove: () => void;
}) {
  const title = blockTitle(block);
  const minutes = block.endMinute - block.startMinute;
  const done = block.task?.status === 'done';
  const size = minutes >= 75 ? 'tall' : minutes >= 40 ? 'mid' : 'short';
  return (
    <article className={`tb-block tb-block--${block.color} tb-block--${size}${dragging ? ' tb-block--dragging' : ''}${done ? ' tb-block--done' : ''}`}
      style={style} tabIndex={0} data-tb-block={block.id} aria-describedby={hintId}
      aria-label={`${title}، ${timeLabel(block.startMinute)} تا ${timeLabel(block.endMinute)} (${durationLabel(minutes)})${done ? '، انجام‌شده' : ''}`}
      onPointerDown={onPointerDown} onKeyDown={onKeyDown}>
      <span className="tb-block__title">{done && <Icon name="check" size={13} />} {title}</span>
      {size !== 'short' && <span className="tb-block__time">{timeLabel(block.startMinute)} – {timeLabel(block.endMinute)}</span>}
      {size === 'tall' && (
        <span className="tb-block__meta">{goal ? goal.title : block.taskId ? '' : 'بدون تسک'} · {durationLabel(minutes)}</span>
      )}
      <span className="tb-block__menu">
        <MenuButton label={`گزینه‌های «${title}»`} items={[
          { label: 'ویرایش', onSelect: onEdit },
          ...(onOpenTask ? [{ label: 'جزئیات تسک', onSelect: onOpenTask }] : []),
          { label: 'حذف بلاک', danger: true, confirm: `بلاک «${title}» حذف شود؟ تسک آن تغییری نمی‌کند.`, onSelect: onRemove },
        ]} />
      </span>
      <span className="tb-block__resize" onPointerDown={onResizeDown} aria-hidden="true" title="برای تغییر طول بکشید" />
    </article>
  );
}

/** Create / edit a block: task or own title, day, start, end, colour. Overlaps are refused before saving. */
function BlockDialog({ state, days, tasks, blocks, goalOf, onClose, onSaved }: {
  state: DialogState; days: { date: IsoDate; label: string }[]; tasks: Task[]; blocks: TimeBlock[];
  goalOf: (id: string | null) => Goal | undefined; onClose: () => void; onSaved: (message: string) => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [kind, setKind] = useState<'task' | 'free'>('free');
  const [taskId, setTaskId] = useState('');
  const [title, setTitle] = useState('');
  const [date, setDate] = useState<IsoDate>(days[0].date);
  const [startMinute, setStart] = useState(9 * 60);
  const [endMinute, setEnd] = useState(10 * 60);
  const [color, setColor] = useState<BlockColor>('teal');
  const [colorTouched, setColorTouched] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [fieldError, setFieldError] = useState<string | null>(null);

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (state && !d.open) {
      const b = state.mode === 'edit' ? state.block : null;
      const span = b ?? (state.mode === 'create' ? state.span : null)!;
      const tid = b ? b.taskId : state.mode === 'create' ? state.taskId : null;
      setKind(tid ? 'task' : 'free');
      setTaskId(tid ?? '');
      setTitle(b?.title ?? '');
      setDate(span.date);
      setStart(span.startMinute);
      setEnd(span.endMinute);
      const t = tasks.find((x) => x.id === tid);
      setColor(b?.color ?? colorForGoal(t?.effectiveGoalId ?? null));
      setColorTouched(Boolean(b));
      setError(null);
      setFieldError(null);
      d.showModal();
    }
    if (!state && d.open) d.close();
  }, [state]); // eslint-disable-line react-hooks/exhaustive-deps

  const editing = state?.mode === 'edit' ? state.block : null;
  // the linked task may be done (or not in the active list any more); keep it selectable
  const choices = tasks.filter((t) => t.status !== 'done' || t.id === taskId);
  if (editing?.task && !choices.some((t) => t.id === editing.task!.id)) choices.unshift(editing.task);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (kind === 'task' && !taskId) return setFieldError('task');
    if (kind === 'free' && !title.trim()) return setFieldError('title');
    if (endMinute <= startMinute) return setFieldError('end');
    const clash = overlapping(blocks, date, startMinute, endMinute, editing?.id);
    if (clash) return setError(new ApiError(409, 'time_block_overlap', `این بازه با «${blockTitle(clash)}» (${timeLabel(clash.startMinute)}–${timeLabel(clash.endMinute)}) هم‌پوشانی دارد.`));
    const input: TimeBlockInput = {
      date, startMinute, endMinute, color,
      taskId: kind === 'task' ? taskId : null,
      title: kind === 'free' ? title.trim() : (editing?.taskId === taskId ? editing?.title ?? null : null),
    };
    setSaving(true);
    setError(null);
    try {
      if (editing) await timeBlocksApi.update(editing.id, input);
      else await timeBlocksApi.create(input);
      const name = kind === 'task' ? tasks.find((t) => t.id === taskId)?.title ?? '' : title.trim();
      onSaved(`«${name}» ${editing ? 'ذخیره' : 'اضافه'} شد.`);
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!editing || !window.confirm(`بلاک «${blockTitle(editing)}» حذف شود؟ تسک آن تغییری نمی‌کند.`)) return;
    setSaving(true);
    try {
      await timeBlocksApi.remove(editing.id);
      onSaved(`«${blockTitle(editing)}» حذف شد.`);
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }

  const starts = timeOptions(0, DAY_MINUTES - SNAP);
  const ends = timeOptions(startMinute + SNAP, DAY_MINUTES);
  return (
    <dialog ref={ref} className="modal" aria-labelledby="tb-dialog-heading" onClose={onClose} onClick={(e) => e.target === e.currentTarget && onClose()}>
      {state && (
        <div className="modal__body">
          <button type="button" className="icon-button modal__close" aria-label="بستن" onClick={onClose}>✕</button>
          <form className="card form" onSubmit={submit} noValidate>
            <h2 id="tb-dialog-heading">{editing ? 'ویرایش بلاک زمانی' : 'افزودن بلاک زمانی'}</h2>
            <fieldset className="tb-kind">
              <legend className="visually-hidden">نوع بلاک</legend>
              <label><input type="radio" name="tb-kind" checked={kind === 'task'} onChange={() => setKind('task')} /> برای یک تسک</label>
              <label><input type="radio" name="tb-kind" checked={kind === 'free'} onChange={() => setKind('free')} /> عنوان آزاد</label>
            </fieldset>
            {kind === 'task' ? (
              <div className="field">
                <label htmlFor="tb-task">تسک <span aria-hidden="true">*</span></label>
                <select id="tb-task" value={taskId} aria-invalid={fieldError === 'task' || undefined}
                  onChange={(e) => {
                    setTaskId(e.target.value);
                    setFieldError(null);
                    if (!colorTouched) setColor(colorForGoal(tasks.find((t) => t.id === e.target.value)?.effectiveGoalId ?? null));
                  }}>
                  <option value="">انتخاب تسک…</option>
                  {choices.map((t) => {
                    const g = goalOf(t.effectiveGoalId);
                    return <option key={t.id} value={t.id}>{g ? `${t.title} — ${g.title}` : t.title}</option>;
                  })}
                </select>
                {fieldError === 'task' && <p className="field__error">یک تسک انتخاب کنید.</p>}
              </div>
            ) : (
              <div className="field">
                <label htmlFor="tb-title">عنوان <span aria-hidden="true">*</span></label>
                <input id="tb-title" value={title} maxLength={200} placeholder="مثلاً استراحت / ناهار" aria-invalid={fieldError === 'title' || undefined}
                  onChange={(e) => { setTitle(e.target.value); setFieldError(null); }} />
                {fieldError === 'title' && <p className="field__error">عنوان را بنویسید.</p>}
              </div>
            )}
            <div className="form__row">
              <div className="field">
                <label htmlFor="tb-day">روز</label>
                <select id="tb-day" value={date} onChange={(e) => setDate(e.target.value)}>
                  {days.map((d) => <option key={d.date} value={d.date}>{`${d.label} ${formatJalaliDate(d.date).replace(/ \S+$/, '')}`}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor="tb-start">از ساعت</label>
                <select id="tb-start" value={startMinute} onChange={(e) => {
                  const s = Number(e.target.value);
                  setStart(s);
                  if (endMinute <= s) setEnd(Math.min(s + 60, DAY_MINUTES));
                }}>
                  {starts.map((m) => <option key={m} value={m}>{timeLabel(m)}</option>)}
                </select>
              </div>
              <div className="field">
                <label htmlFor="tb-end">تا ساعت</label>
                <select id="tb-end" value={endMinute} aria-invalid={fieldError === 'end' || undefined} onChange={(e) => { setEnd(Number(e.target.value)); setFieldError(null); }}>
                  {ends.map((m) => <option key={m} value={m}>{timeLabel(m)}</option>)}
                </select>
              </div>
            </div>
            <p className="meta">مدت: {durationLabel(Math.max(0, endMinute - startMinute))}</p>
            <fieldset className="tb-colors">
              <legend>رنگ</legend>
              {BLOCK_COLORS.map((c) => (
                <label key={c} className={`tb-swatch tb-block--${c}`} title={COLOR_LABELS[c]}>
                  <input type="radio" name="tb-color" checked={color === c} onChange={() => { setColor(c); setColorTouched(true); }} />
                  <span className="visually-hidden">{COLOR_LABELS[c]}</span>
                </label>
              ))}
            </fieldset>
            {error != null && <ErrorMessage error={error} />}
            <div className="form__actions">
              <button type="submit" className="button" disabled={saving}>{saving ? 'در حال ذخیره…' : editing ? 'ذخیره' : 'افزودن'}</button>
              <button type="button" className="button button--secondary" onClick={onClose}>انصراف</button>
              {editing && <button type="button" className="button button--danger tb-delete" disabled={saving} onClick={() => void remove()}>حذف بلاک</button>}
            </div>
          </form>
        </div>
      )}
    </dialog>
  );
}
