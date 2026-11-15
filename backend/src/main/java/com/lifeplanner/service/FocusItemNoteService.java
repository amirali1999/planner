package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.FocusItem;
import com.lifeplanner.model.FocusItemNote;
import com.lifeplanner.model.srv.FocusItemNoteSrv;
import com.lifeplanner.model.srv.NoteCountSrv;
import com.lifeplanner.model.vo.FocusItemNoteVO;
import com.lifeplanner.repository.FocusItemNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notes of a focus item: plain text kept with the item, distinct from tasks. Notes of an archived focus item stay
 * readable but cannot be added or changed (409 focus_item_archived); GET never writes.
 */
@Service
public class FocusItemNoteService {

    static final String MSG_NOT_FOUND = "یادداشت پیدا نشد.";
    static final String MSG_ARCHIVED = "یادداشت آیتم تمرکز بایگانی‌شده را نمی‌توان تغییر داد.";

    private final FocusItemNoteRepository notes;
    private final FocusItemService items;
    private final Clock clock;

    public FocusItemNoteService(FocusItemNoteRepository notes, FocusItemService items, Clock clock) {
        this.notes = notes;
        this.items = items;
        this.clock = clock;
    }

    public List<FocusItemNoteSrv> list(String focusItemId) {
        items.get(focusItemId); // 404 for unknown / another user's item
        return notes.findByFocusItem(focusItemId).stream().map(FocusItemNoteSrv::from).toList();
    }

    public List<NoteCountSrv> counts() {
        return notes.counts();
    }

    @Transactional
    public FocusItemNoteSrv create(String focusItemId, FocusItemNoteVO req) {
        requireActive(items.get(focusItemId));
        Instant now = now();
        FocusItemNote note = new FocusItemNote(UUID.randomUUID().toString(), focusItemId, req.body().strip(), now, now);
        notes.insert(note);
        return FocusItemNoteSrv.from(note);
    }

    @Transactional
    public FocusItemNoteSrv update(String noteId, FocusItemNoteVO req) {
        FocusItemNote current = find(noteId);
        requireActive(items.get(current.focusItemId()));
        FocusItemNote updated = new FocusItemNote(noteId, current.focusItemId(), req.body().strip(), current.createdAt(), now());
        notes.update(updated);
        return FocusItemNoteSrv.from(updated);
    }

    @Transactional
    public void delete(String noteId) {
        FocusItemNote current = find(noteId);
        requireActive(items.get(current.focusItemId()));
        notes.delete(noteId);
    }

    private FocusItemNote find(String id) {
        return notes.findById(id).orElseThrow(() -> new ApiException.NotFoundException(MSG_NOT_FOUND, Map.of("noteId", id)));
    }

    private static void requireActive(FocusItem item) {
        if (item.isArchived()) {
            throw new ApiException.ConflictException("focus_item_archived", MSG_ARCHIVED, Map.of("focusItemId", item.id()));
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
