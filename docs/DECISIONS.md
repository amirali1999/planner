# Decisions, Assumptions, and Change Log

## Accepted by user
- Flexible parent/child Goals; independent Goals allowed.
- Four goal periods: annual/quarterly/monthly/weekly; weekly starts Saturday; calendar Jalali.
- User wants both KPI outcomes and task execution tracked; separate outcome and execution now resolves conceptual conflict in prior averaging formula.
- Java + Spring Boot backend, React frontend, local SQLite storage; incremental top-down demo-first development.
- Required full-product scope: Goal tree, success criteria/deadlines, Focus Items/Tasks, Backlog, weekly and daily planning, carryover, status, dashboards, time estimate/actual, reviews/history, durable local storage, mobile UI, export/backup, offline-host operation.

## Recommended product decisions (not independently confirmed by user)
- Multiple weighted numeric KRs, KR check-ins append-only, Goal Outcome fallback to direct-child average only when no own KRs.
- Inbox Quick Capture and triage from MVP v0; Milestones from MVP v0; Life Areas and habits deferred to MVP v2.
- Weekly commitments independent of Goals; over-capacity warning (non-blocking), default 20% buffer suggestion.
- Goal statuses `not_started`, `doing`, `done`, `on_hold`, `dropped`; Task stays 3 statuses plus blockedReason.
- `task.goalId` overrides `focusItem.goalId` for a unique effective Goal.
- Dates stored Gregorian ISO; UI converts to Jalali; default timezone from browser IANA zone.
- local-only server loopback until security design for mobile access.

## Superseded v1 behavior
- REMOVE `goal_kpis` with UNIQUE(goal_id) and direct mutable current score as the sole measure.
- REMOVE `(KPI + Task)/2` formula and `combinedProgress` in API/dashboard.
- REPLACE with many `key_results` and append-only `key_result_checkins`; Outcome and Execution independently measured.
- KEEP Task / Goal / Focus relation concepts, Flyway, SQLite, React and Java stack.

## Explicit limitations / next decisions after initial version
- Cross-device sync and iPhone offline: deferred separate architecture decision; don't promise that a server-only SQLite app runs offline on iPhone.
- User-selectable KR weighting and retrospective corrections need UX validation after first real usage; technical model permits weights.
- Full recurrence exceptions and habit streak algorithm should be specified during MVP v2, not guessed in Demo 0.
