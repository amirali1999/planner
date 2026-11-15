import { apiRequest } from '../../shared/api/client';
import type { DeletionResult } from '../archive/types';
import type { CreateTaskRequest, Task, TaskFilters, UpdateTaskRequest } from './types';

export const tasksApi = {
  list: (filters: TaskFilters, signal?: AbortSignal) => {
    const params = new URLSearchParams({ archived: filters.archived });
    if (filters.status) params.set('status', filters.status);
    if (filters.goalId) params.set('goalId', filters.goalId);
    if (filters.focusItemId) params.set('focusItemId', filters.focusItemId);
    return apiRequest<Task[]>(`/tasks?${params.toString()}`, { signal });
  },
  create: (body: CreateTaskRequest) => apiRequest<Task>('/tasks', { method: 'POST', body }),
  update: (taskId: string, body: UpdateTaskRequest) =>
    apiRequest<Task>(`/tasks/${encodeURIComponent(taskId)}`, { method: 'PATCH', body }),
  /** Permanent; only an archived task (409 not_archived otherwise). Also removes it from daily plans. */
  remove: (taskId: string) => apiRequest<DeletionResult>(`/tasks/${encodeURIComponent(taskId)}`, { method: 'DELETE' }),
};
