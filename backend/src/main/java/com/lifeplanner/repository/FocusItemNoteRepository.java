package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.FocusItemNote;
import com.lifeplanner.model.srv.NoteCountSrv;
import com.lifeplanner.service.CurrentUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/** focus_item_notes is owned through focus_items: every query joins the item and checks its user_id. */
@Repository
public class FocusItemNoteRepository {

    private static final String COLUMNS = "n.id, n.focus_item_id, n.body, n.created_at, n.updated_at";
    private static final String OWNED = " FROM focus_item_notes n JOIN focus_items f ON f.id = n.focus_item_id WHERE f.user_id = ?";

    private static final RowMapper<FocusItemNote> MAPPER = (rs, i) -> new FocusItemNote(
            rs.getString("id"), rs.getString("focus_item_id"), rs.getString("body"),
            UtcTimestamps.fromDb(rs.getString("created_at")), UtcTimestamps.fromDb(rs.getString("updated_at")));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public FocusItemNoteRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    /** Newest first. */
    public List<FocusItemNote> findByFocusItem(String focusItemId) {
        return db.sql("SELECT " + COLUMNS + OWNED + " AND n.focus_item_id = ? ORDER BY n.created_at DESC, n.id DESC")
                .params(currentUser.id(), focusItemId).query(MAPPER).list();
    }

    public Optional<FocusItemNote> findById(String id) {
        return db.sql("SELECT " + COLUMNS + OWNED + " AND n.id = ?").params(currentUser.id(), id).query(MAPPER).optional();
    }

    /** Counts per focus item (only items that have notes). */
    public List<NoteCountSrv> counts() {
        return db.sql("SELECT n.focus_item_id, count(*) AS c" + OWNED + " GROUP BY n.focus_item_id")
                .params(currentUser.id()).query((rs, i) -> new NoteCountSrv(rs.getString("focus_item_id"), rs.getInt("c"))).list();
    }

    /** The caller has checked that the focus item is the current user's. */
    public void insert(FocusItemNote n) {
        db.sql("INSERT INTO focus_item_notes (id, focus_item_id, body, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
                .params(n.id(), n.focusItemId(), n.body(), UtcTimestamps.toDb(n.createdAt()), UtcTimestamps.toDb(n.updatedAt())).update();
    }

    public void update(FocusItemNote n) {
        db.sql("UPDATE focus_item_notes SET body = ?, updated_at = ? WHERE id = ? AND focus_item_id IN (SELECT id FROM focus_items WHERE user_id = ?)")
                .params(n.body(), UtcTimestamps.toDb(n.updatedAt()), n.id(), currentUser.id()).update();
    }

    public int delete(String id) {
        return db.sql("DELETE FROM focus_item_notes WHERE id = ? AND focus_item_id IN (SELECT id FROM focus_items WHERE user_id = ?)")
                .params(id, currentUser.id()).update();
    }
}
