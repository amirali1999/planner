import { useState } from 'react';
import {
  allTimeZones,
  browserTimeZone,
  configuredTimeZone,
  currentTimeZone,
  dateInZone,
  setConfiguredTimeZone,
  timeInZone,
} from '../../shared/date/timeZone';
import { formatJalaliDateWithWeekday, toPersianDigits } from '../../shared/format';
import { usePageTitle } from '../../shared/ui/usePageTitle';

/**
 * Device preference: which IANA timezone decides "today" (Today, Dashboard, default dates) and how timestamps are
 * shown. Stored in this browser only; stored date-only values are never rewritten.
 */
export function SettingsPage() {
  usePageTitle('تنظیمات');
  const [choice, setChoice] = useState(configuredTimeZone() ?? '');
  const [saved, setSaved] = useState<string | null>(null);
  const zones = allTimeZones();
  const effective = choice || browserTimeZone();

  function save() {
    setConfiguredTimeZone(choice || null);
    setSaved(currentTimeZone());
  }

  return (
    <section aria-labelledby="settings-heading">
      <h1 id="settings-heading">تنظیمات</h1>
      <form
        className="card form"
        aria-labelledby="tz-heading"
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <h2 id="tz-heading">منطقه زمانی</h2>
        <p className="meta">
          تعیین می‌کند «امروز» کدام روز است و ساعت‌ها چگونه نمایش داده شوند. تاریخ‌های ذخیره‌شده (مثل بازه اهداف و موعدها)
          تغییر نمی‌کنند.
        </p>
        <div className="field">
          <label htmlFor="tz-select">منطقه زمانی (IANA)</label>
          <select id="tz-select" value={choice} onChange={(e) => { setChoice(e.target.value); setSaved(null); }}>
            <option value="">پیش‌فرض مرورگر ({browserTimeZone()})</option>
            {zones.map((z) => (
              <option key={z} value={z}>
                {z}
              </option>
            ))}
          </select>
        </div>
        <p className="meta" aria-live="polite">
          با این انتخاب، امروز: {formatJalaliDateWithWeekday(dateInZone(new Date(), effective))} — ساعت{' '}
          {toPersianDigits(timeInZone(new Date(), effective))}
        </p>
        <div className="form__actions">
          <button type="submit" className="button">
            ذخیره
          </button>
        </div>
        {saved && (
          <p className="state state--notice" role="status">
            ذخیره شد: {saved}
          </p>
        )}
      </form>
    </section>
  );
}

