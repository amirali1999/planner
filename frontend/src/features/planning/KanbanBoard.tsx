import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react';

export interface KanbanColumn<C extends string> {
  id: C;
  label: string;
  /** Ids of the cards shown in this column, top first. */
  cardIds: string[];
  header?: ReactNode;
  footer?: ReactNode;
  empty?: ReactNode;
}

/** Props for the dotted drag handle of a card. */
export interface HandleProps {
  onPointerDown: (e: ReactPointerEvent<HTMLElement>) => void;
  onKeyDown: (e: KeyboardEvent<HTMLElement>) => void;
  'data-kb-handle': string;
}

interface Props<C extends string> {
  columns: KanbanColumn<C>[];
  renderCard: (id: string, handle: HandleProps | null) => ReactNode;
  /** Card `id` dropped at `index` of column `to` (index counted without the card itself). */
  onMove: (id: string, to: C, index: number) => void;
  disabled?: boolean;
  label: string;
}

interface Drag<C> {
  id: string;
  pointerId: number;
  from: C;
  x: number;
  y: number;
  offsetX: number;
  offsetY: number;
  width: number;
  height: number;
  target: { col: C; index: number } | null;
}

const THRESHOLD = 6;
const EDGE = 48;
const MAX_SPEED = 16;
/** Pressing on these never starts a drag from the card body. */
const NO_DRAG = 'a, button, input, select, textarea, label, form, [role="menu"]';

/**
 * Kanban board: cards move between columns and up/down inside a column. Mouse/pen: drag the card (or its dotted
 * handle); touch: drag the handle. Keyboard on the focused handle: ↑/↓ inside the column, ←/→ to the neighbouring
 * column (RTL: ← = the column on the left). Escape cancels a drag. Columns (and the board, on narrow screens) scroll
 * by themselves while dragging near their edges.
 */
