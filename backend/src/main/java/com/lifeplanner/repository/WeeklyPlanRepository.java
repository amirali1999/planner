package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.WeeklyPlanItem;
import com.lifeplanner.service.CurrentUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

/** weekly_plan_items is a root table: every query is scoped to the current user. */
@Repository
public class WeeklyPlanRepository {

    private static final RowMapper<WeeklyPlanItem> MAPPER = (rs, n) -> new WeeklyPlanItem(rs.getString("id"),
            LocalDate.parse(rs.getString("week_start")), rs.getString("task_id"), UtcTimestamps.fromDb(rs.getString("created_at")));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public WeeklyPlanRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    public List<WeeklyPlanItem> findByWeek(LocalDate weekStart) {
        return db.sql("SELECT id, week_start, task_id, created_at FROM weekly_plan_items WHERE user_id = ? AND week_start = ?"
                        + " ORDER BY created_at, id")
                .params(currentUser.id(), weekStart.toString()).query(MAPPER).list();
    }

    public boolean contains(LocalDate weekStart, String taskId) {
        return db.sql("SELECT count(*) FROM weekly_plan_items WHERE user_id = ? AND week_start = ? AND task_id = ?")
                .params(currentUser.id(), weekStart.toString(), taskId).query(Integer.class).single() > 0;
    }

    public void insert(WeeklyPlanItem item) {
        db.sql("INSERT INTO weekly_plan_items (id, user_id, week_start, task_id, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(item.id(), currentUser.id(), item.weekStart().toString(), item.taskId(), UtcTimestamps.toDb(item.createdAt()))
                .update();
    }

    public int delete(LocalDate weekStart, String taskId) {
        return db.sql("DELETE FROM weekly_plan_items WHERE user_id = ? AND week_start = ? AND task_id = ?")
                .params(currentUser.id(), weekStart.toString(), taskId).update();
    }
}
