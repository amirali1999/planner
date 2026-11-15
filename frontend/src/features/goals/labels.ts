import type { GoalStatus, PeriodType } from './types';

export const PERIOD_LABELS: Record<PeriodType, string> = {
  annual: 'سالانه',
  quarterly: 'فصلی',
  monthly: 'ماهانه',
  weekly: 'هفتگی',
};

export const STATUS_LABELS: Record<GoalStatus, string> = {
  not_started: 'شروع‌نشده',
  doing: 'در حال انجام',
  done: 'انجام‌شده',
  on_hold: 'متوقف',
  dropped: 'کنار گذاشته‌شده',
};

export const PERIOD_ORDER: PeriodType[] = ['annual', 'quarterly', 'monthly', 'weekly'];
export const STATUS_ORDER: GoalStatus[] = ['not_started', 'doing', 'done', 'on_hold', 'dropped'];
