import type { CheckIn, KeyResultHistoryEntry } from './types';

/** One row of a KR timeline: a measurement or an audited structural change. */
export type TimelineEntry =
  | { kind: 'check_in'; at: string; checkIn: CheckIn }
  | { kind: 'change'; at: string; change: KeyResultHistoryEntry };

/**
 * Merges append-only check-ins (placed at their measuredAt) with audited KR changes (placed at createdAt),
 * newest first. Ties keep the backend order, so the result is deterministic.
 */
export function buildTimeline(checkIns: CheckIn[], changes: KeyResultHistoryEntry[]): TimelineEntry[] {
  const entries: TimelineEntry[] = [
    ...checkIns.map((c) => ({ kind: 'check_in' as const, at: c.measuredAt, checkIn: c })),
    ...changes.map((h) => ({ kind: 'change' as const, at: h.createdAt, change: h })),
  ];
  return entries
    .map((e, i) => ({ e, i, t: Date.parse(e.at) }))
    .sort((a, b) => b.t - a.t || b.i - a.i)
    .map((x) => x.e);
}

export const STRUCTURAL_FIELDS = ['baseline', 'target', 'weight'] as const;

/** True when a KR edit changes baseline, target or weight: the backend then requires an explanatory note. */
export function isRecalibration(
  current: { baseline: number; target: number; weight: number },
  next: { baseline: number; target: number; weight: number },
): boolean {
  return STRUCTURAL_FIELDS.some((f) => current[f] !== next[f]);
}
