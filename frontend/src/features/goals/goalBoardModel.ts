import { isoToJalali, type IsoDate } from '../../shared/date/calendar';
import { JALALI_MONTHS, toPersianDigits } from '../../shared/format';
import type { IconName } from '../../shared/ui/Icon';
import type { FocusItem } from '../focus/types';
import type { Goal, PeriodType } from './types';

/** Active focus items per goal id, in their manual order (position; unknown positions last by creation time). */
export function focusItemsByGoal(items: FocusItem[]): Map<string, FocusItem[]> {
  const byGoal = new Map<string, FocusItem[]>();
  for (const item of items) {
    if (!item.goalId || item.archivedAt) continue;
    const list = byGoal.get(item.goalId) ?? [];
    list.push(item);
    byGoal.set(item.goalId, list);
  }
  for (const list of byGoal.values()) {
    list.sort((a, b) => (a.position ?? Infinity) - (b.position ?? Infinity) || a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id));
  }
  return byGoal;
}

/** "۳۰ اسفند" (year only when it differs from the reference year, e.g. "۲ فروردین ۱۴۰۶"). */
export function shortJalali(iso: IsoDate, referenceIso: IsoDate): string {
  const { jy, jm, jd } = isoToJalali(iso);
  const sameYear = isoToJalali(referenceIso).jy === jy;
  return `${toPersianDigits(jd)} ${JALALI_MONTHS[jm - 1]}${sameYear ? '' : ` ${toPersianDigits(jy)}`}`;
}

/** The date shown under the ring: the deadline if set, otherwise the end of the goal's period. */
export function dueLine(goal: Pick<Goal, 'deadline' | 'periodEnd'>, today: IsoDate): { text: string; isDeadline: boolean } {
  return goal.deadline
    ? { text: `تا ${shortJalali(goal.deadline, today)}`, isDeadline: true }
    : { text: `پایان دوره ${shortJalali(goal.periodEnd, today)}`, isDeadline: false };
}

/** Goals have no icon field yet (backend gap); the card icon follows the period type for now. */
export const PERIOD_ICONS: Record<PeriodType, IconName> = {
  annual: 'target',
  quarterly: 'leaf',
  monthly: 'calendar',
  weekly: 'today',
};

export function doneCount(items: FocusItem[]): number {
  return items.filter((i) => i.status === 'done').length;
}
