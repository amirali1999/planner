import { useCallback, useEffect, useLayoutEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react';
import { ApiError } from '../../shared/api/client';
import { formatNumber } from '../../shared/format';
import { Icon } from '../../shared/ui/Icon';
import { move } from './goalQuery';
import type { Goal } from './types';

export interface MoveControls {
  /** 1-based position in the board (= priority when the board shows all active goals). */
  rank: number;
  reorderable: boolean;
  /** Higher priority = one step to the right (RTL start). */
  moveEarlier?: () => void;
  moveLater?: () => void;
}

interface GoalBoardProps {
  goals: Goal[];
  /** Only the complete, unfiltered priority view can be reordered. */
  reorderable: boolean;
  onReorder: (goalIds: string[]) => Promise<void>;
  renderCard: (goal: Goal, controls: MoveControls) => ReactNode;
  /** Scroll this goal into view once it is rendered (e.g. right after creating it). */
  revealId?: string | null;
  /** An extra card after the last goal (the «new goal» form); it is scrolled into view when it appears. */
  trailing?: ReactNode;
}

interface DragState {
  id: string;
  pointerId: number;
  from: number;
  target: number;
  x: number;
  y: number;
  offsetX: number;
  offsetY: number;
  width: number;
  height: number;
}

const DRAG_THRESHOLD = 6;
const EDGE = 96;
const MAX_SPEED = 26;
/** Pressing on these never starts a drag (they keep their own behaviour). */
const NO_DRAG = 'a, button, input, select, textarea, label, form, [role="menu"], .goal-card__list';

/**
 * Horizontal goals board (one row, scrolls sideways). With the mouse/pen a card can be grabbed and dropped at a new
 * position; while dragging near the left/right edge the row scrolls by itself. The first card (right-most in RTL) is
 * priority 1. Touch and keyboard users reorder through the card menu (moveEarlier/moveLater).
 */
export function GoalBoard({ goals, reorderable, onReorder, renderCard, revealId, trailing }: GoalBoardProps) {
  const [order, setOrder] = useState<string[]>(() => goals.map((g) => g.id));
  const [drag, setDrag] = useState<DragState | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const scroller = useRef<HTMLDivElement>(null);
  const pending = useRef<{ id: string; pointerId: number; startX: number; startY: number; el: HTMLElement } | null>(null);
  const pointer = useRef({ x: 0, y: 0 });
  const suppressClick = useRef(false);
  const dragRef = useRef<DragState | null>(null);
  dragRef.current = drag;

  // follow server data unless the user is in the middle of a drag/save
  const serverKey = goals.map((g) => g.id).join(',');
  useEffect(() => {
    if (!dragRef.current && !saving) setOrder(goals.map((g) => g.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serverKey]);

  const byId = new Map(goals.map((g) => [g.id, g]));
  const visible = order.filter((id) => byId.has(id));

  useLayoutEffect(() => {
    if (!revealId) return;
    scroller.current?.querySelector<HTMLElement>(`[data-goal-slot="${revealId}"]`)?.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
  }, [revealId, order.join(',')]); // runs again once the new card is actually in the rendered order

  const commit = useCallback(
    async (next: string[], movedId: string) => {
      const previous = order;
      setOrder(next);
      setSaving(true);
      setError(null);
      try {
        await onReorder(next);
        const title = byId.get(movedId)?.title ?? '';
        setAnnouncement(`«${title}» به اولویت ${formatNumber(next.indexOf(movedId) + 1)} منتقل شد.`);
      } catch (e) {
        setOrder(previous);
        setError(e instanceof ApiError ? e.message : 'ترتیب جدید ذخیره نشد.');
      } finally {
        setSaving(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [order, onReorder, serverKey],
  );

  /** Index the dragged card would get at pointer x: count the other slots that lie before it in reading order. */
  const targetAt = useCallback((x: number, draggedId: string) => {
    const box = scroller.current;
    if (!box) return 0;
    const rtl = getComputedStyle(box).direction === 'rtl';
    const slots = [...box.querySelectorAll<HTMLElement>('[data-goal-slot]')].filter((el) => el.dataset.goalSlot !== draggedId);
    return slots.filter((el) => {
      const r = el.getBoundingClientRect();
      const center = r.left + r.width / 2;
      return rtl ? center > x : center < x;
    }).length;
  }, []);

  // auto-scroll while dragging near the edges, and keep the target in sync with the scrolled content
  useEffect(() => {
    if (!drag) return;
    let frame = 0;
    const tick = () => {
      const box = scroller.current;
      const d = dragRef.current;
      if (box && d) {
        const r = box.getBoundingClientRect();
        const { x } = pointer.current;
        let dx = 0;
        if (x < r.left + EDGE) dx = -Math.ceil(((r.left + EDGE - x) / EDGE) * MAX_SPEED);
        else if (x > r.right - EDGE) dx = Math.ceil(((x - (r.right - EDGE)) / EDGE) * MAX_SPEED);
        if (dx !== 0) {
          box.scrollBy({ left: dx });
          const target = targetAt(x, d.id);
          if (target !== d.target) setDrag({ ...d, target });
        }
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [drag !== null, targetAt]); // eslint-disable-line react-hooks/exhaustive-deps

  function onPointerDown(e: ReactPointerEvent<HTMLDivElement>, id: string) {
    if (!reorderable || saving || e.button !== 0 || e.pointerType === 'touch') return;
    if ((e.target as HTMLElement).closest(NO_DRAG)) return;
    pending.current = { id, pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, el: e.currentTarget };
  }

  function onPointerMove(e: ReactPointerEvent<HTMLDivElement>) {
    pointer.current = { x: e.clientX, y: e.clientY };
    const p = pending.current;
    if (!drag && p && p.pointerId === e.pointerId) {
      if (Math.hypot(e.clientX - p.startX, e.clientY - p.startY) < DRAG_THRESHOLD) return;
      const r = p.el.getBoundingClientRect();
      e.currentTarget.setPointerCapture(e.pointerId);
      const from = visible.indexOf(p.id);
      setDrag({ id: p.id, pointerId: e.pointerId, from, target: from, x: e.clientX, y: e.clientY,
        offsetX: p.startX - r.left, offsetY: p.startY - r.top, width: r.width, height: r.height });
      pending.current = null;
      return;
    }
    if (drag && drag.pointerId === e.pointerId) {
      setDrag({ ...drag, x: e.clientX, y: e.clientY, target: targetAt(e.clientX, drag.id) });
    }
  }

  function endDrag(e: ReactPointerEvent<HTMLDivElement>, cancelled: boolean) {
    pending.current = null;
    if (!drag || drag.pointerId !== e.pointerId) return;
    const d = drag;
    setDrag(null);
    suppressClick.current = true;
    setTimeout(() => (suppressClick.current = false), 0);
    if (!cancelled && d.target !== d.from) void commit(move(visible, d.from, d.target), d.id);
  }

  function step(id: string, delta: number) {
    const from = visible.indexOf(id);
    const to = from + delta;
    if (to < 0 || to >= visible.length) return;
    void commit(move(visible, from, to), id);
    requestAnimationFrame(() =>
      scroller.current?.querySelector<HTMLElement>(`[data-goal-slot="${id}"]`)?.scrollIntoView({ inline: 'nearest', block: 'nearest' }));
  }

  // ‹ › arrows: shown when the cards overflow; RTL — scrollLeft is 0 at the start (priority 1, right) and negative to the left
  const [edges, setEdges] = useState({ overflow: false, atStart: true, atEnd: true });
  const updateEdges = useCallback(() => {
    const el = scroller.current;
    if (!el) return;
    const max = el.scrollWidth - el.clientWidth;
    const pos = Math.abs(el.scrollLeft);
    const next = { overflow: max > 2, atStart: pos <= 2, atEnd: pos >= max - 2 };
    setEdges((e) => (e.overflow === next.overflow && e.atStart === next.atStart && e.atEnd === next.atEnd ? e : next));
  }, []);
  useEffect(() => {
    updateEdges();
    const el = scroller.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(updateEdges);
    ro.observe(el);
    return () => ro.disconnect();
  }, [updateEdges, goals.length, trailing != null]);
  useEffect(() => {
    if (!trailing) return;
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    // RTL: the end of the row (left) is scrollLeft = -(scrollWidth - clientWidth)
    requestAnimationFrame(() => {
      const el = scroller.current;
      if (el) el.scrollTo({ left: -(el.scrollWidth - el.clientWidth), behavior: reduce ? 'auto' : 'smooth' });
    });
  }, [trailing != null]); // eslint-disable-line react-hooks/exhaustive-deps
  /** One card (plus the gap) towards later goals (+1, left) or earlier ones (-1, right). */
  function page(dir: 1 | -1) {
    const el = scroller.current;
    const slot = el?.querySelector<HTMLElement>('.goal-board__slot');
    if (!el || !slot) return;
    const step = slot.getBoundingClientRect().width + parseFloat(getComputedStyle(el).columnGap || '20');
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    el.scrollBy({ left: -dir * step, behavior: reduce ? 'auto' : 'smooth' });
  }

  // while dragging: the other cards in order, with a placeholder at the target slot
  const rest = drag ? visible.filter((id) => id !== drag.id) : visible;
  const slots: (string | null)[] = drag ? [...rest.slice(0, drag.target), null, ...rest.slice(drag.target)] : rest;
  const dragged = drag ? byId.get(drag.id) : undefined;

  return (
    <>
      {error && (
        <p className="state state--error" role="alert">
          {error}
        </p>
      )}
      {reorderable && <p className="meta goal-board__hint">برای تغییر اولویت، کارت را با موس بگیرید و به چپ یا راست ببرید (یا از منوی ⋮).</p>}
      <div className={`goal-board-wrap${edges.overflow ? ' goal-board-wrap--overflow' : ''}`}>
      {edges.overflow && (
        <>
          <button type="button" className="goal-board__arrow goal-board__arrow--right" aria-label="اهداف قبلی (اولویت بالاتر)" title="اهداف قبلی"
            disabled={edges.atStart} onClick={() => page(-1)}>
            <Icon name="chevron-right" size={26} />
          </button>
          <button type="button" className="goal-board__arrow goal-board__arrow--left" aria-label="اهداف بعدی" title="اهداف بعدی"
            disabled={edges.atEnd} onClick={() => page(1)}>
            <Icon name="chevron-left" size={26} />
          </button>
        </>
      )}
      <div
        ref={scroller}
        onScroll={updateEdges}
        className={`goal-board${drag ? ' goal-board--dragging' : ''}`}
        aria-busy={saving}
        onPointerMove={onPointerMove}
        onPointerUp={(e) => endDrag(e, false)}
        onPointerCancel={(e) => endDrag(e, true)}
        onKeyDown={(e) => {
          if (e.key === 'Escape' && drag) setDrag(null);
        }}
        onClickCapture={(e) => {
          if (suppressClick.current) {
            e.preventDefault();
            e.stopPropagation();
          }
        }}
      >
        {slots.map((id, index) => {
          if (id === null) {
            return <div key="placeholder" className="goal-board__slot goal-board__placeholder" aria-hidden="true" style={{ blockSize: drag?.height }} />;
          }
          const goal = byId.get(id)!;
          const rank = visible.indexOf(id) + 1;
          return (
            <div key={id} data-goal-slot={id} className={`goal-board__slot${reorderable ? ' goal-board__slot--movable' : ''}`}
              onPointerDown={(e) => onPointerDown(e, id)}>
              {renderCard(goal, {
                rank: drag ? index + 1 : rank,
                reorderable,
                moveEarlier: reorderable && rank > 1 && !saving ? () => step(id, -1) : undefined,
                moveLater: reorderable && rank < visible.length && !saving ? () => step(id, +1) : undefined,
              })}
            </div>
          );
        })}
        {trailing && <div className="goal-board__slot goal-board__slot--new">{trailing}</div>}
      </div>
      </div>
      {drag && dragged && (
        <div className="goal-board__ghost" aria-hidden="true" inert
          style={{ left: drag.x - drag.offsetX, top: drag.y - drag.offsetY, inlineSize: drag.width, blockSize: drag.height }}>
          {renderCard(dragged, { rank: drag.target + 1, reorderable: false })}
        </div>
      )}
      <p className="visually-hidden" role="status" aria-live="polite">
        {announcement}
      </p>
    </>
  );
}
