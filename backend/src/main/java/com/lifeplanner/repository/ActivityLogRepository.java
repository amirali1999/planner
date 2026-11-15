package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.ActivityLog;
import com.lifeplanner.model.enums.ActivityCategory;
import com.lifeplanner.service.CurrentUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** activity_logs is a root table: every query is scoped to CurrentUser. */
@Repository
public class ActivityLogRepository {

    private static final String COLUMNS = "id, local_date, title, category, start_minute, end_minute, created_at, updated_at, task_id";
    private static final RowMapper<ActivityLog> MAPPER = (rs, n) -> new ActivityLog(
            rs.getString("id"), LocalDate.parse(rs.getString("local_date")), rs.getString("title"),
            ActivityCategory.fromCode(rs.getString("category")), rs.getInt("start_minute"), rs.getInt("end_minute"),
            rs.getString("task_id"), UtcTimestamps.fromDb(rs.getString("created_at")), UtcTimestamps.fromDb(rs.getString("updated_at")));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public ActivityLogRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public List<ActivityLog> findByDate(LocalDate date) {
        return db.sql("SELECT " + COLUMNS + " FROM activity_logs WHERE user_id = ? AND local_date = ? ORDER BY start_minute, created_at, id")
                .params(user(), date.toString()).query(MAPPER).list();
    }

    public Optional<ActivityLog> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM activity_logs WHERE id = ? AND user_id = ?")
                .params(id, user()).query(MAPPER).optional();
    }

    public Optional<String> findOverlapping(LocalDate date, int start, int end, String exceptId) {
        return db.sql("""
                SELECT id FROM activity_logs
                WHERE user_id = ? AND local_date = ? AND start_minute < ? AND end_minute > ? AND id <> ?
                ORDER BY start_minute LIMIT 1""")
                .params(user(), date.toString(), end, start, exceptId == null ? "" : exceptId)
                .query(String.class).optional();
    }

    public void insert(ActivityLog log) {
        db.sql("INSERT INTO activity_logs (" + COLUMNS + ", user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(log.id(), log.date().toString(), log.title(), log.category().code(), log.startMinute(), log.endMinute(),
                        UtcTimestamps.toDb(log.createdAt()), UtcTimestamps.toDb(log.updatedAt()), log.taskId(), user()).update();
    }

    public int update(ActivityLog log) {
        return db.sql("""
                UPDATE activity_logs SET local_date = ?, title = ?, category = ?, start_minute = ?, end_minute = ?, updated_at = ?, task_id = ?
                WHERE id = ? AND user_id = ?""")
                .params(log.date().toString(), log.title(), log.category().code(), log.startMinute(), log.endMinute(),
                        UtcTimestamps.toDb(log.updatedAt()), log.taskId(), log.id(), user()).update();
    }

    public int delete(String id) {
        return db.sql("DELETE FROM activity_logs WHERE id = ? AND user_id = ?").params(id, user()).update();
    }
}
