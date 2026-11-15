# Personal Life Planner v2.0

## لاگ روزانه

`/activity-log` از نوار بالا باز می‌شود: روز شمسی را انتخاب کنید، عنوان کاری که انجام داده‌اید، دسته و ساعت شروع/پایان را وارد کنید.
روی هر فعالیت در خط زمان برای ویرایش یا حذف تأییدشده کلیک کنید. بازه‌ها با دقت یک دقیقه ثبت می‌شوند؛ هم‌پوشانی مجاز نیست و فعالیت عبوری از نیمه‌شب باید در دو روز ثبت شود.
روی زمان خالی خط زمان کلیک کنید، داخل بلاک بنویسید و Enter بزنید؛ بازهٔ پیش‌فرض ۳۰ دقیقه در پنل قابل تغییر است.
پس از کلیک روی بلاک، دسته و تسک مرتبط را انتخاب و ذخیره کنید. خط مرجانی ساعت جاری را نشان می‌دهد.
در کارت تایمر سمت چپ، عنوان فعالیت را بنویسید و «شروع» را بزنید. بلاک هنگام اجرا ثابت است و تنها «توقف و ثبت» بازهٔ نهایی را می‌سازد.
ثبت دستی با کلیک داخل خط زمان انجام می‌شود؛ ویرایش دسته، تسک و بازه با کلیک روی بلاک در پنجرهٔ ویرایش باز می‌شود. کارت‌های آمار و راهنما حذف شده‌اند و کارت تایمر اسکرول داخلی ندارد.
لبهٔ پایین بلاک ثبت‌شده یا پیش‌نویس را بکشید تا ساعت پایان با پیش‌نمایش تغییر کند؛ رهاکردن لبهٔ بلاک ثبت‌شده تغییر را ذخیره می‌کند. کلیدهای ↑/↓ روی دستگیره نیز مدت را پنج دقیقه تغییر می‌دهند؛ Escape کشیدن را لغو می‌کند. افزایش تا شروع فعالیت بعدی یا پایان روز محدود است.
دقت ثبت یک دقیقه و حداقل زمان توقف فوری یک دقیقه است؛ عبور از نیمه‌شب خودکار بین روزها تقسیم می‌شود.
اگر توقف با بازه‌ای هم‌پوشان باشد، تایمر حفظ می‌شود تا بازهٔ قبلی را اصلاح کنید و دوباره توقف را بزنید.
V17 رکوردهای V16 را حفظ می‌کند؛ برای endpointهای جدید backend باید با نسخهٔ جدید راه‌اندازی شود.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/activity-logs?date=YYYY-MM-DD` | فعالیت‌های واقعی روز، متعلق به کاربر جاری |
| POST | `/api/v1/activity-logs` | `{date,title,category,startMinute,endMinute}`؛ دسته `work/study/rest/personal` |
| PUT, DELETE | `/api/v1/activity-logs/{id}` | ویرایش کامل / حذف صریح؛ 404 برای رکورد دیگران؛ 409 برای هم‌پوشانی |
| GET, POST | `/api/v1/activity-timer` | تایمر جاری یا null / شروع با `{title,timeZone}` |
| POST | `/api/v1/activity-timer/stop` | ثبت تراکنشی بازه‌های نهایی و توقف؛ تکرار توقف بدون اثر |

بررسی دستی: یک فعالیت ثبت و صفحه را تازه کنید؛ سپس عنوان/بازه را ویرایش کنید، روز را تغییر دهید و بازگردید، بازهٔ هم‌پوشان را امتحان و حذف را تأیید کنید.

Single-user, local-first planner. Normative specs live in [`docs/`](docs/) (PRD, domain rules, API contract, data model); agent rules in [`AGENTS.md`](AGENTS.md).

```
backend/    Java 21 + Spring Boot 3.5.16 (Maven wrapper)
frontend/   React 19 + TypeScript + Vite 8, Persian RTL, Jalali display (see frontend/README.md)
docs/       Product and contract documents (normative) + IMPLEMENTATION_STATUS.md (status, decisions, hand-off)
scripts/    smoke.sh — API smoke test against a throwaway backend and temp DB
data/       Local SQLite database planner.db (created on first start, gitignored — never commit)
```

## Prerequisites

- JDK 21 (`java -version`). Maven itself is **not** required; `./mvnw` downloads Maven 3.9.16 on first run.
- Node.js 22.12+ and npm for the frontend.
- Network is needed only for the first dependency download. At runtime the backend makes no outbound calls and needs no database credentials (SQLite is an embedded file).

## Run locally

```bash
cd backend && ./mvnw spring-boot:run      # terminal 1: API on 127.0.0.1:8080
cd frontend && npm install && npm run dev # terminal 2: UI on http://127.0.0.1:5173
```

**API documentation (Swagger / OpenAPI 3.1):** http://127.0.0.1:8080/swagger-ui.html (machine-readable:
http://127.0.0.1:8080/v3/api-docs). Every endpoint, parameter, request/response field, error code and header is described
there. To try protected endpoints: call `POST /api/v1/auth/login` (or register) in Swagger UI, copy `accessToken`, click
**Authorize** and paste it. Served by the backend itself (no CDN); the docs need no token and contain no user data.

Open the UI and create an account («یک حساب بسازید»). **The first account ever created on a database receives all data
that existed before authentication was added**; later accounts start empty and never see each other's data. Sessions
last 30 days (refresh cookie). **Temporarily, access tokens do not expire** (no `exp` claim); to restore the 15-minute
expiry set `PLANNER_AUTH_ACCESS_TOKEN_EXPIRES=true` (or `planner.auth.access-token-expires: true`) — the UI already
renews tokens automatically. Auth settings: `PLANNER_AUTH_ACCESS_TOKEN_EXPIRES`, `PLANNER_AUTH_ACCESS_TTL`, `PLANNER_AUTH_REFRESH_TTL`, `PLANNER_AUTH_COOKIE_SECURE` (set `true` only behind HTTPS).

## Verify (release gate)

```bash
cd backend && ./mvnw -o test                                # all backend tests, incl. the end-to-end workflow (isolated temp DBs)
cd frontend && npm ci && npm run typecheck && npm test && npm run build
scripts/smoke.sh                                            # API smoke: own backend on 127.0.0.1:18080 + temp DB, restart, re-verify
```

`-o` (offline) uses the already-downloaded dependencies; drop it on the very first build. Without it Maven can stall
for minutes checking remote repositories.

`scripts/smoke.sh` builds the current jar, never touches `data/planner.db`, refuses to start if the port is busy
(`SMOKE_PORT=... scripts/smoke.sh` to change it) and deletes its temporary database afterwards. It needs JDK 21, Node and curl.

To try the production bundle instead of the dev server: `cd frontend && npm run build && npm run preview`
(http://127.0.0.1:4173, same `/api` proxy; `PLANNER_API_TARGET` overrides the backend URL).

### اجرای خودکار روی macOS (بدون Terminal)

در Finder روی `scripts/macos/install.command` دوبار کلیک کنید. نصب‌کننده نسخه production را build می‌کند و دو
LaunchAgent کاربر می‌سازد: backend روی `127.0.0.1:18080` و UI روی `http://127.0.0.1:4173`. هر دو پس از ورود به macOS
خودکار اجرا، پس از crash دوباره راه‌اندازی و هنگام logout خاموش می‌شوند؛ پورت‌های توسعه 8080/5173 دست‌نخورده می‌مانند.
دیتابیس همان `data/planner.db` است و لاگ‌ها در `~/Library/Logs/Planner` نوشته می‌شوند.

