import { addDays, isIsoDate, type IsoDate } from '../../shared/date/calendar';
import { PERIOD_ORDER, STATUS_ORDER } from './labels';
import type { GoalArchiveFilter, GoalStatus, PeriodType } from './types';

export const GOAL_PAGE_SIZE = 20;

export type DueFilter = 'any' | 'overdue' | 'week' | 'month';
export type GoalSort = 'priority' | 'period' | 'deadline';

/** Goal list filters as kept in the page URL (shareable, survives reload). */
export interface GoalListFilters {
  archived: GoalArchiveFilter;
  statuses: GoalStatus[];
  periodTypes: PeriodType[];
  due: DueFilter;
  sort: GoalSort;
  /** 1-based */
  page: number;
}

export const DEFAULT_FILTERS: GoalListFilters = { archived: 'false', statuses: [], periodTypes: [], due: 'any', sort: 'priority', page: 1 };

const ARCHIVED: GoalArchiveFilter[] = ['false', 'true', 'all'];
const DUE: DueFilter[] = ['any', 'overdue', 'week', 'month'];

function list<T extends string>(raw: string | null, allowed: readonly T[]): T[] {
  if (!raw) return [];
  const values = raw.split(',').filter((v): v is T => (allowed as readonly string[]).includes(v));
  return allowed.filter((v) => values.includes(v)); // canonical order, no duplicates
}

/** Unknown or malformed URL values fall back to defaults instead of breaking the page. */
export function filtersFromSearch(params: URLSearchParams): GoalListFilters {
  const archived = params.get('archived') as GoalArchiveFilter;
  const due = params.get('due') as DueFilter;
  const page = Number(params.get('page'));
  return {
    archived: ARCHIVED.includes(archived) ? archived : 'false',
    statuses: list(params.get('status'), STATUS_ORDER),
    periodTypes: list(params.get('periodType'), PERIOD_ORDER),
    due: DUE.includes(due) ? due : 'any',
    sort: params.get('sort') === 'deadline' ? 'deadline' : params.get('sort') === 'period' ? 'period' : 'priority',
    page: Number.isInteger(page) && page >= 1 ? page : 1,
  };
}

/** Only non-default values go into the URL. */
export function filtersToSearch(f: GoalListFilters): URLSearchParams {
  const p = new URLSearchParams();
  if (f.archived !== 'false') p.set('archived', f.archived);
  if (f.statuses.length) p.set('status', f.statuses.join(','));
  if (f.periodTypes.length) p.set('periodType', f.periodTypes.join(','));
  if (f.due !== 'any') p.set('due', f.due);
  if (f.sort !== 'priority') p.set('sort', f.sort);
  if (f.page > 1) p.set('page', String(f.page));
  return p;
}

/**
 * Backend query for `GET /goals`. Due windows are computed from `today` (the user's local date) as date-only
 * strings; overdue is evaluated by the backend against the same date.
 */
export function goalApiQuery(f: GoalListFilters, today: IsoDate, pageSize: number | null = GOAL_PAGE_SIZE): string {
  if (!isIsoDate(today)) throw new Error(`invalid date ${today}`);
  const p = new URLSearchParams({ archived: f.archived });
  if (f.statuses.length) p.set('status', f.statuses.join(','));
  if (f.periodTypes.length) p.set('periodType', f.periodTypes.join(','));
  if (f.due === 'overdue') p.set('overdueOn', today);
  if (f.due === 'week' || f.due === 'month') {
    p.set('deadlineFrom', today);
    p.set('deadlineTo', addDays(today, f.due === 'week' ? 7 : 30));
  }
  p.set('sort', f.sort);
  if (pageSize != null) {
    p.set('limit', String(pageSize));
    p.set('offset', String((f.page - 1) * pageSize));
  }
  return p.toString();
}

/** Drag-and-drop reordering needs the complete, unfiltered list of active goals in priority order. */
export function canReorder(f: GoalListFilters): boolean {
  return f.archived === 'false' && f.statuses.length === 0 && f.periodTypes.length === 0 && f.due === 'any' && f.sort === 'priority';
}

/** Moves the item at `from` to index `to` (pure). */
export function move<T>(list: readonly T[], from: number, to: number): T[] {
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(Math.max(0, Math.min(to, next.length)), 0, item);
  return next;
}

export function pageCount(totalCount: number, pageSize = GOAL_PAGE_SIZE): number {
  return Math.max(1, Math.ceil(totalCount / pageSize));
}
