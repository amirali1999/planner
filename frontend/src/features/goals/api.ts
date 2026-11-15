import { apiRequest, apiRequestWithHeaders } from '../../shared/api/client';
import type { DeletionResult } from '../archive/types';
import type { Task } from '../tasks/types';
import type {
  CheckIn,
  CreateCheckInRequest,
  CreateGoalRequest,
  CreateKeyResultRequest,
  Goal,
  GoalArchiveFilter,
  GoalTreeResponse,
  KeyResult,
  KeyResultHistoryEntry,
  UpdateKeyResultRequest,
  UpdateGoalRequest,
} from './types';

const enc = encodeURIComponent;

export const goalsApi = {
  /** Default: active goals only; 'all' also returns archived ones (e.g. to show names on linked tasks). */
  list: (archived: GoalArchiveFilter = 'false', signal?: AbortSignal) =>
    apiRequest<Goal[]>(`/goals?archived=${archived}`, { signal }),
  /**
   * Filtered, stably ordered page (query from `goalApiQuery`). The total number of matches comes from `X-Total-Count`.
   */
  search: async (query: string, signal?: AbortSignal) => {
    const { data, headers } = await apiRequestWithHeaders<Goal[]>(`/goals?${query}`, { signal });
    const total = Number(headers.get('X-Total-Count'));
    return { items: data, totalCount: Number.isFinite(total) && headers.has('X-Total-Count') ? total : data.length };
  },
  /** New priority order of ALL active goals (highest first); returns them with their new priority. */
  reorder: (goalIds: string[]) => apiRequest<Goal[]>('/goals/priority', { method: 'PUT', body: { goalIds } }),
  get: (goalId: string, signal?: AbortSignal) => apiRequest<Goal>(`/goals/${enc(goalId)}`, { signal }),
  /** Forest of goals: independent goals and goals with a filtered-out parent are roots; each goal appears once. */
  tree: (archived: GoalArchiveFilter = 'false', signal?: AbortSignal) =>
    apiRequest<GoalTreeResponse>(`/goals/tree?archived=${archived}`, { signal }),
  /** Distinct tasks whose EFFECTIVE goal is this goal (direct link wins over the focus item's goal). */
  tasks: (goalId: string, signal?: AbortSignal) => apiRequest<Task[]>(`/goals/${enc(goalId)}/tasks`, { signal }),
  create: (body: CreateGoalRequest) => apiRequest<Goal>('/goals', { method: 'POST', body }),
  update: (goalId: string, body: UpdateGoalRequest) =>
    apiRequest<Goal>(`/goals/${enc(goalId)}`, { method: 'PATCH', body }),
  /** Idempotent; never archives children, KRs or tasks. */
  archive: (goalId: string) => apiRequest<Goal>(`/goals/${enc(goalId)}/archive`, { method: 'POST' }),
  unarchive: (goalId: string) => apiRequest<Goal>(`/goals/${enc(goalId)}/unarchive`, { method: 'POST' }),
  /** Permanent; only an archived goal. Deletes KRs, check-ins, milestones, history, its focus items and their/its tasks; child goals are detached. */
  remove: (goalId: string) => apiRequest<DeletionResult>(`/goals/${enc(goalId)}`, { method: 'DELETE' }),

  listKeyResults: (goalId: string, signal?: AbortSignal, includeArchived = false) =>
    apiRequest<KeyResult[]>(`/goals/${enc(goalId)}/key-results${includeArchived ? '?includeArchived=true' : ''}`, { signal }),
  updateKeyResult: (krId: string, body: UpdateKeyResultRequest) =>
    apiRequest<KeyResult>(`/key-results/${enc(krId)}`, { method: 'PATCH', body }),
  /** Idempotent; check-ins are kept and stay readable. */
  archiveKeyResult: (krId: string, note?: string) =>
    apiRequest<KeyResult>(`/key-results/${enc(krId)}/archive`, { method: 'POST', body: note ? { note } : undefined }),
  keyResultHistory: (krId: string, signal?: AbortSignal) =>
    apiRequest<KeyResultHistoryEntry[]>(`/key-results/${enc(krId)}/history`, { signal }),
  createKeyResult: (goalId: string, body: CreateKeyResultRequest) =>
    apiRequest<KeyResult>(`/goals/${enc(goalId)}/key-results`, { method: 'POST', body }),
  getKeyResult: (krId: string, signal?: AbortSignal) => apiRequest<KeyResult>(`/key-results/${enc(krId)}`, { signal }),

  listCheckIns: (krId: string, signal?: AbortSignal) =>
    apiRequest<CheckIn[]>(`/key-results/${enc(krId)}/check-ins`, { signal }),
  createCheckIn: (krId: string, body: CreateCheckInRequest) =>
    apiRequest<CheckIn>(`/key-results/${enc(krId)}/check-ins`, { method: 'POST', body }),
};
