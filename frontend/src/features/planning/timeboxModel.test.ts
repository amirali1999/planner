import { describe, expect, it } from 'vitest';
import type { Task } from '../tasks/types';
import {
  blockTitle, colorForGoal, durationLabel, minutesByTask, moved, overlapping, resized, snap, timeLabel, visibleBlocks, type TimeBlock,
} from './timeboxModel';

const block = (id: string, date: string, startMinute: number, endMinute: number, over: Partial<TimeBlock> = {}): TimeBlock => ({
  id, date, startMinute, endMinute, title: id, taskId: null, task: null, color: 'teal', createdAt: '', updatedAt: '', ...over,
});

describe('timebox model', () => {
  it('formats times and durations in Persian digits', () => {
    expect(timeLabel(9 * 60)).toBe('۹:۰۰');
    expect(timeLabel(13 * 60 + 5)).toBe('۱۳:۰۵');
    expect(durationLabel(90)).toBe('۱ ساعت و ۳۰ دقیقه');
    expect(durationLabel(45)).toBe('۴۵ دقیقه');
    expect(durationLabel(120)).toBe('۲ ساعت');
  });

  it('snaps, moves inside the day and keeps a minimum length when resizing', () => {
    expect(snap(547)).toBe(540);
    expect(snap(553)).toBe(555);
    expect(moved(600, 690, 1400)).toEqual({ startMinute: 1350, endMinute: 1440 });
    expect(moved(600, 690, -30)).toEqual({ startMinute: 0, endMinute: 90 });
    expect(resized(600, 590)).toBe(615);
    expect(resized(600, 2000)).toBe(1440);
  });

  it('finds overlaps on the same day only; touching is allowed', () => {
    const list = [block('a', '2026-11-14', 540, 600), block('b', '2026-11-15', 540, 600)];
    expect(overlapping(list, '2026-11-14', 570, 630)?.id).toBe('a');
    expect(overlapping(list, '2026-11-14', 600, 660)).toBeNull();
    expect(overlapping(list, '2026-11-14', 480, 540)).toBeNull();
    expect(overlapping(list, '2026-11-14', 560, 580, 'a')).toBeNull();
  });

  it('hides blocks of archived tasks, titles and sums planned minutes per task', () => {
    const task = { id: 't', title: 'کار', archivedAt: null } as Task;
    const list = [
      block('x', '2026-11-14', 60, 120, { title: null, taskId: 't', task }),
      block('y', '2026-11-15', 60, 90, { taskId: 't', task, title: 'عنوان خودش' }),
      block('z', '2026-11-15', 200, 260, { taskId: 'old', task: { ...task, id: 'old', archivedAt: '2026-11-01T00:00:00Z' } }),
    ];
    expect(visibleBlocks(list).map((b) => b.id)).toEqual(['x', 'y']);
    expect(blockTitle(list[0])).toBe('کار');
    expect(blockTitle(list[1])).toBe('عنوان خودش');
    expect(minutesByTask(visibleBlocks(list)).get('t')).toBe(90);
    expect(colorForGoal('g1')).toBe(colorForGoal('g1'));
    expect(colorForGoal(null)).toBe('teal');
  });
});
