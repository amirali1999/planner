import type { IsoDate } from '../../shared/date/calendar';

/** GET /habits?from&to — a daily habit and the days (inside the range) it was done. */
export interface Habit {
  id: string;
  title: string;
  doneDates: IsoDate[];
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
