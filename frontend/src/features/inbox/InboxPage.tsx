import { useState } from 'react';
import { formatNumber } from '../../shared/format';
import { EmptyState, ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { useApiData } from '../../shared/ui/useApiData';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { inboxApi } from './api';
import { InboxItemRow } from './InboxItemRow';
import { QuickCaptureForm } from './QuickCaptureForm';
import type { InboxView } from './types';

export function InboxPage() {
  usePageTitle('صندوق ورودی');
  const [filter, setFilter] = useState<InboxView>('unprocessed');
  const items = useApiData((signal) => inboxApi.list(filter, signal), [filter]);

  return (
    <section aria-labelledby="inbox-heading">
      <h1 id="inbox-heading">صندوق ورودی</h1>
      <p className="meta">
        هر ایده یا کاری را سریع ثبت کنید. چیزی خودکار دسته‌بندی نمی‌شود؛ پردازش و تبدیل به کار، هدف یا آیتم تمرکز بعداً و
        با تصمیم خودتان انجام می‌شود.
      </p>
      <div className="card">
        <QuickCaptureForm autoFocus onCaptured={items.reload} />
      </div>

      <div className="filters">
        <div className="field field--compact">
          <label htmlFor="inbox-filter">نمایش</label>
          <select id="inbox-filter" value={filter} onChange={(e) => setFilter(e.target.value as InboxView)}>
            <option value="unprocessed">پردازش‌نشده (صف)</option>
            <option value="converted">تبدیل‌شده به کار/هدف/تمرکز</option>
            <option value="reference">مرجع</option>
            <option value="someday">شاید بعداً</option>
            <option value="discard">حذف‌شده‌ها</option>
            <option value="all">همه</option>
          </select>
        </div>
      </div>
      {items.status === 'loading' && !items.data && <Loading />}
      {items.status === 'error' && <ErrorMessage error={items.error} onRetry={items.reload} />}
      {items.data && items.data.length === 0 && (
        <EmptyState title={filter === 'unprocessed' ? 'صندوق ورودی خالی است.' : 'موردی برای نمایش نیست.'} />
      )}
      {items.data && items.data.length > 0 && (
        <>
          <p className="meta">{`${formatNumber(items.data.length)} مورد`}</p>
          <ul className="inbox-list" aria-label="موارد صندوق ورودی">
            {items.data.map((item) => (
              <InboxItemRow key={item.id} item={item} onChanged={items.reload} />
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
