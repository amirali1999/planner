package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.service.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

/**
 * Permanent deletion of archived goals, focus items and tasks (the archive page). Every statement is scoped to the
 * current user; child rows (plan items, key results, check-ins, milestones, history) are reached through their owner.
 * Callers run inside one transaction and delete children before parents (no ON DELETE CASCADE in the schema).
 */
@Repository
public class DeletionRepository {

    private final JdbcClient db;
    private final CurrentUser currentUser;

    public DeletionRepository(JdbcClient db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    private String user() {
        return currentUser.id();
    }

    public List<String> focusItemIdsOfGoal(String goalId) {
        return db.sql("SELECT id FROM focus_items WHERE goal_id = ? AND user_id = ? ORDER BY created_at, id")
                .params(goalId, user()).query(String.class).list();
    }

    /** Tasks linked to the goal directly or through one of its focus items (archived ones included). */
    public List<String> taskIdsOfGoal(String goalId) {
        return db.sql("""
                SELECT id FROM tasks WHERE user_id = ?
                  AND (goal_id = ? OR focus_item_id IN (SELECT id FROM focus_items WHERE goal_id = ? AND user_id = ?))
                ORDER BY created_at, id""")
                .params(user(), goalId, goalId, user()).query(String.class).list();
    }

    public List<String> taskIdsOfFocusItem(String focusItemId) {
        return db.sql("SELECT id FROM tasks WHERE user_id = ? AND focus_item_id = ? ORDER BY created_at, id")
                .params(user(), focusItemId).query(String.class).list();
    }

    public List<String> childGoalIds(String goalId) {
        return db.sql("SELECT id FROM goals WHERE parent_goal_id = ? AND user_id = ? ORDER BY created_at, id")
                .params(goalId, user()).query(String.class).list();
    }

    public void detachFromParent(String childGoalId, Instant now) {
        db.sql("UPDATE goals SET parent_goal_id = NULL, updated_at = ? WHERE id = ? AND user_id = ?")
                .params(UtcTimestamps.toDb(now), childGoalId, user()).update();
    }

    /** Removes the task's time blocks, weekly and daily plan items, then the task. */
    public void deleteTask(String taskId) {
        db.sql("DELETE FROM time_blocks WHERE task_id = ? AND user_id = ?").params(taskId, user()).update();
        db.sql("DELETE FROM weekly_plan_items WHERE task_id = ? AND user_id = ?").params(taskId, user()).update();
        db.sql("DELETE FROM daily_plan_items WHERE task_id = ? AND task_id IN (SELECT id FROM tasks WHERE user_id = ?)")
                .params(taskId, user()).update();
        db.sql("DELETE FROM tasks WHERE id = ? AND user_id = ?").params(taskId, user()).update();
    }

    /** The focus item must have no tasks left. */
    public void deleteFocusItem(String focusItemId) {
        db.sql("DELETE FROM focus_item_notes WHERE focus_item_id = ? AND focus_item_id IN (SELECT id FROM focus_items WHERE user_id = ?)")
                .params(focusItemId, user()).update();
        db.sql("DELETE FROM focus_items WHERE id = ? AND user_id = ?").params(focusItemId, user()).update();
    }

    /**
     * The goal with its milestones, history, check-ins and key results. The goal must be archived (the V10 triggers
     * only allow deleting history/check-ins of archived goals) and have no focus items, tasks or children left.
     */
    public void deleteGoal(String goalId) {
        String owned = "(SELECT id FROM goals WHERE id = ? AND user_id = ?)";
        db.sql("DELETE FROM milestones WHERE goal_id IN " + owned).params(goalId, user()).update();
        db.sql("DELETE FROM goal_history WHERE goal_id IN " + owned).params(goalId, user()).update();
        db.sql("DELETE FROM key_result_checkins WHERE key_result_id IN (SELECT id FROM key_results WHERE goal_id IN " + owned + ")")
                .params(goalId, user()).update();
        db.sql("DELETE FROM key_results WHERE goal_id IN " + owned).params(goalId, user()).update();
        db.sql("DELETE FROM goals WHERE id = ? AND user_id = ?").params(goalId, user()).update();
    }
}
