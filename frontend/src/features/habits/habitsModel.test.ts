import { describe, expect, it } from 'vitest';
import type { Habit } from './types';
import { doneCount, withDay } from './habitsModel';

const habit = (doneDates: string[]): Habit => ({ id: 'h', title: 'h', doneDates, archivedAt: null, createdAt: '', updatedAt: '' });

describe('habits model', () => {
  it('marks and unmarks a day without duplicates, keeping the dates sorted', () => {
    expect(withDay(habit(['2026-11-09']), '2026-11-07', true).doneDates).toEqual(['2026-11-07', '2026-11-09']);
    expect(withDay(habit(['2026-11-07']), '2026-11-07', true).doneDates).toEqual(['2026-11-07']);
    expect(withDay(habit(['2026-11-07', '2026-11-09']), '2026-11-07', false).doneDates).toEqual(['2026-11-09']);
    expect(withDay(habit([]), '2026-11-07', false).doneDates).toEqual([]);
  });
  it('counts only the given days', () => {
    expect(doneCount(habit(['2026-11-07', '2026-11-08', '2026-11-20']), ['2026-11-07', '2026-11-08', '2026-11-09'])).toBe(2);
  });
});
