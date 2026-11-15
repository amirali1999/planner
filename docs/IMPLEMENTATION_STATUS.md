# Implementation Status & Hand-off — Personal Life Planner v2.0

**Last updated:** 2026-11-14 · **Last completed task:** inline day editing in the weekly-plan deadline column (owner request) · **Next task:** the next item in your roadmap

This file lets any developer or coding agent continue the work. It records what is built, decisions taken where the
normative documents are silent, deviations/additions to the API contract, and known pitfalls.

**Read order before a new task:** `AGENTS.md` → `docs/PRD.md`, `docs/DOMAIN_RULES.md`, `docs/API_CONTRACT.md`,
`docs/DATA_MODEL.md` (normative) → this file → `README.md` (run/verify) → `frontend/README.md`.
The normative documents win over this file; this file wins over guessing.

> Source repository: https://github.com/amirali1999/planner . Runtime databases, local-service artifacts,
> logs, dependencies and generated build outputs are excluded from version control.

---

## 1. Completed tasks

Daily-log bottom-edge resizing: saved/draft blocks have a pointer/keyboard resize handle with live end-time
preview, window pointer tracking, edge auto-scroll and Escape/cancel. Saved blocks PUT editable fields only;
errors revert the optimistic end and display feedback. Bounds: minimum one minute, next activity, midnight.
Running timer marker remains fixed until stop. Frontend-only; no API/schema change.

Activity-log layout follow-up: removed statistics/help/permanent activity form and top start button.
Left-side timer card accepts title and starts/stops recording directly. Manual creation remains inline;
clicking a completed block opens a native edit dialog with category/task/time controls. Sidebar has no scrolling.

Activity-log follow-up: V17 preserves V16 records, adds optional active-task attribution, minute precision and
one persisted timer per user. Timeline click opens an inline title editor (30-minute default), existing blocks
open category/task editing, coral line shows current time. Start asks for a title; the running marker stays fixed
until Stop atomically saves intervals split at midnight. Immediate stop uses one minute. Overlap keeps timer running.
Verification: full backend baseline 357 PASS; 27 focused tests PASS after adding timer, V16→V17 upgrade,
restart and minute/task-link scenarios. Frontend 215 PASS, typecheck/build PASS. Scratch-browser inline creation,
timer surviving reload with unchanged marker height, no early interval writes, explicit stop, current-time line
and desktop page-height checks PASS. Running owner services were not replaced.

Activity-log verification (2026-11-14): full backend 357 PASS, frontend 215 PASS, typecheck/build PASS;
scratch-browser create + reload persistence PASS, desktop 1440×900 page-height check PASS. Migration baseline: V1–V17.

