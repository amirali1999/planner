import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from 'react';
import { useSearchParams } from 'react-router';
import { apiRequest } from '../../shared/api/client';
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
import { resizedActivityEnd, useActivityResize } from './activityResize';

type Category = 'work' | 'study' | 'rest' | 'personal';
interface Entry { id: string; date: string; title: string; category: Category; startMinute: number; endMinute: number; taskId: string | null }
interface RunningTimer { title: string; startedAt: string; timeZone: string }
const categories: Record<Category, { label: string; icon: IconName }> = {
  work: { label: 'کار', icon: 'briefcase' }, study: { label: 'مطالعه', icon: 'book' },
  rest: { label: 'استراحت', icon: 'moon' }, personal: { label: 'شخصی', icon: 'status' },
};
const minutes = (value: string) => { const [h, m] = value.split(':').map(Number); return h * 60 + m; };
const inputTime = (value: number) => `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;

export function ActivityLogPage() {
  usePageTitle('لاگ روزانه');
  const [params, setParams] = useSearchParams();
  const raw = params.get('date');
  const date = raw && isIsoDate(raw) ? raw : todayIso();
  const data = useApiData((signal) => apiRequest<Entry[]>(`/activity-logs?date=${date}`, { signal }), [date]);
  const entries = data.data?.filter((entry) => entry.date === date) ?? [];
  const tasks = useApiData((signal) => tasksApi.list({ archived: 'false' }, signal), []);
  const timer = useApiData((signal) => apiRequest<RunningTimer | null>('/activity-timer', { signal }), []);
  const [now, setNow] = useState(() => new Date());
  useEffect(() => { const id = window.setInterval(() => setNow(new Date()), 1000); return () => clearInterval(id); }, []);
  const [taskId, setTaskId] = useState('');
  const [draft, setDraft] = useState(false);
  const [timerTitle, setTimerTitle] = useState('');
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
  const [resizedEnds, setResizedEnds] = useState<Record<string, number>>({});
  useEffect(() => setResizedEnds({}), [data.data]);
  async function resizeEntry(id: string, endMinute: number) {
    if (id === 'draft') { setEnd(inputTime(endMinute)); return; }
    const entry = entries.find((item) => item.id === id);
    if (!entry || saving) return;
    setSaving(true); setError(null);
    setResizedEnds((previous) => ({ ...previous, [id]: endMinute }));
    try {
      await apiRequest(`/activity-logs/${id}`, { method: 'PUT', body: {
        date: entry.date, title: entry.title, category: entry.category,
        startMinute: entry.startMinute, taskId: entry.taskId, endMinute,
      } });
      setNotice(`ساعت پایان «${entry.title}» به ${timeLabel(endMinute)} تغییر کرد.`);
      data.reload();
    } catch (cause) {
      setResizedEnds((previous) => { const next = { ...previous }; delete next[id]; return next; });
      setError(cause);
    } finally { setSaving(false); }
  }
  const resize = useActivityResize(scroll, zoom, date, (id, endMinute) => { void resizeEntry(id, endMinute); });
  const endOf = (entry: Entry) => resize.gesture?.id === entry.id ? resize.gesture.preview : resizedEnds[entry.id] ?? entry.endMinute;
  const draftEnd = resize.gesture?.id === 'draft' ? resize.gesture.preview : minutes(end);
  const resizeLimit = (startMinute: number, id?: string) => Math.min(1440, ...entries.filter((item) => item.id !== id && item.startMinute > startMinute).map((item) => item.startMinute));
  function resizeHandle(id: string, startMinute: number, endMinute: number) {
    const limit = resizeLimit(startMinute, id);
    return <span className="activity-resize" role="slider" tabIndex={saving ? -1 : 0}
      aria-label="تغییر ساعت پایان؛ کلید بالا و پایین یا کشیدن لبه"
      aria-valuemin={startMinute + 1} aria-valuemax={limit} aria-valuenow={endMinute} aria-valuetext={timeLabel(endMinute)}
      aria-disabled={saving} title="لبهٔ پایین را برای تغییر مدت بکشید؛ ↑ و ↓: پنج دقیقه"
      onClick={(event) => { event.preventDefault(); event.stopPropagation(); }}
      onPointerDown={(event) => { if (!saving) resize.begin(event, id, startMinute, endMinute, limit); }}
      onKeyDown={(event) => {
        if (saving || !['ArrowDown', 'ArrowUp'].includes(event.key)) return;
        event.preventDefault(); event.stopPropagation();
        void resizeEntry(id, resizedActivityEnd(startMinute, endMinute, event.key === 'ArrowDown' ? 5 : -5, limit));
      }}><span aria-hidden="true" /></span>;
  }
  function reset() { setSelected(null); setTitle(''); setTaskId(''); setDraft(false); setError(null); }
  function changeDay(day: string) { setParams({ date: day }); reset(); setNotice(''); }
  function edit(entry: Entry) {
    setDraft(false); setTaskId(entry.taskId ?? '');
    setSelected(entry); setTitle(entry.title); setCategory(entry.category);
    setStart(inputTime(entry.startMinute)); setEnd(inputTime(entry.endMinute)); setError(null); setNotice('');
  }
  async function save(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(null); setNotice('');
    try {
      await apiRequest(`/activity-logs${selected ? `/${selected.id}` : ''}`, { method: selected ? 'PUT' : 'POST',
        body: { date, title, category, taskId: taskId || null, startMinute: minutes(start), endMinute: end === '24:00' ? 1440 : minutes(end) } });
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
    setStart(inputTime(from)); setEnd(inputTime(Math.min(from + 30, next))); setCategory('personal'); setDraft(true);
  }
  function gridClick(event: MouseEvent<HTMLDivElement>) {
    if ((event.target as HTMLElement).closest('button,form,input,select,label')) return;
    const minute = firstHour * 60 + Math.floor((event.clientY - event.currentTarget.getBoundingClientRect().top) / (52 * zoom) * 60 / 5) * 5;
    if (!entries.some((entry) => minute >= entry.startMinute && minute < entry.endMinute)) createAt(minute);
  }
  async function startTimer(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(null);
    try {
      await apiRequest('/activity-timer', { method: 'POST', body: { title: timerTitle, timeZone: currentTimeZone() } });
      setTimerTitle(''); changeDay(todayIso()); timer.reload();
    } catch (cause) { setError(cause); } finally { setSaving(false); }
  }
  async function stopTimer() {
    setSaving(true); setError(null);
    try { await apiRequest('/activity-timer/stop', { method: 'POST' }); timer.reload(); data.reload(); setNotice('زمان فعالیت ثبت شد.'); }
    catch (cause) { setError(cause); } finally { setSaving(false); }
  }
  const running = timer.data;
  const timerDate = running ? dateInZone(new Date(running.startedAt), running.timeZone) : null;
  const timerMinute = running ? minutes(timeInZone(new Date(running.startedAt), running.timeZone)) : 0;
  const nowMinute = minutes(timeInZone(now, currentTimeZone()));
  const isToday = date === dateInZone(now, currentTimeZone());
  const firstHour = Math.min(6, ...(isToday ? [Math.floor(nowMinute / 60)] : []), ...(timerDate === date ? [Math.floor(timerMinute / 60)] : []), ...entries.map((entry) => Math.floor(entry.startMinute / 60)));
  const lastHour = Math.max(23, ...(draft ? [Math.ceil(draftEnd / 60)] : []), ...(isToday ? [Math.ceil(nowMinute / 60)] : []), ...(timerDate === date ? [Math.min(24, Math.floor(timerMinute / 60) + 1)] : []), ...entries.map((entry) => Math.ceil(endOf(entry) / 60)));
  const height = (lastHour - firstHour) * 52 * zoom;
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
            {running && timerDate === date && <div className="activity-block activity-block--personal activity-running" style={{ top: (timerMinute - firstHour * 60) / 60 * 52 * zoom, height: 28 }}><strong>{running.title}</strong><span>در حال اجرا از {timeLabel(timerMinute)}</span></div>}
            {draft && <form className="activity-block activity-block--personal activity-inline" style={{ top: (minutes(start) - firstHour * 60) / 60 * 52 * zoom, height: Math.max(70, (draftEnd - minutes(start)) / 60 * 52 * zoom) }} onSubmit={save}>
              <input autoFocus aria-label="عنوان فعالیت در بلاک" placeholder="چه کاری انجام دادید؟" value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={200} onKeyDown={(event) => { if (event.key === 'Escape') reset(); }} />
              <label>از <input type="time" step={60} required value={start} onChange={(event) => setStart(event.target.value)} /></label>
              <label>تا <select required value={end} onChange={(event) => setEnd(event.target.value)}>{Array.from({ length: 1440 }, (_, i) => i + 1).map((minute) => <option key={minute} value={inputTime(minute)}>{timeLabel(minute)}</option>)}</select></label>
              {error != null && <ErrorMessage error={error} />}<button className="button" disabled={saving}>ثبت</button><button type="button" className="button button--secondary" onClick={reset}>×</button>
              {resizeHandle('draft', minutes(start), draftEnd)}
            </form>}
            {entries.map((entry) => <div key={entry.id} className={`activity-block activity-block--${entry.category}${selected?.id === entry.id ? ' activity-block--selected' : ''}`}
              style={{ top: (entry.startMinute - firstHour * 60) / 60 * 52 * zoom, height: Math.max(28, (endOf(entry) - entry.startMinute) / 60 * 52 * zoom) }}>
              <button type="button" className="activity-block__open" disabled={saving || Boolean(resize.gesture)}
                onClick={() => edit({ ...entry, endMinute: endOf(entry) })} aria-label={`${entry.title}، ${timeLabel(entry.startMinute)} تا ${timeLabel(endOf(entry))}؛ ویرایش`}>
                <span className="activity-block__time" dir="ltr">{timeLabel(entry.startMinute)} – {timeLabel(endOf(entry))}</span><strong>{entry.title}</strong><span className="activity-category"><Icon name={categories[entry.category].icon} size={15} />{categories[entry.category].label}</span>
              </button>
              {resizeHandle(entry.id, entry.startMinute, endOf(entry))}
            </div>)}
          </div>
        </div>
        <button className="activity-new" onClick={() => { createAt(firstHour * 60); }}><Icon name="plus" size={18} /> ثبت فعالیت جدید</button>
      </section>
      <aside className="activity-sidebar">
        <form className="activity-timer-card form" onSubmit={startTimer}>
          <h2><Icon name="timer" /> ثبت با تایمر</h2>
          {running ? <>
            <strong className="activity-timer-card__title">{running.title}</strong>
            <output className="activity-timer-card__clock" dir="ltr">{(() => {
              const seconds = Math.floor(Math.max(0, now.getTime() - Date.parse(running.startedAt)) / 1000);
              return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60].map((part) => String(part).padStart(2, '0')).join(':');
            })()}</output>
            <p className="meta">شروع از {timeLabel(timerMinute)}</p>
            <button type="button" className="button" disabled={saving} onClick={stopTimer}>توقف و ثبت</button>
          </> : <>
            <label htmlFor="timer-title">چه کاری می‌خواهید انجام دهید؟</label>
            <input id="timer-title" required maxLength={200} value={timerTitle} onChange={(event) => setTimerTitle(event.target.value)} placeholder="عنوان فعالیت" />
            <button className="button" disabled={timer.status !== 'success' || saving}>شروع</button>
          </>}
          {timer.status === 'loading' && <Loading />}
          {timer.status === 'error' && <ErrorMessage error={timer.error} onRetry={timer.reload} />}
          {error != null && !selected && !draft && <ErrorMessage error={error} />}
          {notice && <p role="status" className="meta">{notice}</p>}
        </form>
      </aside>
    </div>
    {selected && <dialog ref={editDialog} className="activity-edit-dialog" onCancel={reset} aria-label="ویرایش فعالیت">
        <form className="activity-editor form" onSubmit={save}>
          <h2><Icon name="layers" /> ویرایش فعالیت</h2>
          <div className="field"><label htmlFor="activity-title">چه کاری انجام دادید؟</label><input id="activity-title" value={title} onChange={(event) => setTitle(event.target.value)} required maxLength={200} placeholder="مثلاً مطالعه فصل چهارم" /></div>
          <fieldset className="activity-categories"><legend>دسته‌بندی</legend>{(Object.keys(categories) as Category[]).map((key) => <label key={key} className={category === key ? 'is-active' : ''}><input type="radio" name="activity-category" value={key} checked={category === key} onChange={() => setCategory(key)} /><Icon name={categories[key].icon} size={16} />{categories[key].label}</label>)}</fieldset>
          <div className="activity-times"><div className="field"><label htmlFor="activity-start">از ساعت</label><input id="activity-start" type="time" step={60} required value={start} onChange={(event) => setStart(event.target.value)} /></div><div className="field"><label htmlFor="activity-end">تا ساعت</label><select id="activity-end" value={end} onChange={(event) => setEnd(event.target.value)}>{Array.from({ length: 1440 }, (_, i) => i + 1).map((minute) => <option key={minute} value={inputTime(minute)}>{timeLabel(minute)}</option>)}</select></div></div>
          <div className="field"><label htmlFor="activity-task">زمان صرف‌شده روی تسک</label><select id="activity-task" value={taskId} onChange={(event) => setTaskId(event.target.value)}><option value="">بدون اتصال به تسک</option>{tasks.data?.map((task) => <option key={task.id} value={task.id}>{task.title}</option>)}</select></div>
          {tasks.status === 'error' && <ErrorMessage error={tasks.error} onRetry={tasks.reload} />}
          <p className="meta">مدت: {durationLabel(Math.max(0, minutes(end) - minutes(start)))}</p>
          {error != null && <ErrorMessage error={error} />}
          <button className="button" disabled={saving}>{saving ? 'در حال ذخیره…' : selected ? 'ذخیره تغییرات' : 'ثبت فعالیت'}</button>
          {selected && <div className="form__actions"><button type="button" className="button button--secondary" disabled={saving} onClick={reset}>انصراف</button><button type="button" className="button button--secondary" disabled={saving} onClick={remove}>حذف فعالیت</button></div>}
          {notice && <p role="status" className="meta">{notice}</p>}
        </form>

    </dialog>}
  </section>;
}
