# سند نیازمندی محصول — Personal Life Planner

**Version:** 2.0  
**Status:** Implementation-ready baseline (product assumptions explicitly marked)  
**Date:** 2026-11-03  
**Audience:** Owner/developer and Codex

> این سند بر اساس نیازمندی‌های تأییدشده کاربر و تلفیق اصول OKR، GTD، برنامه‌ریزی دوره‌ای، پایش اجرا و بازبینی نوشته شده است. «Personal Goal & Execution System» نام طراحی این محصول است، نه نام یک استاندارد رسمی. تمام تصمیم‌های اجرایی باید با `DOMAIN_RULES.md` و `API_CONTRACT.md` سازگار باشند.

## 1. مسئله و ارزش محصول

کاربر می‌خواهد اهداف سالانه، فصلی، ماهانه و هفتگی را تعریف کند، آن‌ها را به فعالیت‌های قابل انجام تبدیل کند، کارهای روزانه را برنامه‌ریزی و اجرا کند، ظرفیت و زمان واقعی را بسنجد و از طریق بازبینی دوره‌ای برنامه را اصلاح کند. محصول باید از اولین نسخه یک جریان کامل و پایدار را اجرا کند، نه مجموعه‌ای از صفحات نمایشی بدون ذخیره واقعی.

**ارزش محوری:** انتخاب فعالیت‌های معنادار، اجرای واقع‌بینانه، سنجش نتیجه مستقل از فعالیت و اصلاح برنامه با استفاده از شواهد.

**کاربر هدف:** یک کاربر شخصی، ابتدا روی مک‌بوک، بعد رابط قابل استفاده روی موبایل. احراز هویت/همگام‌سازی میان چند دستگاه خارج از محدوده اولیه است.

## 2. تصمیم‌های تثبیت‌شده

- برنامه‌ریزی چهار بازه **سالانه، فصلی، ماهانه، هفتگی** دارد. برنامه روزانه جدا از Goal است.
- تقویم نمایشی **شمسی (Jalali)** و هفته **شنبه تا جمعه** است. تاریخ‌های صرف در API و DB به Gregorian ISO (`YYYY-MM-DD`) ذخیره می‌شوند.
- والد Goal اختیاری است؛ هدف مستقل مجاز است. درخت بدون دور و با امکان عبور از یک سطح ساخته می‌شود.
- **Outcome Progress** از Key Results و **Execution Progress** از فعالیت‌های مرتبط محاسبه و **مجزا** نمایش داده می‌شوند؛ میانگین ترکیبیِ آن‌ها **ممنوع** است.
- `Focus Item` (پروژه/کتاب/موضوع) و `Task` موجودیت‌های مجزا هستند. Task ممکن است مستقیم به Goal، از طریق Focus Item، یا بدون Goal مرتبط باشد.
- تسک سه وضعیت اصلی `not_started`, `doing`, `done` دارد. مانع اجرا به صورت ویژگی مستقل (blocked flag/reason) اضافه می‌شود، نه یک وضعیت چهارم اجباری.
- محصول single-user، local-first، با Java 21 + Spring Boot + Spring JDBC + SQLite + Flyway در Backend و React + TypeScript + Vite در Frontend است.
- زبان UI فارسی، RTL، mobile-first؛ کد و API انگلیسی هستند. زمان اجرا نباید به CDN یا API بیرونی نیاز داشته باشد.

## 3. مفاهیم دامنه

| مفهوم | تعریف | الزام ارتباطی |
|---|---|---|
| Life Area | حوزه زندگی مثل رشد فردی یا کار | اختیاری، از MVP v2 |
| Goal / Objective | نتیجه مطلوب با بازه، deadline و وضعیت مستقل | می‌تواند مستقل یا زیرمجموعه یک Goal دیگر باشد |
| Key Result (KR) | سنجه عددیِ نتیجه، با baseline/target و check-in | متعلق به یک Goal؛ صفر تا چند KR |
| Milestone | نقطه عطف تحویل/پایان مرحله؛ **KR نیست** | می‌تواند به Goal وصل شود |
| Focus Item | پروژه، کتاب، دوره یا محور اقدام | می‌تواند به Goal وصل شود |
| Task | اقدام اجرایی قابل انجام | اتصال اختیاری به Goal یا Focus Item |
| Inbox Item | ایده/کار ثبت‌شده بدون نیاز به ساختاردهی فوری | می‌تواند بعداً به Goal/Focus/Task تبدیل شود |
| Weekly Plan | تعهد و ظرفیت اجرایی یک هفته | بدون نیاز به ساخت Weekly Goal |
| Daily Plan | کارهای انتخاب‌شده برای یک تاریخ | مستقل از وجود Daily Goal |
| Review | جمع‌بندی واقعی و تصمیم برای دوره بعد | به دوره و داده‌های همان دوره متصل |

