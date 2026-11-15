/** DELETE /goals|focus-items|tasks/{id} — what was removed. */
export interface DeletionResult {
  goals: number;
  focusItems: number;
  tasks: number;
  detachedChildGoals: number;
}