export function KanbanBoard<C extends string>({ columns, renderCard, onMove, disabled, label }: Props<C>) {
  const [drag, setDrag] = useState<Drag<C> | null>(null);
  const board = useRef<HTMLDivElement>(null);
  const dragRef = useRef<Drag<C> | null>(null);
  dragRef.current = drag;
  const pending = useRef<{ id: string; col: C; pointerId: number; x: number; y: number; el: HTMLElement } | null>(null);
  const pointer = useRef({ x: 0, y: 0 });
  const onMoveRef = useRef(onMove);
  onMoveRef.current = onMove;
  const suppressClick = useRef(false);

  function targetAt(x: number, y: number, draggedId: string): { col: C; index: number } | null {
    const cols = [...(board.current?.querySelectorAll<HTMLElement>('[data-kb-col]') ?? [])];
    if (cols.length === 0) return null;
    // the column under x, else the nearest one
    let best = cols[0];
    let bestDist = Infinity;
    for (const c of cols) {
      const r = c.getBoundingClientRect();
      const dist = x < r.left ? r.left - x : x > r.right ? x - r.right : 0;
      if (dist < bestDist) {
        best = c;
        bestDist = dist;
      }
    }
    const cards = [...best.querySelectorAll<HTMLElement>('[data-kb-card]')].filter((el) => el.dataset.kbCard !== draggedId);
    const index = cards.filter((el) => {
      const r = el.getBoundingClientRect();
      return r.top + r.height / 2 < y;
    }).length;
    return { col: best.dataset.kbCol as C, index };
  }

  function start(id: string, col: C, pointerId: number, x: number, y: number, el: HTMLElement) {
    const card = (el.closest('[data-kb-card]') as HTMLElement | null) ?? el;
    const r = card.getBoundingClientRect();
    pointer.current = { x, y };
    setDrag({ id, pointerId, from: col, x, y, offsetX: x - r.left, offsetY: y - r.top, width: r.width, height: r.height, target: targetAt(x, y, id) });
  }

  // follow the pointer on the window: the pressed card is replaced by the placeholder while dragging
  useEffect(() => {
    const onPointerMove = (e: PointerEvent) => {
      pointer.current = { x: e.clientX, y: e.clientY };
      const p = pending.current;
      if (p && p.pointerId === e.pointerId && !dragRef.current) {
        if (Math.hypot(e.clientX - p.x, e.clientY - p.y) >= THRESHOLD) {
          pending.current = null;
          start(p.id, p.col, p.pointerId, p.x, p.y, p.el);
        }
        return;
      }
      const d = dragRef.current;
      if (!d || d.pointerId !== e.pointerId) return;
      e.preventDefault();
      setDrag({ ...d, x: e.clientX, y: e.clientY, target: targetAt(e.clientX, e.clientY, d.id) });
    };
    const onEnd = (e: PointerEvent) => {
      if (pending.current?.pointerId === e.pointerId) pending.current = null;
      const d = dragRef.current;
      if (!d || d.pointerId !== e.pointerId) return;
      setDrag(null);
      suppressClick.current = true;
      setTimeout(() => (suppressClick.current = false), 0);
      if (e.type === 'pointerup' && d.target) onMoveRef.current(d.id, d.target.col, d.target.index);
    };
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape' && dragRef.current) setDrag(null);
    };
    window.addEventListener('pointermove', onPointerMove, { passive: false });
    window.addEventListener('pointerup', onEnd);
    window.addEventListener('pointercancel', onEnd);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
      window.removeEventListener('keydown', onKey);
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // auto-scroll the column under the pointer (vertical) and the board (horizontal, narrow screens)
  useEffect(() => {
    if (!drag) return;
    let frame = 0;
    const speed = (d: number) => Math.ceil((d / EDGE) * MAX_SPEED);
    const tick = () => {
      const d = dragRef.current;
      const { x, y } = pointer.current;
      let scrolled = false;
      const b = board.current;
      if (b && b.scrollWidth > b.clientWidth) {
        const r = b.getBoundingClientRect();
        if (x < r.left + EDGE) { b.scrollBy({ left: -speed(r.left + EDGE - x) }); scrolled = true; }
        else if (x > r.right - EDGE) { b.scrollBy({ left: speed(x - (r.right - EDGE)) }); scrolled = true; }
      }
      const list = [...(b?.querySelectorAll<HTMLElement>('.kb-col__list') ?? [])].find((l) => {
        const r = l.getBoundingClientRect();
        return x >= r.left && x <= r.right;
      });
      if (list && list.scrollHeight > list.clientHeight) {
        const r = list.getBoundingClientRect();
        if (y < r.top + EDGE && y > r.top - EDGE) { list.scrollBy({ top: -speed(r.top + EDGE - y) }); scrolled = true; }
        else if (y > r.bottom - EDGE && y < r.bottom + EDGE) { list.scrollBy({ top: speed(y - (r.bottom - EDGE)) }); scrolled = true; }
      }
      if (scrolled && d) {
        const target = targetAt(x, y, d.id);
        if (target?.col !== d.target?.col || target?.index !== d.target?.index) setDrag({ ...d, target });
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [drag !== null]); // eslint-disable-line react-hooks/exhaustive-deps

  function handleProps(id: string, col: C): HandleProps {
    return {
      'data-kb-handle': id,
      onPointerDown: (e) => {
        if (disabled || e.button !== 0) return;
        e.preventDefault();
        e.stopPropagation();
        start(id, col, e.pointerId, e.clientX, e.clientY, e.currentTarget);
      },
      onKeyDown: (e) => {
        if (disabled) return;
        const ci = columns.findIndex((c) => c.id === col);
        const ids = columns[ci].cardIds;
        const at = ids.indexOf(id);
        const rtl = getComputedStyle(e.currentTarget).direction === 'rtl';
        let to: { col: C; index: number } | null = null;
        if (e.key === 'ArrowUp' && at > 0) to = { col, index: at - 1 };
        else if (e.key === 'ArrowDown' && at < ids.length - 1) to = { col, index: at + 1 };
        else if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
          const step = (e.key === 'ArrowLeft') === rtl ? 1 : -1;
          const next = columns[ci + step];
          if (next) to = { col: next.id, index: Math.min(at, next.cardIds.length) };
        }
        if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) e.preventDefault();
        if (!to) return;
        onMove(id, to.col, to.index);
      },
    };
  }

  function onCardPointerDown(e: ReactPointerEvent<HTMLElement>, id: string, col: C) {
    if (disabled || e.button !== 0 || e.pointerType === 'touch') return;
    if ((e.target as HTMLElement).closest(NO_DRAG)) return;
    pending.current = { id, col, pointerId: e.pointerId, x: e.clientX, y: e.clientY, el: e.currentTarget };
  }

  const dragged = drag?.id;
  return (
    <>
      <div ref={board} className={`kb-board${drag ? ' kb-board--dragging' : ''}`} role="group" aria-label={label}
        onClickCapture={(e) => {
          if (suppressClick.current) {
            e.preventDefault();
            e.stopPropagation();
          }
        }}>
        {columns.map((c) => {
          const ids = dragged ? c.cardIds.filter((id) => id !== dragged) : c.cardIds;
          const slots: (string | null)[] = drag?.target?.col === c.id
            ? [...ids.slice(0, drag.target.index), null, ...ids.slice(drag.target.index)]
            : ids;
          return (
            <section key={c.id} className={`kb-col kb-col--${c.id}${drag?.target?.col === c.id ? ' kb-col--over' : ''}`}
              data-kb-col={c.id} aria-label={`${c.label}، ${c.cardIds.length} تسک`}>
              {c.header}
              <ul className="kb-col__list">
                {slots.map((id) =>
                  id === null ? (
                    <li key="placeholder" className="kb-placeholder" aria-hidden="true" style={{ blockSize: drag?.height }} />
                  ) : (
                    <li key={id} data-kb-card={id} className="kb-col__item" onPointerDown={(e) => onCardPointerDown(e, id, c.id)}>
                      {renderCard(id, handleProps(id, c.id))}
                    </li>
                  ),
                )}
                {slots.length === 0 && c.empty && <li className="kb-col__empty">{c.empty}</li>}
              </ul>
              {c.footer}
            </section>
          );
        })}
      </div>
      {drag && (
        <div className="kb-ghost" aria-hidden="true" inert
          style={{ left: drag.x - drag.offsetX, top: drag.y - drag.offsetY, inlineSize: drag.width, blockSize: drag.height }}>
          {renderCard(drag.id, null)}
        </div>
      )}
    </>
  );
}
