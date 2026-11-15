package com.lifeplanner.repository;

import com.lifeplanner.service.CurrentUser;
import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.GoalStatus;
import com.lifeplanner.model.enums.PeriodType;
import com.lifeplanner.model.vo.GoalQueryVO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class GoalRepository {

    private static final String INSERT_COLUMNS = """
            id, title, description, period_type, period_start, period_end, deadline, status,
            parent_goal_id, archived_at, created_at, updated_at""";
    private static final String COLUMNS = INSERT_COLUMNS + ", priority";

    private static final RowMapper<Goal> MAPPER = (rs, n) -> new Goal(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("description"),
            PeriodType.fromCode(rs.getString("period_type")),
            LocalDate.parse(rs.getString("period_start")),
            LocalDate.parse(rs.getString("period_end")),
            parseDate(rs.getString("deadline")),
            GoalStatus.fromCode(rs.getString("status")),
            rs.getString("parent_goal_id"),
            UtcTimestamps.fromDb(rs.getString("archived_at")),
            UtcTimestamps.fromDb(rs.getString("created_at")),
            UtcTimestamps.fromDb(rs.getString("updated_at")),
            nullableInt(rs, "priority"));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public GoalRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public void insert(Goal g) {
        // a new active goal goes to the end of the owner's priority order
        db.sql("INSERT INTO goals (" + INSERT_COLUMNS + ", user_id, priority) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "CASE WHEN ? IS NULL THEN (SELECT coalesce(max(priority), 0) + 1 FROM goals WHERE user_id = ? AND archived_at IS NULL) END)")
                .params(g.id(), g.title(), g.description(), g.periodType().code(),
                        g.periodStart().toString(), g.periodEnd().toString(),
                        g.deadline() == null ? null : g.deadline().toString(),
                        g.status().code(), g.parentGoalId(),
                        g.archivedAt() == null ? null : UtcTimestamps.toDb(g.archivedAt()),
                        UtcTimestamps.toDb(g.createdAt()), UtcTimestamps.toDb(g.updatedAt()), user(),
                        g.archivedAt() == null ? null : UtcTimestamps.toDb(g.archivedAt()), user())
                .update();
    }

    /** Ids of the owner's active goals in priority order. */
    public List<String> activeIdsByPriority() {
        return db.sql("SELECT id FROM goals WHERE user_id = ? AND archived_at IS NULL ORDER BY priority IS NULL, priority, created_at, id")
                .param(user()).query(String.class).list();
    }

    public void setPriority(String goalId, Integer priority) {
        db.sql("UPDATE goals SET priority = ? WHERE id = ? AND user_id = ?").params(priority, goalId, user()).update();
    }

    /** Puts a (restored) goal at the end of the active order. */
    public void appendToPriority(String goalId) {
        db.sql("""
                UPDATE goals SET priority = (SELECT coalesce(max(priority), 0) + 1 FROM goals
                                             WHERE user_id = ? AND archived_at IS NULL AND id <> ?)
                WHERE id = ? AND user_id = ?""").params(user(), goalId, goalId, user()).update();
    }

    /** Renumbers the owner's active goals 1..n in their current order (closes gaps left by archiving). */
    public void renumberPriorities() {
        List<String> ids = activeIdsByPriority();
        for (int i = 0; i < ids.size(); i++) {
            setPriority(ids.get(i), i + 1);
        }
    }

    /** Own goals only: another user's goal is indistinguishable from a missing one. */
    public Optional<Goal> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM goals WHERE id = ? AND user_id = ?").params(id, user()).query(MAPPER).optional();
    }

    /** One page of goals matching {@code q} in its total, stable order. */
    public List<Goal> find(GoalQueryVO q) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM goals WHERE user_id = ? AND ");
        List<Object> params = new java.util.ArrayList<>(List.of(user()));
        params.addAll(q.where(sql));
        sql.append(" ORDER BY ").append(q.orderBy());
        if (q.limit() != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(q.limit());
            params.add(q.offset());
        } else if (q.offset() > 0) {
            sql.append(" LIMIT -1 OFFSET ?");
            params.add(q.offset());
        }
        return db.sql(sql.toString()).params(params).query(MAPPER).list();
    }

    /** Number of goals matching {@code q} regardless of the page window. */
    public int count(GoalQueryVO q) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM goals WHERE user_id = ? AND ");
        List<Object> params = new java.util.ArrayList<>(List.of(user()));
        params.addAll(q.where(sql));
        return db.sql(sql.toString()).params(params).query(Integer.class).single();
    }

    /** Goals by archive state; archived goals are filtered, never dropped (they carry archivedAt). */
    public List<Goal> find(ArchiveFilter archive) {
        return db.sql("SELECT " + COLUMNS + " FROM goals WHERE user_id = ? AND " + archive.sqlCondition("archived_at")
                + " ORDER BY period_start, created_at, id").param(user()).query(MAPPER).list();
    }

    /** Writes every mutable column; id, period_type and created_at never change here. */
    public void update(Goal g) {
        db.sql("""
                UPDATE goals SET title = ?, description = ?, period_start = ?, period_end = ?, deadline = ?, status = ?,
                       parent_goal_id = ?, archived_at = ?, updated_at = ?
                WHERE id = ? AND user_id = ?""")
                .params(g.title(), g.description(), g.periodStart().toString(), g.periodEnd().toString(),
                        g.deadline() == null ? null : g.deadline().toString(), g.status().code(), g.parentGoalId(),
                        g.archivedAt() == null ? null : UtcTimestamps.toDb(g.archivedAt()),
                        UtcTimestamps.toDb(g.updatedAt()), g.id(), user())
                .update();
    }

    public List<Goal> findChildren(String goalId, ArchiveFilter archive) {
        return db.sql("SELECT " + COLUMNS + " FROM goals WHERE parent_goal_id = ? AND user_id = ? AND " + archive.sqlCondition("archived_at")
                + " ORDER BY period_start, created_at, id").params(goalId, user()).query(MAPPER).list();
    }

    /**
     * Ids of the goal itself and all its ancestors, nearest first. The depth cap only guards against corrupt data;
     * strictly coarser parents make real chains at most four levels deep.
     */
    public List<String> selfAndAncestorIds(String goalId) {
        return db.sql("""
                WITH RECURSIVE chain(id, parent_goal_id, depth) AS (
                    SELECT id, parent_goal_id, 0 FROM goals WHERE id = ? AND user_id = ?
                    UNION ALL
                    SELECT g.id, g.parent_goal_id, c.depth + 1
                    FROM goals g JOIN chain c ON g.id = c.parent_goal_id
                    WHERE c.depth < 64 AND g.user_id = ?
                )
                SELECT id FROM chain ORDER BY depth""")
                .params(goalId, user(), user()).query(String.class).list();
    }

    /**
     * Goals relevant on {@code date}: not archived, not finished/dropped by the user, and whose period
     * contains the date (ISO text compares chronologically).
     */
    public List<Goal> findActiveOn(LocalDate date) {
        return db.sql("SELECT " + COLUMNS + """
                 FROM goals
                WHERE user_id = ? AND archived_at IS NULL
                  AND status NOT IN ('done', 'dropped')
                  AND period_start <= ? AND period_end >= ?
                ORDER BY period_start, created_at, id""")
                .params(user(), date.toString(), date.toString()).query(MAPPER).list();
    }

    /** The SQLite driver cannot map NULL with getObject(col, Integer.class); getInt + wasNull can. */
    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDate parseDate(String text) {
        return text == null ? null : LocalDate.parse(text);
    }
}