## 4. گردش کار محصول

1. **Capture:** ورود سریع به Inbox بدون فیلد اجباری غیر از متن.
2. **Clarify/Organize:** انتخاب اینکه مورد Inbox اقدام است، پروژه/هدف است، مرجع است یا حذف/تعویق شود.
3. **Define outcome:** ساخت Goal و حداکثر چند KR کاربردی با baseline و target؛ انتخاب سطح دوره و deadline.
4. **Plan:** تبدیل فعالیت‌های Focus Item و Backlog به تعهدات هفته، کنترل ظرفیت، سپس انتخاب کارهای هر روز.
5. **Execute:** تغییر وضعیت Task، ثبت زمان در نسخه بعدی و نگهداشت تاریخچه جابه‌جایی برنامه.
6. **Review:** مقایسه Outcome، Execution و زمان واقعی؛ ثبت تصمیم‌های اصلاحی.

**قاعده UX:** اجبار به ساخت Goal/Focus Item برای ثبت یک Task ممنوع است. هدف ماهانه مستقل مجاز است. کاربر برای داشتن برنامه هفتگی به Goal هفتگی نیاز ندارد.

## 5. نیازمندی‌های عملکردی و معیارهای کلان

### FR-G — Goals & KRs
- FR-G01: ایجاد/مشاهده/ویرایش/آرشیو Goal با عنوان، نوع دوره، محدوده تاریخ، deadline اختیاری، توضیح، وضعیت و parent اختیاری.
- FR-G02: نمایش درخت قابل پیمایش و جلوگیری از self-parent و cycle؛ پرش بین سطوح مجاز است.
- FR-G03: چند KR عددی برای یک Goal با baseline, target, unit, weight, current و check-in تاریخچه‌دار.
- FR-G04: Milestone با شرح، موعد و وضعیت انجام؛ تکمیل Milestone مستقیماً Outcome را تغییر نمی‌دهد.
- FR-G05: نمایش مستقل Outcome Progress و Execution Progress؛ وضعیت Goal مستقل از درصد پیشرفت.
- FR-G06: اعتبارسنجی بازه‌های شمسی (سال/فصل/ماه/هفته) و تاریخ‌های مرزی.

### FR-E — Execution & Task
- FR-E01: Focus Item و Task با ارتباط‌های اختیاری، عنوان، اولویت، برآورد زمان، due date و وضعیت سه‌گانه.
- FR-E02: Backlog واحد؛ یک Task در نماها کپی نمی‌شود.
- FR-E03: انتخاب تعهدات هفته و Today و امکان برگشت Task به Backlog.
- FR-E04: برنامه‌ریزی مطابق ظرفیت هفتگی؛ over-capacity هشدار است نه خطای مانع ثبت.
- FR-E05: انتقال برنامه‌ریزی Task به تاریخ دیگر همراه با تاریخچه؛ due date با planned date متفاوت است.
- FR-E06: امکان ثبت مانع (blocked reason) بدون تغییر enum سه‌گانه Task.
- FR-E07: Quick Capture و triage در Inbox؛ حفظ اتصال مورد پردازش‌شده به شیء مقصد.

### FR-A — Monitoring & Review
- FR-A01: داشبورد Today: تعداد کارهای برنامه‌ریزی‌شده و تکمیل‌شده، نرخ اجرای امروز، اهداف فعال با Outcome/Execution مستقل.
- FR-A02: داشبورد هفته: تعهدات، زمان برنامه‌ریزی‌شده در برابر ظرفیت، زمان واقعی و کارهای منتقل‌شده (در نسخه‌های بعدی).
- FR-A03: time log چندگانه برای هر Task؛ مقایسه estimated/actual بدون تحریف تاریخچه.
- FR-A04: Daily/Weekly/Monthly/Quarterly review با نکات مهم، موانع، تصمیم‌ها و لینک داده‌های دوره.
- FR-A05: روند و Snapshot زمانی برای فهم «در آن زمان چه برنامه‌ای وجود داشت».

