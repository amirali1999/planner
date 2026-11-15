import { describe, expect, it } from 'vitest';
import type { FocusItem } from '../focus/types';
import { doneCount, dueLine, focusItemsByGoal, shortJalali } from './goalBoardModel';

const item = (id: string, goalId: string | null, extra: Partial<FocusItem> = {}): FocusItem => ({
  id, title: id, kind: 'other', description: null, goalId, status: 'not_started', position: null, archivedAt: null,
  createdAt: '2026-11-01T00:00:00.000Z', updatedAt: '2026-11-01T00:00:00.000Z', ...extra,
});

describe('goal board model', () => {
  it('groups active focus items by goal, keeping server order', () => {
    const map = focusItemsByGoal([item('a', 'g1'), item('b', 'g2'), item('c', 'g1'), item('d', null),
      item('e', 'g1', { archivedAt: '2026-11-02T00:00:00.000Z' })]);
    expect(map.get('g1')?.map((i) => i.id)).toEqual(['a', 'c']);
    expect(map.get('g2')?.map((i) => i.id)).toEqual(['b']);
    expect(map.has('null')).toBe(false);
  });

  it('orders each goal\'s items by their manual position', () => {
    const map = focusItemsByGoal([item('a', 'g', { position: 3 }), item('b', 'g', { position: 1 }), item('c', 'g', { position: 2 }),
      item('d', 'g', { position: null })]);
    expect(map.get('g')?.map((i) => i.id)).toEqual(['b', 'c', 'a', 'd']);
  });

  it('formats short Jalali dates, adding the year only when it differs', () => {
    expect(shortJalali('2027-03-20', '2026-11-09')).toBe('۲۹ اسفند'); // 1405 has 29 days in Esfand
    expect(shortJalali('2027-03-21', '2026-11-09')).toBe('۱ فروردین ۱۴۰۶');
  });

  it('shows the deadline, else the end of the period', () => {
    expect(dueLine({ deadline: '2026-12-21', periodEnd: '2027-03-20' }, '2026-11-09')).toEqual({ text: 'تا ۳۰ آذر', isDeadline: true });
    expect(dueLine({ deadline: null, periodEnd: '2026-11-21' }, '2026-11-09')).toEqual({ text: 'پایان دوره ۳۰ آبان', isDeadline: false });
  });

  it('counts done items', () => {
    expect(doneCount([item('a', 'g', { status: 'done' }), item('b', 'g'), item('c', 'g', { status: 'doing' })])).toBe(1);
  });
});
