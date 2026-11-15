import { describe, expect, it } from 'vitest';
import { allParentIds, treeKeyAction, visibleItems } from './treeNav';
import type { GoalTreeNode } from './types';

function node(id: string, children: GoalTreeNode[] = []): GoalTreeNode {
  return {
    id, title: id, status: 'doing', periodType: 'monthly', periodStart: '2026-10-23', periodEnd: '2026-11-21', deadline: null,
    parentGoalId: null, detachedFromParent: false, archivedAt: null, outcomeProgress: null, outcomeSource: 'not_measured',
    keyResultsCount: 0, contributingChildCount: 0, executionProgress: null, activeTaskCount: 0, doneTaskCount: 0, children,
  };
}

// a (annual) → q → m → w ; s standalone root
const roots = [node('a', [node('q', [node('m', [node('w')])])]), node('s')];

describe('tree navigation', () => {
  it('lists only visible items with level and position', () => {
    expect(visibleItems(roots, new Set()).map((i) => [i.node.id, i.level, i.posInSet, i.setSize])).toEqual([
      ['a', 1, 1, 2],
      ['s', 1, 2, 2],
    ]);
    expect(visibleItems(roots, allParentIds(roots)).map((i) => `${i.node.id}@${i.level}`)).toEqual(['a@1', 'q@2', 'm@3', 'w@4', 's@1']);
  });

  it('RTL: ArrowLeft expands then enters; ArrowRight collapses then goes to parent', () => {
    const closed = new Set<string>();
    expect(treeKeyAction(visibleItems(roots, closed), 'a', 'ArrowLeft', closed)).toEqual({ type: 'expand', id: 'a' });
    const open = new Set(['a']);
    const items = visibleItems(roots, open);
    expect(treeKeyAction(items, 'a', 'ArrowLeft', open)).toEqual({ type: 'focus', id: 'q' });
    expect(treeKeyAction(items, 'q', 'ArrowRight', open)).toEqual({ type: 'focus', id: 'a' });
    expect(treeKeyAction(items, 'a', 'ArrowRight', open)).toEqual({ type: 'collapse', id: 'a' });
    expect(treeKeyAction(items, 's', 'ArrowLeft', open)).toEqual({ type: 'none' }); // leaf
    expect(treeKeyAction(items, 's', 'ArrowRight', open)).toEqual({ type: 'none' }); // root leaf
  });

  it('Up/Down/Home/End move through visible items; Enter activates', () => {
    const all = allParentIds(roots);
    const items = visibleItems(roots, all);
    expect(treeKeyAction(items, 'm', 'ArrowDown', all)).toEqual({ type: 'focus', id: 'w' });
    expect(treeKeyAction(items, 'w', 'ArrowDown', all)).toEqual({ type: 'focus', id: 's' });
    expect(treeKeyAction(items, 's', 'ArrowDown', all)).toEqual({ type: 'none' });
    expect(treeKeyAction(items, 'q', 'ArrowUp', all)).toEqual({ type: 'focus', id: 'a' });
    expect(treeKeyAction(items, 'w', 'Home', all)).toEqual({ type: 'focus', id: 'a' });
    expect(treeKeyAction(items, 'a', 'End', all)).toEqual({ type: 'focus', id: 's' });
    expect(treeKeyAction(items, 'm', 'Enter', all)).toEqual({ type: 'activate', id: 'm' });
  });
});
