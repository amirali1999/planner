import { apiRequest } from '../../shared/api/client';
import type { DeletionResult } from '../archive/types';
import type { CreateTaskRequest, Task, TaskFilters, TaskNote, UpdateTaskRequest } from './types';

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

export const taskNotesApi = {
  list: (taskId: string, signal?: AbortSignal) =>
    apiRequest<TaskNote[]>(`/tasks/${encodeURIComponent(taskId)}/notes`, { signal }),
  add: (taskId: string, body: string) =>
    apiRequest<TaskNote>(`/tasks/${encodeURIComponent(taskId)}/notes`, { method: 'POST', body: { body } }),
  update: (noteId: string, body: string) =>
    apiRequest<TaskNote>(`/task-notes/${encodeURIComponent(noteId)}`, { method: 'PUT', body: { body } }),
  remove: (noteId: string) => apiRequest<void>(`/task-notes/${encodeURIComponent(noteId)}`, { method: 'DELETE' }),
  counts: (signal?: AbortSignal) => apiRequest<{ taskId: string; count: number }[]>('/task-notes/counts', { signal }),
};
