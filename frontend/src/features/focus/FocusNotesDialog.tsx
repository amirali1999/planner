import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { formatNumber, formatTimestamp } from '../../shared/format';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import { Icon } from '../../shared/ui/Icon';
import { useApiData } from '../../shared/ui/useApiData';
import { focusNotesApi } from './api';
import type { FocusItemNote } from './types';

/**
 * Notes of one focus item as a pop-up (native <dialog>): add a note on top, the notes newest first below; each can be
 * edited in place or deleted. Notes are not tasks — they are only shown here. Ctrl/⌘+Enter saves.
 */
export function FocusNotesDialog({ item, onClose, onChanged }: {
  item: { id: string; title: string; archived: boolean } | null; onClose: () => void; onChanged: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const open = item !== null;
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog ref={ref} className="modal notes-modal" aria-labelledby="notes-heading" onClose={() => open && onClose()}
      onClick={(e) => e.target === e.currentTarget && onClose()}>
      {item && <NotesBody key={item.id} item={item} onClose={onClose} onChanged={onChanged} />}
    </dialog>
  );
}

function NotesBody({ item, onClose, onChanged }: { item: { id: string; title: string; archived: boolean }; onClose: () => void; onChanged: () => void }) {
  const notes = useApiData((signal) => focusNotesApi.list(item.id, signal), [item.id]);
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
      await focusNotesApi.add(item.id, draft.trim());
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
        <h2 id="notes-heading"><Icon name="file" size={22} /> یادداشت‌های «{item.title}»</h2>
        <p className="meta">یادداشت با تسک فرق دارد: وضعیت و برنامه ندارد و فقط همین‌جا دیده می‌شود.
          {' '}<Link to={`/focus/${item.id}`} onClick={onClose}>جزئیات آیتم</Link></p>
      </header>
      {item.archived ? (
        <p className="state state--notice">این آیتم بایگانی شده است؛ یادداشت‌هایش فقط خواندنی‌اند.</p>
      ) : (
        <form className="notes__add" onSubmit={add}>
          <label htmlFor="note-new" className="visually-hidden">یادداشت جدید</label>
          <textarea id="note-new" autoFocus rows={3} maxLength={4000} value={draft} placeholder="یادداشت جدید… (Ctrl+Enter برای ثبت)"
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e: KeyboardEvent<HTMLTextAreaElement>) => (e.ctrlKey || e.metaKey) && e.key === 'Enter' && void add()} />
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
              {list.map((n) => <NoteItem key={n.id} note={n} readOnly={item.archived} onChanged={reload} />)}
            </ul>
          </>
        )}
      </section>
    </div>
  );
}

function NoteItem({ note, readOnly, onChanged }: { note: FocusItemNote; readOnly: boolean; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(note.body);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function save() {
    if (!text.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      await focusNotesApi.update(note.id, text.trim());
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
      await focusNotesApi.remove(note.id);
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
            onKeyDown={(e) => { if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') void save(); if (e.key === 'Escape') { e.stopPropagation(); setEditing(false); setText(note.body); } }} />
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
                <button type="button" className="link-button" disabled={busy} onClick={() => setEditing(true)}>ویرایش</button>
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
