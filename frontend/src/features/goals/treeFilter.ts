import type { GoalStatus, GoalTreeNode } from './types';

export interface FilteredTree {
  roots: GoalTreeNode[];
  /** Kept only because a descendant matches; shown muted as context. */
  contextIds: Set<string>;
  matchCount: number;
}

/**
 * Status filter for the tree: keeps matching goals and, as context, the ancestors leading to them, so a match is never
 * shown without its place in the hierarchy. Empty `statuses` = no filter. Pure; input is not modified.
 */
export function filterTreeByStatus(roots: GoalTreeNode[], statuses: readonly GoalStatus[]): FilteredTree {
  const contextIds = new Set<string>();
  if (statuses.length === 0) {
    let count = 0;
    const walk = (nodes: GoalTreeNode[]) => nodes.forEach((n) => (count++, walk(n.children)));
    walk(roots);
    return { roots, contextIds, matchCount: count };
  }
  let matchCount = 0;
  const visit = (node: GoalTreeNode): GoalTreeNode | null => {
    const children = node.children.map(visit).filter((c): c is GoalTreeNode => c !== null);
    const matches = statuses.includes(node.status);
    if (matches) matchCount++;
    if (!matches && children.length === 0) return null;
    if (!matches) contextIds.add(node.id);
    return { ...node, children };
  };
  const kept = roots.map(visit).filter((n): n is GoalTreeNode => n !== null);
  return { roots: kept, contextIds, matchCount };
}
