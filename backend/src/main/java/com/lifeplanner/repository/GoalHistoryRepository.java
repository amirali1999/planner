package com.lifeplanner.repository;

import com.lifeplanner.service.CurrentUser;
import com.lifeplanner.db.UtcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Append-only audit rows for Goal lifecycle and KR structural changes (DB triggers forbid update/delete). */
@Repository
public class GoalHistoryRepository {

    /** One audit row; JSON fields hold only the changed values, keyed by API field name. */
    public record HistoryEntry(String id, String goalId, String keyResultId, String eventType, String beforeJson,
                               String afterJson, String note, Instant createdAt) {
    }

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public GoalHistoryRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public void append(String goalId, String eventType, String beforeJson, String afterJson, Instant at) {
        appendKeyResult(goalId, null, eventType, beforeJson, afterJson, null, at);
    }

    public void appendKeyResult(String goalId, String keyResultId, String eventType, String beforeJson, String afterJson,
                                String note, Instant at) {
        db.sql("""
                INSERT INTO goal_history (id, goal_id, key_result_id, event_type, before_json, after_json, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(UUID.randomUUID().toString(), goalId, keyResultId, eventType, beforeJson, afterJson, note,
                        UtcTimestamps.toDb(at))
                .update();
    }

    /** Chronological (insertion order breaks ties within the same millisecond). */
    public List<HistoryEntry> findByKeyResult(String keyResultId) {
        return db.sql("""
                SELECT id, goal_id, key_result_id, event_type, before_json, after_json, note, created_at
                FROM goal_history WHERE key_result_id = ? AND goal_id IN (SELECT id FROM goals WHERE user_id = ?) ORDER BY created_at, rowid""")
                .params(keyResultId, user())
                .query((rs, n) -> new HistoryEntry(rs.getString("id"), rs.getString("goal_id"), rs.getString("key_result_id"),
                        rs.getString("event_type"), rs.getString("before_json"), rs.getString("after_json"),
                        rs.getString("note"), UtcTimestamps.fromDb(rs.getString("created_at"))))
                .list();
    }
}
