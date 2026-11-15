import type { GoalTreeNode } from './types';

/** A tree item currently rendered (its ancestors are all expanded). */
export interface VisibleItem {
  node: GoalTreeNode;
  level: number;
  parentId: string | null;
  posInSet: number;
  setSize: number;
}

/** Depth-first list of visible items, in display order. */
export function visibleItems(roots: GoalTreeNode[], expanded: ReadonlySet<string>): VisibleItem[] {
  const out: VisibleItem[] = [];
  const walk = (nodes: GoalTreeNode[], level: number, parentId: string | null) => {
    nodes.forEach((node, i) => {
      out.push({ node, level, parentId, posInSet: i + 1, setSize: nodes.length });
      if (node.children.length > 0 && expanded.has(node.id)) walk(node.children, level + 1, node.id);
    });
  };
  walk(roots, 1, null);
  return out;
}

export function allParentIds(roots: GoalTreeNode[]): Set<string> {
  const ids = new Set<string>();
  const walk = (nodes: GoalTreeNode[]) =>
    nodes.forEach((n) => {
      if (n.children.length > 0) {
        ids.add(n.id);
        walk(n.children);
      }
    });
  walk(roots);
  return ids;
}

export type TreeAction =
  | { type: 'focus'; id: string }
  | { type: 'expand'; id: string }
  | { type: 'collapse'; id: string }
  | { type: 'activate'; id: string }
  | { type: 'none' };

/**
 * WAI-ARIA treeview keyboard model for an RTL tree: ArrowLeft opens (expand, then first child), ArrowRight closes
 * (collapse, then parent). Up/Down move through visible items, Home/End jump, Enter/Space activate.
 */
export function treeKeyAction(items: VisibleItem[], currentId: string, key: string, expanded: ReadonlySet<string>): TreeAction {
  const index = items.findIndex((i) => i.node.id === currentId);
  if (index < 0) return { type: 'none' };
  const item = items[index];
  const hasChildren = item.node.children.length > 0;
  const isOpen = hasChildren && expanded.has(item.node.id);
  switch (key) {
    case 'ArrowDown':
      return index + 1 < items.length ? { type: 'focus', id: items[index + 1].node.id } : { type: 'none' };
    case 'ArrowUp':
      return index > 0 ? { type: 'focus', id: items[index - 1].node.id } : { type: 'none' };
    case 'Home':
      return { type: 'focus', id: items[0].node.id };
    case 'End':
      return { type: 'focus', id: items[items.length - 1].node.id };
    case 'ArrowLeft': // RTL "inward"
      if (hasChildren && !isOpen) return { type: 'expand', id: item.node.id };
      if (isOpen) return { type: 'focus', id: item.node.children[0].id };
      return { type: 'none' };
    case 'ArrowRight': // RTL "outward"
      if (isOpen) return { type: 'collapse', id: item.node.id };
      if (item.parentId) return { type: 'focus', id: item.parentId };
      return { type: 'none' };
    case 'Enter':
    case ' ':
      return { type: 'activate', id: item.node.id };
    default:
      return { type: 'none' };
  }
}
