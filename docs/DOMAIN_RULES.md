# Domain Rules — Normative Contract (v2.0)

## Terminology and IDs
All IDs are UUID strings. All entities have UTC createdAt and updatedAt; soft archive uses archivedAt. The UI can show a Jalali date but API date-only fields always contain Gregorian ISO `YYYY-MM-DD`. No client-generated fake data in production.

## Goal
- `periodType`: `annual | quarterly | monthly | weekly`.
- `status`: `not_started | doing | done | on_hold | dropped`; status is explicitly set, never inferred from numeric progress.
- `parentGoalId`: optional; no self-parent/cycles. Parent must have strictly coarser periodType; skip levels allowed. Date mismatch is a warning, not silent correction.
- Goals without KRs are legitimate; blank Outcome is not 0%.
- Archiving a parent does not cascade-archive descendants or delete historical relationships.

## KR & Check-in
- Each Goal has 0..N active KRs. KR fields: `id, goalId, name, unit?, baseline(decimal), target(decimal), weight(decimal>0), archivedAt?`.
- Current is a **derived** value: latest recorded check-in by `(measuredAt, createdAt, id)` or baseline if none. Baseline alone counts as a numeric measurement (score 0%, because progress has been explicitly measured at baseline). An archived KR is not counted.
- Check-ins are append-only (`id, keyResultId, value, measuredAt (UTC), note?, createdAt`); no destructive update. A correction is a new check-in with a note. Restrict editing history in initial versions.
- Reject `target == baseline`, NaN/Infinity, nonpositive/invalid weights, huge arbitrary precision not accommodated by persistence (document configured decimal scale).
- `KR score = clamp(100*(current-baseline)/(target-baseline),0,100)`. Decreasing targets work.
- `Outcome(goal)` = weighted average of its own active KR scores if any; else unweighted average of **direct active child** outcomes with a defined value; else `null`. Do not mix own KRs and child outcomes in one score. For parent with own KRs, show child outcomes separately for transparency.

## Task & attribution
- `status`: `not_started | doing | done`. `blockedReason` optional; Task may be blocked without adding a fourth status. `dueDate` is a real deadline; `plannedDate` lives on a plan item.
- `goalId` and `focusItemId` both nullable. **Effective Goal** is direct `task.goalId` if set; otherwise associated `focusItem.goalId`; otherwise `null`. If both direct Goal and Focus Item Goal differ, direct Goal wins and UI warns.
- A task can appear in Today, a weekly plan, a project and a Goal view **without cloning its row**.
- `Execution(goal)` = `100 × (unique active Done tasks in its effective goal subtree)/(unique active tasks in that subtree)`, or `null` if no eligible tasks; ignore archived tasks. A task in a child goal is counted for each ancestor independently exactly once, not twice within one aggregate.

## Planning and reviews
- Daily plan unique by `localDate` (ISO Gregorian). Its items have unique `(dailyPlanId, taskId)`; adding an existing task to the same date returns 409. An empty plan's completion is null (`no_plan`).
- Weekly plan is independent of Weekly Goal and unique by Saturday `weekStartDate`; weekEnd is Friday `+6 days`. The user chooses weekly commitments; capacity warning never silently blocks.
- `estimatedMinutes` is optional, non-negative integer; 0 is valid but unestimated tasks should be distinguished (`null` vs 0).
- When schedule changes (MVP v1), keep plan item audit history; original weekly commitment snapshot is not overwritten.
- `Inbox` accepts free text; triage must preserve origin and record resulting entity reference and decision. In Demo 0 Inbox does not exist yet.
- Review snapshots preserve point-in-time plan and measured results, not just live aggregate queries.

## Privacy and offline
- Default binding host `127.0.0.1`. No LAN exposed without authentication/explicit config, and no unsolicited outbound API requests.
- Browser/mobile offline behavior is distinct from backend SQLite persistence. Offline syncing across devices is out of initial scope.

## Contract error codes
`400 validation_error` for shape/date/semantic validation; `404 not_found`; `409 conflict` for duplicates/cycle/transitions; `500 internal_error` sanitized. JSON `{code, message, details?}`; Persian user-facing message, stable English machine code.

## Calculation acceptance vectors
1. KR baseline=20 target=60 check-in current=40 => 50.
2. KR baseline=100 target=60 check-in current=80 => 50.
3. 2 KRs: score=100 weight=1 and score=0 weight=3 => Outcome=25.
4. Goal has KR=100 and two Done out of four active tasks => Outcome=100, Execution=50; **no combined score**.
5. Goal with no KRs but 2 children with outcomes 40 and 80 => Outcome=60.
6. Goal with no KR, no measured child => Outcome=null; without tasks => Execution=null.
7. Same Task linked to a Focus Item and directly linked to same Goal => counted once.
8. Task directly linked to Goal A and Focus Item linked to Goal B => only Goal A attribution, not B.
9. Task in both weekly and daily plan => one Task row; no duplicate toward Goal Execution.
10. A week starting on a Jalali month's last Saturday ends on Friday of next month; seven Gregorian ISO dates.
