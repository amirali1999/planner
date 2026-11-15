// API checks for scripts/smoke.sh. Phase "workflow" writes data and records every GET response;
// phase "verify" (after a backend restart) asserts the same GETs return identical bodies.
import { readFileSync, writeFileSync } from 'node:fs';

const [api, phase, stateFile] = process.argv.slice(2);
const DAY = '2026-11-03';
const EMPTY_DAY = '2026-11-10';
let checks = 0;

function check(condition, label, detail) {
  if (!condition) {
    console.error(`✘ ${label}${detail === undefined ? '' : `\n  got: ${JSON.stringify(detail)}`}`);
    process.exit(1);
  }
  checks += 1;
  console.log(`✔ ${label}`);
}

const ACCOUNT = { email: 'smoke@planner.local', password: 'smoke-password-123' };
let accessToken = null;

async function call(method, path, body) {
  const headers = body === undefined ? {} : { 'Content-Type': 'application/json' };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const response = await fetch(api + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  if (/combined/i.test(text)) check(false, `no blended progress in ${method} ${path}`, text);
  return { status: response.status, text, json: text ? JSON.parse(text) : undefined };
}

async function ok(method, path, body) {
  const r = await call(method, path, body);
  if (r.status < 200 || r.status > 201) check(false, `${method} ${path} succeeds`, r.json);
  return r.json;
}

/** workflow: create the account (first account = owner); verify: log in again after the restart. */
async function signIn() {
  check((await call('GET', '/goals')).status === 401, 'data endpoints refuse requests without a token');
  const path = phase === 'workflow' ? '/auth/register' : '/auth/login';
  const response = await fetch(api + path, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(ACCOUNT),
  });
  const json = await response.json();
  check(response.ok && json.tokenType === 'Bearer' && typeof json.accessToken === 'string', `${path} returns a Bearer token`, json);
  check(/HttpOnly/i.test(response.headers.get('set-cookie') ?? ''), 'refresh token is an httpOnly cookie');
  accessToken = json.accessToken;
}

if (phase === 'workflow' || phase === 'verify') {
  await signIn();
}

if (phase === 'workflow') {
  check((await ok('GET', '/health')).status === 'UP', 'health is UP');

  const goal = await ok('POST', '/goals', {
    title: 'یادگیری RAG', periodType: 'quarterly', periodStart: '2026-09-23', periodEnd: '2026-12-21', status: 'doing',
  });
  const unmeasured = await ok('POST', '/goals', {
    title: 'هدف بدون سنجه', periodType: 'monthly', periodStart: '2026-10-23', periodEnd: '2026-11-21',
  });
  const krA = await ok('POST', `/goals/${goal.id}/key-results`, { name: 'Recall@5', unit: '%', baseline: 20, target: 60 });
  const krB = await ok('POST', `/goals/${goal.id}/key-results`, { name: 'Latency', unit: 'ms', baseline: 100, target: 60 });
  check(krA.score === 0 && krA.currentSource === 'baseline', 'new KR starts at baseline with explicit 0%', krA);

  await ok('POST', `/key-results/${krA.id}/check-ins`, { value: 40, measuredAt: '2026-11-03T08:00:00Z' });
  await ok('POST', `/key-results/${krB.id}/check-ins`, { value: 80, measuredAt: '2026-11-03T08:00:00Z' });
  check((await ok('GET', `/key-results/${krB.id}`)).score === 50, 'decreasing KR 100→60 at 80 scores 50');

  const equal = await call('POST', `/goals/${goal.id}/key-results`, { name: 'x', baseline: 5, target: 5 });
  check(equal.status === 400 && equal.json.details.target === 'equals_baseline', 'target == baseline → 400', equal.json);

  const tasks = [];
  for (const title of ['کار ۱', 'کار ۲', 'کار ۳', 'کار ۴']) tasks.push(await ok('POST', '/tasks', { title, goalId: goal.id }));
  const standalone = await ok('POST', '/tasks', { title: 'خرید کتاب' });
  check(standalone.goalId === null, 'standalone task has no goal');

  const emptyPlan = await ok('GET', `/plans/daily?date=${DAY}`);
  check(emptyPlan.exists === false && emptyPlan.completionPercent === null, 'GET plan for an unplanned day: empty, null completion');
  for (const t of [tasks[0], tasks[3], standalone]) await ok('POST', `/plans/daily/${DAY}/items`, { taskId: t.id });
  const dup = await call('POST', `/plans/daily/${DAY}/items`, { taskId: standalone.id });
  check(dup.status === 409 && dup.json.code === 'duplicate_plan_task', 'same task twice on one day → 409 duplicate_plan_task', dup.json);

  for (const t of [tasks[0], tasks[1], tasks[2], standalone]) await ok('PATCH', `/tasks/${t.id}`, { status: 'done' });

  const progress = await ok('GET', `/goals/${goal.id}/progress`);
  check(progress.outcomeProgress === 50 && progress.executionProgress === 75,
    'Outcome 50 and Execution 75 reported separately (no 62.5)', progress);
  const none = await ok('GET', `/goals/${unmeasured.id}/progress`);
  check(none.outcomeProgress === null && none.executionProgress === null && none.outcomeSource === 'not_measured',
    'goal without KRs/tasks: both null (not 0)', none);

  const dashboard = await ok('GET', `/dashboard/today?date=${DAY}`);
  check(dashboard.today.completionPercent === 66.67 && dashboard.today.plannedCount === 3, 'today: 2 of 3 planned done = 66.67', dashboard.today);
  check(dashboard.goals.length === 2, 'dashboard lists both active goals');
  const emptyDash = await ok('GET', `/dashboard/today?date=${EMPTY_DAY}`);
  check(emptyDash.today.completionPercent === null, 'empty day: completionPercent null');
  check((await ok('GET', `/goals/${goal.id}`)).status === 'doing', 'goal status unchanged by progress');

  const missing = await call('GET', '/goals/00000000-0000-0000-0000-000000000000');
  check(missing.status === 404 && missing.json.code === 'not_found' && !/trace|exception/i.test(missing.text), '404 uses sanitized error body');
  check((await call('GET', '/health-check')).status === 404, 'unknown path → 404');

  const reads = [
    '/goals', `/goals/${goal.id}`, `/goals/${goal.id}/key-results`, `/key-results/${krA.id}`,
    `/key-results/${krA.id}/check-ins`, '/tasks', `/tasks/${standalone.id}`, `/plans/daily?date=${DAY}`,
    `/plans/daily?date=${EMPTY_DAY}`, `/goals/${goal.id}/progress`, `/goals/${unmeasured.id}/progress`,
    `/dashboard/today?date=${DAY}`, `/dashboard/today?date=${EMPTY_DAY}`,
  ];
  const snapshot = {};
  for (const path of reads) snapshot[path] = (await call('GET', path)).text;
  const again = (await call('GET', `/plans/daily?date=${EMPTY_DAY}`)).json;
  check(again.exists === false, 'reading an empty day did not create a plan');
  writeFileSync(stateFile, JSON.stringify(snapshot));
} else if (phase === 'verify') {
  const snapshot = JSON.parse(readFileSync(stateFile, 'utf8'));
  for (const [path, body] of Object.entries(snapshot)) {
    const r = await call('GET', path);
    check(r.status === 200 && r.text === body, `after restart identical: GET ${path}`);
  }
} else {
  console.error('usage: smoke-api.mjs <api> workflow|verify <stateFile>');
  process.exit(2);
}
console.log(`${checks} checks passed (${phase})`);
