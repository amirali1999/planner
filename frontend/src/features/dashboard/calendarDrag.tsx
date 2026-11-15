import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import type { IsoDate } from '../../shared/date/calendar';
import type { CalendarEntry } from './calendarModel';

const THRESHOLD = 6;

export interface CalendarDrag {
  entry: CalendarEntry;
  from: IsoDate;
  x: number;
  y: number;
  /** The day under the pointer (an element with data-cal-day), or null. */
  target: IsoDate | null;
}

/**
 * Drag-and-drop of calendar tasks between days (project rule: pointer events followed on the window, a ghost, Escape
 * cancels). A press becomes a drag after a few pixels; a plain click is left alone. Drop targets are elements with
 * `data-cal-day`.
 */
export function useCalendarDrag(onDrop: (entry: CalendarEntry, from: IsoDate, to: IsoDate) => void) {
  const [drag, setDrag] = useState<CalendarDrag | null>(null);
  const dragRef = useRef<CalendarDrag | null>(null);
  const pending = useRef<{ entry: CalendarEntry; from: IsoDate; x: number; y: number; pointerId: number } | null>(null);
  const suppress = useRef(false);
  const dropRef = useRef(onDrop);
  dropRef.current = onDrop;
  const set = (d: CalendarDrag | null) => {
    dragRef.current = d;
    setDrag(d);
  };

  useEffect(() => {
    const dayAt = (x: number, y: number) =>
      (document.elementFromPoint(x, y)?.closest('[data-cal-day]') as HTMLElement | null)?.dataset.calDay ?? null;
    const move = (e: PointerEvent) => {
      const p = pending.current;
      if (p && !dragRef.current && p.pointerId === e.pointerId && Math.hypot(e.clientX - p.x, e.clientY - p.y) >= THRESHOLD) {
        set({ entry: p.entry, from: p.from, x: e.clientX, y: e.clientY, target: dayAt(e.clientX, e.clientY) });
        return;
      }
      const d = dragRef.current;
      if (!d) return;
      e.preventDefault();
      set({ ...d, x: e.clientX, y: e.clientY, target: dayAt(e.clientX, e.clientY) });
    };
    const end = (e: PointerEvent) => {
      pending.current = null;
      const d = dragRef.current;
      if (!d) return;
      set(null);
      suppress.current = true; // the click that follows the drop must not open the day
      setTimeout(() => (suppress.current = false), 0);
      if (e.type === 'pointerup' && d.target && d.target !== d.from) dropRef.current(d.entry, d.from, d.target);
    };
    const key = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && dragRef.current) {
        pending.current = null;
        set(null);
      }
    };
    window.addEventListener('pointermove', move, { passive: false });
    window.addEventListener('pointerup', end);
    window.addEventListener('pointercancel', end);
    window.addEventListener('keydown', key);
    return () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', end);
      window.removeEventListener('pointercancel', end);
      window.removeEventListener('keydown', key);
    };
  }, []);

  return {
    drag,
    /** Call from an entry's onPointerDown. */
    press(e: ReactPointerEvent, entry: CalendarEntry, from: IsoDate) {
      if (e.button !== 0) return;
      pending.current = { entry, from, x: e.clientX, y: e.clientY, pointerId: e.pointerId };
    },
    /** True right after a drop: skip the click. */
    justDropped: () => suppress.current,
  };
}

/** The floating copy of the dragged task. */
export function CalendarGhost({ drag }: { drag: CalendarDrag | null }) {
  if (!drag) return null;
  return (
    <div className="cal-ghost" aria-hidden="true" style={{ left: drag.x + 12, top: drag.y + 10 }}>
      {drag.entry.task.title}
    </div>
  );
}
