import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { formatNumber, formatTimestamp } from '../../shared/format';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { focusApi } from '../focus/api';
import { KIND_LABELS } from '../focus/labels';
import { goalsApi } from '../goals/api';
import { PERIOD_LABELS } from '../goals/labels';
import { tasksApi } from '../tasks/api';
import { TASK_STATUS_LABELS } from '../tasks/labels';
import { archivedOnly, deletionImpact, type ArchiveKind } from './archiveModel';

const TABS: { kind: ArchiveKind; label: string }[] = [
  { kind: 'goal', label: 'اهداف' },
  { kind: 'focus', label: 'آیتم‌های تمرکز' },
  { kind: 'task', label: 'تسک‌ها' },
];

interface Target { kind: ArchiveKind; id: string; title: string }

/**
 * «بایگانی»: every archived goal, focus item and task in one place. Each can be restored, or deleted permanently
 * (after a confirmation that lists what goes with it). Archived things are hidden from the working pages.
 */
export function ArchivePage() {
  usePageTitle('بایگانی');
  const goals = useApiData((signal) => goalsApi.list('all', signal), []);
  const items = useApiData((signal) => focusApi.list({ archived: 'all' }, signal), []);
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'all' }, signal), []);
  const reload = () => {
    goals.reload();
    items.reload();
    tasks.reload();
  };
  const [tab, setTab] = useState<ArchiveKind>('goal');
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<{ text: string; error?: boolean } | null>(null);
  const [confirm, setConfirm] = useState<Target | null>(null);

  const ready = goals.data && items.data && tasks.data;
  const error = goals.error ?? items.error ?? tasks.error;
  const lists = {
    goal: archivedOnly(goals.data ?? [], query),
    focus: archivedOnly(items.data ?? [], query),
    task: archivedOnly(tasks.data ?? [], query),
  };
  const goalName = (id: string | null) => (id ? goals.data?.find((g) => g.id === id)?.title : undefined);
  const itemName = (id: string | null) => (id ? items.data?.find((i) => i.id === id)?.title : undefined);

  async function restore(t: Target) {
    setBusy(t.id);
    setMessage(null);
    try {
      if (t.kind === 'goal') await goalsApi.unarchive(t.id);
      else if (t.kind === 'focus') await focusApi.update(t.id, { archived: false });
      else await tasksApi.update(t.id, { archived: false });
      setMessage({ text: `«${t.title}» از بایگانی خارج شد و دوباره در بخش‌های دیگر دیده می‌شود.` });
      reload();
    } catch (e) {
      setMessage({ text: e instanceof ApiError ? e.message : 'بازگردانی انجام نشد.', error: true });
    } finally {
      setBusy(null);
    }
  }

  async function remove(t: Target) {
    setBusy(t.id);
    setMessage(null);
    try {
      const r = t.kind === 'goal' ? await goalsApi.remove(t.id) : t.kind === 'focus' ? await focusApi.remove(t.id) : await tasksApi.remove(t.id);
      setMessage({ text: `«${t.title}» برای همیشه حذف شد${impactText(r, true)}.` });
      setConfirm(null);
      reload();
    } catch (e) {
      setMessage({ text: e instanceof ApiError ? e.message : 'حذف انجام نشد.', error: true });
      setConfirm(null);
    } finally {
      setBusy(null);
    }
  }

  function onTabKey(e: KeyboardEvent<HTMLButtonElement>) {
    const i = TABS.findIndex((x) => x.kind === tab);
    const rtl = getComputedStyle(e.currentTarget).direction === 'rtl';
    let next = i;
    if (e.key === (rtl ? 'ArrowLeft' : 'ArrowRight')) next = (i + 1) % TABS.length;
    else if (e.key === (rtl ? 'ArrowRight' : 'ArrowLeft')) next = (i - 1 + TABS.length) % TABS.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = TABS.length - 1;
    else return;
    e.preventDefault();
    setTab(TABS[next].kind);
    document.getElementById(`archive-tab-${TABS[next].kind}`)?.focus();
  }

  const actions = (t: Target) => (
    <div className="archive-actions">
      <button type="button" className="button button--secondary archive-btn" disabled={busy !== null} onClick={() => void restore(t)}
        aria-label={`بازگردانی «${t.title}»`}>
        بازگردانی
      </button>
      <button type="button" className="button archive-btn archive-btn--danger" disabled={busy !== null} aria-haspopup="dialog"
        onClick={() => setConfirm(t)} aria-label={`حذف دائمی «${t.title}»`}>
        حذف
      </button>
    </div>
  );
  const archivedAt = (iso: string | null) => (iso ? <time dateTime={iso}>{formatTimestamp(iso)}</time> : null);

  return (
    <section className="weekly archive" aria-labelledby="archive-heading">
      <header className="weekly__title">
        <h1 id="archive-heading"><Icon name="archive" size={30} className="dash-card__icon" /> بایگانی</h1>
        <p className="meta">موارد بایگانی‌شده در هیچ بخش دیگری نمایش داده نمی‌شوند. از اینجا می‌توانید آن‌ها را بازگردانید یا برای همیشه حذف کنید.</p>
      </header>

      <div className="weekly__toolbar">
        <div className="archive-tabs" role="tablist" aria-label="نوع مورد بایگانی‌شده">
          {TABS.map((x) => (
            <button key={x.kind} id={`archive-tab-${x.kind}`} type="button" role="tab" aria-selected={tab === x.kind}
              aria-controls="archive-panel" tabIndex={tab === x.kind ? 0 : -1} className={`kb-chip${tab === x.kind ? ' kb-chip--on' : ''}`}
              onClick={() => setTab(x.kind)} onKeyDown={onTabKey}>
              {x.label} <span>{ready ? formatNumber(lists[x.kind].length) : '…'}</span>
            </button>
          ))}
        </div>
        <div className="focus-page__search weekly__search">
          <label htmlFor="archive-search" className="visually-hidden">جستجو در بایگانی</label>
          <Icon name="search" size={20} />
          <input id="archive-search" type="search" placeholder="جستجو در بایگانی…" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      </div>

      {message && <p className={message.error ? 'state state--error' : 'state state--notice'} role={message.error ? 'alert' : 'status'}>{message.text}</p>}
      {error != null && <ErrorMessage error={error} onRetry={reload} />}
      {!ready && error == null && <Loading />}
      {ready && (
        <div id="archive-panel" role="tabpanel" aria-labelledby={`archive-tab-${tab}`} className="weekly-table" tabIndex={0}>
          {tab === 'goal' && (
            <Table head={['عنوان هدف', 'دوره', 'هدف والد', 'زیرهدف‌ها', 'تاریخ بایگانی', 'عملیات']} empty={lists.goal.length === 0} query={query}>
              {lists.goal.map((g) => (
                <tr key={g.id}>
                  <th scope="row" className="weekly-cell weekly-cell--title"><Link to={`/goals/${g.id}`}>{g.title}</Link></th>
                  <td className="weekly-cell">{PERIOD_LABELS[g.periodType]}</td>
                  <td className="weekly-cell">{goalName(g.parentGoalId) ?? <span className="ft-muted">—</span>}</td>
                  <td className="weekly-cell weekly-cell--center">{formatNumber((goals.data ?? []).filter((c) => c.parentGoalId === g.id).length)}</td>
                  <td className="weekly-cell">{archivedAt(g.archivedAt)}</td>
                  <td className="weekly-cell">{actions({ kind: 'goal', id: g.id, title: g.title })}</td>
                </tr>
              ))}
            </Table>
          )}
          {tab === 'focus' && (
            <Table head={['عنوان آیتم تمرکز', 'نوع', 'هدف', 'تسک‌ها', 'تاریخ بایگانی', 'عملیات']} empty={lists.focus.length === 0} query={query}>
              {lists.focus.map((i) => (
                <tr key={i.id}>
                  <th scope="row" className="weekly-cell weekly-cell--title"><Link to={`/focus/${i.id}`}>{i.title}</Link></th>
                  <td className="weekly-cell">{KIND_LABELS[i.kind]}</td>
                  <td className="weekly-cell">{goalName(i.goalId) ?? <span className="ft-muted">—</span>}</td>
                  <td className="weekly-cell weekly-cell--center">{formatNumber((tasks.data ?? []).filter((t) => t.focusItemId === i.id).length)}</td>
                  <td className="weekly-cell">{archivedAt(i.archivedAt)}</td>
                  <td className="weekly-cell">{actions({ kind: 'focus', id: i.id, title: i.title })}</td>
                </tr>
              ))}
            </Table>
          )}
          {tab === 'task' && (
            <Table head={['عنوان تسک', 'هدف', 'آیتم تمرکز', 'وضعیت', 'تاریخ بایگانی', 'عملیات']} empty={lists.task.length === 0} query={query}>
              {lists.task.map((t) => (
                <tr key={t.id}>
                  <th scope="row" className="weekly-cell weekly-cell--title">{t.title}</th>
                  <td className="weekly-cell">{goalName(t.effectiveGoalId) ?? <span className="ft-muted">—</span>}</td>
                  <td className="weekly-cell">{itemName(t.focusItemId) ?? <span className="ft-muted">—</span>}</td>
                  <td className="weekly-cell">{TASK_STATUS_LABELS[t.status]}</td>
                  <td className="weekly-cell">{archivedAt(t.archivedAt)}</td>
                  <td className="weekly-cell">{actions({ kind: 'task', id: t.id, title: t.title })}</td>
                </tr>
              ))}
            </Table>
          )}
        </div>
      )}

      <ConfirmDelete target={confirm} busy={busy !== null}
        impact={confirm ? deletionImpact(confirm.kind, confirm.id, goals.data ?? [], items.data ?? [], tasks.data ?? []) : null}
        onCancel={() => setConfirm(null)} onConfirm={() => confirm && void remove(confirm)} />
    </section>
  );
}

