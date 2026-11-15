# frontend/

React 19 + TypeScript + Vite 8, Persian RTL, Jalali display. All data comes from the backend API (`/api/v1`); there is no mock or sample data.

```bash
cd frontend
npm install        # first time (network needed once)
npm run dev        # http://127.0.0.1:5173 — proxies /api to the backend on 127.0.0.1:8080
npm test           # unit tests (Vitest), run in the Asia/Tehran zone to catch date off-by-one bugs
npm run build      # typecheck + production bundle in dist/
```

Start the backend first (`cd backend && ./mvnw spring-boot:run`). If the backend runs on another port,
point the proxy at it: `PLANNER_API_TARGET=http://127.0.0.1:18080 npm run dev`.

## Dates

- API/DB date-only values are Gregorian ISO `YYYY-MM-DD`. Jalali exists only in the UI.
- Jalali conversion uses the `jalaali-js` library's integer API only (`g2d/d2g/j2d/d2j`), wrapped by
  `src/shared/date/calendar.ts`. Its Date-based helpers are not used. Never build a date-only string with
  `toISOString()`; use `todayIso()` / `dateInZone()`.
- `src/shared/date/periods.ts`: Jalali week (Saturday–Friday), month, quarter, year; labels. `monthGrid.ts`: calendar grid
  and RTL keyboard movement.
- Pickers: `shared/ui/JalaliCalendar` (Saturday-first grid, Friday weekend, arrows/Home/End/PageUp/PageDown),
  `JalaliDateField` (date-only fields), `PeriodPicker` (goal periods; custom ranges stay possible).
- `/settings` chooses the IANA timezone (stored in this browser; default: browser zone). It decides "today" and how
  timestamps are shown; check-in times are interpreted in it. Stored date-only values are never rewritten.

## Layout

```
src/
  shared/api          typed fetch client, ApiError {status, code, message, details}
  shared/date         calendar.ts (ISO ↔ Jalali via jalaali-js, todayIso), periods.ts, monthGrid.ts, timeZone.ts
  shared/ui           JalaliCalendar, JalaliDateField, PeriodPicker, TextField, AsyncState (loading/error/empty),
                      useApiData, fieldErrors (Persian text for backend field codes)
  features/goals      list (+archived filter), tree (/goals/tree: GoalTree + treeNav.ts, WAI-ARIA treeview, RTL keys),
                      create, detail (children with progress, KR history preview, linked tasks), edit (incl. parent
                      picker), archive/restore, warnings; hierarchy.ts (parent eligibility)
  features/tasks      list with filters, create/edit form, status control, archive/restore
  features/planning   Today: daily plan for the configured-timezone date (?date=), add existing tasks
  features/dashboard  dashboard + ProgressPair (Outcome and Execution cards, formatted only, never combined)
  features/inbox      /inbox queue + views, QuickCaptureForm (also in the header «+ ثبت سریع»), TriagePanel (reuses the task/goal/focus
                      create forms; discard confirmation); plain-text rendering
  features/focus      /focus list + /focus/:id detail (project/book/course/area/other, optional goal, archive)
  features/milestones goal milestones section (delivery checkpoints, not KRs)
  features/settings   IANA timezone preference
  pages               layout/navigation, not-found page
```

## Conventions

- All data comes from the API; components never compute business aggregates (scores, progress, completion).
- Date-only values travel as ISO strings; use `JalaliDateField` / `PeriodPicker` for input, `format.ts` for display.
- New API calls go in the feature's `api.ts`; show server field codes with `error.fieldCodes()` + `fieldErrors.ts`.
- Tests: `npm test` (Vitest, node environment). Components are tested by rendering to static markup
  (`react-dom/server`); pure logic lives in `.ts` modules next to them.