### FR-Q — Quality
- FR-Q01: ذخیره ماندگار در SQLite، Migration نسخه‌دار، اعمال Foreign Key در هر connection.
- FR-Q02: رابط فارسی RTL، قابل استفاده با موبایل و صفحه‌کلید، همراه با حالت loading/empty/error.
- FR-Q03: Export/Backup/Restore و صحت داده‌ها در RC؛ امکان اجرای لوکال بدون اینترنت پس از نصب.
- FR-Q04: تمرکز امنیتی: در حالت بدون احراز هویت، server فقط روی loopback اجرا شود. دسترسی LAN/remote بدون مدل دسترسی جدید ممنوع است.

## 6. قاعده محاسبه پیشرفت — الزام قطعی

- هر **KR** یک `baseline`, `target`, `current` و `weight>0` دارد. `target != baseline`.
- `KRProgress = clamp(100 × (current - baseline)/(target - baseline), 0, 100)`؛ برای هدف کاهشی نیز کار می‌کند.
- `OutcomeProgress(goal)` اگر KR فعال دارد = `Σ(KRProgress × weight)/Σ(weight)`. فقط KRهای فعال لحاظ شوند.
- اگر KR فعال ندارد ولی فرزندان فعال دارای outcome تعریف‌شده هستند، Outcome = میانگین **Outcome مستقیم فرزندان دارای مقدار**، با وزن برابر در v2.0؛ هر زیرشاخه از طریق یک فرزند وارد می‌شود و شمارش دوباره مجاز نیست.
- اگر نه KR فعال و نه Outcome فرزند تعریف‌شده وجود دارد، Outcome = `null` (`not_measured`). Task completion **هرگز** مقدار Outcome را تولید نمی‌کند.
- `ExecutionProgress(goal)` = `100 × تعداد تسک‌های done مرتبط / تعداد تسک‌های فعال مرتبط`؛ با انتساب هر Task فقط به یک Goal موثر (`task.goalId` مقدم بر `focusItem.goalId`)، اجتماع یکتای زیرشاخه و حذف Taskهای آرشیوشده. اگر صفر Task فعال است = `null`.
- `DailyCompletion` = `done distinct planned task IDs / distinct planned task IDs`; برنامه خالی = `null`/`no_plan`.
- این اعداد بدون میانگین‌گیری بین Outcome و Execution نمایش داده می‌شوند. `Goal Health` (اختیاری در MVP v2) شاخص تشخیصی جداگانه و همراه با توضیح است.
- `Goal.status` از درصد محاسبه نمی‌شود؛ تغییر خودکار وضعیت از 100٪ ممنوع است.

### مثال پذیرش
Goal با یک KR از baseline=20 به target=60، current=40 => Outcome=50٪. اگر ۴ تسک دارد و ۳ تسک Done شده‌اند => Execution=75٪. UI **دو عدد 50 و 75** نشان می‌دهد؛ **62.5٪ ممنوع است**.

## 7. قرارداد زمان، تقویم و دوره

- date-only به عنوان Gregorian `YYYY-MM-DD` بین UI/API/SQLite ردوبدل شود؛ آن را از `Date.toISOString()` محلی نسازید.
- timestampها UTC و شامل offset/`Z` هستند؛ نمایش طبق timezone IANA تنظیم کاربر (پیش‌فرض timezone مرورگر).
- بازه‌ها inclusive `startDate` و `endDate` هستند؛ week با شنبه شروع و جمعه پایان می‌یابد، حتی اگر از دو ماه شمسی عبور کند.
- Annual/Quarterly/Monthly در UI به تقویم شمسی تعریف می‌شوند؛ Backend بعد از دریافت تاریخ ISO، ترتیب و منطق بازه را کنترل می‌کند؛ اعتبارسنجی دقیق تقویم در frontend shared module همراه با تست انجام شود.
- `Goal.deadline` می‌تواند مستقل از پایان دوره باشد و اگر خارج از بازه باشد، در MVP v0 هشدار داده شود، نه حذف بی‌اطلاع.
- Task.dueDate (موعد الزام) با DailyPlan.date (روز اختصاص یافته برای اجرا) متفاوت است.

## 8. قواعد والد/فرزند و سناریوهای مرزی

