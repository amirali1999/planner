import type { IsoDate } from '../../shared/date/calendar';
import type { GoalStatus, PeriodType } from '../goals/types';

/**
 * Two independent measurements computed by the backend (2 decimals). null = not measured — shown as
 * «سنجیده نشده», never as 0%. There is deliberately no combined value.
 */
export type OutcomeSource = 'own_key_results' | 'child_rollup' | 'not_measured';

/**
 * Outcome: own active KRs (weighted), else equal-weight mean of direct active children that have an Outcome
 * (`child_rollup`), else null. Execution: distinct active tasks whose effective Goal is in the active subtree
 * (task counts are subtree counts).
 */
export interface ProgressValues {
  outcomeProgress: number | null;
  outcomeSource: OutcomeSource;
  keyResultsCount: number;
  /** Children averaged into a `child_rollup` Outcome; 0 otherwise. */
  contributingChildCount: number;
  measuredAt: string | null;
  executionProgress: number | null;
  activeTaskCount: number;
  doneTaskCount: number;
}

/** How one active KR feeds Outcome (backend-computed, 2 decimals). Contributions add up to Outcome. */
export interface KeyResultContribution {
  keyResultId: string;
  name: string;
  score: number;
  weight: number;
  weightPercent: number;
  contribution: number;
}

/** A direct active child's Outcome — listed even when the parent's own KRs decide its Outcome. */
export interface ChildOutcome {
  goalId: string;
  outcomeProgress: number | null;
  outcomeSource: OutcomeSource;
}

export interface GoalProgress extends ProgressValues {
  goalId: string;
  keyResults: KeyResultContribution[];
  childOutcomes: ChildOutcome[];
  /** Goals whose tasks entered Execution: this goal + its active descendants. */
  subtreeGoalCount: number;
}

export interface GoalCard extends ProgressValues {
  goalId: string;
  title: string;
  status: GoalStatus;
  periodType: PeriodType;
  periodStart: IsoDate;
  periodEnd: IsoDate;
}

export interface DashboardToday {
  date: IsoDate;
  today: {
    planExists: boolean;
    plannedCount: number;
    activeTaskCount: number;
    doneTaskCount: number;
    completionPercent: number | null;
  };
  goals: GoalCard[];
}
