package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.Habit;
import com.lifeplanner.service.CurrentUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** habits is a root table (scoped to the current user); habit_checks is reached only through an owned habit. */
@Repository
public class HabitRepository {

    private static final String COLUMNS = "id, title, archived_at, created_at, updated_at";

    private static final RowMapper<Habit> MAPPER = (rs, n) -> new Habit(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("archived_at") == null ? null : UtcTimestamps.fromDb(rs.getString("archived_at")),
            UtcTimestamps.fromDb(rs.getString("created_at")),
            UtcTimestamps.fromDb(rs.getString("updated_at")));

    /** One check row: habit + local day. */
    public record Check(String habitId, LocalDate date) {
    }

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public HabitRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public List<Habit> findAll(boolean archived) {
        return db.sql("SELECT " + COLUMNS + " FROM habits WHERE user_id = ? AND (archived_at IS NOT NULL) = ? ORDER BY created_at, id")
                .params(user(), archived).query(MAPPER).list();
    }

    public Optional<Habit> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM habits WHERE id = ? AND user_id = ?").params(id, user()).query(MAPPER).optional();
    }

    /** Checks of the current user's habits between two local days (inclusive). */
    public List<Check> findChecks(LocalDate from, LocalDate to) {
        return db.sql("""
                        SELECT c.habit_id, c.local_date FROM habit_checks c JOIN habits h ON h.id = c.habit_id
                        WHERE h.user_id = ? AND c.local_date >= ? AND c.local_date <= ? ORDER BY c.local_date""")
                .params(user(), from.toString(), to.toString())
                .query((rs, n) -> new Check(rs.getString("habit_id"), LocalDate.parse(rs.getString("local_date")))).list();
    }

    public void insert(Habit h) {
        db.sql("INSERT INTO habits (" + COLUMNS + ", user_id) VALUES (?, ?, ?, ?, ?, ?)")
                .params(h.id(), h.title(), h.archivedAt() == null ? null : UtcTimestamps.toDb(h.archivedAt()),
                        UtcTimestamps.toDb(h.createdAt()), UtcTimestamps.toDb(h.updatedAt()), user())
                .update();
    }

    public void update(Habit h) {
        db.sql("UPDATE habits SET title = ?, archived_at = ?, updated_at = ? WHERE id = ? AND user_id = ?")
                .params(h.title(), h.archivedAt() == null ? null : UtcTimestamps.toDb(h.archivedAt()), UtcTimestamps.toDb(h.updatedAt()),
                        h.id(), user())
                .update();
    }

    /** Idempotent: marking an already-done day changes nothing. The caller has checked ownership. */
    public void check(String habitId, LocalDate date, Instant now) {
        db.sql("INSERT OR IGNORE INTO habit_checks (habit_id, local_date, created_at) VALUES (?, ?, ?)")
                .params(habitId, date.toString(), UtcTimestamps.toDb(now)).update();
    }

    /** Idempotent. The caller has checked ownership. */
    public void uncheck(String habitId, LocalDate date) {
        db.sql("DELETE FROM habit_checks WHERE habit_id = ? AND local_date = ?").params(habitId, date.toString()).update();
    }
}
