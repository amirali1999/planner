import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react';
import { ApiError } from '../../shared/api/client';
import { formatNumber } from '../../shared/format';
import { Icon } from '../../shared/ui/Icon';
import { focusApi } from '../focus/api';
import type { FocusItem } from '../focus/types';
import { move } from './goalQuery';

interface Props {
  goalId: string;
  items: FocusItem[];
  /** One row: the checkbox/title part. The list adds the drag handle. */
  renderRow: (item: FocusItem, handle: ReactNode) => ReactNode;
  onChanged: () => void;
}

interface Drag {
  id: string;
  pointerId: number;
  from: number;
  target: number;
  y: number;
  offsetY: number;
  left: number;
  width: number;
  height: number;
}

const EDGE = 40;
const MAX_SPEED = 14;

/**
 * Vertical reordering of ONE goal's focus items with the dotted handle (mouse, pen or touch), or with ↑/↓ on the
 * focused handle. The dragged row is confined to its own list (it cannot leave the card), and the list scrolls
 * by itself near its top/bottom edge. The new order is saved with PUT /goals/{goalId}/focus-items/order.
 */
export function FocusSortableList({ goalId, items, renderRow, onChanged }: Props) {
  const [order, setOrder] = useState(() => items.map((i) => i.id));
  const [drag, setDrag] = useState<Drag | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');
  const list = useRef<HTMLUListElement>(null);
  const pointerY = useRef(0);
  const dragRef = useRef<Drag | null>(null);
  dragRef.current = drag;

  const serverKey = items.map((i) => i.id).join(',');
  useEffect(() => {
    if (!dragRef.current && !saving) setOrder(items.map((i) => i.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serverKey]);

  const byId = new Map(items.map((i) => [i.id, i]));
  const visible = order.filter((id) => byId.has(id));

  /** Target index at pointer y: number of other rows whose middle lies above it. */
  function targetAt(y: number, draggedId: string): number {
    const rows = [...(list.current?.querySelectorAll<HTMLElement>('[data-item-id]') ?? [])].filter((r) => r.dataset.itemId !== draggedId);
    return rows.filter((r) => {
      const b = r.getBoundingClientRect();
      return b.top + b.height / 2 < y;
    }).length;
  }

  /** The element that actually scrolls (the card's list area), not the <ul> inside it. */
  function scroller(): HTMLElement | null {
    return list.current?.closest<HTMLElement>('.goal-card__list') ?? list.current;
  }

  /** Keeps the ghost inside the visible list area: the row can only move up and down within its own card. */
  function clampY(y: number, d: Pick<Drag, 'offsetY' | 'height'>): number {
    const box = scroller()?.getBoundingClientRect();
    if (!box) return y;
    return Math.min(Math.max(y, box.top + d.offsetY), box.bottom - d.height + d.offsetY);
  }

  async function commit(next: string[], movedId: string) {
    const previous = order;
    setOrder(next);
    setSaving(true);
    setError(null);
    try {
      await focusApi.reorder(goalId, next);
      setAnnouncement(`«${byId.get(movedId)?.title ?? ''}» به جایگاه ${formatNumber(next.indexOf(movedId) + 1)} رفت.`);
      onChanged();
    } catch (e) {
      setOrder(previous);
      setError(e instanceof ApiError ? e.message : 'ترتیب جدید ذخیره نشد.');
    } finally {
      setSaving(false);
    }
  }

  // auto-scroll the list while the pointer is held near its top/bottom edge
  useEffect(() => {
    if (!drag) return;
    let frame = 0;
    const tick = () => {
      const box = scroller();
      const d = dragRef.current;
      if (box && d) {
        const r = box.getBoundingClientRect();
        const y = pointerY.current;
        let dy = 0;
        if (y < r.top + EDGE) dy = -Math.ceil(((r.top + EDGE - y) / EDGE) * MAX_SPEED);
        else if (y > r.bottom - EDGE) dy = Math.ceil(((y - (r.bottom - EDGE)) / EDGE) * MAX_SPEED);
        if (dy !== 0) {
          box.scrollBy({ top: dy });
          const target = targetAt(y, d.id);
          if (target !== d.target) setDrag({ ...d, target });
        }
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [drag !== null]); // eslint-disable-line react-hooks/exhaustive-deps

  // The pressed handle is replaced by the placeholder as soon as the drag starts, so pointer events are followed on
  // the window for the duration of the drag (not on the handle element).
  const visibleRef = useRef(visible);
  visibleRef.current = visible;
  const commitRef = useRef(commit);
  commitRef.current = commit;
  useEffect(() => {
    if (!drag) return;
    const pointerId = drag.pointerId;
    const onMove = (e: PointerEvent) => {
      const d = dragRef.current;
      if (!d || e.pointerId !== pointerId) return;
      e.preventDefault();
      pointerY.current = e.clientY;
      setDrag({ ...d, y: e.clientY, target: targetAt(e.clientY, d.id) });
    };
    const onEnd = (e: PointerEvent) => {
      const d = dragRef.current;
      if (!d || e.pointerId !== pointerId) return;
      setDrag(null);
      if (e.type === 'pointerup' && d.target !== d.from) void commitRef.current(move(visibleRef.current, d.from, d.target), d.id);
    };
    const onKey = (e: globalThis.KeyboardEvent) => e.key === 'Escape' && setDrag(null);
    window.addEventListener('pointermove', onMove, { passive: false });
    window.addEventListener('pointerup', onEnd);
    window.addEventListener('pointercancel', onEnd);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
      window.removeEventListener('keydown', onKey);
    };
  }, [drag !== null]); // eslint-disable-line react-hooks/exhaustive-deps

  function onHandleDown(e: ReactPointerEvent<HTMLButtonElement>, id: string) {
    if (saving || e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation(); // never starts a card drag on the board
    const row = (e.currentTarget.closest('[data-item-id]') as HTMLElement).getBoundingClientRect();
    pointerY.current = e.clientY;
    const from = visible.indexOf(id);
    setDrag({ id, pointerId: e.pointerId, from, target: from, y: e.clientY, offsetY: e.clientY - row.top,
      left: row.left, width: row.width, height: row.height });
  }


  function onHandleKey(e: KeyboardEvent<HTMLButtonElement>, id: string) {
    const from = visible.indexOf(id);
    const to = e.key === 'ArrowUp' ? from - 1 : e.key === 'ArrowDown' ? from + 1 : from;
    if (to === from) return;
    e.preventDefault();
    if (to < 0 || to >= visible.length || saving) return;
    void commit(move(visible, from, to), id).then(() =>
      requestAnimationFrame(() => list.current?.querySelector<HTMLElement>(`[data-item-id="${id}"] .focus-row__grip`)?.focus()));
  }

  const rest = drag ? visible.filter((id) => id !== drag.id) : visible;
  const rows: (string | null)[] = drag ? [...rest.slice(0, drag.target), null, ...rest.slice(drag.target)] : rest;
  const dragged = drag ? byId.get(drag.id) : undefined;

  return (
    <>
      <ul ref={list} className={`check-list focus-sortable${drag ? ' focus-sortable--dragging' : ''}`} aria-busy={saving}>
        {rows.map((id) => {
          if (id === null) return <li key="placeholder" className="focus-sortable__placeholder" aria-hidden="true" style={{ blockSize: drag?.height }} />;
          const item = byId.get(id)!;
          const handle = (
            <button
              type="button"
              className="focus-row__grip"
              aria-label={`جابه‌جایی «${item.title}» — جایگاه ${formatNumber(visible.indexOf(id) + 1)} از ${formatNumber(visible.length)}؛ با کلیدهای بالا و پایین`}
              title="بکشید یا با کلیدهای ↑ و ↓ جابه‌جا کنید"
              disabled={saving && !drag}
              onPointerDown={(e) => onHandleDown(e, id)}
              onKeyDown={(e) => onHandleKey(e, id)}
            >
              <Icon name="grip" size={18} />
            </button>
          );
          return (
            <li key={id} data-item-id={id} className="focus-sortable__item">
              {renderRow(item, handle)}
            </li>
          );
        })}
      </ul>
      {drag && dragged && (
        <div className="focus-sortable__ghost" aria-hidden="true" inert
          style={{ left: drag.left, top: clampY(drag.y, drag) - drag.offsetY, inlineSize: drag.width, blockSize: drag.height }}>
          <div className={`check-row focus-row${dragged.status === 'done' ? ' check-row--done' : ''}`}>
            <span className={`check${dragged.status === 'done' ? ' check--on' : ''}`} />
            <span className="check-row__title">{dragged.title}</span>
            <span className="focus-row__grip"><Icon name="grip" size={18} /></span>
          </div>
        </div>
      )}
      {error && <p className="field__error" role="alert">{error}</p>}
      <p className="visually-hidden" role="status" aria-live="polite">{announcement}</p>
    </>
  );
}
