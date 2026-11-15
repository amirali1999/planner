import { describe, expect, it } from 'vitest';
import { buildTimeline, isRecalibration } from './krTimeline';
import type { CheckIn, KeyResultHistoryEntry } from './types';

const checkIn = (id: string, measuredAt: string, value = 1): CheckIn =>
  ({ id, keyResultId: 'k', value, measuredAt, note: null, createdAt: '2026-11-05T00:00:00Z' });
const change = (id: string, createdAt: string, eventType: KeyResultHistoryEntry['eventType'] = 'key_result_changed'): KeyResultHistoryEntry =>
  ({ id, eventType, before: {}, after: {}, note: null, createdAt });

describe('KR timeline', () => {
  it('merges check-ins and changes newest first by their own timestamps', () => {
    const t = buildTimeline(
      [checkIn('c1', '2026-11-01T08:00:00Z'), checkIn('c2', '2026-11-03T08:00:00Z')],
      [change('h0', '2026-10-30T10:00:00Z', 'key_result_created'), change('h1', '2026-11-02T09:00:00Z')],
    );
    expect(t.map((e) => (e.kind === 'check_in' ? e.checkIn.id : e.change.id))).toEqual(['c2', 'h1', 'c1', 'h0']);
  });

  it('is deterministic on equal timestamps (later input first)', () => {
    const t = buildTimeline([checkIn('a', '2026-11-03T08:00:00Z'), checkIn('b', '2026-11-03T08:00:00Z')], []);
    expect(t.map((e) => (e.kind === 'check_in' ? e.checkIn.id : ''))).toEqual(['b', 'a']);
  });

  it('detects recalibration only for baseline/target/weight', () => {
    const kr = { baseline: 20, target: 60, weight: 1 };
    expect(isRecalibration(kr, { ...kr })).toBe(false);
    expect(isRecalibration(kr, { ...kr, target: 100 })).toBe(true);
    expect(isRecalibration(kr, { ...kr, weight: 3 })).toBe(true);
  });
});
