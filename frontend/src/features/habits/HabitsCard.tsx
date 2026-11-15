import { useEffect, useState } from 'react';
import { ApiError } from '../../shared/api/client';
import { isoToJalali, todayIso, type IsoDate } from '../../shared/date/calendar';
import { formatJalaliDateWithWeekday, formatNumber, toPersianDigits } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { ExpandingAdd } from '../../shared/ui/ExpandingAdd';
import { MenuButton } from '../../shared/ui/MenuButton';
import { useApiData } from '../../shared/ui/useApiData';
import { habitsApi } from './api';
import { doneCount, withDay } from './habitsModel';
import type { Habit } from './types';

/**
 * «عادت‌ها» (dashboard): each habit with a checkbox for today and seven circles for Saturday–Friday of the shown week —
 * filled = done that day. Ticks save immediately (optimistic, reverted on error); future days cannot be ticked.
 */
export function HabitsCard({ week }: { week: { date: IsoDate; label: string }[] }) {
  const from = week[0].date;
  const to = week[6].date;
  const today = todayIso();
  const data = useApiData((signal) => habitsApi.list(from, to, signal), [from]);
  const [habits, setHabits] = useState<Habit[] | null>(null);
  useEffect(() => setHabits(data.data ?? null), [data.data]);
  const [error, setError] = useState<string | null>(null);
  async function toggle(h: Habit, date: IsoDate) {
    const done = !h.doneDates.includes(date);
    setError(null);
    setHabits((list) => list?.map((x) => (x.id === h.id ? withDay(x, date, done) : x)) ?? null);
    try {
      await habitsApi.setDone(h.id, date, done);
    } catch (e) {
      setHabits((list) => list?.map((x) => (x.id === h.id ? withDay(x, date, !done) : x)) ?? null);
      setError(e instanceof ApiError ? e.message : 'ذخیره نشد.');
    }
  }
  async function archive(h: Habit) {
    await habitsApi.update(h.id, { archived: true });
    data.reload();
  }

  return (
    <section className="dash-card dash-card--habits" aria-labelledby="habits-card">
      <div className="dash-card__header">
        <h2 id="habits-card" className="dash-card__title">
          <Icon name="layers" size={24} className="dash-card__icon" />
          عادت‌ها
        </h2>
        <span className="habit-days" aria-hidden="true">
          {week.map((d) => (
            <span key={d.date} className={`habit-days__name${d.date === today ? ' habit-days__name--today' : ''}`}>{d.label.charAt(0)}</span>
          ))}
        </span>
      </div>
      <div className="dash-card__body" tabIndex={0} role="group" aria-label="عادت‌های این هفته">
        {data.status === 'error' && <ErrorMessage error={data.error} onRetry={data.reload} />}
        {!habits && data.status !== 'error' && <Loading />}
        {habits && habits.length === 0 && <p className="dash-empty">هنوز عادتی ندارید؛ با «افزودن عادت» اولین را بسازید.</p>}
        {error && <p className="field__error" role="alert">{error}</p>}
        {habits && habits.length > 0 && (
          <ul className="habits">
            {habits.map((h) => {
              const doneToday = h.doneDates.includes(today);
              const todayInWeek = week.some((d) => d.date === today);
              return (
                <li key={h.id} className="habit">
                  {todayInWeek && (
                    <input type="checkbox" className="check" checked={doneToday} onChange={() => toggle(h, today)}
                      aria-label={`«${h.title}» امروز انجام شد`} />
                  )}
                  <span className="habit__title" title={`${formatNumber(doneCount(h, week.map((d) => d.date)))} از ۷ روز این هفته`}>{h.title}</span>
                  <span className="habit__days" role="group" aria-label={`روزهای هفتهٔ «${h.title}»`}>
                    {week.map((d) => {
                      const done = h.doneDates.includes(d.date);
                      const future = d.date > today;
                      return (
                        <button key={d.date} type="button" disabled={future} onClick={() => toggle(h, d.date)}
                          className={`habit-dot${done ? ' habit-dot--done' : ''}${d.date === today ? ' habit-dot--today' : ''}`}
                          aria-pressed={done} title={`${formatJalaliDateWithWeekday(d.date)}${future ? ' (هنوز نرسیده)' : ''}`}
                          aria-label={`${d.label} ${toPersianDigits(isoToJalali(d.date).jd)}: ${done ? 'انجام شد' : 'انجام نشد'}`}>
                          {done && <Icon name="check" size={13} />}
                        </button>
                      );
                    })}
                  </span>
                  <MenuButton label={`گزینه‌های «${h.title}»`} items={[
                    { label: 'بایگانی عادت', danger: true, confirm: `عادت «${h.title}» بایگانی شود؟ روزهای ثبت‌شده‌اش می‌ماند.`, onSelect: () => archive(h) },
                  ]} />
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <ExpandingAdd label="افزودن عادت" inputLabel="عنوان عادت جدید" placeholder="مثلاً مطالعه روزانه (۳۰ دقیقه)"
        onAdd={async (t) => { await habitsApi.create(t); data.reload(); }} />
    </section>
  );
}
