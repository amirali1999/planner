package com.lifeplanner.repository;

import com.lifeplanner.service.CurrentUser;
import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.DailyPlan;
import com.lifeplanner.model.DailyPlanItem;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class DailyPlanRepository {

    private static final RowMapper<DailyPlan> PLAN_MAPPER = (rs, n) -> new DailyPlan(
            rs.getString("id"),
            LocalDate.parse(rs.getString("local_date")),
            UtcTimestamps.fromDb(rs.getString("created_at")),
            UtcTimestamps.fromDb(rs.getString("updated_at")));

    private static final RowMapper<DailyPlanItem> ITEM_MAPPER = (rs, n) -> new DailyPlanItem(
            rs.getString("id"),
            rs.getString("plan_id"),
            rs.getString("task_id"),
            rs.getInt("position"),
            UtcTimestamps.fromDb(rs.getString("created_at")));

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public DailyPlanRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public Optional<DailyPlan> findByDate(LocalDate date) {
        return db.sql("SELECT id, local_date, created_at, updated_at FROM daily_plans WHERE local_date = ? AND user_id = ?")
                .params(date.toString(), user()).query(PLAN_MAPPER).optional();
    }

    public void insert(DailyPlan plan) {
        db.sql("INSERT INTO daily_plans (id, local_date, created_at, updated_at, user_id) VALUES (?, ?, ?, ?, ?)")
                .params(plan.id(), plan.localDate().toString(),
                        UtcTimestamps.toDb(plan.createdAt()), UtcTimestamps.toDb(plan.updatedAt()), user())
                .update();
    }

    public void touch(String planId, Instant updatedAt) {
        db.sql("UPDATE daily_plans SET updated_at = ? WHERE id = ? AND user_id = ?").params(UtcTimestamps.toDb(updatedAt), planId, user()).update();
    }

    public List<DailyPlanItem> findItems(String planId) {
        return db.sql("""
                SELECT id, plan_id, task_id, position, created_at FROM daily_plan_items
                WHERE plan_id = ? AND plan_id IN (SELECT id FROM daily_plans WHERE user_id = ?) ORDER BY position, created_at, id""")
                .params(planId, user()).query(ITEM_MAPPER).list();
    }

    public boolean containsTask(String planId, String taskId) {
        return db.sql("SELECT count(*) FROM daily_plan_items WHERE plan_id = ? AND task_id = ? AND plan_id IN (SELECT id FROM daily_plans WHERE user_id = ?)")
                .params(planId, taskId, user()).query(Integer.class).single() > 0;
    }

    public int nextPosition(String planId) {
        return db.sql("SELECT coalesce(max(position) + 1, 0) FROM daily_plan_items WHERE plan_id = ? AND plan_id IN (SELECT id FROM daily_plans WHERE user_id = ?)")
                .params(planId, user()).query(Integer.class).single();
    }

    public void setItemPosition(String planId, String itemId, int position) {
        db.sql("UPDATE daily_plan_items SET position = ? WHERE id = ? AND plan_id = ? AND plan_id IN (SELECT id FROM daily_plans WHERE user_id = ?)")
                .params(position, itemId, planId, user()).update();
    }

    public void insertItem(DailyPlanItem item) {
        db.sql("INSERT INTO daily_plan_items (id, plan_id, task_id, position, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(item.id(), item.planId(), item.taskId(), item.position(), UtcTimestamps.toDb(item.createdAt()))
                .update();
    }

    public Optional<DailyPlanItem> findItemByTask(String planId, String taskId) {
        return db.sql("""
                SELECT id, plan_id, task_id, position, created_at FROM daily_plan_items
                WHERE plan_id = ? AND task_id = ? AND plan_id IN (SELECT id FROM daily_plans WHERE user_id = ?)""")
                .params(planId, taskId, user()).query(ITEM_MAPPER).optional();
    }

    public int deleteItem(String planId, String itemId) {
        return db.sql("DELETE FROM daily_plan_items WHERE id = ? AND plan_id = ? AND plan_id IN (SELECT id FROM daily_plans WHERE user_id = ?)")
                .params(itemId, planId, user()).update();
    }
}