- `start.command`: سرویس‌های متوقف‌شده را بدون rebuild اجرا و مرورگر را باز می‌کند.
- `stop.command`: هر دو سرویس را تا اجرای دوباره یا login بعدی متوقف می‌کند.
- `uninstall.command`: اجرای خودکار را حذف می‌کند؛ دیتابیس و اطلاعات کاربر را حذف نمی‌کند.
- پس از تغییر کد، دوباره `install.command` را اجرا کنید تا build جدید جایگزین شود.

### Demo checklist (manual, about 10 minutes)

Start the backend and `npm run dev`, open http://127.0.0.1:5173. Use a fresh database if you do not want demo data in
your own: `PLANNER_DB_PATH=/tmp/planner-demo.db ./mvnw spring-boot:run`.

1. **Goals → هدف جدید**: the period picker starts on the current Jalali quarter (year + quarter selects; the ISO range is shown). Create the goal.
2. On the goal page add two KRs: `20 → 60` and `100 → 60` (decreasing). Both show **۰٪** («فقط مقدار پایه»). Outcome shows **۰٪**, Execution «سنجیده نشده».
3. **ثبت اندازه‌گیری** on the first KR with `40`: KR **۵۰٪**, Outcome **۲۵٪** (weighted mean of 50 and 0).
4. **کارها → کار جدید**: create four tasks linked to the goal and one without a goal; mark three linked ones «انجام‌شده».
5. **امروز**: add two tasks; adding is limited to tasks not yet on the day. Change a status there; it is the same task as on /tasks.
6. **داشبورد** (the home page): the goal appears under «اهداف فصل» with its Outcome ring (**۲۵٪**) and the line «نتیجه: ۲۵٪ · اجرا: ۷۵٪». There is no combined number.
7. Restart the backend and reload: everything is unchanged. A day without a plan shows «برای امروز کاری برنامه‌ریزی نشده», not ۰٪.

MVP v0 so far (G-01 … G-03):

8. Create an **annual** goal. On the quarterly goal click **ویرایش** and pick the annual goal as **هدف والد** (only coarser,
   active, non-descendant goals are offered). The goal page now shows the parent link; the annual goal lists it as a child.
9. Create a **weekly** goal: in the calendar click 3 Azar 1405 → the week Saturday 30 Aban … Friday 6 Azar (seven dates listed).
   Make the Aban monthly goal its parent → saved with a yellow warning (dates partly outside the parent), never blocked.
10. **بایگانی هدف** on a goal with tasks: the confirmation lists what is kept. Afterwards the goal is hidden from the list
    (visible with «فیلتر بایگانی»), its tasks keep their status, children stay linked. **خروج از بایگانی** restores it.
11. Open any date field (e.g. deadline): Esfand 1403 has 30 days, Esfand 1404 has 29. Arrow keys move in RTL direction.
12. **تنظیمات**: pick another IANA timezone → «امروز» follows it; stored goal/task dates do not change.
13. **اهداف → نمای درختی**: the standalone goal is a root, nested goals are indented by level, each node shows
    Outcome and Execution separately. Keyboard: ↑/↓ move, ← opens, → closes / goes to parent, Enter opens the goal page.
    The goal page lists children with their measurements, the latest 3 check-ins per KR, directly linked tasks, and an
    honest empty state for focus items (not built yet).
14. On a goal with two KRs (weights 1 and 3) check in 100% on the first: Outcome **۲۵٪**, and the breakdown table shows
    25 + 0. **ویرایش** the second KR's weight to 1: saving needs a reason; Outcome becomes ۵۰٪ and the timeline shows
    «بازتنظیم — وزن: ۳ ← ۱» with the reason. **بایگانی** it: Outcome ۱۰۰٪; its card moves to «نتایج کلیدی بایگانی‌شده»
    with the full, read-only timeline.
