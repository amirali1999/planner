import { toPersianDigits } from '../../shared/format';
import type { IsoDate } from '../../shared/date/calendar';
import type { Task } from '../tasks/types';

/** Colour keys of a time block (mirror of the backend enum / V12 CHECK). */
export type BlockColor = 'teal' | 'blue' | 'purple' | 'green' | 'olive' | 'amber' | 'rose' | 'slate';
export const BLOCK_COLORS: BlockColor[] = ['teal', 'blue', 'purple', 'green', 'olive', 'amber', 'rose', 'slate'];
export const COLOR_LABELS: Record<BlockColor, string> = {
  teal: 'فیروزه‌ای', blue: 'آبی', purple: 'بنفش', green: 'سبز', olive: 'زیتونی', amber: 'کهربایی', rose: 'صورتی', slate: 'خاکستری',
};

/** GET /time-blocks: planned time [startMinute, endMinute) on a local day. */
export interface TimeBlock {
  id: string;
  date: IsoDate;
  startMinute: number;
  endMinute: number;
  title: string | null;
  taskId: string | null;
  /** The live linked task (may be archived); null for a free block. */
  task: Task | null;
  color: BlockColor;
  createdAt: string;
  updatedAt: string;
}

export interface TimeBlockInput {
  date: IsoDate;
  startMinute: number;
  endMinute: number;
  title?: string | null;
  taskId?: string | null;
  color?: BlockColor;
}

/** Visible hours of the grid (06:00–24:00); blocks may still be anywhere in the day. */
export const GRID_START = 6 * 60;
export const GRID_END = 24 * 60;
/** Mouse/keyboard moves snap to this many minutes (the API accepts multiples of 5). */
export const SNAP = 15;
export const DAY_MINUTES = 1440;

export const snap = (minute: number, step = SNAP) => Math.round(minute / step) * step;
export const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

/** "۹:۳۰" */
export function timeLabel(minute: number): string {
  const h = Math.floor(minute / 60);
  const m = minute % 60;
  return toPersianDigits(`${h}:${String(m).padStart(2, '0')}`);
}

/** "۱ ساعت و ۳۰ دقیقه", "۴۵ دقیقه", "۲ ساعت" */
export function durationLabel(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  const parts = [h > 0 ? `${toPersianDigits(h)} ساعت` : '', m > 0 ? `${toPersianDigits(m)} دقیقه` : ''].filter(Boolean);
  return parts.join(' و ') || '۰ دقیقه';
}

/** Title shown on a block: its own title, else the linked task's. */
export function blockTitle(b: Pick<TimeBlock, 'title' | 'task'>): string {
  return b.title ?? b.task?.title ?? '';
}

/** Blocks shown on the grid: those of archived tasks are hidden (archived entities appear only in the archive). */
export function visibleBlocks(blocks: TimeBlock[]): TimeBlock[] {
  return blocks.filter((b) => !b.task?.archivedAt);
}

/** Another visible block of `date` intersecting [start, end) (touching is fine), or null. */
export function overlapping(blocks: TimeBlock[], date: IsoDate, start: number, end: number, exceptId?: string): TimeBlock | null {
  return blocks.find((b) => b.id !== exceptId && b.date === date && b.startMinute < end && b.endMinute > start) ?? null;
}

/** Moves [start, end) so that it starts at `newStart`, keeping its length inside the day. */
export function moved(start: number, end: number, newStart: number): { startMinute: number; endMinute: number } {
  const len = end - start;
  const s = clamp(newStart, 0, DAY_MINUTES - len);
  return { startMinute: s, endMinute: s + len };
}

/** New end after resizing; at least one snap step long, at most the end of the day. */
export function resized(start: number, newEnd: number): number {
  return clamp(newEnd, start + SNAP, DAY_MINUTES);
}

/** Minutes planned per task id (visible blocks only). */
export function minutesByTask(blocks: TimeBlock[]): Map<string, number> {
  const out = new Map<string, number>();
  for (const b of blocks) if (b.taskId) out.set(b.taskId, (out.get(b.taskId) ?? 0) + b.endMinute - b.startMinute);
  return out;
}

/** Stable default colour of a task's blocks: by its goal (same goal → same colour), teal without a goal. */
export function colorForGoal(goalId: string | null): BlockColor {
  if (!goalId) return 'teal';
  let h = 0;
  for (const c of goalId) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return BLOCK_COLORS[h % BLOCK_COLORS.length];
}

/** Every 15 minutes of the day as select options ("۰:۰۰" … "۲۴:۰۰"). */
export function timeOptions(from = 0, to = DAY_MINUTES): number[] {
  const out: number[] = [];
  for (let m = from; m <= to; m += SNAP) out.push(m);
  return out;
}