| ID | Scope delivered |
|---|---|
| Daily actual activity log | Owner request 2026-11-14: `/activity-log`, top-bar navigation, Jalali day picker, four category colours, actual-time timeline, day totals, create/edit/explicit delete. `GET/POST /activity-logs`, `PUT/DELETE /activity-logs/{id}`; V16 `activity_logs`, CurrentUser-scoped. Minute-precision intervals, per-user/day overlap validation, no task/plan effects. Persisted timer and inline creation are included in V17; drag/resize is not included. API CRUD/isolation and real restart tests added; MigrationCount=17. |
| D-01 | Spring Boot 3.5.16 / Java 21 backend, `/api/v1/health`, error contract `{code,message,details?}`, loopback-only guard, CORS for the Vite dev origin, Maven wrapper. |
| D-02 | SQLite + Flyway V1 (all Demo 0 tables + inactive `focus_items`), FK + busy timeout on every pooled connection, decimal/timestamp codecs, isolated test DBs. |
| D-03 | Goals (create/read), multiple KRs per goal, append-only check-ins, KR score (increasing/decreasing, exact decimals). |
| D-04 | React 19 + Vite 8 RTL app, goals list/create, goal detail with KR + check-in, Jalali display, honest placeholders. |
| D-05 | Tasks API (3 statuses + `blockedReason`, PATCH with absent-vs-null semantics, archive via PATCH) and `/tasks` UI. |
| D-06 | Daily plan API (side-effect-free GET, idempotent PUT, add item with 409 `duplicate_plan_task`) and `/today` UI. |
| D-07 | `ProgressCalculator` (single source), `GET /goals/{id}/progress`, `GET /dashboard/today`, two separate Outcome/Execution cards. |
| D-08 | End-to-end release-gate test, `scripts/smoke.sh`, offline/mobile verification, preview-proxy fix, README demo checklist. |
| macOS local service | (2026-11-13, owner request) `scripts/macos`: double-click installer builds the backend JAR + frontend production bundle and installs per-user LaunchAgents. Backend stays loopback-only on 18080, Vite preview on 4173 proxies `/api`; both start at login, restart on crash and stop at logout. Double-click start/stop/uninstall controls are included; uninstall preserves `data/planner.db`. Runtime JAR is copied to gitignored `.runtime/`; logs go to `~/Library/Logs/Planner`. |
| G-01 | Goal PATCH, idempotent archive/unarchive (no cascade), archived filter on lists, V2 `goal_history` (append-only, same transaction). |
| G-02 | `parentGoalId` via PATCH with cycle protection (`goal_cycle`), granularity (`incompatible_period_granularity`), archived-parent rules, non-blocking date warnings. |
| G-03 | `jalaali-js` conversion, reusable Jalali calendar / date field / period picker (Saturday–Friday weeks), `/settings` IANA timezone for "today". |
| Task priority default + picker | (2026-11-10, owner request) Priority is required: create without it → `low`; PATCH `priority:null` → 400 `required`; V9 sets existing NULL priorities to `low`. Frontend: form default `low` (no empty option), order پایین·متوسط·بالا, `PriorityPicker` (badge = menu button, three menuitemradio choices, saves immediately, focus returns to the badge) on the focus tree and the dashboard week card. |
| Dashboard goals/focus card | (2026-11-10, owner request) «اهداف ماه» and «اهداف فصل» merged into one card spanning both rows of the 3rd column (≥1281px): header tabs «اهداف» (every active goal of the date — annual, quarterly, monthly, weekly — in one list, `period-tag` سالانه/فصلی/ماهانه/هفتگی, coarse periods first then manual goal priority; fixed 2026-11-11: annual goals were missing) / «آیتم‌های تمرکز» (loaded on first open: goals by priority, focus items, tasks → `buildFocusTree`, ring = done ÷ active tasks, null without tasks). Tabs follow the WAI-ARIA tab pattern. No backend change. |
| Task details pop-up (weekly) | (2026-11-12, owner request) On /weekly, the task title (list view) and the task title in the time-box side panel / block ⋮ «جزئیات تسک» open `TaskDetailDialog` (native <dialog>): status, priority, goal (effective), focus item, due date, planned days of the week, estimate, blocker, created/completed, warnings; «ویرایش» keeps the same details layout and turns every value into its input in place (title, status/priority chips, description, goal, focus item, due date, estimate, blocker; validated with `parseTaskForm`, PATCH only changed fields via `toUpdateRequest`) — no separate form, «انجام شد / برگرداندن». Also on /focus: task row ⋮ «ویرایش» (was «ویرایش در صفحهٔ کارها») opens the same pop-up directly in the edit form (`startEditing`), details after saving; the week line is hidden there. Today board and inbox keep their links to /tasks. Frontend only. |
| Redesign: top bar + glowing panels | (2026-11-12, owner screenshots) `Layout` = top bar (brand/date, main NAV, quick capture, inbox bell with unprocessed count via `GET /inbox-items?status=unprocessed`, avatar `AccountMenu` with inbox/archive/settings/theme/logout) instead of the sidebar — deviates from the AGENTS.md sidebar list/order; «کارها» stays out of the menu. Dark tokens sampled from the owner’s second image (graphite: bg #141c22, card gradient #20282e→#172025, border white 7.5%, accent #4edbc8 + secondary coral `--coral` #FF8A65 (quick capture, inbox badge, «نیازمند توجه» icon/due notes, timer needle, today in habits/week, time-box now line, calendar due dates, goal priority, «به برنامه امروز» buttons, focus-table deadline icon, weekly «این هفته» status, time-box today column, today-board «در انتظار» column/reasons, calendar today cell/column/«امروز» button/day tag and «در انتظار» chip); no bottom glow line/glow at the owner’s request; `--panel-bg`/`--panel-glow`), `--panel-*` tokens give every card the same glow; `--topbar-h` sizes full-height pages. Dashboard slide 1 rebuilt: WeekCard (`weekSummary`), AttentionCard (`attentionItems`, BEHIND_MARGIN 20, unmeasured goals never flagged), DailyTasksCard (own day switch), FocusTimer, goal/focus ring grid; stat cards and the daily-log placeholder removed. Calendar count/legend moved into its toolbar. Frontend only. |
| Dashboard slideshow + month calendar | (2026-11-12, owner request; fit updated 2026-11-13) Dashboard = two slides (`?slide=calendar` for the second; dots = ARIA tablist). `MonthCalendar` (month/week/day) + `calendarModel` (tested): per day the tasks planned on it (`GET /plans/daily` per visible day) ∪ tasks due that day (`GET /tasks?archived=false`), archived hidden, sorted doing → not started → done then priority; dot colour = `goalColor(effectiveGoalId)` for active goals. Month view measures the usable height of each row and shows every complete task line that fits; only the remainder becomes `+… کار دیگر`. Read-only, frontend only — no API/schema change. Legend lists goals instead of the design's work/study/personal categories (no such field exists). |
| Today board planned-only flow (تخته امروز) | (2026-11-11/14, owner requests) Page/nav renamed «تخته امروز» («تخته روز» for other dates). V11 `tasks.completed_at` (set by `TaskRepository` on the transition to done incl. create-as-done, kept while done, cleared on reopen; existing done tasks backfilled with `updated_at`), `completedAt` in TaskSrv. `todayBoardModel.buildBoard` now returns only persisted items of the selected daily plan; the board no longer calls carry-over or shows implicit «مانده از» cards. An earlier unfinished task appears only after «به برنامه امروز» in the dashboard persists it with `POST /plans/daily/{today}/items`. Read-only `GET /plans/daily/{date}/carry-over` remains the dashboard source (`CarriedTaskSrv{plannedOn, task}`); future-rescheduled tasks are excluded. No schema change. |
| Archive page + permanent delete | (2026-11-10, owner request and decisions) `/archive` (`ArchivePage`, `archiveModel` tested): archived goals/focus items/tasks, restore, delete with impact confirmation. Backend `DeletionController`/`DeletionService`/`DeletionRepository`: `DELETE /goals/{id}`, `/focus-items/{id}`, `/tasks/{id}` — archived only (409 `not_archived`). Owner chose: goal delete removes KRs, check-ins, milestones, its history, its focus items and all tasks linked to the goal or those items; child goals detached (+ `parent_changed` history); focus item delete removes its tasks; task delete removes its plan items. V10 recreates the check-in/goal-history no-delete triggers with `WHEN` so rows can only be deleted for an archived goal (append-only otherwise). **Deviation from AGENTS.md/OpenAPI "nothing is hard-deleted" by explicit owner request**; inbox `createdEntityId` may now point to a deleted entity. Hidden elsewhere: dashboard (`withoutArchived`), today board and weekly ignore archived goals/items as references. |
| Today plan Kanban | (2026-11-10, owner design `ui/today plan.png`) `/today` = `TodayBoardPage` (old list `TodayPage`/`PlanItemRow` removed; nav label «برنامه امروز»). Columns done/doing/waiting/not_started; «در انتظار» = not done + `blockedReason` (DOMAIN_RULES: no 4th status) — `todayBoardModel` (tested): `columnOf`, `groupByColumn`, `moveInPlan` (new full order, hidden/archived items keep their place), `patchForColumn` (to waiting needs a reason, done→waiting restarts as not_started, leaving waiting clears the reason). `KanbanBoard`: pointer drag (mouse on the card body with threshold, handle for mouse/pen/touch) followed on window, ghost, placeholder, column + board auto-scroll, keyboard ↑↓←→ on the handle, Escape cancels; ⋮ menu moves. Optimistic update, then `PATCH /tasks/{id}` (if the column changes) + `PUT /plans/daily/{date}/items/order`; on error revert + reload. Backend: new `PUT /plans/daily/{date}/items/order` (`ReorderPlanItemsVO`, `DailyPlanService.reorder`, full list of the plan's items, 400/404, per-user, never creates a plan) — no migration (position column existed). Not done: removing a task from a day's plan (no endpoint; history rule). |
| Calendar drag-and-drop | (2026-11-13, owner request) New `POST /plans/daily/{date}/tasks/{taskId}/move` (`MovePlanTaskVO{toDate}`, `DailyPlanService.moveTask`, `DailyPlanRepository.findItemByTask/deleteItem`; one transaction; removes the plan item from the source day — first remove path for daily plan items, used only for an explicit move). `DailyPlanMoveIntegrationTest` (2). Frontend `calendarDrag.tsx` (`useCalendarDrag`: pointer events on window, threshold, ghost, Esc, `data-cal-day` targets) in the month and week views; planned → `dailyPlanApi.move`, deadline-only → PATCH dueDate; day view «روز قبل / روز بعد» as the keyboard alternative. |
| Daily list order + separate remaining card | (2026-11-13/14, owner requests) Dashboard «کارهای روزانه» contains only the selected day's persisted plan and uses `sortDaily` (unfinished high → low priority, plan order for ties; done last). Unfinished active carry-over tasks are in a separate «کارهای باقی‌مانده» card below «برنامه این هفته» and above «نیازمند توجه»; compact rows reuse `TaskMeta` to show the active effective goal (colour dot), focus item and priority under the smaller title. The source day is deliberately hidden; the compact «+ امروز» button sits on the right and persists with `POST /plans/daily/{today}/items`, then refreshes the remaining, daily, week and planned-only Today-board data. «برنامه این هفته» judges each day with `dayOutcome` (done that day vs. finished later → «انجام‌شده در …», read-only; unfinished past → «منتقل شد»); day bars use it too. `dailyOrder.test.ts` covers filtering/order. Frontend only; no API/schema change. |
| Focus item notes | (2026-11-12, owner request) V15 `focus_item_notes`. Backend `FocusItemNoteController` (tag "Focus item notes"), `FocusItemNoteService`, `FocusItemNoteRepository` (ownership through the item), `FocusItemNote`, `FocusItemNoteLimits`, `FocusItemNoteSrv`, `NoteCountSrv`, `FocusItemNoteVO`: GET/POST `/focus-items/{id}/notes`, GET `/focus-item-notes/counts`, PUT/DELETE `/focus-item-notes/{noteId}` (409 `focus_item_archived` on writes; notes stay readable). Permanently deleting a focus item deletes its notes. `FocusItemNoteIntegrationTest` (2). Frontend `focusNotesApi`, `FocusNotesDialog` (add, newest first, edit in place, delete; Ctrl/⌘+Enter); focus page: «یادداشت» button (count badge) and «＋ تسک» (drop-down add box under the row, replaces the inline add under the list) next to each item, clicking the item name opens the notes too, ⋮ «یادداشت‌ها» (details page stays in ⋮). Notes are not tasks (no status/planning/progress). New DELETE path for notes (free text, not history). |
| Habits | (2026-11-12, owner request; **habits brought forward from MVP v2**) V14 `habits` + `habit_checks`. Backend `HabitController` (tag "Habits"), `HabitService`, `HabitRepository`, `HabitPatchReader`, `Habit`, `HabitLimits`, `HabitSrv{id,title,doneDates,archivedAt,…}`, `CreateHabitVO`, `UpdateHabitVO`: GET `/habits?from&to[&archived]` (≤ 62 days), POST, PATCH (title/archived), PUT/DELETE `/habits/{id}/checks/{date}` (idempotent tick/untick; 409 `habit_archived`). `HabitIntegrationTest` (4, incl. another user → 404). Frontend `features/habits` (`habitsApi`, `habitsModel` tested, `HabitsCard`): dashboard «عادت‌ها» — per habit a checkbox for today + 7 circles Sat–Fri (future days disabled), optimistic toggles, inline add, ⋮ archive. Not done: archived habits are not on /archive yet (API supports `archived=true` + restore), no rename UI, no streaks. |
| Week without a day | (2026-11-12, owner request; removal added 2026-11-14) «+ به برنامه هفتگی» on the focus page no longer asks for a day: one click → `POST /plans/weekly/{weekStart}/items`, then «✓ در برنامه هفتگی» (`WeekPlanButton` rewritten like `TodayPlanButton`; `PlannedWeek` context on `FocusTreePage`). V13 `weekly_plan_items` (root table: user_id, week_start = Saturday (CHECK), task_id, UNIQUE(user, week, task)) — membership only, **a minimal weekly entity brought forward by owner request** (still no capacity/commitments/reviews, which stay MVP v1). Backend `WeeklyPlanController`/`WeeklyPlanService`/`WeeklyPlanRepository`, `WeeklyPlanItem`, `WeeklyPlanItemSrv` (reuses `AddPlanItemVO`); task permanent delete removes its items. `DELETE /plans/weekly/{weekStart}/tasks/{taskId}` atomically removes both the undated membership and every daily-plan item in that week, while preserving task fields and other weeks. `weeklyModel.buildWeekRows(…, weekly)` adds these tasks (deadline column «بدون روز» unless a day/dueDate exists; sorted after dated rows); `WeeklyPlanPage` loads them, so they also appear in the time-box panel. `WeeklyPlanIntegrationTest` (4). |
| Weekly time-box | (2026-11-12, owner request + screenshot; **brought forward from MVP v2 «time blocks» by explicit owner decision**, no actual-time logging) `/weekly?view=timebox`: view switch «فهرست / تایم‌باکس» on the weekly page (`?view=`), `TimeboxView` + `timeboxModel` (tested: labels, snap/move/resize, overlap, hidden blocks of archived tasks, minutes per task, colour). Grid Sat–Fri × 06:00–24:00 (56 px/h, inner scroll, sticky day header, today column, current-time line), side panel «تسک‌های این هفته» (unfinished week rows, boxed minutes). Pointer gestures followed on window (move incl. other day, resize by lower edge, drag task from panel → 1 h block), 15-min snapping, auto-scroll, Escape cancels, red clash preview; keyboard ↑↓ / ←→ / Shift+↑↓ / Enter / Delete; ⋮ menu; dialog for create/edit/delete. Optimistic save + revert/reload. Backend: V12 `time_blocks` (root table, user_id; local_date, start/end minute (multiples of 5, end > start ≤ 1440), title?, task_id?, color CHECK; index user/date/start), `TimeBlockController` (`GET ?from&to` ≤ 42 days, POST, PATCH via `TimeBlockPatchReader`, DELETE 204), `TimeBlockService` (no overlap per user/day — blocks of archived tasks ignored; link only own non-archived tasks; existing link may stay), `TimeBlockRepository`, `TimeBlockColor`, `TimeBlockLimits`, `CreateTimeBlockVO`/`UpdateTimeBlockVO`/`TimeBlockSrv`; `DeletionRepository.deleteTask` also deletes the task's blocks. `TimeBlockIntegrationTest` (7). Not done: «واقعی» (actual) time from the design (time logging is MVP v2), recurring blocks, icons per block type. |
| Weekly plan page | (2026-11-10, owner design `ui/weekly plan.png`; inline day editing and week removal added 2026-11-14) `/weekly` = `WeeklyPlanPage`, sidebar item enabled. Rows = tasks in the seven daily plans of the Sat–Fri week ∪ week-without-day items ∪ tasks with dueDate in the week ∪ (current week only) undone tasks with past dueDate; live task data overrides plan copies (`weeklyModel`, tested). The deadline/day cell is an inline Saturday–Friday selector: a planned task uses the transactional daily-plan move endpoint, a due-only task PATCHes `dueDate`, and a week-without-day item is added to the selected daily plan; errors stay in the cell. Status: انجام‌شده (done) / عقب‌افتاده (past week, dueDate < today, or every planned day passed) / این هفته / برنامه‌ریزی‌شده (future week). Week nav via `?week=`, search (task/goal/focus item), filter popover, sort menu, add-task dialog (`POST /tasks` then `POST /plans/daily/{date}/items` — two calls, not atomic), PriorityPicker, compact TodayPlanButton (`short`). Clicking the status chip of an actually planned row removes it from all seven daily plans plus the undated week list; due-only rows have a read-only chip because dueDate is not scheduling. Data: 7 daily-plan GETs + tasks + goals + focus items. No schema change. |
| Focus items tree page | (2026-11-10, owner design `ui/focus item.png`; collapsed-by-default 2026-11-14) `/focus` = `FocusTreePage`: goal (priority order) → focus items (position) → tasks, expandable rows with title / progress / task count / deadline. Every visit starts with all goals collapsed; each goal opens only on the user's click (search temporarily expands matching branches). Search across all levels (`focusTreeModel`, tested), inline add of focus items and tasks, task checkbox + priority badge + compact `TodayPlanButton` (one click → today's daily plan; ✓ when already there, from one `GET /plans/daily?date=today` shared via context) + `WeekPlanButton` (plans the task on a day of the current Sat–Fri week via the daily plan; no weekly-plan entity), ⋮ menus (`shared/ui/MenuButton`), create popup. Goal ring (right of the title) = Outcome — the separate Execution ring column was removed at the owner's request, that column («اولویت») now only holds task priority badges; focus-item ring = done ÷ active tasks of that item (null without tasks). Direct goal tasks get a «تسک‌های بدون آیتم تمرکز» group. Old filtered list moved to `/focus/list`. Data: 5 requests (goals, progress batch, focus items, tasks, all goals for the form). No backend change. |
| Focus item order | (2026-11-09, owner request) V8 `focus_items.position` (per goal, 1 = top; NULL without goal; existing items numbered by creation order). `PUT /goals/{goalId}/focus-items/order` (complete list of the goal's active items, else 400 — items can never move between goals here), `position` in FocusItemSrv; create appends, relink (PATCH goalId) appends to the new goal, unlink clears. The public `GET /focus-items` order is unchanged; the board sorts by position. UI `FocusSortableList`: dotted handle = button; pointer drag (mouse/pen/touch) followed on `window` (the pressed handle is replaced by the placeholder), ghost clamped to its own list, list auto-scroll, ↑/↓ on the focused handle, optimistic save + revert. |
| Goal priority + board DnD | (2026-11-09, owner request) V7 `goals.priority` (1 = highest, NULL when archived; existing active goals ranked in display order per owner). `PUT /goals/priority` (complete list of active goals, else 400), `sort=priority`, `priority` in GoalSrv; new goals append, archive clears + renumbers, unarchive appends; no goal-history row for priority. UI: single horizontal row (`GoalBoard`), mouse/pen drag with ghost + placeholder + edge auto-scroll, optimistic save with revert, menu «انتقال به راست/چپ» for keyboard/touch, live announcement; «اولویت n» replaces the Execution line on the card; «افزودن هدف» is a native `<dialog>`; board loads all goals (no paging). |
| Goals board | (2026-11-09, owner design `ui/goal.png`) `/goals` is a board of fixed-height goal cards (`GoalBoardCard`): period icon, title, ⋮ menu (details, manual status as menuitemradio, archive/restore with confirm), Outcome ring + separate Execution line, deadline or period end (overdue flag), the goal's active focus items as a scrollable checklist (PATCH status done/not_started) with quick add (kind `other`). All focus items come from one `GET /focus-items` grouped client-side. G-06 filters/paging kept behind a «فیلترها» toggle. Drag handles are decorative (no ordering in the backend). |
| Dashboard redesign | (2026-11-09, owner design `ui/9a643a2d-….png`) App shell with sidebar (dark theme by default + light toggle, bundled Vazirmatn font, inline SVG icons); dashboard is the home page: stat cards (quarterly/monthly mean Outcome rings, distinct planned tasks of the Jalali month / Sat–Fri week, client-only 25-min focus timer), today's tasks (tick = PATCH status, inline create+plan), week agenda (one row per day, priority badges), monthly/quarterly goal lists (Outcome ring + separate Outcome/Execution line). Habits and daily log are honest «به‌زودی» placeholders (no API); «برنامه هفتگی» and «گزارش‌ها» menu items disabled. Priority labels now بالا/متوسط/پایین. |
| Swagger / OpenAPI | (2026-11-09, owner request) springdoc 2.10.0: OpenAPI 3.1 `/v3/api-docs`, Swagger UI `/swagger-ui.html` (local, public, no user data). All 43 operations with summary/description, every parameter, request body, response (incl. headers Location, X-Total-Count, Set-Cookie, Retry-After, WWW-Authenticate) and every VO/Srv field documented with examples and allowed codes; bearer JWT security scheme with public endpoints marked; common 400/401/500 + `ApiErrorSrv` added by `OpenApiConfig`; `OpenApiDocumentationTest` enforces completeness; the generated document is written to `backend/target/openapi.json` by that test. |
| Authentication | (2026-11-09, owner request) Multi-user accounts with open registration; JWT access tokens (HS256, `typ=at+jwt`, 15 min) as `Authorization: Bearer`; opaque rotating refresh tokens (SHA-256 hashed, httpOnly SameSite=Strict cookie, reuse detection, 30-day absolute session); BCrypt; login throttle; per-user scoping of every repository query (`CurrentUser`); first account claims pre-auth data; V6 migration; login/register pages, route guard, in-memory token with single-flight refresh, logout. |
| Backend restructure | (2026-11-09, owner request, not a numbered task) Global layer packages for the whole backend (`controller`/`service`/`repository`/`model` + `model/enums`, `model/vo` for inputs `…VO`, `model/srv` for outputs `…Srv`; infra `config`/`exception`/`util`/`db`), no feature sub-packages; `*Dtos` holders split into one file per record; nested enums extracted (`GoalSort`, `EffectiveGoalSource`, `MilestoneStatusFilter`, `GoalMatch`, `OutcomeSource`); limits constants in `model/*Limits`. Pure move/rename — API JSON, SQL and behaviour unchanged (274 backend tests identical). |
| G-06 | `GET /goals` filters `status`/`periodType` (multi), archive, due window `deadlineFrom`/`deadlineTo`, `overdueOn`, `sort=period\|deadline`, `limit`/`offset` with total order and `X-Total-Count` (body stays an array); `GET /goals/progress?ids=` batch progress. UI: goal list with URL-backed filters, pagination, inline manual status select, measured progress line, Jalali deadline with overdue/due-today/due-soon flags; tree status filter (ancestors kept as context) + inline status + deadline flags; goal page inline status + deadline flag. |
| G-05 | Hierarchical Outcome rollup (`ProgressRollup`, pure + cycle-safe): own active KRs win (`own_key_results`), else equal-weight mean of direct active children with a defined Outcome, recursively (`child_rollup`), else `null` (`not_measured`). Execution over distinct active tasks whose effective goal is in the active subtree. Archived/dropped goals and their whole branch are pruned from ancestors' rollups. `/progress` adds `contributingChildCount`, `childOutcomes[]`, `subtreeGoalCount`; tree nodes and dashboard cards add `contributingChildCount`. UI captions for child rollup/subtree execution, goal page marks children excluded from the parent's rollup. |
| I-02 | `POST /inbox-items/{id}/process` (task/goal/focus_item conversions through the normal create services + reference/someday/discard) in one transaction with a conditional "processed" update (409 on reprocessing or a concurrent triage, entity rolled back); `decision` list filter; triage UI with decision picker, reused create forms, discard confirmation, navigation to the created entity, views for converted/reference/someday/discarded. |
| I-01 | V5 `inbox_items` (all DATA_MODEL columns incl. triage fields, CHECKs forbid decisions/entities on unprocessed items), `GET/POST /inbox-items`, `GET /inbox-items/{id}`; header «+ ثبت سریع» panel (removed from the top bar 2026-11-12, owner: unused; capture stays on `/inbox`) + `/inbox` queue; strict text-field coercion for all request bodies (`JacksonConfig`); `smoke.sh` builds offline-first. |
| F-02 | `task.focusItemId` in task create/PATCH; effective goal = direct `goalId` else focus item's goal (`EffectiveGoal`), never rewriting `task.goal_id`; `GET /goals/{id}/tasks`; Execution counts by effective goal (each task once); task responses expose attribution + `goal_differs_from_focus_item` warning; UI: focus picker with inline warning, shared `TaskAttribution`, goal/focus detail task lists. |
| F-01 | Focus Items activated on the V1 table (no migration): `GET/POST /focus-items`, `GET/PATCH /focus-items/{id}` (kinds project/book/course/area/other, statuses not_started/doing/done, optional single goal, archive flag); `/focus` list + `/focus/:id` detail, goal page section. |
| M-01 | V4 `milestones` (+`archived_at`), `GET/POST /goals/{id}/milestones` with status/date/archived filters, `PATCH /milestones/{id}` (edit, complete, archive); milestones section on the goal page, labelled as delivery checkpoint (not KR), optimistic completion checkbox. Regression test: completion never changes Outcome/Execution/KR score. |
| K-01 | `PATCH /key-results/{id}` (recalibration of baseline/target/weight requires a note, audited), idempotent `POST /key-results/{id}/archive`, `GET /key-results/{id}/history`, V3 `goal_history` rebuild (+`key_result_id`, `note`), per-KR Outcome breakdown in `/goals/{id}/progress`; KR edit/archive UI, merged check-in + change timeline, breakdown table, archived KRs section. |
| G-04 | `GET /goals/tree` (cycle-safe forest, independent/detached roots, stable order, per-node Outcome/Execution via batched progress), `/goals/tree` accessible RTL treeview, goal detail: children with progress, KR history preview, linked tasks, honest focus-item placeholder. |

**Latest focus-planning behavior (2026-11-14):** on `/focus`, «به برنامه امروز» uses
`POST /plans/daily/{date}/items?addToWeek=true`; the daily item and the containing week's undated membership are
created in one transaction, then both the today and weekly buttons show ✓. Other `TodayPlanButton` usages retain the
daily-only behavior. No schema change.

**Test baseline (all passing at hand-off):** backend 352 (`cd backend && ./mvnw -o test`), frontend 215
(`cd frontend && npm test`), `npm run build` (includes typecheck), `scripts/smoke.sh` → `SMOKE PASS`.

---

## 2. Decisions taken where the docs are silent

These are implementation choices, not user-confirmed product decisions. Revisit if the owner disagrees.

| Topic | Decision |
|---|---|
| Decimal storage | KR baseline/target/weight and check-in values are canonical decimal **TEXT**, read as `BigDecimal`. Max 6 fractional and 15 integer digits; larger values are rejected (400 `precision_exceeded`), never rounded. Output scores are rounded to 2 decimals (HALF_UP) only at the edge. |
| Timestamps | Stored as fixed-width UTC `YYYY-MM-DDTHH:MM:SS.sssZ` so text order equals time order (`ORDER BY measured_at` correctness). |
| Strict JSON | Unknown fields, numbers sent as strings, floats for integers, lenient dates and NaN/Infinity are all 400 with a per-field code in `details`. |
| Task `priority` | `low \| medium \| high` or null, validated in the API. The DB column has no CHECK (vocabulary can change without a table rebuild). |
| `focus_items.kind` / `.status` | V1 enforces only non-empty values; the vocabulary is enforced in the API since F-01 (see "Focus Items" below). |
| Daily plan creation | `POST /plans/daily/{date}/items` creates the day's plan if missing (same transaction). `GET` never creates. |
| Daily `completionPercent` | done ÷ non-archived tasks on the plan; archived items stay visible but are not counted; `null` with nothing to count. |
| Dashboard "active goals" | Not archived, status not `done`/`dropped`, and period contains the requested date (`on_hold` is shown with its badge). |
| Focus Items | Kind vocabulary `project/book/course/area/other` and status `not_started/doing/done` (PRD FR-E01: "three statuses") are validated in the API; the V1 columns only require non-empty values (no table rebuild needed). Single optional `goalId`; many-to-many is deferred (a `goalIds` field is rejected as unknown). Linking needs an existing, active goal; an existing link to a later-archived goal stays. Tasks cannot reference focus items yet (`focusItemId` on task create is still rejected) — that is the next task. |
| Milestones | Delivery checkpoints only: no link to `ProgressCalculator`, no audit rows. `completed` is a boolean API over `completedAt` (idempotent; un-completing clears it). Overdue/upcoming are computed in SQL against a client-supplied local date (backend has no user timezone); completed milestones are never overdue; undated ones are neither overdue nor upcoming. Archive via PATCH `archived`, like tasks. |
| KR recalibration | Changing baseline, target or weight is a "recalibration": `note` is mandatory and the change is audited. Name/unit edits need no note. Check-ins are never rewritten; `current`/`score` are re-derived. Numerically equal values (`60` vs `60.00`) are not a change. KRs of an archived goal and archived KRs are read-only (409). No KR unarchive yet. |
| Goal tree roots | A goal is a root if it has no parent **or its parent is excluded by the archive filter** (`detachedFromParent: true`), so an active child of an archived parent is never hidden. Sibling order: period start, coarser period first, creation time, id. Default view expands everything. |
| Inbox capture | Only `rawText`; outer whitespace trimmed, everything else stored exactly as typed; 1–4000 Unicode code points (same counting in UI and API; SQLite `length()` also counts characters). Newest first. No AI/inference: nothing is created from the text until explicit triage (I-02). Rendering is plain text (React escaping, `white-space: pre-wrap`, `dir="auto"`); never `dangerouslySetInnerHTML`. |
| Inbox triage | Request carries exactly the payload of its decision (`task` / `goal` / `focusItem`; none for reference/someday/discard), checked BEFORE validating that payload, so a wrong payload reports `not_allowed_for_decision` instead of its own field errors. Payloads go through the regular create services (all their rules apply). Marking processed is `UPDATE … WHERE status='unprocessed'`: zero rows → 409 and the created entity is rolled back. Discarded items are kept (status processed, decision discard). Processed items cannot be re-opened (not in scope). |
| Strict text fields | `JacksonConfig` makes JSON numbers/booleans fail for `String` fields (`"title": 123` → 400 `invalid_value`); `application.yml` already blocks the opposite direction. |
| Execution | Tasks whose **effective** goal is the goal: `COALESCE(task.goal_id, focus_item.goal_id)` — computed by a join on every read, never stored. Each task row counts once (direct+focus to the same goal = 1; direct A + focus B = A only). Archived focus items keep attributing (archive ≠ delete) but cannot receive new links. `/tasks?goalId=` stays the *direct* filter; `/goals/{id}/tasks` is the effective list (direct only, no descendants). Since G-05 `executionProgress`/`activeTaskCount`/`doneTaskCount` cover the goal's **active subtree**: each task has exactly one effective goal and the subtree visit is de-duplicated, so a task linked via a child directly and via the parent's focus item, or planned on several days, counts once. |
| Outcome rollup (G-05) | Own active KRs → weighted Outcome; children are then listed in `childOutcomes` but never mixed in. Without own active KRs → unweighted mean of direct *rollup-eligible* children whose Outcome is not null (each child's own value is itself recursive); none → `null`. Rollup-eligible = not archived and status ≠ `dropped` (`done`/`on_hold` children count). An excluded child prunes its **whole branch** (an active grandchild under an archived child does not reach the grandparent), for Outcome and Execution alike. An excluded goal still computes its own numbers when requested. Corrupt parent cycles/self-parents are tolerated (visited sets), never loop. Computed in memory from 3 queries (all goals, active KRs, effective task counts) — no N+1. |
| Goal list default | `GET /goals` returns **active goals only** (changed in G-01 from "all"); `?archived=true\|all`. |
| Goal list paging (G-06) | Body stays a plain array (all existing callers/pickers unchanged); total matches in `X-Total-Count`; no `limit` = all matches. Offset paging over a **total** order (`period_start`, coarser period first, `created_at`, `id`; `sort=deadline` puts dated goals first by deadline) — stable while data does not change. Empty `status=`/`periodType=` = no filter (what a cleared form field sends). |
| Overdue (G-06) | Date-only: `deadline < today` where today is the client's local date (`overdueOn`, like milestones; backend has no user timezone). Only open statuses (`not_started`, `doing`, `on_hold`) can be overdue; `done`/`dropped` show the date without a flag. Deadline day itself = «موعد امروز», not overdue. UI "due soon" = within 7 days. UI computes from ISO strings/day numbers only (no `Date`). |
| Status vs progress (G-06) | Status is only ever changed by an explicit PATCH (inline selects or edit form). Nothing writes status from KR scores or task completion; tests pin `doing` at Outcome 100/Execution 100 and `done` with Outcome 40. UI labels status «انتخاب شما» and progress «سنجیده‌شده». |
| Inclusion rules by status (G-06) | Dashboard "active goals on date": not archived, status ∉ {done, dropped}, period contains the date (on_hold included). Outcome/Execution rollups: archived or dropped goals (and their branch) excluded, done/on_hold included. Goal list/tree: every status, filtered only on request. |
| Goal `periodType` | Not editable after creation (400 `not_editable`) — changing it needs parent *and* children granularity checks. |
| Date mismatch | Child outside parent, child outside a shortened parent, deadline outside period → `warnings` in single-goal responses; never blocks, never auto-adjusts. Boundary weeks are not exempt. |
| Goal history | One row per category per change: `status_changed`, `parent_changed`, `details_changed`, plus `archived`/`unarchived`; before/after JSON contains only changed fields. No-op PATCH writes nothing. |
| Timezone | Device preference in `localStorage` (`planner.timeZone`), default = browser zone. Decides "today" and timestamp display; check-in wall time is converted with it. Never rewrites date-only data. Not stored server-side. |
| Jalali library | `jalaali-js` (MIT) — **integer API only** (`g2d/d2g/j2d/d2j`); its `Date`-based helpers depend on the runtime timezone and must not be used. |

---

## 3. API: additions and deviations vs `docs/API_CONTRACT.md`

- **Inbox (contract):** `GET/POST /inbox-items`. Additions: `GET /inbox-items/{id}`, `capturedAt`, list `status` filter.
  `POST /inbox-items/{id}/process` implemented (I-02); additions: `decision` and `processedAt` in its response, list `decision` filter,
  conflict code `inbox_item_already_processed`, validation code `not_allowed_for_decision`.
- **Effective attribution:** `GET /goals/{id}/tasks` (contract) with `status`/`archived` filters; task fields `focusItemId`
  (now writable), `focusItemGoalId`, `effectiveGoalId`, `effectiveGoalSource`, `warnings`; `/tasks?focusItemId=` filter.
- **Focus items (contract):** `GET/POST /focus-items`, `GET/PATCH /focus-items/{id}` with `archived` flag. Additions: list filters
  `archived`/`goalId`/`kind`/`status`, conflict code `focus_item_archived`.
- **Milestones (contract):** `GET/POST /goals/{id}/milestones`, `PATCH /milestones/{id}`. Additions: `description`,
  `archived` flag and `archivedAt`, list filters `status`/`date`/`archived`, conflict code `milestone_archived`.
- **Added:** `GET /key-results/{id}/history`; `note` field on KR PATCH and optional `{"note"}` body on KR archive;
  `keyResults[]` breakdown in `/goals/{id}/progress` (dashboard and tree nodes do not carry it).
- **Added:** `POST /goals/{id}/unarchive` (idempotent restore; the contract only lists archive).
- **Added (G-06):** `GET /goals` filters/sort/paging beyond the contract's `status`/`periodType` (`deadlineFrom`, `deadlineTo`, `overdueOn`, `sort`, `limit`, `offset`, header `X-Total-Count`); `GET /goals/progress?ids=` (batch).
- **Added:** `GET /tasks` filters `status`, `goalId` (direct link), `archived=false|true|all`; `GET /goals?archived=…`;
  `GET /goals/{id}/key-results?includeArchived=true`.
- **Implemented with additions:** `GET /goals/tree` takes `archived=false|true|all` and returns `{archived, goalCount, roots}`; nodes carry
  `detachedFromParent` and the progress fields of `/goals/{id}/progress` (minus `goalId`/`measuredAt`).
- **Added:** `warnings: [{code, message, details}]` on single-goal responses (GET by id, POST, PATCH, archive/unarchive). Lists omit it.
- **Added:** task archive/restore via `PATCH /tasks/{id} {"archived": true|false}`.
- **Conflict codes in use:** `duplicate_plan_task`, `goal_archived`, `task_archived`, `key_result_archived`,
  `parent_goal_archived`, `goal_cycle`, `incompatible_period_granularity`. Generic `conflict` is reserved.
- **Other codes:** 405 `method_not_allowed` and 415 `unsupported_media_type` use the same error shape (beyond the four documented statuses).
- **Behavior change:** a parent goal that is not strictly coarser was 400 in D-03 and is **409 `incompatible_period_granularity`** since G-02.
- Lists are plain JSON arrays (no pagination yet).

---

## 4. Schema and migrations

- `V1__demo0_schema.sql`: Demo 0 tables + `focus_items` (no API). CHECKs enforce ISO dates, timestamp format, decimal text, enums where the docs define them, `baseline <> target`, `weight > 0`. Check-ins are append-only via triggers. No `ON DELETE CASCADE`.
- `V2__goal_history.sql`: `goal_history` (append-only via triggers, `json_valid` checks).
- `V3__goal_history_key_results.sql`: rebuilds `goal_history` (SQLite cannot alter a CHECK) with `key_result_id`,
  `note` and KR event types; drops/recreates the append-only triggers; all V2 rows copied. Upgrade tested from populated V1 and V2.
- `V5__inbox_items.sql`: `inbox_items` with triage columns (`decision`, `created_entity_type/_id`, `processed_at`) and CHECKs
  tying them to `status`; index `(status, captured_at)`.
- `V4__milestones.sql`: `milestones` (FK goal, ISO due date, UTC `completed_at`/`archived_at`, index `(goal_id, due_date)`).
- `V6__authentication.sql`: `users`, `refresh_tokens` (hash only, family, revocation reason), `signing_keys`; `user_id`
  on `goals`/`tasks`/`focus_items`/`inbox_items`/`daily_plans` (NULL = pre-auth data until the first registration claims it);
  `daily_plans` + `daily_plan_items` rebuilt so the unique key is `(user_id, local_date)` (all rows copied; FK re-pointed by RENAME).
- `V7__goal_priority.sql`: `goals.priority` + index `(user_id, priority)`; active goals ranked per owner by the previous display order.
- `V8__focus_item_position.sql`: `focus_items.position` + index `(goal_id, position)`; linked items numbered per goal by creation order.
- `V9__task_priority_default_low.sql`: tasks without priority get `low` (the column stays nullable; the service guarantees a value).
- `V10__permanent_delete_of_archived.sql`: check-in / goal-history no-delete triggers only fire for goals that are not archived.
- `V11__task_completed_at.sql`: `tasks.completed_at` (UTC, CHECK format) + index; done tasks backfilled with `updated_at`.
- `V12__time_blocks.sql`: `time_blocks` (weekly time-box) + indexes `(user_id, local_date, start_minute)`, `(task_id)`.
- `V13__weekly_plan_items.sql`: `weekly_plan_items` (task on a Saturday–Friday week without a day) + index on task_id.
- `V14__habits.sql`: `habits` (root table: user_id, title, archived_at) + `habit_checks` (habit_id, local_date, PK both; a row = done that day).
- `V15__focus_item_notes.sql`: `focus_item_notes` (focus_item_id, body, timestamps; owned through focus_items) + index.
- **All migrations are applied in tests and possibly on the owner's DB; never edit them.** Add `V16__…` for changes. Test upgrades from a populated older DB
  (see `FlywayMigrationTest.populatedV1DatabaseUpgradesToLatestWithoutDataLoss`).
- The owner's real DB (`data/planner.db`) was still at **V1** at hand-off; V2–V5 apply automatically on next start.

---

## 5. Code map and reusable building blocks

**Backend** (`backend/src/main/java/com/lifeplanner/`) — global layers, every class of a kind side by side (see AGENTS.md):
- `controller/`: all REST controllers (`GoalController`, `TaskController`, `DashboardController`, …), PATCH readers
  (`GoalPatchReader`, …), `GlobalExceptionHandler`, `ApiErrorController` (/error), `HealthController`.
- `service/`: all services, plus pure logic: `ProgressCalculator` — **the only place** progress is computed;
  `ProgressRollup` (hierarchy); `ProgressService.forGoals` batches any number of goals into three queries — use it for
  every list/tree view; `GoalWarnings`, `KeyResultScore`, `GoalTreeService`, `DashboardService`.
- `repository/`: all `JdbcClient` repositories (parameterized SQL only).
- `model/`: domain records (`Goal`, `Task`, `KeyResult`, `CheckIn`, `FocusItem`, `InboxItem`, `Milestone`, `DailyPlan`,
  `EffectiveGoal`, …) and `*Limits` (shared length limits). `model/enums/`: every enum (`GoalStatus`, `PeriodType`,
  `ArchiveFilter`, `OutcomeSource`, …). `model/vo/`: inputs `…VO` (`CreateGoalVO`, `GoalQueryVO`, …). `model/srv/`: outputs
  `…Srv` with `public static from(domain)` (`GoalSrv`, `GoalProgressSrv`, `ApiErrorSrv`, …). VO/Srv class names never appear in JSON.
- `exception/` (`ApiException` + Validation/NotFound/Conflict, `ErrorCodes`), `util/` (`IsoDates.parseParam` strict date
  params, `JsonPatchBody` PATCH absent-vs-null — reuse for every new PATCH, `ApiPaths`), `config/` (`LoopbackBindingGuard`,
  `TimeConfig` single `Clock`, `JacksonConfig`, `WebConfig` CORS, `PlannerProperties`).
- `db/`: `SqliteDataSources` (pragmas per connection), `SqliteExceptionTranslator` (UNIQUE → `DuplicateKeyException`,
  constraints → `DataIntegrityViolationException`, BUSY → `CannotAcquireLockException`), `DecimalCodec`, `UtcTimestamps`.

**Frontend** (`frontend/src/`)
- `shared/api/client.ts` (typed fetch, `ApiError.fieldCodes()`), `shared/ui/useApiData.ts` (load/reload, abort).
- `shared/date/`: `calendar.ts` (ISO ↔ Jalali, `todayIso()`), `periods.ts`, `monthGrid.ts`, `timeZone.ts`.
- `shared/ui/`: `JalaliCalendar`, `JalaliDateField`, `PeriodPicker`, `TextField`, `AsyncState`, `fieldErrors.ts` (Persian text for backend field codes).
- `features/goals|tasks|planning|dashboard|settings`. `goals/GoalTree.tsx` + `treeNav.ts` (pure keyboard model) implement the treeview. `dashboard/ProgressPair.tsx` renders Outcome and Execution — formatting only, never computing.

---

## 6. How to implement the next task in this codebase

**Backend (global layer packages)**
1. `controller/XController` (thin; takes `model/vo/…VO`, returns `model/srv/…Srv`) → `service/XService` (validation,
   rules, `@Transactional` on writes) → `repository/XRepository` (`JdbcClient`, parameterized SQL only). Domain rows are
   records in `model/` (`Goal`, `Task`, …); every enum goes to `model/enums/`; shared length limits in `model/XLimits`.
   New input object → `model/vo/<Name>VO`; new output object → `model/srv/<Name>Srv` with `public static from(...)`.
2. Errors: throw `ApiException.ValidationException` (400, `details` = `{field: code}` with stable codes),
   `NotFoundException` (404), `ConflictException(code, …)` (409 with a specific code). Persian `message`, English `code`.
   Never return error bodies by hand; `GlobalExceptionHandler` does it.
3. PATCH: read the body with `JsonPatchBody` (absent ≠ null), never bind a record directly.
4. Dates: request params via `IsoDates.parseParam`; bodies as `LocalDate`/`Instant`; check `IsoDates.isStorable`.
   Store dates with `toString()`, timestamps with `UtcTimestamps`, decimals with `DecimalCodec`. Time comes from the `Clock` bean.
5. Lists with archive state: `ArchiveFilter.fromParam(...)`. Archiving never cascades and nothing is hard-deleted.
6. Progress numbers only through `ProgressCalculator`. Never add a combined/blended value.
7. Schema change → new `V<n>__*.sql`; bump `MigrationCount.APPLIED` (test sources, `db`) and add an upgrade test
   from a populated previous DB (see `FlywayMigrationTest`). Changing a CHECK in SQLite needs a table rebuild — copy V3's pattern
   (drop triggers → create new table → copy rows → drop old → rename → recreate indexes and triggers).

**Tests expected per task** (patterns to copy):
- API integration: `@SpringBootTest @AutoConfigureMockMvc` (e.g. `GoalHierarchyIntegrationTest`): positive, invalid input,
  404, 409, archived cases, null-vs-zero.
- Restart: `SpringApplicationBuilder` twice on a `@TempDir` DB with `--server.port=0` (e.g. `GoalEditRestartTest`).
- Pure logic: plain JUnit in the class's own package (e.g. `service/ProgressCalculatorTest`, `service/GoalWarningsTest`); API/restart tests in `integration/`.
- Frontend: Vitest unit tests for logic; static-markup render tests for components.
- Before reporting: `./mvnw -o test`, `npm test`, `npm run build`, `scripts/smoke.sh`; extend `scripts/smoke-api.mjs`
  and the README demo checklist when a core workflow changes.

**Report format** (from `AGENTS.md`): behavior, files changed, API/schema deltas, exact commands with PASS/FAIL/NOT RUN,
manual steps, open issues, next task ID, and Outcome/Execution values where relevant.

**Keep this file current:** after each task update §1 (row), §2/§3 (new decisions or API deltas), §7 (open items) and the
"Last completed task" line at the top.

---

## 7. Pitfalls found during implementation (save the next person time)

1. **Maven may hang for minutes resolving remote repositories.** Dependencies are cached; use `./mvnw -o …`. `scripts/smoke.sh` builds offline first and falls back to online.
2. **Jackson maps an absent record field and an explicit `null` both to `Optional.empty()`.** Use `JsonPatchBody` for PATCH.
3. **Spring has no SQLite error codes**; without `SqliteExceptionTranslator` everything is `UncategorizedSQLException`.
4. **Binding a config value directly to `java.nio.file.Path` fails for `../…` in a web context** (servlet resource loader). Bind as `String`, convert with `Path.of`.
5. **SQLite TEXT affinity** turns a numeric `20.5` into the text `'20.5'` before CHECKs run.
6. **macOS filesystem is case-insensitive**: `TaskForm.tsx` and `taskForm.ts` collide (hence `taskFormModel.ts`).
7. **Never `toISOString()` for date-only values** (off by one around midnight east of UTC); tests run in `Asia/Tehran` to catch this.
8. **Port 8080 may be the owner's IDE backend on the real DB.** Run test backends on another port (`PLANNER_SERVER_PORT=18080 PLANNER_DB_PATH=/tmp/x.db`) and point Vite at it with `PLANNER_API_TARGET`. Never write test data to `data/planner.db`.
9. Rebuild the jar before manual runs (`./mvnw -o -DskipTests package`); a stale jar once caused a misleading 404.
10. macOS has no `timeout` command; in zsh, `GID` is a read-only variable. The owner's shell may alias `python3` to a
    missing interpreter and agent shells may start with a minimal `PATH`: use `/usr/bin/python3` for scripts and Node from
    `~/.nvm/versions/node/v22.14.0/bin` (the version all checks used).
11. Test isolation: shared Spring contexts share one temp DB — give each test its own dates/years when filtering by date.
    Tests that need corrupt or global data use their own DB: `@SpringBootTest(properties = "planner.datasource.path=target/test-db/x-${random.uuid}.db")`.
12. `useApiData` keeps the previous data visible while reloading; effects that react to new data must check that the
    data belongs to the current request (the tree response echoes `archived` for this).
13. Tests that assert exact list/filter results over *all* goals need an empty DB: `GoalFilterIntegrationTest` uses its own
    random DB path plus `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` (append-only triggers make deleting rows the wrong tool).
14. Filters kept in the URL (`useSearchParams`) update in a React transition: a controlled checkbox shows its new state only
    after the URL commits (browser tests: click, then wait for the URL). A `<fieldset>` needs `min-inline-size: 0` or it
    refuses to shrink on phones.
15. Tests: the auto-configured `MockMvc` sends a fresh token of `owner@test.local` on every request (`config/TestAuthConfig`,
    test sources); real-HTTP tests use `integration/TestAuth`. Code that calls repositories outside a request needs a user:
    `TestAuthConfig.runAs(userId, …)`. Auth-focused tests build their own `MockMvc` without the default token.
16. Rows inserted with raw SQL have `user_id NULL` and are invisible to the API until claimed — set `user_id` in fixtures.

---

## 8. Known limitations / open items

- No read endpoint for goal-level `goal_history` (KR history has one); Goal `periodType` not editable; no KR unarchive.
- Daily plan: no remove/move of items (rescheduling with audit is MVP v1). `/today` keeps the date from page load past midnight.
- No automated UI test in the repo (browser smoke scripts were run ad hoc with `playwright-core` + local Chrome outside the repo). The manual checklist is in `README.md`.
- Only Friday is shown as weekend; official holidays are not marked.
- **Temporary owner setting:** access tokens are issued WITHOUT `exp` (`planner.auth.access-token-expires=false`, default).
  Revert: set it to `true` (env `PLANNER_AUTH_ACCESS_TOKEN_EXPIRES=true`), then delete `AccessTokenWithoutExpiryTest`;
  `AuthIntegrationTest` already runs in the expiring mode. While off, a leaked access token stays valid until the signing
  key in `signing_keys` is replaced (logout only ends the refresh session).
- Dashboard needs from the backend (see owner report 2026-11-09): habits API, daily time-log API (also to persist focus-timer
  sessions), a ranged daily-plan read (the page now makes 7 + up to 31 GETs), per-period Outcome summary on
  `/dashboard/today` (the mean is computed in the UI for now), and an atomic create-and-plan endpoint.
- Focus items page needs from the backend: a focus-item deadline (`dueDate`) for the «ددلاین» column (shows «—»), and a
  real weekly plan if «به برنامه هفتگی» should mean more than “planned on a day of this week” (weekly plans are MVP v1).
- Goals board needs from the backend: a goal `icon` field (cards use the period icon for now), optional `kind` on quick-add, and possibly a focus-item filter by
  several goals (the page loads all active focus items at once).
- Auth limits: no password reset/change, no e-mail verification, no account deletion; access tokens cannot be revoked
  before their 15-minute expiry (logout ends the refresh session); login throttle is in memory per e-mail (cleared on
  restart, not per IP); registration is not rate-limited; no TLS (cookie `Secure` off by default for loopback HTTP).
- **Owner data note:** `data/planner.db` contains a goal «یادگیری RAG» (`263d92fe-b78d-49d8-8b03-4b806323ee6d`) created by mistake by a smoke-test setup during D-05. The owner has not yet decided whether to delete it; do not touch it without their approval.