15. **نقاط تحویل**: add milestones with Jalali due dates (one in the past, one ahead, one without date). Tick one as
    done: Outcome and Execution on the same page do not change. Try the overdue/upcoming filters, archive one and show it
    with «نمایش بایگانی‌شده‌ها». Restart the backend: due dates and completion are unchanged.
16. **تمرکز**: create a book without a goal («بدون هدف») and a project linked to a goal; the goal page lists the project.
    Archive that goal: the project stays, marked «هدف بایگانی‌شده». Archive/restore a focus item; filters by kind/status.
17. Create a task with focus item «project of goal B» only → it counts for B («از طریق آیتم تمرکز»). Create another with
    direct goal A **and** that focus item → the form warns but saves; it is listed and counted for A only, never for B.
18. Open **صندوق ورودی** (bell in the top bar; the global «+ ثبت سریع» button was removed at the owner’s request on 2026-11-12), type a note (even `<script>…</script>`) and press Ctrl/⌘+Enter: «ثبت شد». The note
    appears on **صندوق ورودی** as plain text, unprocessed, with no goal/task created. Blank or >4000 characters are refused
    with a Persian message. Restart the backend: the notes are still there.
19. **صندوق ورودی → پردازش** on a note → «کار»: the task form is prefilled from the note; save → you land on «کارها» with
    the new task highlighted, and the note shows «پردازش‌شده: کار» with a link to it. File another note as «مرجع» and
    discard a third (needs confirmation): both leave the queue but stay visible in the «مرجع» / «حذف‌شده‌ها» views.
20. Annual goal without KRs, two child goals with one KR each checked in at 40 and 80: the annual goal shows Outcome
    **۶۰٪** («میانگین ساده پیشرفت نتیجه ۲ زیرهدف فعال»). Add a KR at 90 to the annual goal → Outcome **۹۰٪**, children
    unchanged. A task linked directly to a child *and* via a focus item of the annual goal counts once. Archive one child →
    it is marked «خارج از محاسبه والد» and leaves both numbers.
21. **اهداف**: tick «در حال انجام» in the status filter (the URL gets `?status=doing`) — only those goals, archived ones
    excluded. A goal whose KR reached its target shows «نتیجه: ۱۰۰٪» and still «در حال انجام»; change it inline with its
    status select (persists, list reloads). «موعد → گذشته از موعد» lists open goals whose deadline was before today
    («n روز از موعد گذشته»); a goal due today shows «موعد امروز» and is not overdue; done/dropped goals are never
    flagged. With more than 20 goals the list pages («صفحه ۱ از ۲»). In **نمای درختی** the status filter keeps
    non-matching parents as faded context.
22. Open the app signed out → «ورود» page. Create an account (password ≥ 8 characters); you land on the page you asked
    for, your e-mail is in the header. Reload: still signed in. **خروج** → login page; the old session cannot be resumed.
    Create a second account: it sees none of the first account's goals/tasks/plans.
