import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type RefObject } from 'react';
import { clamp, snap } from '../planning/timeboxModel';

/** Five-minute drag steps, preserving one-minute records and the next activity's boundary. */
export function resizedActivityEnd(start: number, end: number, delta: number, limit = 1440): number {
  return clamp(end + snap(delta, 5), start + 1, Math.min(limit, 1440));
}

interface Gesture {
  id: string; pointerId: number; start: number; end: number; limit: number;
  y0: number; y: number; scroll0: number; preview: number; moved: boolean;
}

export function useActivityResize(
  scroll: RefObject<HTMLDivElement | null>, zoom: number, date: string,
  commit: (id: string, endMinute: number) => void,
) {
  const [gesture, setGesture] = useState<Gesture | null>(null);
  const current = useRef<Gesture | null>(null);
  const commitRef = useRef(commit);
  commitRef.current = commit;
  const update = (value: Gesture | null) => { current.current = value; setGesture(value); };

  useEffect(() => { update(null); }, [date]);
  useEffect(() => {
    if (!gesture) return;
    let frame = 0;
    function preview() {
      const g = current.current;
      if (!g) return;
      const deltaPx = g.y - g.y0 + (scroll.current?.scrollTop ?? 0) - g.scroll0;
      update({ ...g, moved: g.moved || Math.abs(deltaPx) >= 4,
        preview: resizedActivityEnd(g.start, g.end, deltaPx / (52 * zoom) * 60, g.limit) });
    }
    const move = (event: PointerEvent) => {
      if (event.pointerId !== current.current?.pointerId) return;
      event.preventDefault();
      current.current = { ...current.current, y: event.clientY };
      preview();
    };
    const finish = (event: PointerEvent) => {
      const g = current.current;
      if (!g || event.pointerId !== g.pointerId) return;
      update(null);
      if (event.type === 'pointerup' && g.moved && g.preview !== g.end) commitRef.current(g.id, g.preview);
    };
    const key = (event: KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); update(null); } };
    function autoScroll() {
      const g = current.current;
      const box = scroll.current;
      if (!g || !box) return;
      const rect = box.getBoundingClientRect();
      const speed = g.y < rect.top + 40 ? -10 : g.y > rect.bottom - 40 ? 10 : 0;
      if (speed) { box.scrollTop += speed; preview(); }
      frame = requestAnimationFrame(autoScroll);
    }
    frame = requestAnimationFrame(autoScroll);
    window.addEventListener('pointermove', move, { passive: false });
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
    window.addEventListener('keydown', key);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
      window.removeEventListener('keydown', key);
    };
  }, [Boolean(gesture), zoom]);

  function begin(event: ReactPointerEvent<HTMLElement>, id: string, start: number, end: number, limit = 1440) {
    if (event.button !== 0) return;
    event.preventDefault(); event.stopPropagation();
    update({ id, pointerId: event.pointerId, start, end, limit, y0: event.clientY, y: event.clientY,
      scroll0: scroll.current?.scrollTop ?? 0, preview: end, moved: false });
  }
  return { gesture, begin };
}
