# Data Model & Migration Plan — v2.0

This is a **logical contract**, not a drop-in Flyway SQL migration. Codex must generate portable, tested SQLite DDL in the numbered implementation tasks. Prefix primary keys as `TEXT UUID`, date-only as ISO `TEXT`, UTC timestamps as ISO `TEXT`. Use CHECK constraints where appropriate, FK indexes, unique constraints, and explicit application validation for cycle detection.

## Demo 0 tables (Migration V1 onward)

| Table | Core columns | Constraints |
|---|---|---|
| `goals` | id, title, description, period_type, period_start, period_end, deadline, status, parent_goal_id, archived_at, created_at, updated_at | parent nullable self FK, check valid period/status; cycle check service |
| `key_results` | id, goal_id, name, unit, baseline, target, weight, archived_at, created_at, updated_at | FK goal, `baseline <> target`, `weight > 0`; use DECIMAL-like storage plan |
| `key_result_checkins` | id, key_result_id, value, measured_at, note, created_at | FK KR, append-only |
| `tasks` | id, title, description, goal_id, focus_item_id NULL, status, priority, estimated_minutes, due_date, blocked_reason NULL, archived_at, created_at, updated_at | FK goal; deferred focus FK planning note below |
| `daily_plans` | id, local_date, created_at, updated_at | unique local_date |
| `daily_plan_items` | id, plan_id, task_id, position, created_at | FK, unique(plan_id, task_id) |

**SQLite DDL evolution note:** The `tasks.focus_item_id` foreign key must be created only once the `focus_items` table exists. For simplicity either (a) create `focus_items` in initial migration as an inactive schema element (no API yet), allowing valid FK from `tasks`, OR (b) defer both column and FK and perform explicit tested SQLite table rebuild later. **Preferred: option (a).** This is an intentional anticipatory schema only; do not expose Focus Item UI in Demo 0.

**Numeric precision:** SQLite has no native fixed-decimal type. Choose and document a stable strategy that avoids Java double surprise (for instance, validated decimal string storage and Java BigDecimal conversion); unit-test weighted progress and decreasing targets. No NaN/Infinity. Use deterministic numeric output rounding for percent display (two decimals), but preserve unrounded values for computation.

## MVP v0 tables

| Table | Core columns | Constraints |
|---|---|---|
| `focus_items` | id, title, kind, description, goal_id, status, archived_at, created_at, updated_at | Goal FK nullable; can be precreated in Demo migration |
| `milestones` | id, goal_id, title, due_date, completed_at, created_at, updated_at | FK Goal; milestone completion does NOT change KR score |
| `inbox_items` | id, raw_text, status, decision, created_entity_type, created_entity_id, processed_at, created_at, updated_at | free-text capture; immutable origin audit |
| `goal_history` | id, goal_id, event_type, before_json, after_json, created_at | selective audit for status, parent, KR structural changes |

## MVP v1 planned tables
`weekly_plans(id, week_start_date, capacity_minutes, status, created_at, ...)`; `weekly_plan_items(plan_id, task_id, priority, committed_estimate_minutes, ...)`; `plan_change_events(id, task_id, from_plan_id?, to_plan_id?, changed_at, reason?)`; `reviews(id, period_type, period_start, narrative, decisions_json, ...)`. Snapshot commits preserve original weekly commitment. One weekly plan per Saturday start.

## MVP v2 planned tables
`time_logs`, `time_blocks`, `recurrence_rules`, `goal_progress_snapshots`, `life_areas`.

## Migrations and integrity

Owner-requested V16 adds `activity_logs(id,user_id,local_date,title,category,start_minute,end_minute,created_at,updated_at)`.
Each row belongs to its user. Actual local-day intervals are independent from tasks and plans; explicit edit/delete
is allowed for correcting the personal log. V16 is additive and preserves existing records.
V17 rebuilds activity_logs preserving V16 rows, removing the five-minute restriction and adding nullable task_id
(ON DELETE SET NULL). activity_timers stores one running title/UTC started_at/IANA time_zone per user.
Completed intervals are written only on explicit stop, in the same transaction as clearing the running timer.
V18 adds nullable `activity_timers.planned_minutes` with a 1..120 CHECK. Null identifies the ordinary open-ended timer;
a value identifies a persisted Pomodoro whose completed log is capped at that duration. Existing V17 timers migrate as null.
The service treats active timer intervals and completed `activity_logs` as mutually exclusive: timer start checks existing
logs, while manual log create/update checks the running timer. Explicit timer cancellation deletes only `activity_timers`.

V19 adds `task_notes(id,task_id,body,created_at,updated_at)` with a required foreign key to `tasks` and an index on
`(task_id,created_at,id)`. Notes inherit ownership from the task. Archiving keeps them readable, and the existing
permanent task deletion transaction removes them before deleting the task. V19 preserves existing task rows.

1. Flyway versions are forward-only, no rewrite of applied scripts.
2. SQLite `PRAGMA foreign_keys=ON` on each connection and `busy_timeout` configured and integration tested.
3. Non-destructive archive; no `ON DELETE CASCADE` for user data history by default.
4. Index FK fields and common queries (goal parent, goal period/status, task status/goal/focus, check-in measured_at, daily plan date).
5. Tests create and destroy only isolated temporary DB, never production `data/planner.db`.
6. Before future migrations that restructure a table, test upgrade from a data-populated previous DB.
