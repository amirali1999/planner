package com.lifeplanner.repository;

import com.lifeplanner.service.CurrentUser;
import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.Task;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.GoalMatch;
import com.lifeplanner.model.enums.TaskPriority;
import com.lifeplanner.model.enums.TaskStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskRepository {

    private static final String COLUMNS = """
            id, title, description, goal_id, focus_item_id, status, priority, estimated_minutes,
            due_date, blocked_reason, archived_at, created_at, updated_at""";

    /** Every read joins the linked focus item to derive the effective goal (task.goal_id is never rewritten). */
    private static final String SELECT = """
            SELECT t.id, t.title, t.description, t.goal_id, t.focus_item_id, t.status, t.priority, t.estimated_minutes,
                   t.due_date, t.blocked_reason, t.archived_at, t.created_at, t.updated_at, t.completed_at,
                   f.goal_id AS focus_item_goal_id
            FROM tasks t LEFT JOIN focus_items f ON f.id = t.focus_item_id""";

    /** SQL expression of the effective goal: direct link wins over the focus item's goal. */
    private static final String EFFECTIVE_GOAL = "COALESCE(t.goal_id, f.goal_id)";

    private static final RowMapper<Task> MAPPER = (rs, n) -> {
        int minutes = rs.getInt("estimated_minutes");
        boolean noEstimate = rs.wasNull();
        String priority = rs.getString("priority");
        String due = rs.getString("due_date");
        return new Task(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("goal_id"),
                rs.getString("focus_item_id"),
                TaskStatus.fromCode(rs.getString("status")),
                priority == null ? null : TaskPriority.fromCode(priority),
                noEstimate ? null : minutes,
                due == null ? null : LocalDate.parse(due),
                rs.getString("blocked_reason"),
                UtcTimestamps.fromDb(rs.getString("archived_at")),
                UtcTimestamps.fromDb(rs.getString("created_at")),
                UtcTimestamps.fromDb(rs.getString("updated_at")),
                rs.getString("focus_item_goal_id"),
                UtcTimestamps.fromDb(rs.getString("completed_at")));
    };

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public TaskRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public void insert(Task t) {
        // a task created as done is completed at its creation time
        db.sql("INSERT INTO tasks (" + COLUMNS + ", user_id, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                        + " CASE WHEN ? = 'done' THEN ? END)")
                .params(t.id(), t.title(), t.description(), t.goalId(), t.focusItemId(), t.status().code(),
                        t.priority() == null ? null : t.priority().code(), t.estimatedMinutes(),
                        t.dueDate() == null ? null : t.dueDate().toString(), t.blockedReason(),
                        t.archivedAt() == null ? null : UtcTimestamps.toDb(t.archivedAt()),
                        UtcTimestamps.toDb(t.createdAt()), UtcTimestamps.toDb(t.updatedAt()), user(),
                        t.status().code(), UtcTimestamps.toDb(t.createdAt()))
                .update();
    }

    /**
     * Writes every mutable column of an existing row; id and created_at never change. completed_at follows the status
     * (the right-hand side sees the OLD row): becoming done stamps updated_at, staying done keeps the stamp, leaving
     * done clears it.
     */
    public void update(Task t) {
        db.sql("""
                UPDATE tasks SET title = ?, description = ?, goal_id = ?, focus_item_id = ?, status = ?, priority = ?,
                       estimated_minutes = ?, due_date = ?, blocked_reason = ?, archived_at = ?, updated_at = ?,
                       completed_at = CASE WHEN ? <> 'done' THEN NULL
                                           WHEN status = 'done' AND completed_at IS NOT NULL THEN completed_at
                                           ELSE ? END
                WHERE id = ? AND user_id = ?""")
                .params(t.title(), t.description(), t.goalId(), t.focusItemId(), t.status().code(),
                        t.priority() == null ? null : t.priority().code(), t.estimatedMinutes(),
                        t.dueDate() == null ? null : t.dueDate().toString(), t.blockedReason(),
                        t.archivedAt() == null ? null : UtcTimestamps.toDb(t.archivedAt()),
                        UtcTimestamps.toDb(t.updatedAt()), t.status().code(), UtcTimestamps.toDb(t.updatedAt()),
                        t.id(), user())
                .update();
    }

    /** A task planned on an earlier day than the board's date, with the latest such day. */
    public record CarriedTask(Task task, LocalDate plannedOn) {
    }

    /**
     * Non-archived tasks that were planned before {@code date} and have no plan on or after {@code date}, and that are
     * either still unfinished or were finished inside [{@code completedFrom}, {@code completedTo}) (both null = only the
     * unfinished ones). A task rescheduled to a future day must not immediately return to today's board as carry-over.
     * Oldest planned day first. One row per task.
     */
    public List<CarriedTask> findCarriedOver(LocalDate date, java.time.Instant completedFrom, java.time.Instant completedTo) {
        String sql = SELECT.replace("FROM tasks t", ", MAX(p.local_date) AS planned_on FROM tasks t") + """

                JOIN daily_plan_items i ON i.task_id = t.id
                JOIN daily_plans p ON p.id = i.plan_id AND p.user_id = t.user_id
                WHERE t.user_id = ? AND t.archived_at IS NULL AND p.local_date < ?
                  AND NOT EXISTS (SELECT 1 FROM daily_plan_items i2 JOIN daily_plans p2 ON p2.id = i2.plan_id
                                   WHERE i2.task_id = t.id AND p2.user_id = t.user_id AND p2.local_date >= ?)
                  AND (t.status <> 'done' OR (? IS NOT NULL AND t.completed_at >= ? AND t.completed_at < ?))
                GROUP BY t.id
                ORDER BY planned_on, t.created_at, t.id""";
        String from = completedFrom == null ? null : UtcTimestamps.toDb(completedFrom);
        String to = completedTo == null ? null : UtcTimestamps.toDb(completedTo);
        return db.sql(sql)
                .params(user(), date.toString(), date.toString(), from, from, to)
                .query((rs, n) -> new CarriedTask(MAPPER.mapRow(rs, n), LocalDate.parse(rs.getString("planned_on"))))
                .list();
    }

    public Optional<Task> findById(String id) {
        return db.sql(SELECT + " WHERE t.id = ? AND t.user_id = ?").params(id, user()).query(MAPPER).optional();
    }

    /** Tasks by id, any archive state; unknown ids are simply absent from the result. */
    public List<Task> findByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        List<Object> params = new ArrayList<>(ids);
        params.add(user());
        return db.sql(SELECT + " WHERE t.id IN (" + placeholders + ") AND t.user_id = ?")
                .params(params).query(MAPPER).list();
    }

    /**
     * Active (non-archived) and done task counts per EFFECTIVE goal, in ONE grouped query. Each task row is counted
     * once, for exactly one goal, even when its direct and focus-item goals coincide or differ.
     */
    public record GoalTaskCounts(int active, int done) {
    }

    public java.util.Map<String, GoalTaskCounts> countActiveByGoals(Collection<String> goalIds) {
        if (goalIds.isEmpty()) {
            return java.util.Map.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(goalIds.size(), "?"));
        java.util.Map<String, GoalTaskCounts> counts = new java.util.HashMap<>();
        db.sql("SELECT " + EFFECTIVE_GOAL + " AS goal_id, count(*) AS active,"
                        + " sum(CASE WHEN t.status = 'done' THEN 1 ELSE 0 END) AS done"
                        + " FROM tasks t LEFT JOIN focus_items f ON f.id = t.focus_item_id"
                        + " WHERE t.user_id = ? AND t.archived_at IS NULL AND " + EFFECTIVE_GOAL + " IN (" + placeholders + ")"
                        + " GROUP BY " + EFFECTIVE_GOAL)
                .params(withUserFirst(goalIds))
                .query((rs, n) -> counts.put(rs.getString("goal_id"), new GoalTaskCounts(rs.getInt("active"), rs.getInt("done"))))
                .list();
        return counts;
    }

    /**
     * Filters are optional. {@code goalId} matches the direct link ({@link GoalMatch#DIRECT}, /tasks?goalId=) or the
     * effective goal ({@link GoalMatch#EFFECTIVE}, /goals/{id}/tasks). One row per task: never duplicated.
     */
    public List<Task> find(TaskStatus status, String goalId, GoalMatch match, String focusItemId, ArchiveFilter archive) {
        StringBuilder sql = new StringBuilder(SELECT + " WHERE t.user_id = ?");
        List<Object> params = new ArrayList<>(List.of(user()));
        if (status != null) {
            sql.append(" AND t.status = ?");
            params.add(status.code());
        }
        if (goalId != null) {
            sql.append(match == GoalMatch.EFFECTIVE ? " AND " + EFFECTIVE_GOAL + " = ?" : " AND t.goal_id = ?");
            params.add(goalId);
        }
        if (focusItemId != null) {
            sql.append(" AND t.focus_item_id = ?");
            params.add(focusItemId);
        }
        sql.append(" AND ").append(archive.sqlCondition("t.archived_at"));
        sql.append(" ORDER BY t.created_at, t.id");
        return db.sql(sql.toString()).params(params).query(MAPPER).list();
    }

    private List<Object> withUserFirst(Collection<String> values) {
        List<Object> params = new ArrayList<>(List.of(user()));
        params.addAll(values);
        return params;
    }

    /** Backwards-compatible direct-link lookup. */
    public List<Task> find(TaskStatus status, String goalId, ArchiveFilter archive) {
        return find(status, goalId, GoalMatch.DIRECT, null, archive);
    }
}
