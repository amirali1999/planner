import type { IsoDate } from '../../shared/date/calendar';
import type { Habit } from './types';

/** The habit with `date` marked done / not done (optimistic update; keeps doneDates sorted and unique). */
export function withDay(habit: Habit, date: IsoDate, done: boolean): Habit {
  const rest = habit.doneDates.filter((d) => d !== date);
  return { ...habit, doneDates: done ? [...rest, date].sort() : rest };
}

/** How many of the given days are done. */
export function doneCount(habit: Habit, days: IsoDate[]): number {
  const set = new Set(habit.doneDates);
  return days.filter((d) => set.has(d)).length;
}
