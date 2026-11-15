import { describe, expect, it } from 'vitest';
import { canReorder, DEFAULT_FILTERS, filtersFromSearch, filtersToSearch, goalApiQuery, move, pageCount } from './goalQuery';

describe('goal list query', () => {
  it('round-trips filters through the URL and drops defaults', () => {
    const f = { ...DEFAULT_FILTERS, statuses: ['on_hold', 'doing'] as const, due: 'overdue' as const, page: 3 };
    const search = filtersToSearch({ ...f, statuses: [...f.statuses] });
    expect(search.toString()).toBe('status=on_hold%2Cdoing&due=overdue&page=3');
    expect(filtersFromSearch(search)).toEqual({ ...DEFAULT_FILTERS, statuses: ['doing', 'on_hold'], due: 'overdue', page: 3 });
    expect(filtersToSearch(DEFAULT_FILTERS).toString()).toBe('');
  });

  it('ignores unknown or malformed URL values', () => {
    const f = filtersFromSearch(new URLSearchParams('archived=x&status=doing,blocked,doing&periodType=yearly&due=soon&sort=x&page=-2'));
    expect(f).toEqual({ ...DEFAULT_FILTERS, statuses: ['doing'] });
  });

  it('builds the backend query with date-only windows', () => {
    const base = { ...DEFAULT_FILTERS, statuses: ['doing' as const, 'dropped' as const], periodTypes: ['monthly' as const] };
    const q = new URLSearchParams(goalApiQuery({ ...base, due: 'overdue', page: 2 }, '2026-11-09'));
    expect(q.get('status')).toBe('doing,dropped');
    expect(q.get('periodType')).toBe('monthly');
    expect(q.get('overdueOn')).toBe('2026-11-09');
    expect(q.get('offset')).toBe('20');
    expect(q.get('limit')).toBe('20');
    const week = new URLSearchParams(goalApiQuery({ ...DEFAULT_FILTERS, due: 'week' }, '2026-12-28'));
    expect([week.get('deadlineFrom'), week.get('deadlineTo')]).toEqual(['2026-12-28', '2027-01-04']);
    const month = new URLSearchParams(goalApiQuery({ ...DEFAULT_FILTERS, due: 'month' }, '2027-03-01'));
    expect(month.get('deadlineTo')).toBe('2027-03-31');
    expect(month.get('overdueOn')).toBeNull();
  });

  it('computes page count, at least one page', () => {
    expect(pageCount(0)).toBe(1);
    expect(pageCount(20)).toBe(1);
    expect(pageCount(21)).toBe(2);
  });

  it('defaults to the manual priority order and can omit paging for the board', () => {
    expect(DEFAULT_FILTERS.sort).toBe('priority');
    expect(filtersFromSearch(new URLSearchParams('sort=period')).sort).toBe('period');
    expect(filtersToSearch({ ...DEFAULT_FILTERS, sort: 'deadline' }).toString()).toBe('sort=deadline');
    const q = new URLSearchParams(goalApiQuery(DEFAULT_FILTERS, '2026-11-09', null));
    expect(q.get('sort')).toBe('priority');
    expect(q.has('limit')).toBe(false);
    expect(q.has('offset')).toBe(false);
  });

  it('allows reordering only on the complete, unfiltered priority view', () => {
    expect(canReorder(DEFAULT_FILTERS)).toBe(true);
    expect(canReorder({ ...DEFAULT_FILTERS, statuses: ['doing'] })).toBe(false);
    expect(canReorder({ ...DEFAULT_FILTERS, archived: 'all' })).toBe(false);
    expect(canReorder({ ...DEFAULT_FILTERS, sort: 'deadline' })).toBe(false);
    expect(canReorder({ ...DEFAULT_FILTERS, due: 'overdue' })).toBe(false);
  });

  it('moves an item within a list', () => {
    expect(move(['a', 'b', 'c', 'd'], 0, 2)).toEqual(['b', 'c', 'a', 'd']);
    expect(move(['a', 'b', 'c', 'd'], 3, 0)).toEqual(['d', 'a', 'b', 'c']);
    expect(move(['a', 'b'], 0, 9)).toEqual(['b', 'a']);
  });
});
