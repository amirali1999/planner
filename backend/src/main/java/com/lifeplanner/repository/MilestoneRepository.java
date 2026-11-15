package com.lifeplanner.repository;

import com.lifeplanner.service.CurrentUser;
import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.Milestone;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.MilestoneStatusFilter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MilestoneRepository {

    private static final String COLUMNS =
            "id, goal_id, title, description, due_date, completed_at, archived_at, created_at, updated_at";

    private static final RowMapper<Milestone> MAPPER = (rs, n) -> {
        String due = rs.getString("due_date");
        return new Milestone(rs.getString("id"), rs.getString("goal_id"), rs.getString("title"), rs.getString("description"),
                due == null ? null : LocalDate.parse(due),
                UtcTimestamps.fromDb(rs.getString("completed_at")), UtcTimestamps.fromDb(rs.getString("archived_at")),
                UtcTimestamps.fromDb(rs.getString("created_at")), UtcTimestamps.fromDb(rs.getString("updated_at")));
    };

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public MilestoneRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public void insert(Milestone m) {
        db.sql("INSERT INTO milestones (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(m.id(), m.goalId(), m.title(), m.description(), m.dueDate() == null ? null : m.dueDate().toString(),
                        ts(m.completedAt()), ts(m.archivedAt()), ts(m.createdAt()), ts(m.updatedAt()))
                .update();
    }

    public void update(Milestone m) {
        db.sql("""
                UPDATE milestones SET title = ?, description = ?, due_date = ?, completed_at = ?, archived_at = ?, updated_at = ?
                WHERE id = ? AND goal_id IN (SELECT id FROM goals WHERE user_id = ?)""")
                .params(m.title(), m.description(), m.dueDate() == null ? null : m.dueDate().toString(),
                        ts(m.completedAt()), ts(m.archivedAt()), ts(m.updatedAt()), m.id(), user())
                .update();
    }

    public Optional<Milestone> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM milestones WHERE id = ? AND goal_id IN (SELECT id FROM goals WHERE user_id = ?)").params(id, user()).query(MAPPER).optional();
    }

    /** Stable order: dated first by due date, undated last, then creation time and id. */
    public List<Milestone> findByGoal(String goalId, MilestoneStatusFilter status, LocalDate today, ArchiveFilter archive) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM milestones WHERE goal_id = ? AND goal_id IN (SELECT id FROM goals WHERE user_id = ?) AND ")
                .append(archive.sqlCondition("archived_at"));
        List<Object> params = new ArrayList<>(List.of(goalId, user()));
        switch (status) {
            case ALL -> { }
            case OPEN -> sql.append(" AND completed_at IS NULL");
            case COMPLETED -> sql.append(" AND completed_at IS NOT NULL");
            case OVERDUE -> {
                sql.append(" AND completed_at IS NULL AND due_date IS NOT NULL AND due_date < ?");
                params.add(today.toString());
            }
            case UPCOMING -> {
                sql.append(" AND completed_at IS NULL AND due_date IS NOT NULL AND due_date >= ?");
                params.add(today.toString());
            }
        }
        sql.append(" ORDER BY due_date IS NULL, due_date, created_at, id");
        return db.sql(sql.toString()).params(params).query(MAPPER).list();
    }

    private static String ts(java.time.Instant i) {
        return i == null ? null : UtcTimestamps.toDb(i);
    }
}