23. **داشبورد** (design `ui/…png`, dark by default; ☀ switches to light): quarterly/monthly rings show the mean Outcome of
    measured goals (unmeasured ones are left out, never 0%); «کارهای این ماه/هفته» count distinct tasks planned on those
    days; in «کارهای این هفته» the day buttons switch the list in place (today is preselected, the dashboard is not left;
    «مشاهده همه» opens that day's page); ticking a task in «کارهای امروز» or «کارهای این هفته» saves it; «افزودن تسک جدید برای امروز» creates and plans
    a task; the focus timer counts down 25:00 (not saved). One tall card (left column) switches between «اهداف» (this
    date's active goals of every period together, tagged سالانه / فصلی / ماهانه / هفتگی, coarse first then by goal priority) and «آیتم‌های تمرکز» (active items in goal
    priority order, «x از y تسک انجام شده», ring = done ÷ active tasks). «عادت‌ها» and «لاگ روزانه» say «به‌زودی» (no backend yet).
    Cards have a fixed size whether empty or full: on screens ≥ 1281×720 the whole dashboard fits one screen without page
    scrolling; long lists scroll inside their own card (the add button stays visible).
24. **اهداف** (design `ui/goal.png`): one horizontal row of goal cards (scroll left/right), priority 1 on the right —
    each card shows «اولویت n», its Outcome ring, deadline («تا ۳۰ آذر», red when overdue) and its focus items as a checklist.
    Grab a card with the mouse and drop it elsewhere: priorities renumber and are saved (reload keeps them); dragging into
    the left/right edge scrolls the row. Keyboard/touch: ⋮ → «انتقال به راست/چپ». «افزودن هدف» opens a popup; after saving
    the new goal appears at the end (left) and is scrolled into view. Reordering is available on the unfiltered
    «اولویت» view only. Inside a card, drag a focus item by its dotted handle (or focus the handle and press ↑/↓) to
    reorder it; it stays in its own card (cannot be dropped on another goal) and long lists scroll while dragging.
25. **آیتم‌های تمرکز** (design `ui/focus item.png`): goals (priority order) → their focus items (manual order) → tasks, as
    an expandable table: all goals start collapsed on each visit and open individually; title with a progress ring on its right (goal: Outcome; focus item: done ÷ active tasks), task
    priority, task count, deadline. Search filters all three levels. Tick a task, «+ به برنامه امروز» puts it on today's plan and this week atomically in one click
    (shows both «✓ در برنامه امروز» and «✓ در برنامه هفتگی»), «+ به برنامه هفتگی» puts it on this week without a day in one click (shows «✓ در برنامه هفتگی»; on the weekly page its day column says «بدون روز»), «افزودن تسک جدید» / «افزودن آیتم تمرکز جدید برای این هدف» add inline, «افزودن
    آیتم تمرکز» opens a popup. Tasks linked to a goal without a focus item have their own group; items without a goal
    are under «بدون هدف». The previous list with all filters (incl. archived) is at «فهرست کامل و فیلترها» (`/focus/list`).
    Only the table scrolls (title, search and the column header stay in place; on phones the page scrolls normally).
26. **اولویت تسک**: every task has one of پایین · متوسط · بالا; a new task without a choice gets **پایین** (also tasks that
    had none before, via migration V9). Click the priority badge (focus items page, dashboard week card) to choose
    one of the three; it is saved at once. In the task form the priority list has no empty option.
27. **برنامه هفتگی** (design `ui/weekly plan.png`, `/weekly`): a table of this week's tasks (Saturday–Friday) — tasks
    planned on a day of the week, tasks whose deadline is in the week, and (this week only) unfinished overdue tasks.
    Columns: task, goal, focus item, priority (click to change), deadline/day (choose another Saturday–Friday day in place;
    a planned task moves between daily plans, a deadline-only task updates its due date, and «بدون روز» gets planned), «+ امروز», status. Use ‹ › / «این هفته»
    to change week (`?week=YYYY-MM-DD`), search, Filter (status/priority/goal), Sort, and «افزودن تسک هفتگی» (creates
    the task and puts it on the chosen day). Click the status chip to mark a task done/undone. Only the table scrolls.
27b. **تایم‌باکس** (`/weekly?view=timebox`, switch «فهرست / تایم‌باکس» at the top of برنامه هفتگی; design: owner screenshot):
    a Saturday–Friday × 06:00–24:00 grid of time blocks with «تسک‌های این هفته» beside it (unfinished week tasks with the
    time already boxed). Click an empty slot or «افزودن بلاک زمانی» → dialog (task or free title, day, from/to in 15-minute
    steps, colour); drag a task by ⠿ onto the grid → 1-hour block; drag a block (also to another day) to move it, drag its
    lower edge to resize; red preview + message on overlap (blocks of a day never overlap). Keyboard on a block: ↑/↓ 15 min,
    ←/→ day, Shift+↑/↓ length, Enter edit, Delete remove; ⋮ menu. Blocks survive a reload; a block never changes its task;
    a red line marks the current time. Requires a backend restart (V12).
28. **تخته امروز** (design `ui/today plan.png`, `/today`; sidebar «تخته امروز»): the day's plan as a Kanban board with columns انجام شده ·
    در حال انجام · در انتظار · انجام نشده. Drag a card (anywhere with the mouse, or by its ⠿ handle, also on touch) to
    another column → its status changes; drop it into «در انتظار» → you are asked for the reason (stored as the
    task's `blockedReason`; no 4th status), leaving that column clears it. Drag up/down inside a column → the order is
    saved and survives a reload. Keyboard: focus ⠿, ↑/↓ inside the column, ←/→ to the neighbouring column; the ⋮ menu
    offers the same moves. Chips filter by column (with counts), search covers task/goal/focus item/reason, ‹ › and the
    date (calendar) change day (`?date=`), «+ افزودن تسک» at the bottom of a column creates a task there, «افزودن تسک
    امروز» creates one (any column, goal, focus item) or adds an existing task. Only the columns scroll.
    The board shows only tasks persisted on that day's plan. Unfinished tasks from earlier days stay in the dashboard's
    «کارهای باقی‌مانده» card; clicking «به برنامه امروز» persists one on today's plan and only then makes it appear on
    «تخته امروز». A done planned task shows only on the local day it was finished (`completedAt`).
27d. **سربرگ برنامه هفتگی در یک ردیف**: title · week switch · search · فیلتر · «مرتب‌سازی» (current order in its tooltip) · round ＋ (افزودن تسک هفتگی / in تایم‌باکس افزودن بلاک زمانی) · فهرست/تایم‌باکس, on one row down to 1366 px wide (the week's date range is hidden below 1500 px).
28f. **تایمر تمرکز** (dashboard, owner design, English text): big minutes + «minutes», round + / − (hold to repeat), and a half-circle dial whose minute ticks slide under a fixed needle like a clock — drag it (left = more), scroll it or use ←/→ (PageUp/PageDown ±5); 1–120 min, remembered per device. Start → mm:ss «remaining» and the dial turns with the countdown; Pause/Resume, Reset, «Again» when done. Client-only (no time log yet).
28e. **حرکت و حس دکمه‌ها**: every page enters with a short fade/slide (`page-in`, off with reduced motion); buttons/tabs/pills ease their colours and press in slightly; menus, pop-overs and dialogs pop in. On the dashboard the round ＋ (کارهای روزانه, عادت‌ها) turns into × and a pill opens smoothly to its right with the field and a ✓ (`shared/ui/ExpandingAdd`): Enter/✓ adds and keeps it open for the next one, Esc/× closes it (animated).
24c. **افزودن هدف در خود ردیف**: «افزودن هدف» no longer opens a dialog: a new card of the same size appears after the last goal (left), the board scrolls to it and its title field is focused; fill title, description, period type, status, period and optional deadline, «تایید» creates it (it becomes a normal card), «انصراف» or Esc removes the draft. Empty title shows the error inside the card.
24b. **فلش‌های صفحهٔ اهداف**: when there are more goals than fit, round ‹ › buttons sit in empty gutters left and right of the cards (never on top of them); each click scrolls one card smoothly, the one with nothing further dims (› at priority 1, ‹ at the last goal).
25b. **یادداشت آیتم تمرکز** (after the backend restart for V15): in the «اولویت» column of each focus item (next to its «اولویت n» label, before «تعداد تسک») a «📄 یادداشت» button (with the count when there are notes) and a turquoise «＋ تسک»: «＋ تسک» drops a box down under the row — type the title, «افزودن» (or Enter) puts the task into that item and opens its list, the box stays open for more, Esc/✕ closes it (the old «افزودن تسک جدید» under the list is gone); «یادداشت» — or clicking the item name, or ⋮ → «یادداشت‌ها» — opens a pop-up: write a note and «افزودن یادداشت» (or Ctrl/⌘+Enter), newest first, «ویرایش» / «حذف» per note. Notes never appear as tasks. The details page is still under ⋮ → «جزئیات و ویرایش».
28g. **جابه‌جایی در تقویم** (after the backend restart for the new endpoint): in the calendar slide (ماه / هفته) drag a task onto another day — the target day lights up, a turquoise copy follows the pointer, Esc cancels; a planned task moves on the daily plans, a deadline-only task (⚑) gets that day as its deadline, a task that is both keeps its deadline. In the day view «روز قبل / روز بعد» do the same without a mouse.
28d. **عادت‌ها** (dashboard, after the backend restart for V14): the round turquoise ＋ at the bottom-left of the card («افزودن عادت») adds a habit (same round ＋ on «کارهای روزانه»); each row has a checkbox for today and seven circles شنبه…جمعه — click to tick/untick a day (future days disabled), reload keeps them; ⋮ → «بایگانی عادت» hides it (its days stay).
27c. **جزئیات تسک در برنامه هفتگی**: click a task title on /weekly (list, or the side panel / block ⋮ «جزئیات تسک» in تایم‌باکس) → a pop-up with all details; «ویرایش» turns the same view into inputs in place (ذخیره / انصراف), «انجام شد» toggles, Esc/✕/«بستن»/backdrop close; the page is not left. On آیتم‌های تمرکز the task ⋮ «ویرایش» opens the same pop-up straight in the edit form.
28c. **بازطراحی (نوار بالا + کارت‌های درخشان)** (owner screenshots 2026-11-12): the sidebar is replaced by a top bar — brand + today's date, main pages (داشبورد · اهداف · آیتم‌های تمرکز · برنامه هفتگی · تخته امروز · گزارش‌ها soon), a bell that opens صندوق ورودی with the number of unprocessed notes, and an avatar menu (صندوق ورودی · بایگانی · تنظیمات · theme · خروج). Dark palette is navy + turquoise and every page uses the same glowing cards. Dashboard slide 1 has three columns: «برنامه این هفته» (only the day boxes with done/planned bars, a small ⌄ on each box and the hint «روی هر روز بزنید…» underneath; each day is judged for that day — a task finished on a later day counts as «انجام نشده» there with «انجام‌شده در …» (read-only), an unfinished past one says «منتقل شد», and the day bars count the same way; clicking a day opens it inside the card — animated like the ＋ pill — with its «انجام نشده» and «انجام شده» tasks, tickable, and a «تخته روز» link; the same day, × or Esc closes it) + «کارهای باقی‌مانده» (unfinished active tasks from earlier days, between week and attention; compact rows show the full task title, active goal with its colour dot, focus item and priority below it; no source-day badge is shown, and the compact «+ امروز» button on the right adds the selected task to today's persisted plan and removes it from this list after refresh) + «نیازمند توجه» (overdue / due today / due tomorrow tasks, measured goals whose Outcome trails the elapsed period by > 20 points); «کارهای روزانه» (‹ › switches the day, the date returns to today; this card contains only that day's own planned tasks, unfinished high → medium → low priority and finished ones at the bottom; each task shows its goal with its colour dot · its focus item in small text under the full title, and its priority — changeable — at the end) + «عادت‌ها»; «تایمر تمرکز» + اهداف / آیتم‌های تمرکز as a grid of rings (goal ring = Outcome, «اجرا» written under it). The previous stat cards and «لاگ روزانه» are gone. Check: no page scroll on full-height pages at 1440×900 and up, mobile wraps the bar, «کارها» is not in the bar (`/tasks` still opens from links).
28b. **داشبورد اسلایدی + تقویم ماه** (`/dashboard`, owner screenshot): two slides switched by the vertical ○ ● dots on the dashboard’s right edge (also ↑/↓ on a focused dot, or the mouse wheel over the dots); the next slide comes in from below, the previous one from above; slide 2 (`/dashboard?slide=calendar`) is a month calendar with views ماه · هفته · روز. Each day shows the tasks planned on it and the tasks due that day (⚑), dot colour = the task's goal (same colour as on تخته امروز; grey = بدون هدف), done tasks struck through, and every day cell shows at least five tasks plus every additional complete line that fits its row (only the remaining tasks become «+n کار دیگر» next to the day number). The whole month (six rows) fits on one screen without page scrolling from ~1440×900 up (task lines shrink with the window height, never below 12px; on shorter windows the month scrolls inside the card). Check: resize the window and confirm each row fills its usable task space; arrows change month/week/day, «امروز» returns, clicking a day opens its day view with status/priority/goal chips and «باز کردن تختهٔ این روز», archived tasks never appear, the footer counts distinct tasks, no page scroll on desktop, dots-only cells on a phone.
29. **بایگانی** (`/archive`): tabs اهداف · آیتم‌های تمرکز · تسک‌ها (with counts) list everything archived, newest first, with
    search. «بازگردانی» restores; «حذف» asks for confirmation (listing what goes with it) and deletes permanently.
    Archived things are no longer shown on the dashboard (today/week cards and counts), the today board, the weekly
    plan (also not as goal/focus-item names) or the goals board.

## Backend

```bash
cd backend
./mvnw test                 # run all tests
./mvnw spring-boot:run      # start on http://127.0.0.1:8080
```

Or build and run the jar:

```bash
cd backend
./mvnw package
java -jar target/planner-backend-0.0.1-SNAPSHOT.jar
```

Check it:

```bash
curl -i http://127.0.0.1:8080/api/v1/health
# HTTP/1.1 200
# {"status":"UP","apiVersion":"v1"}
```

### Configuration

| Env variable | Default | Notes |
|---|---|---|
| `PLANNER_SERVER_PORT` | `8080` | |
| `PLANNER_SERVER_ADDRESS` | `127.0.0.1` | Must be loopback (`127.0.0.1`, `::1`). Startup fails otherwise: there is no authentication, so LAN exposure is not allowed. |
| `PLANNER_DB_PATH` | `../data/planner.db` | SQLite file, relative to the working directory (i.e. `<repo>/data/planner.db` when started from `backend/`). The resolved absolute path is logged at startup. Parent directories are created; an existing file is never deleted or overwritten. |
| `PLANNER_DB_BUSY_TIMEOUT` | `5s` | How long a connection waits for a SQLite lock before failing. |
| `PLANNER_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Comma-separated; applies to `/api/v1/**` only. |

### Persistence

- SQLite via Xerial JDBC, HikariCP pool (max 4) and `JdbcClient`. Every pooled connection opens with `PRAGMA foreign_keys=ON` and the busy timeout. (A plain `sqlite3` CLI session does **not** enforce FKs unless you run `PRAGMA foreign_keys=ON;` yourself.)
- Schema is versioned by Flyway in `backend/src/main/resources/db/migration`. Migrations are forward-only: never edit an applied `V<n>__*.sql`; add a new version. `clean` is disabled.
- Storage formats: IDs are UUID text; date-only fields are Gregorian `YYYY-MM-DD` text; timestamps are fixed-width UTC `YYYY-MM-DDTHH:MM:SS.sssZ`; KR baseline/target/weight and check-in values are canonical decimal text read as `BigDecimal` (max 6 fractional and 15 integer digits; larger values are rejected, never rounded). See `common/db/DecimalCodec` and `UtcTimestamps`.
- Key Result check-ins and `goal_history` are append-only (DB triggers reject UPDATE/DELETE). Goal edits, status/parent changes, archive/restore and KR create/recalibrate/archive (with `key_result_id` and `note`) are recorded in `goal_history` in the same transaction as the change.
- SQLite errors are translated by `SqliteExceptionTranslator` (UNIQUE → `DuplicateKeyException`, other constraints → `DataIntegrityViolationException`, BUSY/LOCKED → `CannotAcquireLockException`); Spring has no built-in SQLite mapping.
- Tests use throwaway files under `backend/target/test-db/` or JUnit temp dirs, never `data/planner.db`.

To inspect your data: `sqlite3 data/planner.db` (stop the backend first if you intend to write).

### API conventions

- Every endpoint lives under `/api/v1`. Any other path returns `404`.
- Errors are JSON `{"code": "...", "message": "...", "details": {...}?}` with a stable English `code` and Persian `message`: `400 validation_error`, `404 not_found`, `409 conflict` (or a more specific code), `500 internal_error`. No stack traces or internal messages are exposed.
- Input is strict: unknown JSON fields, numbers sent as strings (and numbers/booleans sent for text fields), `NaN`/`Infinity` and non-`YYYY-MM-DD` dates are rejected with `400` and a per-field code in `details` (e.g. `{"periodStart":"invalid_value"}`).
- See [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md) for the full contract.

Implemented endpoints so far:

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/health` | public |
| PUT | `/api/v1/goals/{goalId}/focus-items/order` | `{focusItemIds:[…]}` = every active focus item of THAT goal once, top first → positions 1..n; items of other goals / partial lists → 400; unknown goal → 404. New items are appended; relinking an item to another goal (PATCH) appends it there |
| PUT | `/api/v1/goals/priority` | `{goalIds:[…]}` = every active goal once, highest first → priorities 1..n; 400 for a partial/duplicate/foreign list. `GET /goals?sort=priority` lists by it; new goals are appended, archiving removes the priority and closes the gap, restoring appends |
| POST | `/api/v1/auth/register` | public. `{email, password, displayName?}` → 201 `{accessToken, tokenType:"Bearer", expiresIn?, user}` (`expiresIn` absent while access tokens do not expire) + httpOnly refresh cookie; 409 `email_taken`; first account claims pre-auth data |
| POST | `/api/v1/auth/login` | public. `{email, password}` → 200 (same body + cookie); 401 `invalid_credentials`; 429 `too_many_attempts` (+`Retry-After`) after 5 failures / 15 min |
| POST | `/api/v1/auth/refresh` | public, uses the refresh cookie → new access token + rotated cookie; 401 `invalid_refresh_token` (cookie cleared) or `refresh_token_rotated` (parallel tab: retry) |
| POST | `/api/v1/auth/logout` | public, revokes the cookie's session (other devices stay signed in), clears the cookie; 204 |
| GET | `/api/v1/auth/me` | current account |
| — | everything else | requires `Authorization: Bearer <accessToken>`; 401 `unauthorized`/`invalid_token` with `WWW-Authenticate: Bearer …`; other users' data is 404 |
| GET, POST | `/api/v1/goals` | list (body = array, total matches in header `X-Total-Count`): `archived=false` (default, active only) \| `true` \| `all`; `status`, `periodType` (repeatable or comma-separated; empty = no filter); `deadlineFrom`/`deadlineTo` (inclusive window, undated goals excluded); `overdueOn=YYYY-MM-DD` (client's local date: deadline before it and status `not_started`/`doing`/`on_hold`); `sort=period` (default) \| `deadline`; `limit` 1–200 (default all), `offset`. Total, stable order (ties by creation time, then id). Unknown values → 400 with the field in `details` |
| GET | `/api/v1/goals/progress?ids=a,b` | progress of up to 200 goals in request order (same values as `/goals/{id}/progress`); 404 for an unknown id |
| GET, PATCH | `/api/v1/goals/{goalId}` | PATCH: title, description, status, periodStart, periodEnd, deadline, parentGoalId (omitted = unchanged, `null` = clear; `periodType` not editable). Single-goal responses carry non-blocking `warnings` (date mismatch with parent/children, deadline outside period). Parent rules: 404 unknown, 409 `goal_cycle`, 409 `incompatible_period_granularity` (annual > quarterly > monthly > weekly, skipping levels allowed), 409 `parent_goal_archived`; archived goal → 409 `goal_archived` |
| GET | `/api/v1/goals/tree?archived=false\|true\|all` | forest: independent goals and goals whose parent is filtered out (`detachedFromParent: true`) are roots; each goal once; stable order (start, coarser period, creation); per node `outcomeProgress` and `executionProgress` separately. Constant number of queries (no N+1) |
| POST | `/api/v1/goals/{goalId}/archive`, `/unarchive` | idempotent (200); never archives children, KRs or tasks |
| GET, POST | `/api/v1/goals/{goalId}/key-results` | `?includeArchived=true` to include archived KRs |
| GET, PATCH | `/api/v1/key-results/{krId}` | GET includes derived `current`, `currentSource` (`check_in`/`baseline`) and `score` (0–100, 2 decimals). PATCH: name, unit; baseline/target/weight only with `note` (400 `required_for_recalibration`), audited; old check-ins never rewritten. 409 `key_result_archived` / `goal_archived` |
| POST | `/api/v1/key-results/{krId}/archive` | idempotent (200), optional `{"note"}`; check-ins kept, KR stops counting toward Outcome |
| GET, POST | `/api/v1/goals/{goalId}/milestones` | delivery checkpoints (not KRs; never affect Outcome/Execution). List: `status=all\|open\|completed\|overdue\|upcoming` (overdue/upcoming need `date`, the client's local date), `archived=false\|true\|all`; order: due date, undated last. Archived goal → 409 `goal_archived` on writes |
| PATCH | `/api/v1/milestones/{milestoneId}` | title, description, dueDate (`null` clears), `completed` (sets/clears `completedAt`), `archived`; archived milestone → 409 `milestone_archived` unless restored in the same request |
| GET | `/api/v1/key-results/{krId}/history` | audited changes (`key_result_created` / `_changed` / `_archived`) with before/after and note |
| GET | `/api/v1/goals/{goalId}/progress` | `keyResults[]` breakdown (score, weight, weightPercent, contribution; contributions sum to Outcome). `outcomeProgress` + `outcomeSource`: weighted own active KRs (`own_key_results`), else equal mean of direct active children with an Outcome (`child_rollup`, `contributingChildCount`), else `null` (`not_measured`); `childOutcomes[]` always lists active children. `executionProgress`: done/active distinct tasks whose effective goal is in the active subtree (`subtreeGoalCount`); `null` without tasks. Archived/dropped branches excluded — separate, never blended |
| GET | `/api/v1/dashboard/today?date=YYYY-MM-DD` | read-only: today's plan counts/`completionPercent` (null without plan) + active goals (not archived, not done/dropped, period contains the date) with both measurements |
| GET, POST | `/api/v1/key-results/{krId}/check-ins` | append-only, chronological; no update/delete |
| GET, POST | `/api/v1/inbox-items` | quick capture: body `{"rawText"}` only (1–4000 characters, counted as Unicode code points; stored as typed, never interpreted). List `status=unprocessed` (default) \| `processed` \| `all`, newest first |
| GET | `/api/v1/inbox-items/{inboxItemId}` | single item incl. provenance (`decision`, `createdEntityType/Id`, `processedAt`). List also takes `decision=` (comma list), e.g. `status=processed&decision=reference` |
| POST | `/api/v1/inbox-items/{inboxItemId}/process` | triage in ONE transaction: `{"decision":"task","task":{…}}` / `goal` / `focusItem` (validated like their create endpoints, errors as `task.title`), or `reference` / `someday` / `discard` with no payload. Returns `{inboxItemId, status, decision, createdEntityType, createdEntityId, processedAt}`; already processed → 409 `inbox_item_already_processed`; any failure leaves the item unprocessed and creates nothing |
| GET, POST | `/api/v1/focus-items` | project/book/course/area/other with optional single `goalId` (404 unknown, 409 `goal_archived` for new links; goals never auto-created). Filters `archived=false\|true\|all`, `goalId`, `kind`, `status` |
| GET, PATCH | `/api/v1/focus-items/{focusItemId}` | PATCH: title, kind, description, goalId (`null` unlinks), status (`not_started\|doing\|done`), `archived` (409 `focus_item_archived` for edits unless restored). Archiving a goal never touches its focus items |
| GET, POST | `/api/v1/tasks` | filters: `status`, `goalId` (direct link), `focusItemId`, `archived=false\|true\|all` (default `false`). Tasks accept an optional `focusItemId` (404 unknown, 409 `focus_item_archived` for new links). Responses carry `focusItemGoalId`, `effectiveGoalId`, `effectiveGoalSource` (`direct`\|`focus_item`) and `warnings` (`goal_differs_from_focus_item`) |
| GET | `/api/v1/goals/{goalId}/tasks` | distinct tasks whose effective goal is this goal (direct link wins over the focus item's goal); `status`, `archived` filters |
| GET | `/api/v1/plans/daily?date=YYYY-MM-DD` | pure read; a missing plan returns `exists:false`, no items, `completionPercent:null` |
| PUT | `/api/v1/plans/daily/{date}` | idempotent create-or-get (201 first time, then 200, same `planId`) |
| POST | `/api/v1/plans/daily/{date}/items[?addToWeek=true]` | `{"taskId"}` adds an existing task; `addToWeek=true` also adds it to the containing Saturday–Friday week in the same transaction. Same task twice on one date → 409 `duplicate_plan_task` |
| POST | `/api/v1/plans/daily/{date}/tasks/{taskId}/move` | `{"toDate"}` → the item on the target date; takes the task off `date`'s plan and appends it to `toDate` (plan created if needed) in one transaction; already there → just leaves `date`; task fields unchanged; 404 not on that plan / foreign, 409 `task_archived` |
| GET, POST | `/api/v1/focus-items/{id}/notes` | notes of a focus item, newest first / `{"body"}` → 201 (409 `focus_item_archived`; 404 foreign/unknown) |
| GET | `/api/v1/focus-item-notes/counts` | `[{focusItemId, count}]` for items that have notes |
| PUT, DELETE | `/api/v1/focus-item-notes/{noteId}` | replace the text / delete (204); 409 `focus_item_archived` |
| GET | `/api/v1/habits?from=&to=[&archived=true]` | Read-only: your active (or archived) habits in creation order with `doneDates` inside from..to (≤ 62 days) |
| POST | `/api/v1/habits` | `{"title"}` → 201 |
| PATCH | `/api/v1/habits/{id}` | `{"title"?, "archived"?}`; archiving hides the habit and keeps its days; 404 for another user's |
| PUT, DELETE | `/api/v1/habits/{id}/checks/{date}` | tick / untick one local day (idempotent, 204); 409 `habit_archived` |
| GET | `/api/v1/plans/weekly/{weekStart}/items` | Read-only: tasks put on the Saturday–Friday week without a day (`{id, weekStart, addedAt, task}`, oldest first; done/archived included, UI filters); weekStart not a Saturday → 400 |
| POST | `/api/v1/plans/weekly/{weekStart}/items` | `{"taskId"}` → 201; 409 `duplicate_plan_task` (already on that week) / `task_archived`; 404 unknown/foreign task. No day, no daily plan is created. Permanently deleting a task removes its items |
| DELETE | `/api/v1/plans/weekly/{weekStart}/tasks/{taskId}` | 204: removes both the undated weekly item and every daily-plan item of the task in that Saturday–Friday week, atomically; task status/dueDate and entries in other weeks stay unchanged. 404 unknown/foreign/not planned in that week |
| GET | `/api/v1/time-blocks?from=&to=` | Read-only: your blocks with local day in from..to (inclusive, ≤ 42 days), by day/start; `task` = live linked task (blocks of archived tasks included, UI hides them) |
| POST | `/api/v1/time-blocks` | `{date, startMinute, endMinute, title?, taskId?, color?}` → 201; minutes multiples of 5 in 0..1440, end > start; title or taskId required; color `teal|blue|purple|green|olive|amber|rose|slate` (default teal); 409 `time_block_overlap` (details.timeBlockId) / `task_archived`; 404 unknown/foreign task |
| PATCH | `/api/v1/time-blocks/{id}` | Partial: date/startMinute/endMinute/title/taskId/color (null clears title or taskId, one must remain); same rules; unknown/read-only fields 400 |
| DELETE | `/api/v1/time-blocks/{id}` | 204; removes only the block (planning aid, not history); 404 if missing/foreign. Permanently deleting a task removes its blocks |
| GET | `/api/v1/plans/daily/{date}/carry-over[?completedFrom=&completedTo=]` | Read-only source for the dashboard's «کارهای باقی‌مانده»: non-archived tasks planned earlier with no plan on or after this date, still unfinished (oldest first, `{plannedOn, task}`). With the local day as a UTC window (both or neither, from < to, else 400), also those finished inside it; the Today board does not call this endpoint |
| PUT | `/api/v1/plans/daily/{date}/items/order` | `{"itemIds":[…]}` = every item of that date's plan once (also archived tasks' items), top first → positions 0..n-1; partial/duplicate/foreign → 400; date without plan → 404 (nothing created) |
| GET, PATCH | `/api/v1/tasks/{taskId}` | PATCH: omitted field = unchanged, `null` = clear; `"archived": true/false` archives/restores. |
| DELETE | `/api/v1/goals/{goalId}`, `/api/v1/focus-items/{focusItemId}`, `/api/v1/tasks/{taskId}` | permanent, **archived only** (409 `not_archived` otherwise; 404 unknown/other user). Goal → also its KRs, check-ins, milestones, history, focus items and every task linked to it or to those items; child goals are kept and detached (recorded in their history). Focus item → also its tasks. Tasks are removed from daily plans. Returns `{goals, focusItems, tasks, detachedChildGoals}` |

```bash
curl -s -H 'Content-Type: application/json' \
  -d '{"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}' \
  http://127.0.0.1:8080/api/v1/goals
```

### Package layout

`com.lifeplanner.<feature>`: `common`, `goal`, `kr`, `task`, `focus`, `inbox`, `milestone`, `planning`, `dashboard`. Feature packages hold their own controller/service/repository/DTOs. `focus`, `inbox` and `milestone` are still empty (MVP v0 work).

Implementation status, decisions beyond `docs/` and hand-off notes: [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md).
