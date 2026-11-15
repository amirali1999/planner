import { describe, expect, it } from 'vitest';
import { filterTreeByStatus } from './treeFilter';
import type { GoalStatus, GoalTreeNode } from './types';

const node = (id: string, status: GoalStatus, children: GoalTreeNode[] = []): GoalTreeNode => ({
  id, title: id, status, periodType: 'monthly', periodStart: '2026-10-23', periodEnd: '2026-11-21', deadline: null,
  parentGoalId: null, detachedFromParent: false, archivedAt: null, outcomeProgress: null, outcomeSource: 'not_measured',
  keyResultsCount: 0, contributingChildCount: 0, executionProgress: null, activeTaskCount: 0, doneTaskCount: 0, children,
});

describe('filterTreeByStatus', () => {
  const roots = [
    node('year', 'doing', [node('q', 'not_started', [node('m-hold', 'on_hold')]), node('q-done', 'done')]),
    node('solo', 'dropped'),
  ];

  it('returns everything without a filter', () => {
    const r = filterTreeByStatus(roots, []);
    expect(r.roots).toBe(roots);
    expect(r.matchCount).toBe(5);
    expect(r.contextIds.size).toBe(0);
  });

  it('keeps ancestors of a deep match as context', () => {
    const r = filterTreeByStatus(roots, ['on_hold']);
    expect(r.matchCount).toBe(1);
    expect(r.roots.map((n) => n.id)).toEqual(['year']);
    expect(r.roots[0].children.map((n) => n.id)).toEqual(['q']);
    expect([...r.contextIds].sort()).toEqual(['q', 'year']);
  });

  it('matches several statuses, each goal once', () => {
    const r = filterTreeByStatus(roots, ['doing', 'dropped', 'done']);
    expect(r.matchCount).toBe(3);
    expect(r.roots.map((n) => n.id)).toEqual(['year', 'solo']);
    expect(r.roots[0].children.map((n) => n.id)).toEqual(['q-done']);
    expect(r.contextIds.size).toBe(0);
  });

  it('returns no roots when nothing matches, without mutating the input', () => {
    expect(filterTreeByStatus(roots, ['done']).roots[0].children).toHaveLength(1);
    expect(roots[0].children).toHaveLength(2);
    expect(filterTreeByStatus([node('a', 'doing')], ['dropped']).roots).toEqual([]);
  });
});
