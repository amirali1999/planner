package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.TimeBlock;
import com.lifeplanner.model.enums.TimeBlockColor;
import com.lifeplanner.service.CurrentUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** time_blocks is a root table: every query is scoped to the current user. */
@Repository
public class TimeBlockRepository {

    private static final String COLUMNS = "id, local_date, start_minute, end_minute, title, task_id, color, created_at, updated_at";

    private static final RowMapper<TimeBlock> MAPPER = (rs, n) -> new TimeBlock(
            rs.getString("id"),
            LocalDate.parse(rs.getString("local_date")),
            rs.getInt("start_minute"),
            rs.getInt("end_minute"),
            rs.getString("title"),
            rs.getString("task_id"),
            TimeBlockColor.fromCode(rs.getString("color")),
            UtcTimestamps.fromDb(rs.getString("created_at")),
            UtcTimestamps.fromDb(rs.getString("updated_at")));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public TimeBlockRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public List<TimeBlock> findBetween(LocalDate from, LocalDate to) {
        return db.sql("SELECT " + COLUMNS + " FROM time_blocks WHERE user_id = ? AND local_date >= ? AND local_date <= ?"
                        + " ORDER BY local_date, start_minute, created_at, id")
                .params(user(), from.toString(), to.toString()).query(MAPPER).list();
    }

    public Optional<TimeBlock> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM time_blocks WHERE id = ? AND user_id = ?")
                .params(id, user()).query(MAPPER).optional();
    }

    /**
     * Another block of the same day intersecting [start, end), ignoring {@code exceptId} and blocks whose task is
     * archived (those are hidden in the UI, so they must not block the time either).
     */
    public Optional<String> findOverlapping(LocalDate date, int start, int end, String exceptId) {
        return db.sql("""
                        SELECT b.id FROM time_blocks b LEFT JOIN tasks t ON t.id = b.task_id
                        WHERE b.user_id = ? AND b.local_date = ? AND b.start_minute < ? AND b.end_minute > ?
                          AND b.id <> ? AND (b.task_id IS NULL OR t.archived_at IS NULL)
                        ORDER BY b.start_minute LIMIT 1""")
                .params(user(), date.toString(), end, start, exceptId == null ? "" : exceptId)
                .query(String.class).optional();
    }

    public void insert(TimeBlock b) {
        db.sql("INSERT INTO time_blocks (" + COLUMNS + ", user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(b.id(), b.date().toString(), b.startMinute(), b.endMinute(), b.title(), b.taskId(), b.color().code(),
                        UtcTimestamps.toDb(b.createdAt()), UtcTimestamps.toDb(b.updatedAt()), user())
                .update();
    }

    public void update(TimeBlock b) {
        db.sql("""
                        UPDATE time_blocks SET local_date = ?, start_minute = ?, end_minute = ?, title = ?, task_id = ?, color = ?,
                               updated_at = ?
                        WHERE id = ? AND user_id = ?""")
                .params(b.date().toString(), b.startMinute(), b.endMinute(), b.title(), b.taskId(), b.color().code(),
                        UtcTimestamps.toDb(b.updatedAt()), b.id(), user())
                .update();
    }

    public int delete(String id) {
        return db.sql("DELETE FROM time_blocks WHERE id = ? AND user_id = ?").params(id, user()).update();
    }
}