- `parentGoalId` nullable؛ هیچ هدفی مجبور به اتصال به Goal دیگری نیست.
- Parent باید سطح زمانی طولانی‌تر از child داشته باشد (`annual > quarterly > monthly > weekly`)، پرش مجاز است؛ self-parent و cycle همیشه 409.
- ناسازگاری یا عدم انطباق کامل تاریخ‌های child/parent در MVP v0 هشدار/validation response غیرمسدودکننده است، نه جلوگیری قطعی؛ هفته مرزی استثنا نیست.
- Archive کردن Goal نباید Task، KR، Focus Item یا تاریخچه را بی‌صدا حذف کند؛ آرشیو Goal والد باید رفتار زیرشاخه را روشن کند (در v0: هیچ زیرشاخه‌ای خودکار آرشیو نمی‌شود و در UI هشدار نمایش داده می‌شود).
- هیچ Taskی نباید فقط به‌خاطر انتقال بین روزها تکثیر شود؛ نسبت پیشرفت با شناسه‌های یکتا محاسبه می‌شود.

## 9. اطلاعات صفحات و UX

### Demo 0
- `/goals`: ایجاد Goal و KR اولیه، فهرست و خلاصه وضعیت.
- `/tasks`: ساخت/مشاهده/تغییر وضعیت Task.
- `/today`: افزودن Task موجود به برنامه امروز، تغییر وضعیت، ثبت ماندگار.
- `/dashboard`: شمارنده Today و Outcome/Execution مجزای اهداف.

### MVP v0
- `/goals/:id`: جزئیات، KRهای چندگانه، check-in، milestones و تسک‌های موثر.
- `/goals/tree`: نمایش درخت، هدف‌های مستقل، فیلتر وضعیت.
- `/focus`: Focus Items و Taskهای مرتبط.
- `/inbox`: ورود فوری و پردازش/دسته‌بندی موارد.

### MVP v1+
- `/week`: تعهدات و ظرفیت برنامه‌ریزی، اولویت روزها؛ `/reviews` بازبینی.
- تحلیل‌های زمانی، تایمر، تکرار، snapshot و عملیات Backup در نسخه‌های بعدی.

**UX:** همه صفحات امکان برگشت/ویرایش دارند؛ empty state باید عمل بعدی را روشن کند. درخت RTL باید با صفحه‌کلید قابل پیمایش باشد. نمایش پیشرفت ناموجود به صورت «سنجیده نشده» است، نه صفر.

## 10. محدوده نسخه‌ها و خروجی پذیرش

### Demo 0 — Vertical Slice واقعی
Backend و SQLite راه‌اندازی شوند؛ Goal با یک یا چند KR، Task، Today و Dashboard واقعی کار کنند. CRUD مقدماتی لازم کافی است؛ KR check-in append-only از ابتدا پیاده شود. پس از restart داده‌ها بمانند. عددهای Outcome و Execution جدا باشند و وضعیت Done به KPI دست نزند. استفاده لوکال بدون اینترنت پس از نصب، جزو Smoke Test است.

### MVP v0 — Model & Planning Foundation
Goal CRUD/Archive، Hierarchy، Jalali calendar picker، Tree، KR management/check-in history، Milestones، Focus Items، انتساب موثر Task، Inbox quick capture/triage، status/filter و rollup مبتنی بر نتایج اضافه شوند. Today و Dashboard نسخه Demo بدون regression باشند.

### MVP v1 — Weekly Planning
Backlog، Weekly Plan مستقل از Goal، تعهدات هفته، اولویت، ظرفیت، انتقال با تاریخچه، Daily planning improvements و Weekly Review اجرایی.

### MVP v2 — Time & Learning
Time logs/Timer، Time Blocks، recurring tasks/habits پایه، Monthly/Quarterly Review، Life Areas و تحلیل روند/Goal Health توضیح‌دار.

### MVP RC — Reliable Personal Release
Mobile/PWA با درک محدودیت offline iPhone، Export/Backup/Restore، تست یکپارچه، امنیت و مستندسازی نصب، Recovery.

## 11. Non-goals و محدودیت‌ها

