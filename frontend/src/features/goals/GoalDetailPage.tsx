import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { todayIso } from '../../shared/date/calendar';
import { formatJalaliRange, formatNumber, formatPercent } from '../../shared/format';
import type { GoalProgress, OutcomeSource } from '../dashboard/types';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { PROGRESS_NOTE, ProgressPair } from '../dashboard/ProgressPair';
import { progressApi } from '../dashboard/api';
import { goalsApi } from './api';
import { focusApi } from '../focus/api';
import { FOCUS_STATUS_LABELS, KIND_LABELS } from '../focus/labels';
import { MilestonesSection } from '../milestones/MilestonesSection';
import { TASK_STATUS_LABELS } from '../tasks/labels';
import type { GoalTreeNode } from './types';
import { DeadlineBadge } from './DeadlineBadge';
import { GoalEditForm } from './GoalEditForm';
import { GoalLifecyclePanel } from './GoalLifecyclePanel';
import { KeyResultCard } from './KeyResultCard';
import { KeyResultCreateForm } from './KeyResultCreateForm';
import { GoalStatusSelect } from './GoalStatusSelect';
import { PERIOD_LABELS, STATUS_LABELS } from './labels';

export function GoalDetailPage() {
  const { goalId = '' } = useParams();
  const goal = useApiData((signal) => goalsApi.get(goalId, signal), [goalId]);
  const allGoals = useApiData((signal) => goalsApi.list('all', signal), [goalId]);
  const keyResults = useApiData((signal) => goalsApi.listKeyResults(goalId, signal), [goalId]);
  const allKeyResults = useApiData((signal) => goalsApi.listKeyResults(goalId, signal, true), [goalId]);
  const progress = useApiData((signal) => progressApi.goal(goalId, signal), [goalId]);
  // children with their own measurements come from the tree (2 batched queries server-side, no N+1)
  const tree = useApiData((signal) => goalsApi.tree('all', signal), [goalId]);
  const tasks = useApiData((signal) => goalsApi.tasks(goalId, signal), [goalId]);
  const focusItems = useApiData((signal) => focusApi.list({ goalId, archived: 'false' }, signal), [goalId]);
  const [editing, setEditing] = useState(false);
  usePageTitle(goal.data?.title ?? 'جزئیات هدف');

  if (goal.status === 'loading' && !goal.data) return <Loading />;
  if (goal.status === 'error' && !goal.data) {
    const notFound = goal.error instanceof ApiError && goal.error.status === 404;
    return (
      <section>
        <h1>{notFound ? 'هدف پیدا نشد' : 'خطا در بارگذاری هدف'}</h1>
        <ErrorMessage error={goal.error} onRetry={notFound ? undefined : goal.reload} />
        <p>
          <Link to="/goals">بازگشت به فهرست اهداف</Link>
        </p>
      </section>
    );
  }
  const g = goal.data;
  if (!g) return null;

  const goals = allGoals.data ?? [];
  const parent = g.parentGoalId ? goals.find((x) => x.id === g.parentGoalId) : undefined;
  const children = goals.filter((x) => x.parentGoalId === g.id);
  const activeChildren = children.filter((x) => !x.archivedAt);
  const childNodes = findNode(tree.data?.roots ?? [], g.id)?.children ?? [];
  const archivedKeyResults = (allKeyResults.data ?? []).filter((kr) => kr.archivedAt);
  const keyResultChanged = () => {
    keyResults.reload();
    allKeyResults.reload();
    progress.reload();
  };
  const refresh = () => {
    goal.reload();
    allGoals.reload();
    progress.reload();
    tree.reload();
  };

  return (
    <section aria-labelledby="goal-heading">
      <p>
        <Link to="/goals">→ همه اهداف</Link>
      </p>
      <div className="page-header">
        <h1 id="goal-heading">{g.title}</h1>
        {!g.archivedAt && !editing && (
          <button type="button" className="button" onClick={() => setEditing(true)}>
            ویرایش
          </button>
        )}
      </div>
      <p className="meta">
        <span className="badge">{PERIOD_LABELS[g.periodType]}</span>
        {g.archivedAt ? (
          <span className={`badge badge--status-${g.status}`}>{STATUS_LABELS[g.status]}</span>
        ) : (
          !editing && <GoalStatusSelect goalId={g.id} goalTitle={g.title} status={g.status} onChanged={refresh} />
        )}
        {g.archivedAt && <span className="badge badge--muted">بایگانی‌شده</span>}
      </p>
      <p className="meta">وضعیت را خودتان تعیین می‌کنید؛ رسیدن نتیجه یا اجرا به ۱۰۰٪ آن را تغییر نمی‌دهد.</p>

      {g.warnings && g.warnings.length > 0 && (
        <div className="state state--notice" role="status" aria-label="هشدارهای تاریخ">
          <p className="state__title">هشدار (ذخیره انجام شده است):</p>
          <ul>
            {g.warnings.map((w, i) => (
              <li key={i}>
                {w.message}
                {typeof w.details?.childTitle === 'string' && <> — «{w.details.childTitle}»</>}
              </li>
            ))}
          </ul>
        </div>
      )}

      {editing ? (
        <GoalEditForm
          goal={g}
          allGoals={goals}
          onCancel={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            refresh();
          }}
        />
      ) : (
        <>
          <dl className="facts">
            <div>
              <dt>بازه</dt>
              <dd>{formatJalaliRange(g.periodStart, g.periodEnd)}</dd>
            </div>
            {g.deadline && (
              <div>
                <dt>موعد</dt>
                <dd>
                  <DeadlineBadge deadline={g.deadline} status={g.status} today={todayIso()} />
                </dd>
              </div>
            )}
            <div>
              <dt>هدف والد</dt>
              <dd>
                {parent ? (
                  <>
                    <Link to={`/goals/${parent.id}`}>{parent.title}</Link>
                    {parent.archivedAt && <span className="badge badge--muted">بایگانی‌شده</span>}
                  </>
                ) : g.parentGoalId ? (
                  '—'
                ) : (
                  'ندارد (هدف مستقل)'
                )}
              </dd>
            </div>
          </dl>
          {g.description && <p className="description">{g.description}</p>}
        </>
      )}

      {children.length > 0 && (
        <section aria-labelledby="children-heading" className="section">
          <h2 id="children-heading">زیرهدف‌ها</h2>
          {progress.data && <p className="meta">{childRollupNote(progress.data.outcomeSource)}</p>}
          <ul className="child-list">
            {children.map((c) => {
              const measured = childNodes.find((n) => n.id === c.id);
              const inRollup = progress.data?.childOutcomes.some((o) => o.goalId === c.id);
              return (
                <li key={c.id}>
                  <Link to={`/goals/${c.id}`}>{c.title}</Link> <span className="badge">{PERIOD_LABELS[c.periodType]}</span>
                  {c.archivedAt && <span className="badge badge--muted">بایگانی‌شده</span>}
                  <span className="meta"> {formatJalaliRange(c.periodStart, c.periodEnd)}</span>
                  {measured && (
                    <span className="meta">
                      {' · '}نتیجه: {measured.outcomeProgress == null ? 'سنجیده نشده' : formatPercent(measured.outcomeProgress)} · اجرا:{' '}
                      {measured.executionProgress == null ? 'سنجیده نشده' : formatPercent(measured.executionProgress)}
                    </span>
                  )}
                  {progress.data && !inRollup && (
                    <span className="badge badge--muted">خارج از محاسبه والد (بایگانی‌شده یا رهاشده)</span>
                  )}
                </li>
              );
            })}
          </ul>
          <p className="meta">
            <Link to="/goals/tree">نمایش در درخت اهداف</Link>
          </p>
        </section>
      )}

      <section aria-labelledby="progress-heading" className="section">
        <h2 id="progress-heading">پیشرفت</h2>
        {progress.status === 'loading' && !progress.data && <Loading />}
        {progress.status === 'error' && <ErrorMessage error={progress.error} onRetry={progress.reload} />}
        {progress.data && <ProgressPair progress={progress.data} />}
        {progress.data && progress.data.keyResults.length > 0 && <OutcomeBreakdown progress={progress.data} />}
        <p className="meta">{PROGRESS_NOTE}</p>
      </section>

      <section aria-labelledby="kr-heading" className="section">
        <h2 id="kr-heading">نتایج کلیدی</h2>
        <p className="meta">امتیاز هر نتیجه کلیدی از آخرین اندازه‌گیری ثبت‌شده در سرور محاسبه می‌شود.</p>
        {keyResults.status === 'loading' && !keyResults.data && <Loading />}
        {keyResults.status === 'error' && <ErrorMessage error={keyResults.error} onRetry={keyResults.reload} />}
        {keyResults.data && keyResults.data.length === 0 && (
          <EmptyState
            title={
              progress.data?.outcomeSource === 'child_rollup'
                ? 'این هدف نتیجه کلیدی مستقیم ندارد؛ پیشرفت نتیجه از زیرهدف‌ها جمع‌بندی می‌شود.'
                : 'این هدف هنوز نتیجه کلیدی ندارد؛ پیشرفت نتیجه «سنجیده نشده» است.'
            }
          >
            {!g.archivedAt && <p>با فرم زیر یک یا چند نتیجه کلیدی قابل اندازه‌گیری اضافه کنید.</p>}
          </EmptyState>
        )}
        {keyResults.data && keyResults.data.length > 0 && (
          <div className="kr-list">
            {keyResults.data.map((kr) => (
              <KeyResultCard key={kr.id} initial={kr} onChanged={keyResultChanged} readOnly={Boolean(g.archivedAt)} />
            ))}
          </div>
        )}
        {!g.archivedAt && (
          <KeyResultCreateForm
            goalId={g.id}
            onCreated={keyResultChanged}
          />
        )}
        {archivedKeyResults.length > 0 && (
          <details className="archived-krs">
            <summary>{`نتایج کلیدی بایگانی‌شده (${formatNumber(archivedKeyResults.length)}) — فقط برای مشاهده تاریخچه`}</summary>
            <div className="kr-list">
              {archivedKeyResults.map((kr) => (
                <KeyResultCard key={kr.id} initial={kr} readOnly />
              ))}
            </div>
          </details>
        )}
      </section>

      <MilestonesSection goalId={g.id} readOnly={Boolean(g.archivedAt)} />

      <section aria-labelledby="tasks-heading" className="section">
        <h2 id="tasks-heading">کارهای مرتبط</h2>
        <p className="meta">
          کارهایی که هدف موثرشان این هدف است: پیوند مستقیم، یا از طریق آیتم تمرکزی که به این هدف وصل است (پیوند مستقیم مقدم است).
          هر کار فقط یک بار شمرده می‌شود. پیشرفت اجرا علاوه بر این فهرست، کارهای زیرهدف‌های فعال را هم (هر کار یک بار) در بر دارد.
        </p>
        {tasks.status === 'loading' && !tasks.data && <Loading />}
        {tasks.status === 'error' && <ErrorMessage error={tasks.error} onRetry={tasks.reload} />}
        {tasks.data && tasks.data.length === 0 && (
          <EmptyState title="کار فعالی به این هدف وصل نیست؛ پیشرفت اجرا «سنجیده نشده» است.">
            <p>
              در بخش <Link to="/tasks">کارها</Link> این هدف یا یکی از آیتم‌های تمرکز آن را برای کار انتخاب کنید.
            </p>
          </EmptyState>
        )}
        {tasks.data && tasks.data.length > 0 && (
          <ul className="child-list">
            {tasks.data.map((t) => (
              <li key={t.id}>
                {t.title} <span className={`badge task-status--${t.status}`}>{TASK_STATUS_LABELS[t.status]}</span>
                <span className="meta">{t.effectiveGoalSource === 'focus_item' ? ' · از طریق آیتم تمرکز' : ' · پیوند مستقیم'}</span>
                {t.warnings.length > 0 && <span className="badge badge--warning">آیتم تمرکز آن به هدف دیگری وصل است</span>}
                {t.blockedReason && <span className="task__blocked"> · مانع: {t.blockedReason}</span>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="focus-heading" className="section">
        <h2 id="focus-heading">آیتم‌های تمرکز</h2>
        {focusItems.status === 'loading' && !focusItems.data && <Loading />}
        {focusItems.status === 'error' && <ErrorMessage error={focusItems.error} onRetry={focusItems.reload} />}
        {focusItems.data && focusItems.data.length === 0 && (
          <EmptyState title="آیتم تمرکزی به این هدف وصل نیست.">
            <p>
              در بخش <Link to="/focus">تمرکز</Link> هنگام ساخت یا ویرایش یک پروژه، کتاب یا دوره، این هدف را انتخاب کنید.
            </p>
          </EmptyState>
        )}
        {focusItems.data && focusItems.data.length > 0 && (
          <ul className="child-list">
            {focusItems.data.map((f) => (
              <li key={f.id}>
                <Link to={`/focus/${f.id}`}>{f.title}</Link> <span className="badge">{KIND_LABELS[f.kind]}</span>
                <span className={`badge badge--status-${f.status}`}>{FOCUS_STATUS_LABELS[f.status]}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="lifecycle-heading" className="section">
        <h2 id="lifecycle-heading">بایگانی</h2>
        <GoalLifecyclePanel
          goal={g}
          activeChildCount={activeChildren.length}
          activeTaskCount={progress.data?.activeTaskCount}
          keyResultCount={keyResults.data?.length}
          onChanged={refresh}
        />
      </section>
    </section>
  );
}

function findNode(nodes: GoalTreeNode[], id: string): GoalTreeNode | undefined {
  for (const n of nodes) {
    if (n.id === id) return n;
    const inChild = findNode(n.children, id);
    if (inChild) return inChild;
  }
  return undefined;
}

/** Per-KR share of Outcome, exactly as computed by the backend (formatting only). */
function OutcomeBreakdown({ progress }: { progress: GoalProgress }) {
  return (
    <table className="breakdown">
      <caption>سهم هر نتیجه کلیدی از پیشرفت نتیجه (میانگین وزنی؛ مجموع سهم‌ها = پیشرفت نتیجه)</caption>
      <thead>
        <tr>
          <th scope="col">نتیجه کلیدی</th>
          <th scope="col">امتیاز</th>
          <th scope="col">وزن</th>
          <th scope="col">سهم از وزن</th>
          <th scope="col">سهم از نتیجه</th>
        </tr>
      </thead>
      <tbody>
        {progress.keyResults.map((k) => (
          <tr key={k.keyResultId}>
            <th scope="row">{k.name}</th>
            <td>{formatPercent(k.score)}</td>
            <td>{formatNumber(k.weight)}</td>
            <td>{formatPercent(k.weightPercent)}</td>
            <td>{formatNumber(k.contribution)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function childRollupNote(source: OutcomeSource): string {
  if (source === 'own_key_results') {
    return 'پیشرفت نتیجه این هدف فقط از نتایج کلیدی خودش است؛ پیشرفت زیرهدف‌ها جداگانه نمایش داده می‌شود و در آن وارد نمی‌شود.';
  }
  return 'این هدف نتیجه کلیدی فعال ندارد؛ پیشرفت نتیجه‌اش میانگین ساده زیرهدف‌های فعالِ سنجیده‌شده است. زیرهدف‌های بایگانی‌شده یا رهاشده (و شاخه زیرشان) لحاظ نمی‌شوند. کارهای همه زیرهدف‌های فعال در پیشرفت اجرا شمرده می‌شوند.';
}
