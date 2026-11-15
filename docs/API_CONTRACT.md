# REST API Contract — Personal Life Planner v2.0

**Prefix:** `/api/v1`. **JSON:** camelCase. **Dates:** date-only Gregorian ISO `YYYY-MM-DD`; timestamps UTC ISO-8601. **IDs:** UUID strings. **Errors:** `{ "code":"validation_error", "message":"...", "details":{} }`; validation=400, not found=404, conflict=409, unexpected=500 sanitized. No authentication only while bound to loopback.

Status vocabulary: `Goal.status = not_started | doing | done | on_hold | dropped`; `Task.status = not_started | doing | done`. No `combinedProgress` property anywhere.

## Demo 0 endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | backend health (no personal data) |
| GET, POST | `/goals` | list/create Goal |
| GET | `/goals/{goalId}` | Goal details |
| GET, POST | `/goals/{goalId}/key-results` | list/create active KR |
| GET | `/key-results/{krId}` | KR details + computed current and score |
| GET, POST | `/key-results/{krId}/check-ins` | append-only measurement history |
| GET, POST | `/tasks` | list/create Task |
| GET, PATCH | `/tasks/{taskId}` | Task details/update status (other fields if supported) |
| GET | `/plans/daily?date=YYYY-MM-DD` | read plan without DB side effect; missing means empty |
| PUT | `/plans/daily/{date}` | idempotent create-or-get daily plan |
| POST | `/plans/daily/{date}/items` | assign existing task to date, unique by plan+task |
| GET | `/dashboard/today?date=YYYY-MM-DD` | Today stats, active goals and separated outcome/execution |
| GET | `/goals/{goalId}/progress` | own KR based score and task based score |

**Goal example POST**
```json
{"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21","deadline":"2026-12-21"}
```
**Goal example response (simplified)**
```json
{"id":"uuid","title":"یادگیری RAG","status":"not_started","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21","parentGoalId":null}
```
**KR POST** (`POST /goals/{goalId}/key-results`)
```json
{"name":"Recall@5","unit":"%","baseline":20,"target":80,"weight":1}
```
**Check-in POST** (`POST /key-results/{krId}/check-ins`)
```json
{"value":50,"measuredAt":"2026-11-03T12:00:00Z","note":"evaluation set v1"}
```
**Progress response**
```json
{"goalId":"uuid","outcomeProgress":50.0,"executionProgress":75.0,"outcomeSource":"own_key_results","keyResultsCount":1,"activeTaskCount":4,"doneTaskCount":3,"measuredAt":"2026-11-03T12:00:00Z"}
```
If a Goal has no active KR/defined child outcome, `outcomeProgress=null` and `outcomeSource="not_measured"`. If it has no active linked task, `executionProgress=null`. `measuredAt` may be null without check-ins. **Never return blended progress.**

**Today item POST** (`POST /plans/daily/{date}/items`)
```json
{"taskId":"uuid"}
```
201 created; 409 `duplicate_plan_task` if present. Daily `completionPercent=null` on no items.

## MVP v0 endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| PATCH | `/goals/{goalId}` | update fields/status/parent |
| POST | `/goals/{goalId}/archive` | archive safely, 200 idempotent |
| GET | `/goals/tree` | independent roots + nested Goals |
| PATCH | `/key-results/{krId}` | update name/target/baseline/weight with audit note if values changed |
| POST | `/key-results/{krId}/archive` | archive KR without deleting check-ins |
| GET, POST | `/goals/{goalId}/milestones` | list/create |
| PATCH | `/milestones/{milestoneId}` | update milestone / mark complete |
| GET, POST | `/focus-items` | list/create |
| GET, PATCH | `/focus-items/{focusItemId}` | retrieve/update/archive via archived flag |
| GET, POST | `/inbox-items` | list/create unprocessed item |
| POST | `/inbox-items/{inboxItemId}/process` | triage transactionally; decision includes task, goal, focus_item, reference, someday, discard |
| GET | `/goals/{goalId}/tasks` | direct+effective linked distinct Tasks |
| GET | `/goals/{goalId}/progress` | now includes recursive rollup when no own KRs |
| GET | `/goals?status=...&periodType=...` | list filters; stable pagination |

**Inbox process example**
```json
{"decision":"task","task":{"title":"مطالعه فصل دوم","status":"not_started"}}
```
Response returns `{inboxItemId, status:"processed", createdEntityType:"task", createdEntityId:"uuid"}`. Reprocessing already processed item returns 409, and errors do not partially convert the source item.

## Future contracts — explicitly NOT Demo 0/MVP v0
`/plans/weekly`, `/reviews`, `/time-logs`, `/time-blocks`, `/recurrence-rules`, `/exports`, `/backups`. Do not stub success responses or implement in early prompts. Implement their contracts only when their milestone is reached.

## Cross-cutting API requirements

### Actual daily activity logs (owner request 2026-11-14)
`GET /activity-logs?date=YYYY-MM-DD` returns your actual activities ordered by start; an empty day returns `[]` without writes.
`POST /activity-logs` creates `{date,title,category,startMinute,endMinute}` (201 + Location).
`PUT /activity-logs/{id}` replaces those fields (200); `DELETE /activity-logs/{id}` explicitly deletes the record (204).
Category is `work|study|rest|personal`; title is 1–200 trimmed characters. Times use minute precision,
`0 <= startMinute < endMinute <= 1440`. Same-user/day intervals may touch but not overlap (409 `activity_log_overlap`).
Foreign or missing records return 404. These records have no effect on tasks, plans or goal progress.
Optional `taskId` links an owned active task (404 foreign, 409 archived); null on PUT clears it. Permanent task deletion detaches the log.
Timer: GET /activity-timer returns the user's running timer or null. POST with {title,timeZone} captures the server start instant
and IANA timezone (409 timer_running if already running). POST /activity-timer/stop atomically writes final intervals,
split by local midnight, then clears the timer. Minimum duration is one minute; repeated stop is a no-op.
Overlap returns 409 and preserves the running timer. GET never creates or extends an interval.
This capability is brought forward from MVP v2 by owner request.
- All writes validate input at both DTO and service level; use transactions. SQL parameterized.
- Unknown enum or malformed date => 400; nonexistent IDs => 404; cycle/duplicate => 409.
- Goal statuses never automatically transition based on KR score; manual editing only.
- Date-only conversion never invokes timezone conversion; API is independent from Jalali presentation.
- Read endpoints do not mutate storage, including `GET /plans/daily` and `/dashboard/today`.
