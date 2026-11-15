package com.lifeplanner.repository;

import com.lifeplanner.service.CurrentUser;
import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.InboxItem;
import com.lifeplanner.model.enums.InboxStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class InboxRepository {

    private static final String COLUMNS = """
            id, raw_text, status, decision, created_entity_type, created_entity_id, processed_at, captured_at,
            created_at, updated_at""";

    private static final RowMapper<InboxItem> MAPPER = (rs, n) -> new InboxItem(
            rs.getString("id"), rs.getString("raw_text"), InboxStatus.fromCode(rs.getString("status")),
            rs.getString("decision"), rs.getString("created_entity_type"), rs.getString("created_entity_id"),
            UtcTimestamps.fromDb(rs.getString("processed_at")), UtcTimestamps.fromDb(rs.getString("captured_at")),
            UtcTimestamps.fromDb(rs.getString("created_at")), UtcTimestamps.fromDb(rs.getString("updated_at")));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public InboxRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    /** New captures are always unprocessed with no decision or created entity. */
    public void insert(InboxItem i) {
        db.sql("INSERT INTO inbox_items (id, raw_text, status, captured_at, created_at, updated_at, user_id) VALUES (?, ?, 'unprocessed', ?, ?, ?, ?)")
                .params(i.id(), i.rawText(), UtcTimestamps.toDb(i.capturedAt()), UtcTimestamps.toDb(i.createdAt()),
                        UtcTimestamps.toDb(i.updatedAt()), user())
                .update();
    }

    /**
     * Marks an UNPROCESSED item processed. Conditional on the current status, so a concurrent/second triage updates
     * nothing and returns false (the caller turns that into 409 and rolls back any created entity).
     */
    public boolean markProcessed(String id, String decision, String entityType, String entityId, java.time.Instant at) {
        return db.sql("""
                UPDATE inbox_items SET status = 'processed', decision = ?, created_entity_type = ?, created_entity_id = ?,
                       processed_at = ?, updated_at = ?
                WHERE id = ? AND user_id = ? AND status = 'unprocessed'""")
                .params(decision, entityType, entityId, UtcTimestamps.toDb(at), UtcTimestamps.toDb(at), id, user())
                .update() == 1;
    }

    public Optional<InboxItem> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM inbox_items WHERE id = ? AND user_id = ?").params(id, user()).query(MAPPER).optional();
    }

    /**
     * Newest first (captured_at, then id). {@code status == null} lists all; {@code decisions} (optional) keeps only
     * processed items with one of these decisions (e.g. the reference or someday views).
     */
    public List<InboxItem> find(InboxStatus status, List<String> decisions) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM inbox_items WHERE user_id = ?");
        List<Object> params = new java.util.ArrayList<>(List.of(user()));
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.code());
        }
        if (decisions != null && !decisions.isEmpty()) {
            sql.append(" AND decision IN (").append(String.join(", ", java.util.Collections.nCopies(decisions.size(), "?"))).append(")");
            params.addAll(decisions);
        }
        sql.append(" ORDER BY captured_at DESC, id DESC");
        return db.sql(sql.toString()).params(params).query(MAPPER).list();
    }
}