function Table({ head, empty, query, children }: { head: string[]; empty: boolean; query: string; children: ReactNode }) {
  return (
    <>
      <table>
        <thead>
          <tr>{head.map((h) => <th key={h} scope="col">{h}</th>)}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
      {empty && <EmptyState title={query ? 'موردی با این جستجو در بایگانی نیست.' : 'در این بخش چیزی بایگانی نشده است.'} />}
    </>
  );
}

function impactText(r: { goals: number; focusItems: number; tasks: number; detachedChildGoals: number }, past: boolean): string {
  const parts: string[] = [];
  if (r.goals && r.focusItems) parts.push(`${formatNumber(r.focusItems)} آیتم تمرکز`);
  if ((r.goals || r.focusItems) && r.tasks) parts.push(`${formatNumber(r.tasks)} تسک`);
  let text = parts.length ? `؛ همراه آن ${parts.join(' و ')}${past ? ' هم حذف شد' : ''}` : '';
  if (r.detachedChildGoals) text += `؛ ${formatNumber(r.detachedChildGoals)} زیرهدف ${past ? 'باقی ماند و' : 'باقی می‌ماند و'} مستقل ${past ? 'شد' : 'می‌شود'}`;
  return text;
}

function ConfirmDelete({ target, impact, busy, onCancel, onConfirm }: {
  target: Target | null; impact: ReturnType<typeof deletionImpact> | null; busy: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (target && !d.open) d.showModal();
    if (!target && d.open) d.close();
  }, [target]);
  const kindName = target?.kind === 'goal' ? 'هدف' : target?.kind === 'focus' ? 'آیتم تمرکز' : 'تسک';
  return (
    <dialog ref={ref} className="modal" aria-labelledby="delete-heading" aria-describedby="delete-desc" onClose={() => target && onCancel()}
      onClick={(e) => e.target === e.currentTarget && onCancel()}>
      {target && impact && (
        <div className="card form modal__body">
          <h2 id="delete-heading">حذف دائمی {kindName}</h2>
          <div id="delete-desc">
            <p>«{target.title}» برای همیشه حذف می‌شود و قابل بازگشت نیست.</p>
            {target.kind === 'goal' && (
              <ul className="archive-impact">
                <li>نتایج کلیدی، چک‌این‌ها، مایلستون‌ها و تاریخچهٔ این هدف</li>
                {impact.focusItems > 0 && <li>{formatNumber(impact.focusItems)} آیتم تمرکزِ این هدف</li>}
                {impact.tasks > 0 && <li>{formatNumber(impact.tasks)} تسکِ وصل به این هدف یا آیتم‌های تمرکزش (از برنامه‌های روزانه هم برداشته می‌شوند)</li>}
                {impact.detachedChildGoals > 0 && <li className="archive-impact__keep">{formatNumber(impact.detachedChildGoals)} زیرهدف حذف نمی‌شوند؛ فقط مستقل می‌شوند</li>}
              </ul>
            )}
            {target.kind === 'focus' && impact.tasks > 0 && (
              <ul className="archive-impact"><li>{formatNumber(impact.tasks)} تسکِ این آیتم تمرکز هم حذف می‌شوند (و از برنامه‌های روزانه برداشته می‌شوند)</li></ul>
            )}
            {target.kind === 'task' && <p className="meta">این تسک از همهٔ برنامه‌های روزانه هم برداشته می‌شود.</p>}
          </div>
          <div className="form__actions">
            <button type="button" className="button archive-btn--danger" disabled={busy} onClick={onConfirm}>{busy ? 'در حال حذف…' : 'حذف برای همیشه'}</button>
            <button type="button" className="button button--secondary" autoFocus onClick={onCancel}>انصراف</button>
          </div>
        </div>
      )}
    </dialog>
  );
}
