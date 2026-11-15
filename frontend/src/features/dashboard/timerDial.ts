/** Geometry of the focus-timer dial: a half circle whose minute ticks slide under a fixed centre needle. */

export const MIN_MINUTES = 1;
export const MAX_MINUTES = 120;
/** Degrees between two minute ticks. */
export const DEG_PER_MIN = 6;
/** Ticks further than this from the centre are not drawn (only the top of a large circle is shown). */
export const MAX_DEG = 54;

export function clampMinutes(m: number): number {
  return Math.min(MAX_MINUTES, Math.max(MIN_MINUTES, m));
}

export interface Tick {
  minute: number;
  /** Degrees from the top (negative = left). */
  angle: number;
  major: boolean;
}

/**
 * Ticks visible around `value` (may be fractional while animating or counting down): every whole minute from 0 to
 * MAX_MINUTES whose angle fits on the half circle; a major tick (labelled) every 5 minutes.
 */
export function ticksAround(value: number): Tick[] {
  const span = Math.floor(MAX_DEG / DEG_PER_MIN);
  const out: Tick[] = [];
  for (let m = Math.ceil(value - span - 1); m <= Math.floor(value + span + 1); m += 1) {
    if (m < 0 || m > MAX_MINUTES) continue;
    const angle = (m - value) * DEG_PER_MIN;
    if (Math.abs(angle) > MAX_DEG) continue;
    out.push({ minute: m, angle, major: m % 5 === 0 });
  }
  return out;
}

/** Point on a circle around (cx, cy), `angle` degrees from the top, clockwise. */
export function polar(cx: number, cy: number, r: number, angle: number): { x: number; y: number } {
  const rad = (angle * Math.PI) / 180;
  return { x: cx + r * Math.sin(rad), y: cy - r * Math.cos(rad) };
}

/**
 * Dragging the dial like a clock face: moving the pointer left brings the higher minutes (drawn on the right) to the
 * needle. `pxPerMinute` is the arc length of one tick on screen.
 */
export function dragMinutes(startValue: number, dx: number, pxPerMinute: number): number {
  return clampMinutes(startValue - dx / pxPerMinute);
}

/** mm:ss with Latin digits. */
export function mmss(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}