- ادعا نمی‌کنیم این مدل یک استاندارد رسمی با نام اختصاصی است؛ این ترکیب قابل دفاع از چند روش است.
- نه همه Goalها باید KR داشته باشند و نه تمام Taskها باید Goal داشته باشند؛ در حالت بدون نتیجه سنجیده‌شده، Outcome به صورت null نشان داده می‌شود.
- AI planning، توصیه خودکار اجباری، چندکاربره، Sync با Notion/Obsidian/Google Calendar، پرداخت، Microservices و Cloud Hosting در MVP اصلی نیستند.
- PWA روی آیفون و ذخیره محلی بدون اینترنت، جایگزین همگام‌سازی با SQLite روی مک نیست؛ همگام‌سازی/احراز هویت مرحله جداگانه دارد.

## 12. شاخص‌های موفقیت محصول

1. در Demo 0 چرخه Create Goal → KR Check-in → Create Task → Plan Today → Done → Dashboard بدون داده ساختگی کامل شود.
2. در MVP v0 بتوان هدف ماهانه مستقل و درخت سالانه/فصلی/ماهانه/هفتگی ساخت و از Inbox موردی را به Task تبدیل کرد.
3. در MVP v1 بتوان یک هفته را با توجه به ساعت آزاد، برنامه‌ریزی و سپس بازبینی کرد؛ Task به‌صورت تکراری ساخته نشود.
4. هیچ عملیات معمولی نباید اطلاعات تاییدشده گذشته را بی‌صدا از بین ببرد؛ عملیات بازگشت/Backup در RC آزموده شود.

## 13. محدودیت‌های فنی و تست

- Java 21, Maven, supported stable Spring Boot 3.5.x line (pin a tested patch), Spring Web, Bean Validation, Spring JDBC, Xerial SQLite JDBC, Flyway SQLite-compatible support, JUnit 5; React, TypeScript, Vite, React Router, Jalali conversion module.
- یک بانک SQLite محلی با مسیر configurable `data/planner.db`؛ تست‌ها از فایل جدا و موقت استفاده کنند. foreign keys روی **هر connection** فعال و با تست تأیید شوند.
- Migrationها forward-only و بدون drop مخرب؛ index/constraint لازم و transactions.
- DTO با camelCase، DB با snake_case، URLهای `/api/v1/...` و Error JSON یکنواخت.
- تست: backend unit/integration، API contract، frontend calendar/score unit و smoke manual. گزارش شفاف PASS/FAIL/NOT RUN.

## 14. Definition of Done

هر Story فقط وقتی Done است که (۱) رفتار واقعی با داده ماندگار، (۲) Validation و error handling، (۳) تست happy path و edge case، (۴) build/test در دسترس، (۵) اصلاح مختصر اسناد/contract و (۶) عدم regression در قابلیت‌های قبلی تأیید شود. هیچ پیاده‌سازی mock-only برای نسخه قابل قبول نیست.

## 15. فرض‌های قابل بازنگری (تصمیم کاربر نیستند)

- وزن KRها به‌صورت پیش‌فرض ۱ و قابل ویرایش از MVP v0 است.
- در نبود KR، outcome فقط از outcome فرزندان فعال محاسبه می‌شود؛ parent با KR خودش از children برای outcome میانگین نمی‌گیرد.
- هشدار ظرفیت وقتی `sum(estimatedMinutes for weekly committed distinct tasks) > capacityMinutes` رخ می‌دهد؛ بافر پیش‌فرض پیشنهادی ۲۰٪ از ظرفیت در UI است، قابل تغییر.
- وضعیت Goal از `not_started`, `doing`, `done`, `on_hold`, `dropped` استفاده می‌کند؛ Task سه وضعیت اصلی دارد و blocked reason جداگانه است.
- برای اولین MVP بدون login، Backend فقط loopback است؛ دسترسی موبایل به سرور بعد از طراحی کنترل دسترسی فعال می‌شود.

## 16. منابع روش‌شناختی و فنی

- GTD پنج گام: https://gettingthingsdone.com/what-is-gtd/
- OKR: تمایز Key Result از Task: https://www.whatmatters.com/okrs-explained/what-is-a-key-result
- Microsoft: تعریف Objective/Key Result/Initiative/Check-in: https://learn.microsoft.com/en-us/viva/goals/get-to-know-okrs
- SQLite FK: https://www.sqlite.org/foreignkeys.html
- Flyway SQLite: https://documentation.red-gate.com/flyway/reference/database-driver-reference/sqlite
- Jalali conversion: https://github.com/jalaali/jalaali-js
