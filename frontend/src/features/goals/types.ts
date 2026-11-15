import type { OutcomeSource } from '../dashboard/types';
import type { IsoDate } from '../../shared/date/calendar';
import type { PeriodType } from '../../shared/date/periods';

export type { PeriodType };
export type GoalStatus = 'not_started' | 'doing' | 'done' | 'on_hold' | 'dropped';

export type GoalArchiveFilter = 'false' | 'true' | 'all';

/** Non-blocking notice from the backend, e.g. a date mismatch with the parent. Never prevents saving. */
export interface GoalWarning {
  code: 'period_outside_parent' | 'child_period_outside' | 'deadline_outside_period' | string;
  message: string;
  details?: Record<string, unknown>;
}

/** GET /goals/{id} — mirrors backend GoalResponse. Date-only fields are Gregorian ISO. */
export interface Goal {
  id: string;
  title: string;
  description: string | null;
  status: GoalStatus;
  periodType: PeriodType;
  periodStart: IsoDate;
  periodEnd: IsoDate;
  deadline: IsoDate | null;
  parentGoalId: string | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** Present on single-goal responses only (not in lists). */
  warnings?: GoalWarning[];
  /** Manual priority among active goals, 1 = highest (right-most on the board); null when archived. */
  priority: number | null;
}

/** PATCH body: omitted key = unchanged, null = clear. periodType is not editable. */
export interface UpdateGoalRequest {
  title?: string;
  description?: string | null;
  status?: GoalStatus;
  periodStart?: IsoDate;
  periodEnd?: IsoDate;
  deadline?: IsoDate | null;
  parentGoalId?: string | null;
}

export interface CreateGoalRequest {
  title: string;
  description?: string;
  periodType: PeriodType;
  periodStart: IsoDate;
  periodEnd: IsoDate;
  deadline?: IsoDate;
  status: GoalStatus;
}

/** Mirrors backend KeyResultResponse. `score` is computed by the backend (0..100, 2 decimals). */
export interface KeyResult {
  id: string;
  goalId: string;
  name: string;
  unit: string | null;
  baseline: number;
  target: number;
  weight: number;
  current: number;
  currentSource: 'check_in' | 'baseline';
  currentMeasuredAt: string | null;
  score: number;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateKeyResultRequest {
  name: string;
  unit?: string;
  baseline: number;
  target: number;
  weight: number;
}

/** PATCH /key-results/{id}: omitted = unchanged; baseline/target/weight changes need `note`. */
export interface UpdateKeyResultRequest {
  name?: string;
  unit?: string | null;
  baseline?: number;
  target?: number;
  weight?: number;
  note?: string;
}

/** Audited KR structural change (GET /key-results/{id}/history); before/after hold only changed fields. */
export interface KeyResultHistoryEntry {
  id: string;
  eventType: 'key_result_created' | 'key_result_changed' | 'key_result_archived';
  before: Record<string, string | null>;
  after: Record<string, string | null>;
  note: string | null;
  createdAt: string;
}

export interface CheckIn {
  id: string;
  keyResultId: string;
  value: number;
  measuredAt: string;
  note: string | null;
  createdAt: string;
}

export interface CreateCheckInRequest {
  value: number;
  measuredAt: string;
  note?: string;
}

/** GET /goals/tree node. Outcome and Execution are separate backend values (null = not measured). */
export interface GoalTreeNode {
  id: string;
  title: string;
  status: GoalStatus;
  periodType: PeriodType;
  periodStart: IsoDate;
  periodEnd: IsoDate;
  deadline: IsoDate | null;
  parentGoalId: string | null;
  /** Has a parent that the current filter excludes (e.g. archived parent) → shown as a root. */
  detachedFromParent: boolean;
  archivedAt: string | null;
  outcomeProgress: number | null;
  outcomeSource: OutcomeSource;
  keyResultsCount: number;
  contributingChildCount: number;
  executionProgress: number | null;
  activeTaskCount: number;
  doneTaskCount: number;
  children: GoalTreeNode[];
}

export interface GoalTreeResponse {
  archived: GoalArchiveFilter;
  goalCount: number;
  roots: GoalTreeNode[];
}
