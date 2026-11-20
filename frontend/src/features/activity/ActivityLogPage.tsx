import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from 'react';
import { useSearchParams } from 'react-router';
import { ApiError, apiRequest } from '../../shared/api/client';
import { addDays, isIsoDate, todayIso } from '../../shared/date/calendar';
import { formatNumber } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon, type IconName } from '../../shared/ui/Icon';
import { JalaliDateField } from '../../shared/ui/JalaliDateField';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { durationLabel, timeLabel } from '../planning/timeboxModel';
import { currentTimeZone, dateInZone, timeInZone } from '../../shared/date/timeZone';
import { tasksApi } from '../tasks/api';
import { FocusTimer } from '../dashboard/FocusTimer';
import { resizedActivityEnd, resizedActivityStart, useActivityResize, type ActivityResizeEdge } from './activityResize';

type Category = 'work' | 'study' | 'rest' | 'personal';
interface Entry { id: string; date: string; title: string; category: Category; startMinute: number; endMinute: number; taskId: string | null }
interface RunningTimer { title: string; startedAt: string; timeZone: string; plannedMinutes: number | null }
const categories: Record<Category, { label: string; icon: IconName }> = {
  work: { label: 'کار', icon: 'briefcase' }, study: { label: 'مطالعه', icon: 'book' },
  rest: { label: 'استراحت', icon: 'moon' }, personal: { label: 'شخصی', icon: 'status' },
};
const minutes = (value: string) => { const [h, m] = value.split(':').map(Number); return h * 60 + m; };
const inputTime = (value: number) => `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
const inputEndTime = (value: number) => value === 1440 ? '00:00' : inputTime(value);
const activityEndMinute = (value: string) => value === '00:00' || value === '24:00' ? 1440 : minutes(value);

export function ActivityLogPage() {
  usePageTitle('لاگ روزانه');
  const [params, setParams] = useSearchParams();
  const raw = params.get('date');
  const date = raw && isIsoDate(raw) ? raw : todayIso();
  const data = useApiData((signal) => apiRequest<Entry[]>(`/activity-logs?date=${date}`, { signal }), [date]);
  const [completedTimerEntries, setCompletedTimerEntries] = useState<Entry[]>([]);
  const serverEntries = data.data?.filter((entry) => entry.date === date) ?? [];
  const serverEntryIds = new Set(serverEntries.map((entry) => entry.id));
  const entries = [...serverEntries, ...completedTimerEntries.filter((entry) => entry.date === date && !serverEntryIds.has(entry.id))]
    .sort((a, b) => a.startMinute - b.startMinute);
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'false' }, signal), []);
  const timer = useApiData((signal) => apiRequest<RunningTimer | null>('/activity-timer', { signal }), []);
  const [timerOverride, setTimerOverride] = useState<RunningTimer | null | undefined>(undefined);
  const [now, setNow] = useState(() => new Date());
  useEffect(() => { const id = window.setInterval(() => setNow(new Date()), 1000); return () => clearInterval(id); }, []);
  const [taskId, setTaskId] = useState('');
  const [draft, setDraft] = useState(false);
  const [timerTitle, setTimerTitle] = useState('');
  const [timerTitlePromptOpen, setTimerTitlePromptOpen] = useState(false);
  const [selected, setSelected] = useState<Entry | null>(null);
  const editDialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { if (selected) editDialog.current?.showModal(); }, [selected]);
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState<Category>('work');
  const [start, setStart] = useState('09:00');
  const [end, setEnd] = useState('10:00');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState('');
  const [zoom, setZoom] = useState(1);
  const scroll = useRef<HTMLDivElement>(null);
  const [resizedStarts, setResizedStarts] = useState<Record<string, number>>({});
  const [resizedEnds, setResizedEnds] = useState<Record<string, number>>({});
  useEffect(() => { setResizedStarts({}); setResizedEnds({}); }, [data.data]);
  useEffect(() => {
    if (!data.data) return;
    const loaded = new Set(data.data.map((entry) => entry.id));
    setCompletedTimerEntries((previous) => previous.filter((entry) => !loaded.has(entry.id)));
  }, [data.data]);
  async function resizeEntry(id: string, edge: ActivityResizeEdge, minute: number) {
    if (id === 'draft') {
      if (edge === 'start') setStart(inputTime(minute)); else setEnd(inputEndTime(minute));
      return;
    }
    const entry = entries.find((item) => item.id === id);
    if (!entry || saving) return;
    const startMinute = edge === 'start' ? minute : resizedStarts[id] ?? entry.startMinute;
    const endMinute = edge === 'end' ? minute : resizedEnds[id] ?? entry.endMinute;
    setSaving(true); setError(null);
    if (edge === 'start') setResizedStarts((previous) => ({ ...previous, [id]: startMinute }));
    else setResizedEnds((previous) => ({ ...previous, [id]: endMinute }));
    try {
      await apiRequest(`/activity-logs/${id}`, { method: 'PUT', body: {
        date: entry.date, title: entry.title, category: entry.category,
        startMinute, taskId: entry.taskId, endMinute,
      } });
      setNotice(`ساعت ${edge === 'start' ? 'شروع' : 'پایان'} «${entry.title}» به ${timeLabel(minute)} تغییر کرد.`);
      data.reload();
    } catch (cause) {
      const revert = (previous: Record<string, number>) => { const next = { ...previous }; delete next[id]; return next; };
      if (edge === 'start') setResizedStarts(revert); else setResizedEnds(revert);
      setError(cause);
    } finally { setSaving(false); }
  }
  const resize = useActivityResize(scroll, zoom, date, (id, edge, minute) => { void resizeEntry(id, edge, minute); });
  const startOf = (entry: Entry) => resize.gesture?.id === entry.id && resize.gesture.edge === 'start'
    ? resize.gesture.preview : resizedStarts[entry.id] ?? entry.startMinute;
  const endOf = (entry: Entry) => resize.gesture?.id === entry.id && resize.gesture.edge === 'end'
    ? resize.gesture.preview : resizedEnds[entry.id] ?? entry.endMinute;
  const draftStart = resize.gesture?.id === 'draft' && resize.gesture.edge === 'start' ? resize.gesture.preview : minutes(start);
  const draftEnd = resize.gesture?.id === 'draft' && resize.gesture.edge === 'end' ? resize.gesture.preview : activityEndMinute(end);
  const resizeStartLimit = (startMinute: number, id?: string) => Math.max(0,
    ...entries.filter((item) => item.id !== id && startOf(item) < startMinute).map((item) => endOf(item)));
  const resizeEndLimit = (startMinute: number, id?: string) => Math.min(1440,
    ...entries.filter((item) => item.id !== id && startOf(item) > startMinute).map((item) => startOf(item)));
  function resizeHandle(edge: ActivityResizeEdge, id: string, startMinute: number, endMinute: number) {
    const limit = edge === 'start' ? resizeStartLimit(startMinute, id) : resizeEndLimit(startMinute, id);
    const value = edge === 'start' ? startMinute : endMinute;
    return <span className={`activity-resize activity-resize--${edge}`} role="slider" tabIndex={saving ? -1 : 0}
      aria-label={`تغییر ساعت ${edge === 'start' ? 'شروع' : 'پایان'}؛ کلید بالا و پایین یا کشیدن لبه`}
      aria-orientation="vertical"
      aria-valuemin={edge === 'start' ? limit : startMinute + 1} aria-valuemax={edge === 'start' ? endMinute - 1 : limit}
      aria-valuenow={value} aria-valuetext={timeLabel(value)}
      aria-disabled={saving} title={`لبهٔ ${edge === 'start' ? 'بالا' : 'پایین'} را برای تغییر ساعت ${edge === 'start' ? 'شروع' : 'پایان'} بکشید؛ ↑ و ↓: پنج دقیقه`}
      onClick={(event) => { event.preventDefault(); event.stopPropagation(); }}
      onPointerDown={(event) => { if (!saving) resize.begin(event, id, edge, startMinute, endMinute, limit); }}
      onKeyDown={(event) => {
        if (saving || !['ArrowDown', 'ArrowUp'].includes(event.key)) return;
        event.preventDefault(); event.stopPropagation();
        const delta = event.key === 'ArrowDown' ? 5 : -5;
        const next = edge === 'start' ? resizedActivityStart(startMinute, endMinute, delta, limit)
          : resizedActivityEnd(startMinute, endMinute, delta, limit);
        void resizeEntry(id, edge, next);
      }}><span aria-hidden="true" /></span>;
  }
  function reset() { setSelected(null); setTitle(''); setTaskId(''); setDraft(false); setError(null); }
  function changeDay(day: string) { setParams({ date: day }); reset(); setNotice(''); }
  function edit(entry: Entry) {
    setDraft(false); setTaskId(entry.taskId ?? '');
    setSelected(entry); setTitle(entry.title); setCategory(entry.category);
    setStart(inputTime(entry.startMinute)); setEnd(inputEndTime(entry.endMinute)); setError(null); setNotice('');
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    const startMinute = minutes(start); const endMinute = activityEndMinute(end);
    const conflict = entries.find((entry) => entry.id !== selected?.id && startOf(entry) < endMinute && endOf(entry) > startMinute);
    if (conflict) {
      setError(new ApiError(409, 'activity_log_overlap', `این بازه با فعالیت «${conflict.title}» هم‌پوشانی دارد.`, { activityLogId: conflict.id }));
      return;
    }
    setSaving(true); setError(null); setNotice('');
    try {
      await apiRequest(`/activity-logs${selected ? `/${selected.id}` : ''}`, { method: selected ? 'PUT' : 'POST',
        body: { date, title, category, taskId: taskId || null, startMinute, endMinute } });
      reset(); setNotice('فعالیت ذخیره شد.'); data.reload();
    } catch (cause) { setError(cause); } finally { setSaving(false); }
  }
  async function remove() {
    if (!selected || !window.confirm(`فعالیت «${selected.title}» حذف شود؟`)) return;
    setSaving(true); setError(null);
    try { await apiRequest(`/activity-logs/${selected.id}`, { method: 'DELETE' }); reset(); data.reload(); setNotice('فعالیت حذف شد.'); }
    catch (cause) { setError(cause); } finally { setSaving(false); }
  }
  function createAt(minute: number) {
    reset();
    const from = Math.max(0, Math.min(1439, Math.floor(minute)));
    const next = entries.find((entry) => entry.startMinute > from)?.startMinute ?? 1440;
    setStart(inputTime(from)); setEnd(inputEndTime(Math.min(from + 30, next))); setCategory('personal'); setDraft(true);
  }
  function gridClick(event: MouseEvent<HTMLDivElement>) {
    if ((event.target as HTMLElement).closest('button,form,input,select,label')) return;
    const minute = firstHour * 60 + Math.floor((event.clientY - event.currentTarget.getBoundingClientRect().top) / (52 * zoom) * 60 / 5) * 5;
    if (!entries.some((entry) => minute >= entry.startMinute && minute < entry.endMinute)) createAt(minute);
  }
  async function startTimer(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(null);
    try {
      const startedTimer = await apiRequest<RunningTimer>('/activity-timer', { method: 'POST', body: { title: timerTitle, timeZone: currentTimeZone() } });
      setTimerOverride(startedTimer); setTimerTitle(''); setTimerTitlePromptOpen(false); changeDay(todayIso());
    } catch (cause) { setError(cause); } finally { setSaving(false); }
  }
  async function stopTimer() {
    setSaving(true); setError(null);
    try {
      const completed = await apiRequest<Entry[]>('/activity-timer/stop', { method: 'POST' });
      setCompletedTimerEntries((previous) => [...previous, ...completed.filter((entry) => !previous.some((item) => item.id === entry.id))]);
      setTimerOverride(null); data.reload(); setNotice('زمان فعالیت ثبت شد.');
    } catch (cause) { setError(cause); throw cause; } finally { setSaving(false); }
  }
  async function cancelTimer() {
    if (!window.confirm('تایمر بدون ثبت زمان لغو شود؟')) return;
    setSaving(true); setError(null);
    try {
      await apiRequest('/activity-timer', { method: 'DELETE' });
      setTimerOverride(null); setNotice('تایمر بدون ثبت زمان لغو شد.');
    } catch (cause) { setError(cause); throw cause; } finally { setSaving(false); }
  }
  async function startPomodoro(activityTitle: string, plannedMinutes: number) {
    setSaving(true); setError(null);
    try {
      const startedTimer = await apiRequest<RunningTimer>('/activity-timer', {
        method: 'POST', body: { title: activityTitle, timeZone: currentTimeZone(), plannedMinutes },
      });
      setTimerOverride(startedTimer); changeDay(todayIso()); setNotice(`پومودوروی ${formatNumber(plannedMinutes)} دقیقه‌ای شروع شد.`);
    } catch (cause) { setError(cause); throw cause; } finally { setSaving(false); }
  }
  const running = timerOverride === undefined ? timer.data : timerOverride;
  const timerDate = running ? dateInZone(new Date(running.startedAt), running.timeZone) : null;
  const timerMinute = running ? minutes(timeInZone(new Date(running.startedAt), running.timeZone)) : 0;
  const timerEndMinute = running?.plannedMinutes ? Math.min(1440, timerMinute + running.plannedMinutes) : timerMinute;
  const nowMinute = minutes(timeInZone(now, currentTimeZone()));
  const isToday = date === dateInZone(now, currentTimeZone());
  const timerClock = (() => {
    if (!running) return '00:00:00';
    const seconds = Math.floor(Math.max(0, now.getTime() - Date.parse(running.startedAt)) / 1000);
    return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
      .map((part) => String(part).padStart(2, '0')).join(':');
  })();
  const firstHour = Math.min(6, ...(isToday ? [Math.floor(nowMinute / 60)] : []), ...(timerDate === date ? [Math.floor(timerMinute / 60)] : []), ...(draft ? [Math.floor(draftStart / 60)] : []), ...entries.map((entry) => Math.floor(startOf(entry) / 60)));
  const lastHour = Math.max(23, ...(draft ? [Math.ceil(draftEnd / 60)] : []), ...(isToday ? [Math.ceil(nowMinute / 60)] : []), ...(timerDate === date ? [Math.min(24, Math.max(Math.floor(timerMinute / 60) + 1, Math.ceil(timerEndMinute / 60)))] : []), ...entries.map((entry) => Math.ceil(endOf(entry) / 60)));
  const height = (lastHour - firstHour) * 52 * zoom;
  useEffect(() => {
    if (!running?.plannedMinutes || timerDate !== date) return;
    const frame = window.requestAnimationFrame(() => {
      const viewport = scroll.current;
      if (!viewport) return;
      const blockTop = (timerMinute - firstHour * 60) / 60 * 52 * zoom;
      viewport.scrollTo({ top: Math.max(0, blockTop - viewport.clientHeight / 2), behavior: 'smooth' });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [date, firstHour, running?.plannedMinutes, running?.startedAt, timerDate, timerMinute, zoom]);
  return <section className="activity-page" aria-labelledby="activity-heading">
    <header className="activity-header">
      <div><h1 id="activity-heading"><Icon name="calendar" size={30} /> لاگ روزانه</h1><p className="meta">ثبت فعالیت‌هایی که در هر بازهٔ زمانی انجام داده‌اید</p></div>
      <div className="activity-date"><button className="icon-button" aria-label="روز قبل" onClick={() => changeDay(addDays(date, -1))}><Icon name="chevron-right" /></button>
        <JalaliDateField label="روز فعالیت‌ها" value={date} onChange={(day) => day && changeDay(day)} />
        <button className="icon-button" aria-label="روز بعد" onClick={() => changeDay(addDays(date, 1))}><Icon name="chevron-left" /></button></div>
    </header>
    <div className="activity-layout">
      <section className="activity-timeline" aria-labelledby="timeline-heading">
        <header><h2 id="timeline-heading"><Icon name="layers" /> خط زمان فعالیت‌های روز</h2><div className="activity-zoom"><button className="icon-button" aria-label="کوچک‌نمایی" disabled={zoom <= .75} onClick={() => setZoom(zoom - .25)}><Icon name="minus" size={16} /></button><span>{formatNumber(zoom * 100)}٪</span><button className="icon-button" aria-label="بزرگ‌نمایی" disabled={zoom >= 2} onClick={() => setZoom(zoom + .25)}><Icon name="plus" size={16} /></button></div></header>
        <div ref={scroll} className={`activity-scroll${resize.gesture ? ' activity-scroll--resizing' : ''}`} tabIndex={0} aria-label="خط زمان قابل پیمایش">
          {error != null && !selected && !draft && <ErrorMessage error={error} />}
          <span className="visually-hidden" role="status">{notice}</span>
          {data.status === 'loading' && <Loading />}{data.status === 'error' && <ErrorMessage error={data.error} onRetry={data.reload} />}
          {data.status === 'success' && entries.length === 0 && <p className="activity-empty">برای ثبت فعالیت، روی ساعت دلخواه در خط زمان کلیک کنید.</p>}
          <div className="activity-grid" style={{ height }} onClick={gridClick}>
            {Array.from({ length: lastHour - firstHour + 1 }, (_, index) => firstHour + index).map((hour) => <div key={hour} className="activity-hour" style={{ top: (hour - firstHour) * 52 * zoom }}><button type="button" className="activity-hour-add" onClick={() => createAt(Math.min(hour * 60, 1439))} aria-label={`ثبت فعالیت ساعت ${timeLabel(hour * 60)}`} dir="ltr">{timeLabel(hour * 60)}</button></div>)}
            {isToday && <div className="activity-now-line" style={{ top: (nowMinute - firstHour * 60) / 60 * 52 * zoom }}><span>اکنون {timeLabel(nowMinute)}</span></div>}
            {running && timerDate === date && <div className={`activity-block activity-block--personal activity-running${running.plannedMinutes ? ' activity-running--pomodoro' : ''}`}
              style={{ top: (timerMinute - firstHour * 60) / 60 * 52 * zoom, height: running.plannedMinutes ? Math.max(12, (timerEndMinute - timerMinute) / 60 * 52 * zoom) : 28 }}>
              <strong>{running.title}</strong><span>{running.plannedMinutes ? `پومودورو · ${formatNumber(running.plannedMinutes)} دقیقه` : `در حال اجرا از ${timeLabel(timerMinute)}`}</span>
            </div>}
            {draft && <form className="activity-block activity-block--personal activity-inline" style={{ top: (draftStart - firstHour * 60) / 60 * 52 * zoom, height: Math.max(70, (draftEnd - draftStart) / 60 * 52 * zoom) }} onSubmit={save}>
              <input autoFocus aria-label="عنوان فعالیت در بلاک" placeholder="چه کاری انجام دادید؟" value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={200} onKeyDown={(event) => { if (event.key === 'Escape') reset(); }} />
              <label>از <input type="time" step={60} required value={start} onChange={(event) => setStart(event.target.value)} /></label>
              <label>تا <input type="time" step={60} required value={end} onChange={(event) => setEnd(event.target.value)} /></label>
              {error != null && <ErrorMessage error={error} />}<button className="button" disabled={saving}>ثبت</button><button type="button" className="button button--secondary" onClick={reset}>×</button>
              {resizeHandle('start', 'draft', draftStart, draftEnd)}
              {resizeHandle('end', 'draft', draftStart, draftEnd)}
            </form>}
            {entries.map((entry) => {
              const entryStart = startOf(entry); const entryEnd = endOf(entry);
              return <div key={entry.id} className={`activity-block activity-block--${entry.category}${selected?.id === entry.id ? ' activity-block--selected' : ''}`}
                style={{ top: (entryStart - firstHour * 60) / 60 * 52 * zoom, height: Math.max(12, (entryEnd - entryStart) / 60 * 52 * zoom) }}>
                <button type="button" className="activity-block__open" disabled={saving || Boolean(resize.gesture)}
                  onClick={() => edit({ ...entry, startMinute: entryStart, endMinute: entryEnd })} aria-label={`${entry.title}، ${timeLabel(entryStart)} تا ${timeLabel(entryEnd)}؛ ویرایش`}>
                  <span className="activity-block__time" dir="ltr">{timeLabel(entryStart)} – {timeLabel(entryEnd)}</span><strong>{entry.title}</strong><span className="activity-category"><Icon name={categories[entry.category].icon} size={15} />{categories[entry.category].label}</span>
                </button>
                {resizeHandle('start', entry.id, entryStart, entryEnd)}
                {resizeHandle('end', entry.id, entryStart, entryEnd)}
              </div>;
            })}
          </div>
        </div>
        <button className="activity-new" onClick={() => { createAt(firstHour * 60); }}><Icon name="plus" size={18} /> ثبت فعالیت جدید</button>
      </section>
      <aside className="activity-sidebar">
        <section className="activity-timer-card" aria-labelledby="activity-timer-heading">
          <h2 id="activity-timer-heading"><Icon name="timer" size={24} /> ثبت با تایمر</h2>

          {running && <p className="activity-timer-card__title">{running.title}</p>}
          {!running && timerTitlePromptOpen && <form id="activity-timer-title-form" className="activity-timer-card__prompt" onSubmit={startTimer}>
            <label htmlFor="timer-title">روی چه کاری می‌خواهید کار کنید؟</label>
            <input id="timer-title" autoFocus required maxLength={200} value={timerTitle}
              onChange={(event) => setTimerTitle(event.target.value)} placeholder="عنوان فعالیت" />
            <div>
              <button className="button" disabled={saving}>شروع تایمر</button>
              <button type="button" className="button button--secondary" disabled={saving}
                onClick={() => { setTimerTitlePromptOpen(false); setTimerTitle(''); setError(null); }}>انصراف</button>
            </div>
          </form>}

          <div className="activity-timer-card__state" aria-live="polite">
            <span aria-hidden="true" className={running ? 'is-running' : ''} />
            {running ? 'در حال شمارش' : timer.status === 'loading' ? 'در حال آماده‌سازی' : 'در حال شمارش از صفر'}
          </div>

          <output className="activity-timer-card__clock" dir="ltr" aria-label={`زمان سپری‌شده ${timerClock}`}>{timerClock}</output>

          <div className="activity-timer-card__actions">
            <button type="button" className="activity-timer-action activity-timer-action--start"
              aria-expanded={timerTitlePromptOpen} aria-controls="activity-timer-title-form"
              disabled={Boolean(running) || timerTitlePromptOpen || timer.status !== 'success' || saving}
              onClick={() => { setError(null); setTimerTitlePromptOpen(true); }}>
              <Icon name="play" size={21} /> شروع
            </button>
            <button type="button" className="activity-timer-action activity-timer-action--cancel" disabled={!running || saving}
              title="لغو تایمر بدون ثبت زمان" onClick={() => { void cancelTimer().catch(() => undefined); }}>
              <Icon name="reset" size={18} /> لغو تایمر
            </button>
            <button type="button" className="activity-timer-action activity-timer-action--finish"
              disabled={!running || saving} onClick={() => { void stopTimer().catch(() => undefined); }}>
              ثبت و پایان <Icon name="check-circle" size={20} />
            </button>
          </div>

          <div className="activity-timer-card__footer">
            <span><i className={running ? 'is-running' : ''} aria-hidden="true" /> وضعیت: {running ? 'در حال اجرا' : 'آماده برای شروع'}</span>
            <span><Icon name="clock" size={18} /> زمان شروع: <b dir="ltr">{running ? timeLabel(timerMinute) : '—'}</b></span>
          </div>

          <div className="activity-timer-card__feedback">
            {timer.status === 'loading' && <Loading />}
            {timer.status === 'error' && <ErrorMessage error={timer.error} onRetry={timer.reload} />}
            {error != null && !selected && !draft && <ErrorMessage error={error} />}
            {notice && <p role="status" className="meta">{notice}</p>}
          </div>
        </section>
        <FocusTimer activity={{
          session: running?.plannedMinutes ? {
            title: running.title, startedAt: running.startedAt, plannedMinutes: running.plannedMinutes,
          } : null,
          blocked: timer.status !== 'success' || saving || Boolean(running && running.plannedMinutes == null),
          onStart: startPomodoro,
          onStop: stopTimer,
          onCancel: cancelTimer,
        }} />
      </aside>
    </div>
    {selected && <dialog ref={editDialog} className="activity-edit-dialog" onCancel={reset} aria-label="ویرایش فعالیت">
        <form className="activity-editor form" onSubmit={save}>
          <h2><Icon name="layers" /> ویرایش فعالیت</h2>
          <div className="field"><label htmlFor="activity-title">چه کاری انجام دادید؟</label><input id="activity-title" value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={200} placeholder="مثلاً مطالعه فصل چهارم" /></div>
          <fieldset className="activity-categories"><legend>دسته‌بندی</legend><div className="activity-categories__options">{(Object.keys(categories) as Category[]).map((key) => <label key={key} className={category === key ? 'is-active' : ''}><input type="radio" name="activity-category" value={key} checked={category === key} onChange={() => setCategory(key)} /><Icon name={categories[key].icon} size={16} />{categories[key].label}</label>)}</div></fieldset>
          <div className="activity-times"><div className="field"><label htmlFor="activity-start">از ساعت</label><input id="activity-start" type="time" step={60} required value={start} onChange={(event) => setStart(event.target.value)} /></div><div className="field"><label htmlFor="activity-end">تا ساعت</label><input id="activity-end" type="time" step={60} required value={end} onChange={(event) => setEnd(event.target.value)} /></div></div>
          <div className="field"><label htmlFor="activity-task">زمان صرف‌شده روی تسک</label><select id="activity-task" value={taskId} onChange={(event) => setTaskId(event.target.value)}><option value="">بدون اتصال به تسک</option>{tasks.data?.map((task) => <option key={task.id} value={task.id}>{task.title}</option>)}</select></div>
          {tasks.status === 'error' && <ErrorMessage error={tasks.error} onRetry={tasks.reload} />}
          <p className="meta">مدت: {durationLabel(Math.max(0, activityEndMinute(end) - minutes(start)))}</p>
          {error != null && <ErrorMessage error={error} />}
          <button className="button" disabled={saving}>{saving ? 'در حال ذخیره…' : selected ? 'ذخیره تغییرات' : 'ثبت فعالیت'}</button>
          {selected && <div className="form__actions"><button type="button" className="button button--secondary" disabled={saving} onClick={reset}>انصراف</button><button type="button" className="button button--secondary" disabled={saving} onClick={remove}>حذف فعالیت</button></div>}
          {notice && <p role="status" className="meta">{notice}</p>}
        </form>

    </dialog>}
  </section>;
}
