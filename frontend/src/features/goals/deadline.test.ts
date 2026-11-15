import { describe, expect, it } from 'vitest';
import { deadlineFlag, deadlineState } from './deadline';
import type { GoalStatus } from './types';

describe('deadlineState (date-only, no timezone)', () => {
  it.each([
    ['2026-11-08', 'doing', 'overdue', 1],
    ['2026-11-09', 'doing', 'due_today', 0],
    ['2026-11-10', 'not_started', 'due_soon', 1],
    ['2026-11-16', 'on_hold', 'due_soon', 7],
    ['2026-11-17', 'doing', 'upcoming', 8],
    ['2026-10-01', 'on_hold', 'overdue', 39],
    ['2026-11-01', 'done', 'closed', -8],
    ['2026-11-01', 'dropped', 'closed', -8],
    ['2026-11-30', 'done', 'closed', 21],
  ] as const)('deadline %s, %s → %s (%i)', (deadline, status, kind, days) => {
    expect(deadlineState(deadline, status as GoalStatus, '2026-11-09')).toEqual({ kind, days });
  });

  it('has no state without a deadline', () => {
    expect(deadlineState(null, 'doing', '2026-11-09')).toBeNull();
  });

  it('counts calendar days across month, year and Jalali new-year boundaries', () => {
    expect(deadlineState('2026-03-20', 'doing', '2026-03-21')).toEqual({ kind: 'overdue', days: 1 }); // 29 Esfand → 1 Farvardin
    expect(deadlineState('2027-01-01', 'doing', '2026-12-31')).toEqual({ kind: 'due_soon', days: 1 });
  });

  it('does not depend on the runtime timezone (pure string/day-number arithmetic)', () => {
    const before = process.env.TZ;
    for (const tz of ['UTC', 'Asia/Tehran', 'Pacific/Kiritimati', 'America/Adak']) {
      process.env.TZ = tz;
      expect(deadlineState('2026-11-09', 'doing', '2026-11-09')?.kind).toBe('due_today');
      expect(deadlineState('2026-11-08', 'doing', '2026-11-09')?.kind).toBe('overdue');
    }
    process.env.TZ = before;
  });

  it('labels only what needs attention', () => {
    expect(deadlineFlag({ kind: 'overdue', days: 3 })).toBe('۳ روز از موعد گذشته');
    expect(deadlineFlag({ kind: 'due_today', days: 0 })).toBe('موعد امروز');
    expect(deadlineFlag({ kind: 'due_soon', days: 2 })).toBe('۲ روز تا موعد');
    expect(deadlineFlag({ kind: 'upcoming', days: 30 })).toBeNull();
    expect(deadlineFlag({ kind: 'closed', days: -3 })).toBeNull();
  });
});
