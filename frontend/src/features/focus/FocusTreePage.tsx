import { createContext, useContext, useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { todayIso } from '../../shared/date/calendar';
import { formatNumber } from '../../shared/format';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { MenuButton } from '../../shared/ui/MenuButton';
import { ProgressRing } from '../../shared/ui/ProgressRing';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { progressApi } from '../dashboard/api';
import type { GoalProgress } from '../dashboard/types';
import { goalsApi } from '../goals/api';
import type { Goal } from '../goals/types';
import { deadlineState } from '../goals/deadline';
import { dueLine } from '../goals/goalBoardModel';
import { dailyPlanApi, weeklyPlanApi } from '../planning/api';
import { weekStart } from '../planning/weeklyModel';
import { TodayPlanButton } from '../planning/TodayPlanButton';
import { WeekPlanButton } from '../planning/WeekPlanButton';
import { tasksApi } from '../tasks/api';
import { PriorityPicker } from '../tasks/PriorityPicker';
import { TaskDetailDialog } from '../tasks/TaskDetailDialog';
import type { Task } from '../tasks/types';
import { focusApi, focusNotesApi } from './api';
import { FocusNotesDialog } from './FocusNotesDialog';
import { FocusItemForm } from './FocusItemForm';
import { EMPTY_FOCUS_FORM, toCreateRequest } from './focusFormModel';
import { buildFocusTree, filterFocusTree, type FocusNode, type GoalNode } from './focusTreeModel';

/** Ids of the tasks already on today's plan (for the «به برنامه امروز» buttons). */
const PlannedToday = createContext<ReadonlySet<string>>(new Set());
/** Ids of the tasks already put on this week (for the «به برنامه هفتگی» buttons). */
const PlannedWeek = createContext<ReadonlySet<string>>(new Set());
/** Opens the task pop-up (details + edit) for a task id. */
const OpenTask = createContext<(taskId: string) => void>(() => {});
/** Note counts per focus item + opening the notes pop-up. */
const Notes = createContext<{ count: (id: string) => number; open: (item: { id: string; title: string; archived: boolean }) => void }>({ count: () => 0, open: () => {} });

/**
 * «آیتم‌های تمرکز» (design ui/focus item.png): goal → focus item → task as an expandable table with title, progress,
 * task count and deadline, search over all three levels, and quick actions per row.
 */
export function FocusTreePage() {
  usePageTitle('آیتم‌های تمرکز');
  const today = todayIso();
  const [openTaskId, setOpenTaskId] = useState<string | null>(null);
  const [notesItem, setNotesItem] = useState<{ id: string; title: string; archived: boolean } | null>(null);
  const noteCounts = useApiData((signal) => focusNotesApi.counts(signal), []);
  const notesCtx = { count: (id: string) => noteCounts.data?.find((c) => c.focusItemId === id)?.count ?? 0, open: setNotesItem };
  const goals = useApiData((signal) => goalsApi.search('archived=false&sort=priority', signal), []);
  const items = useApiData((signal) => focusApi.list({ archived: 'false' }, signal), []);
  const todayPlan = useApiData((signal) => dailyPlanApi.get(today, signal), [today]);
  const plannedToday = new Set(todayPlan.data?.items.map((i) => i.task.id) ?? []);
  const thisWeek = weekStart(today);
  const weekPlan = useApiData((signal) => weeklyPlanApi.items(thisWeek, signal), [thisWeek]);
  const plannedWeek = new Set(weekPlan.data?.map((i) => i.task.id) ?? []);
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'false' }, signal), []);
  const allGoals = useApiData((signal) => goalsApi.list('all', signal), []);
  const goalIds = goals.data?.items.map((g) => g.id) ?? [];
  const progress = useApiData<GoalProgress[]>(
    (signal) => (goalIds.length ? progressApi.goals(goalIds, signal) : Promise.resolve([])),
    [goalIds.join(',')],
  );
  const [query, setQuery] = useState('');
  // Start with a compact overview; the user opens only the goals they need.
  const [openGoals, setOpenGoals] = useState<Set<string>>(() => new Set());
  const [openItems, setOpenItems] = useState<Set<string>>(new Set());
  const [creating, setCreating] = useState(false);

  const reload = () => {
    items.reload();
    tasks.reload();
    progress.reload();
    todayPlan.reload();
    weekPlan.reload();
  };
  const loading = !goals.data || !items.data || !tasks.data;
  const error = goals.error ?? items.error ?? tasks.error;
  const tree = goals.data && items.data && tasks.data ? buildFocusTree(goals.data.items, items.data, tasks.data) : null;
  const shown = tree ? filterFocusTree(tree, query) : null;
  const searching = query.trim() !== '';
  const goalOpen = (id: string) => searching || openGoals.has(id);
  const itemOpen = (id: string) => searching || openItems.has(id);
  const toggleGoal = (id: string) =>
    setOpenGoals((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const toggleItem = (id: string) =>
    setOpenItems((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <section className="focus-page" aria-labelledby="focus-heading">
      <div className="focus-page__header">
        <h1 id="focus-heading">آیتم‌های تمرکز</h1>
        <div className="focus-page__search">
          <label htmlFor="focus-search" className="visually-hidden">جستجو در اهداف، آیتم‌های تمرکز و تسک‌ها</label>
          <Icon name="search" size={20} />
          <input id="focus-search" type="search" value={query} onChange={(e) => setQuery(e.target.value)}
            placeholder="جستجو در اهداف، آیتم‌های تمرکز و تسک‌ها…" />
        </div>
        <Link to="/focus/list" className="button button--secondary goals-page__tool">فهرست کامل و فیلترها</Link>
        <button type="button" className="button goals-page__add" aria-haspopup="dialog" onClick={() => setCreating(true)}>
          <Icon name="plus" size={20} /> افزودن آیتم تمرکز
        </button>
      </div>

      <CreateFocusDialog open={creating} goals={allGoals.data ?? []} onClose={() => setCreating(false)}
        onCreated={(goalId) => {
          setCreating(false);
          if (goalId) setOpenGoals((prev) => new Set(prev).add(goalId));
          reload();
        }} />

      <FocusNotesDialog item={notesItem} onClose={() => setNotesItem(null)} onChanged={noteCounts.reload} />
      <TaskDetailDialog task={openTaskId ? tasks.data?.find((t) => t.id === openTaskId) ?? null : null} startEditing
        goals={allGoals.data ?? []} focusItems={items.data ?? []} onClose={() => setOpenTaskId(null)} onChanged={reload} />

      {error != null && <ErrorMessage error={error} onRetry={() => { goals.reload(); reload(); }} />}
      {loading && error == null && <Loading />}
      {shown && (
        <PlannedToday.Provider value={plannedToday}>
        <PlannedWeek.Provider value={plannedWeek}>
        <OpenTask.Provider value={setOpenTaskId}>
        <Notes.Provider value={notesCtx}>
        <div className="focus-table" role="region" aria-label="اهداف، آیتم‌های تمرکز و تسک‌ها" tabIndex={0}>
          <div className="focus-table__head" aria-hidden="true">
            <span className="ft-col ft-col--title">عنوان</span>
            <span className="ft-col ft-col--progress">اولویت</span>
            <span className="ft-col ft-col--count">تعداد تسک</span>
            <span className="ft-col ft-col--due">ددلاین</span>
            <span className="ft-col ft-col--menu" />
          </div>
          {shown.goals.length === 0 && shown.unlinked.length === 0 && (
            searching ? <EmptyState title="چیزی با این عبارت پیدا نشد." /> : (
              <EmptyState title="هنوز هدف یا آیتم تمرکزی ندارید.">
                <p>از <Link to="/goals">اهداف</Link> یک هدف بسازید یا با «افزودن آیتم تمرکز» شروع کنید.</p>
              </EmptyState>
            )
          )}
          <ol className="ft-goals">
            {shown.goals.map((node, i) => (
              <GoalRow key={node.goal.id} node={node} index={i + 1} today={today} progress={progress.data?.find((p) => p.goalId === node.goal.id)}
                open={goalOpen(node.goal.id)} onToggle={() => toggleGoal(node.goal.id)} itemOpen={itemOpen} onToggleItem={toggleItem}
                onChanged={reload} canAdd={!searching} />
            ))}
          </ol>
          {shown.unlinked.length > 0 && (
            <section className="ft-unlinked" aria-labelledby="ft-unlinked-heading">
              <h2 id="ft-unlinked-heading" className="ft-unlinked__title">بدون هدف</h2>
              <ol className="ft-items">
                {shown.unlinked.map((n, i) => (
                  <FocusRow key={n.item.id} node={n} index={i + 1} open={itemOpen(n.item.id)} onToggle={() => toggleItem(n.item.id)}
                    onChanged={reload} canAdd={!searching} />
                ))}
              </ol>
            </section>
          )}
        </div>
        </Notes.Provider>
        </OpenTask.Provider>
        </PlannedWeek.Provider>
        </PlannedToday.Provider>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------------------------------------------- rows

function Chevron({ open, onToggle, controls, label }: { open: boolean; onToggle: () => void; controls: string; label: string }) {
  return (
    <button type="button" className={`ft-chevron${open ? ' ft-chevron--open' : ''}`} aria-expanded={open} aria-controls={controls}
      aria-label={`${open ? 'بستن' : 'باز کردن'} ${label}`} onClick={onToggle}>
      <Icon name="chevron-left" size={22} />
    </button>
  );
}

function GoalRow({ node, index, today, progress, open, onToggle, itemOpen, onToggleItem, onChanged, canAdd }: {
  node: GoalNode; index: number; today: string; progress: GoalProgress | undefined; open: boolean; onToggle: () => void;
  itemOpen: (id: string) => boolean; onToggleItem: (id: string) => void; onChanged: () => void; canAdd: boolean;
}) {
  const g = node.goal;
  const childrenId = useId();
  const due = dueLine(g, today);
  const late = due.isDeadline && deadlineState(g.deadline, g.status, today)?.kind === 'overdue';
  return (
    <li className="ft-goal">
      <div className="ft-row ft-row--goal">
        <span className="ft-col ft-col--title">
          <Chevron open={open} onToggle={onToggle} controls={childrenId} label={`هدف «${g.title}»`} />
          <ProgressRing value={progress?.outcomeProgress ?? null} size={52} stroke={5} showValue label={`پیشرفت نتیجهٔ «${g.title}»`} />
          <Link to={`/goals/${g.id}`} className="ft-title ft-title--goal">{g.title}</Link>
        </span>
        <span className="ft-col ft-col--progress ft-col--actions">
            <span className="ft-rank ft-rank--goal" title="اولویت هدف">{`اولویت ${formatNumber(index)}`}</span>
        </span>
        <span className="ft-col ft-col--count" aria-label={`تعداد تسک: ${formatNumber(node.taskCount)}`}>{formatNumber(node.taskCount)}</span>
        <span className={`ft-col ft-col--due${late ? ' ft-col--late' : ''}`} title={late ? 'موعد گذشته است' : due.isDeadline ? 'موعد هدف' : 'پایان دورهٔ هدف (موعد ندارد)'}>
          <Icon name="calendar" size={20} />
          <span>{due.text.replace(/^تا /, '').replace(/^پایان دوره /, '')}</span>
          {late && <span className="visually-hidden">(گذشته از موعد)</span>}
        </span>
        <span className="ft-col ft-col--menu">
          <MenuButton label={`گزینه‌های هدف «${g.title}»`} items={[{ label: 'جزئیات و ویرایش هدف', to: `/goals/${g.id}` }]} />
        </span>
      </div>
      {open && (
        <div id={childrenId} className="ft-children">
          <ol className="ft-items">
            {node.focus.map((n, i) => (
              <FocusRow key={n.item.id} node={n} index={i + 1} open={itemOpen(n.item.id)} onToggle={() => onToggleItem(n.item.id)}
                onChanged={onChanged} canAdd={canAdd} />
            ))}
          </ol>
          {node.focus.length === 0 && <p className="dash-empty ft-indent">هنوز آیتم تمرکزی برای این هدف ندارید.</p>}
          {node.directTasks.length > 0 && (
            <DirectTasks goalTitle={g.title} tasks={node.directTasks} open={itemOpen(`direct:${g.id}`)}
              onToggle={() => onToggleItem(`direct:${g.id}`)} onChanged={onChanged} />
          )}
          {canAdd && (
            <InlineAdd label="افزودن آیتم تمرکز جدید برای این هدف" inputLabel="عنوان آیتم تمرکز"
              onAdd={async (title) => {
                await focusApi.create({ title, kind: 'other', status: 'not_started', goalId: g.id });
                onChanged();
              }} />
          )}
        </div>
      )}
    </li>
  );
}

function FocusRow({ node, index, open, onToggle, onChanged, canAdd }: {
  node: FocusNode; index: number; open: boolean; onToggle: () => void; onChanged: () => void; canAdd: boolean;
}) {
  const item = node.item;
  const childrenId = useId();
  const notes = useContext(Notes);
  const noteCount = notes.count(item.id);
  const openNotes = () => notes.open({ id: item.id, title: item.title, archived: Boolean(item.archivedAt) });
  const [adding, setAdding] = useState(false);
  const addId = useId();
  return (
    <li className="ft-item">
      <div className="ft-row ft-row--item">
        <span className="ft-col ft-col--title">
          <Chevron open={open} onToggle={onToggle} controls={childrenId} label={`آیتم «${item.title}»`} />
          <ProgressRing value={node.progress} size={48} stroke={5} showValue tone="green" label={`پیشرفت تسک‌های «${item.title}»`} />
          <button type="button" className="ft-title ft-title--button" aria-haspopup="dialog" onClick={openNotes}
            title="نمایش یادداشت‌ها">{item.title}</button>
          {item.status === 'done' && <span className="badge badge--muted">انجام‌شده</span>}
        </span>
        <span className="ft-col ft-col--progress ft-col--actions">
            <span className="ft-rank" title="اولویت آیتم تمرکز در این هدف">{`اولویت ${formatNumber(index)}`}</span>
            {canAdd && (
              <button type="button" className={`ft-addtask${adding ? ' ft-addtask--open' : ''}`} aria-expanded={adding} aria-controls={addId}
                onClick={() => setAdding((a) => !a)} title="افزودن تسک به این آیتم" aria-label={`افزودن تسک به «${item.title}»`}>
                <Icon name="plus" size={15} /> تسک
              </button>
            )}
            <button type="button" className={`ft-notes${noteCount ? ' ft-notes--has' : ''}`} aria-haspopup="dialog" onClick={openNotes}
              aria-label={noteCount ? `یادداشت‌ها (${formatNumber(noteCount)})` : 'یادداشت‌ها (خالی)'} title="یادداشت‌های این آیتم">
              <Icon name="file" size={15} /> یادداشت{noteCount > 0 && <span className="ft-notes__count">{formatNumber(noteCount)}</span>}
            </button>
          </span>
        <span className="ft-col ft-col--count" aria-label={`تعداد تسک: ${formatNumber(node.tasks.length)}`}>{formatNumber(node.tasks.length)}</span>
        <span className="ft-col ft-col--due ft-muted" title="آیتم تمرکز هنوز ددلاین ندارد">—</span>
        <span className="ft-col ft-col--menu">
          <MenuButton label={`گزینه‌های «${item.title}»`} items={[
            { label: 'یادداشت‌ها', onSelect: openNotes },
            { label: 'جزئیات و ویرایش', to: `/focus/${item.id}` },
            { label: item.status === 'done' ? 'برگرداندن به «در حال انجام»' : 'علامت انجام‌شده',
              onSelect: async () => { await focusApi.update(item.id, { status: item.status === 'done' ? 'doing' : 'done' }); onChanged(); } },
            { label: 'بایگانی', danger: true, confirm: `آیتم «${item.title}» بایگانی شود؟ تسک‌هایش حذف نمی‌شوند.`,
              onSelect: async () => { await focusApi.update(item.id, { archived: true }); onChanged(); } },
          ]} />
        </span>
      </div>
      <AddTaskBox id={addId} open={adding} itemTitle={item.title} onClose={() => setAdding(false)}
        onAdd={async (title) => {
          await tasksApi.create({ title, status: 'not_started', focusItemId: item.id });
          if (!open) onToggle(); // show the list the new task went into
          onChanged();
        }} />
      {open && (
        <div id={childrenId} className="ft-children ft-children--tasks">
          {node.tasks.length === 0 && <p className="dash-empty ft-indent">هنوز تسکی برای این آیتم ندارید؛ با «＋ تسک» اضافه کنید.</p>}
          <ul className="ft-tasks">
            {node.tasks.map((t) => <TaskRow key={t.id} task={t} onChanged={onChanged} />)}
          </ul>
        </div>
      )}
    </li>
  );
}

/** Tasks linked to the goal itself, without a focus item: a group of their own so they are visible and searchable. */
function DirectTasks({ goalTitle, tasks, open, onToggle, onChanged }: {
  goalTitle: string; tasks: Task[]; open: boolean; onToggle: () => void; onChanged: () => void;
}) {
  const childrenId = useId();
  return (
    <div className="ft-item ft-item--direct">
      <div className="ft-row ft-row--item">
        <span className="ft-col ft-col--title">
          <Chevron open={open} onToggle={onToggle} controls={childrenId} label={`تسک‌های بدون آیتم تمرکز «${goalTitle}»`} />
          <span className="ft-title ft-muted">تسک‌های بدون آیتم تمرکز</span>
        </span>
        <span className="ft-col ft-col--progress" />
        <span className="ft-col ft-col--count" aria-label={`تعداد تسک: ${formatNumber(tasks.length)}`}>{formatNumber(tasks.length)}</span>
        <span className="ft-col ft-col--due" />
        <span className="ft-col ft-col--menu" />
      </div>
      {open && (
        <div id={childrenId} className="ft-children ft-children--tasks">
          <ul className="ft-tasks">
            {tasks.map((t) => <TaskRow key={t.id} task={t} onChanged={onChanged} />)}
          </ul>
        </div>
      )}
    </div>
  );
}

function TaskRow({ task, onChanged }: { task: Task; onChanged: () => void }) {
  const planned = useContext(PlannedToday).has(task.id);
  const onWeek = useContext(PlannedWeek).has(task.id);
  const openTask = useContext(OpenTask);
  const [done, setDone] = useState(task.status === 'done');
  const [synced, setSynced] = useState(task.status);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (synced !== task.status) {
    setSynced(task.status);
    setDone(task.status === 'done');
  }
  const id = useId();
  async function toggle() {
    const next = !done;
    setDone(next);
    setSaving(true);
    setError(null);
    try {
      await tasksApi.update(task.id, { status: next ? 'done' : 'not_started' });
      onChanged();
    } catch (e) {
      setDone(!next);
      setError(e instanceof ApiError ? e.message : 'ذخیره نشد.');
    } finally {
      setSaving(false);
    }
  }
  return (
    <li className={`ft-row ft-row--task${done ? ' check-row--done' : ''}`}>
      <span className="ft-col ft-col--title">
        <input id={id} type="checkbox" className="check" checked={done} disabled={saving} onChange={toggle} />
        <label htmlFor={id} className="check-row__title">{task.title}</label>
        {error && <span className="field__error" role="alert">{error}</span>}
      </span>
      <span className="ft-col ft-col--progress">
        <PriorityPicker task={task} onChanged={onChanged} />
      </span>
      <span className="ft-col ft-col--plan">
        <TodayPlanButton taskId={task.id} taskTitle={task.title} planned={planned} disabled={done} onPlanned={onChanged} alsoPlanWeek />
        <WeekPlanButton taskId={task.id} taskTitle={task.title} planned={onWeek} disabled={done} onPlanned={onChanged} />
      </span>
      <span className="ft-col ft-col--menu">
        <MenuButton label={`گزینه‌های تسک «${task.title}»`} items={[
          { label: 'ویرایش', onSelect: () => openTask(task.id) },
          { label: 'بایگانی تسک', danger: true, confirm: `تسک «${task.title}» بایگانی شود؟`,
            onSelect: async () => { await tasksApi.update(task.id, { archived: true }); onChanged(); } },
        ]} />
      </span>
    </li>
  );
}

/**
 * «＋ تسک» of a focus item: a box that drops down under the item's row (animated), with the title field and «افزودن».
 * Stays open after an add so several tasks can be typed; Escape / ✕ closes it.
 */
function AddTaskBox({ id, open, itemTitle, onAdd, onClose }: {
  id: string; open: boolean; itemTitle: string; onAdd: (title: string) => Promise<void>; onClose: () => void;
}) {
  const [title, setTitle] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (open) requestAnimationFrame(() => input.current?.focus({ preventScroll: true }));
    else setError(null);
  }, [open]);
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim() || saving) return;
    setSaving(true);
    setError(null);
    try {
      await onAdd(title.trim());
      setTitle('');
      input.current?.focus();
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }
  return (
    <div id={id} className={`ft-addbox${open ? ' ft-addbox--open' : ''}`} inert={!open}>
      <div className="ft-addbox__inner">
        <form className="ft-addbox__form" onSubmit={submit} onKeyDown={(e) => e.key === 'Escape' && onClose()}>
          <label htmlFor={`${id}-title`} className="visually-hidden">{`عنوان تسک جدید برای «${itemTitle}»`}</label>
          <Icon name="plus" size={18} />
          <input ref={input} id={`${id}-title`} value={title} maxLength={200} placeholder={`تسک جدید برای «${itemTitle}»…`}
            onChange={(e) => setTitle(e.target.value)} />
          <button type="submit" className="button" disabled={saving || !title.trim()}>{saving ? '…' : 'افزودن'}</button>
          <button type="button" className="icon-button" aria-label="بستن" onClick={onClose}>✕</button>
        </form>
        {error != null && <ErrorMessage error={error} />}
      </div>
    </div>
  );
}

function InlineAdd({ label, inputLabel, onAdd, compact }: { label: string; inputLabel: string; onAdd: (title: string) => Promise<void>; compact?: boolean }) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const id = useId();
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim() || saving) return;
    setSaving(true);
    setError(null);
    try {
      await onAdd(title.trim());
      setTitle('');
      setOpen(false);
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }
  if (!open) {
    return (
      <button type="button" className={`ft-add${compact ? ' ft-add--compact' : ''}`} onClick={() => setOpen(true)}>
        <Icon name="plus" size={22} /> {label}
      </button>
    );
  }
  return (
    <form className="dash-add-form ft-add-form" onSubmit={submit}>
      <label htmlFor={id} className="visually-hidden">{inputLabel}</label>
      <input id={id} autoFocus value={title} maxLength={200} placeholder={`${inputLabel}…`} onChange={(e) => setTitle(e.target.value)}
        onKeyDown={(e) => e.key === 'Escape' && setOpen(false)} />
      <button type="submit" className="button" disabled={saving || !title.trim()}>{saving ? '…' : 'افزودن'}</button>
      <button type="button" className="button button--secondary" onClick={() => setOpen(false)}>انصراف</button>
      {error != null && <ErrorMessage error={error} />}
    </form>
  );
}

function CreateFocusDialog({ open, goals, onClose, onCreated }: {
  open: boolean; goals: Goal[]; onClose: () => void; onCreated: (goalId: string | null) => void;
}): ReactNode {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog ref={ref} className="modal" aria-label="افزودن آیتم تمرکز" onClose={onClose}
      onClick={(e) => e.target === e.currentTarget && onClose()}>
      {open && (
        <div className="modal__body">
          <button type="button" className="icon-button modal__close" aria-label="بستن" onClick={onClose}>✕</button>
          <FocusItemForm heading="افزودن آیتم تمرکز" initial={EMPTY_FOCUS_FORM} goals={goals} submitLabel="ایجاد آیتم"
            onCancel={onClose}
            onSubmit={async (v) => {
              await focusApi.create(toCreateRequest(v));
              onCreated(v.goalId || null);
            }} />
        </div>
      )}
    </dialog>
  );
}

