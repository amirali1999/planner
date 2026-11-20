import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../shared/api/client';
import type { Note } from '../../shared/api/notes';
import { formatNumber, formatTimestamp } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { useApiData } from '../../shared/ui/useApiData';

interface NotesApi {
  list: (ownerId: string, signal?: AbortSignal) => Promise<Note[]>;
  add: (ownerId: string, body: string) => Promise<Note>;
  update: (id: string, body: string) => Promise<Note>;
  remove: (id: string) => Promise<void>;
}

type NoteOwner = { id: string; title: string; archived: boolean };

/**
 * Adds and edits notes in the same dialog for tasks and focus items. Ctrl/⌘+Enter saves.
 */
export function NotesDialog({ item, api, detailsUrl, onClose, onChanged }: {
  item: NoteOwner | null; api: NotesApi; detailsUrl?: string; onClose: () => void; onChanged: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const headingId = useId();
  const open = item !== null;
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) {
      d.showModal();
      d.querySelector<HTMLTextAreaElement>('.notes__add textarea')?.focus({ preventScroll: true });
    }
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog ref={ref} className="modal notes-modal" aria-labelledby={headingId} onClose={() => open && onClose()}
      onClick={(e) => e.target === e.currentTarget && onClose()}>
      {item && <NotesBody key={item.id} item={item} api={api} headingId={headingId} detailsUrl={detailsUrl}
        onClose={onClose} onChanged={onChanged} />}
    </dialog>
  );
}

function NotesBody({ item, api, headingId, detailsUrl, onClose, onChanged }: {
  item: NoteOwner; api: NotesApi; headingId: string; detailsUrl?: string; onClose: () => void; onChanged: () => void;
}) {
  const notes = useApiData((signal) => api.list(item.id, signal), [item.id, api]);
  const textareaId = useId();
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const reload = () => {
    notes.reload();
    onChanged();
  };
  async function add(e?: FormEvent) {
    e?.preventDefault();
    if (!draft.trim() || saving) return;
    setSaving(true);
    setError(null);
    try {
      await api.add(item.id, draft.trim());
      setDraft('');
      reload();
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }
  const list = notes.data ?? [];
  return (
    <div className="modal__body card notes">
      <button type="button" className="icon-button modal__close" aria-label="بستن" onClick={onClose}>✕</button>
      <header className="notes__head">
        <h2 id={headingId}><Icon name="file" size={22} /> یادداشت‌های «{item.title}»</h2>
        {detailsUrl && <p className="meta">یادداشت با تسک فرق دارد: وضعیت و برنامه ندارد و فقط همین‌جا دیده می‌شود.
          {' '}<Link to={detailsUrl} onClick={onClose}>جزئیات آیتم</Link></p>}
      </header>
      {item.archived ? (
        <p className="state state--notice">این مورد بایگانی شده و یادداشت‌هایش فقط خواندنی‌اند.</p>
      ) : (
        <form className="notes__add" onSubmit={add}>
          <label htmlFor={textareaId} className="visually-hidden">یادداشت جدید</label>
          <textarea id={textareaId} rows={3} maxLength={4000} value={draft} placeholder="یادداشت جدید… (Ctrl+Enter برای ثبت)"
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); void add(); }
            }} />
          <div className="notes__add-actions">
            {error != null && <ErrorMessage error={error} />}
            <button type="submit" className="button" disabled={saving || !draft.trim()}>
              <Icon name="plus" size={18} /> {saving ? 'در حال ثبت…' : 'افزودن یادداشت'}
            </button>
          </div>
        </form>
      )}
      <section className="notes__list" aria-label="یادداشت‌ها">
        {notes.status === 'error' && <ErrorMessage error={notes.error} onRetry={notes.reload} />}
        {!notes.data && notes.status !== 'error' && <Loading />}
        {notes.data && list.length === 0 && <p className="dash-empty">هنوز یادداشتی ندارد.</p>}
        {list.length > 0 && (
          <>
            <p className="meta notes__count">{`${formatNumber(list.length)} یادداشت`}</p>
            <ul className="notes__items">
              {list.map((n) => <NoteItem key={n.id} note={n} api={api} readOnly={item.archived} onChanged={reload} />)}
            </ul>
          </>
        )}
      </section>
    </div>
  );
}

function NoteItem({ note, api, readOnly, onChanged }: { note: Note; api: NotesApi; readOnly: boolean; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(note.body);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function save() {
    if (!text.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      await api.update(note.id, text.trim());
      setEditing(false);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'ذخیره نشد.');
    } finally {
      setBusy(false);
    }
  }
  async function remove() {
    if (!window.confirm('این یادداشت حذف شود؟')) return;
    setBusy(true);
    try {
      await api.remove(note.id);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'حذف نشد.');
      setBusy(false);
    }
  }
  const edited = note.updatedAt !== note.createdAt;
  return (
    <li className="note">
      {editing ? (
        <div className="note__edit">
          <label htmlFor={`note-${note.id}`} className="visually-hidden">ویرایش یادداشت</label>
          <textarea id={`note-${note.id}`} autoFocus rows={3} maxLength={4000} value={text} onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); void save(); }
              if (e.key === 'Escape') { e.preventDefault(); setEditing(false); setText(note.body); }
            }} />
          <div className="note__actions">
            <button type="button" key="save" className="button" disabled={busy || !text.trim()} onClick={() => void save()}>ذخیره</button>
            <button type="button" key="cancel" className="button button--secondary" onClick={() => { setEditing(false); setText(note.body); }}>انصراف</button>
          </div>
        </div>
      ) : (
        <>
          <p className="note__body">{note.body}</p>
          <div className="note__foot">
            <span className="meta">{formatTimestamp(note.createdAt)}{edited ? ' · ویرایش‌شده' : ''}</span>
            {!readOnly && (
              <span className="note__tools">
                <button type="button" className="link-button" disabled={busy} onClick={() => { setText(note.body); setEditing(true); }}>ویرایش</button>
                <button type="button" className="link-button note__delete" disabled={busy} onClick={() => void remove()}>حذف</button>
              </span>
            )}
          </div>
        </>
      )}
      {error && <p className="field__error" role="alert">{error}</p>}
    </li>
  );
}
