package com.lifeplanner.db;

import org.springframework.jdbc.core.simple.JdbcClient;
import java.time.Instant;
import java.util.UUID;

/** Minimal valid-row inserts for schema tests. Returns the new id. */
final class TestRows {

    static final String NOW = UtcTimestamps.toDb(Instant.parse("2026-11-03T12:00:00Z"));

    private TestRows() {
    }

    static String id() {
        return UUID.randomUUID().toString();
    }

    static String goal(JdbcClient db) {
        return goal(db, null);
    }

    static String goal(JdbcClient db, String parentId) {
        String id = id();
        db.sql("""
                INSERT INTO goals (id, title, period_type, period_start, period_end, parent_goal_id, created_at, updated_at)
                VALUES (?, 'یادگیری RAG', 'quarterly', '2026-09-23', '2026-12-21', ?, ?, ?)""")
                .params(id, parentId, NOW, NOW).update();
        return id;
    }

    static String keyResult(JdbcClient db, String goalId, String baseline, String target, String weight) {
        String id = id();
        db.sql("""
                INSERT INTO key_results (id, goal_id, name, unit, baseline, target, weight, created_at, updated_at)
                VALUES (?, ?, 'Recall@5', '%', ?, ?, ?, ?, ?)""")
                .params(id, goalId, baseline, target, weight, NOW, NOW).update();
        return id;
    }

    static String checkIn(JdbcClient db, String krId, String value) {
        String id = id();
        db.sql("""
                INSERT INTO key_result_checkins (id, key_result_id, value, measured_at, note, created_at)
                VALUES (?, ?, ?, ?, 'evaluation set v1', ?)""")
                .params(id, krId, value, NOW, NOW).update();
        return id;
    }

    static String task(JdbcClient db, String goalId) {
        String id = id();
        db.sql("""
                INSERT INTO tasks (id, title, goal_id, status, created_at, updated_at)
                VALUES (?, 'مطالعه فصل دوم', ?, 'not_started', ?, ?)""")
                .params(id, goalId, NOW, NOW).update();
        return id;
    }

    static String dailyPlan(JdbcClient db, String localDate) {
        String id = id();
        db.sql("INSERT INTO daily_plans (id, local_date, created_at, updated_at) VALUES (?, ?, ?, ?)")
                .params(id, localDate, NOW, NOW).update();
        return id;
    }

    static String user(JdbcClient db, String email) {
        String id = id();
        db.sql("INSERT INTO users (id, email, password_hash, created_at, updated_at) VALUES (?, ?, 'x', ?, ?)")
                .params(id, email, NOW, NOW).update();
        return id;
    }

    static String dailyPlan(JdbcClient db, String localDate, String userId) {
        String id = id();
        db.sql("INSERT INTO daily_plans (id, local_date, created_at, updated_at, user_id) VALUES (?, ?, ?, ?, ?)")
                .params(id, localDate, NOW, NOW, userId).update();
        return id;
    }

    static String planItem(JdbcClient db, String planId, String taskId) {
        String id = id();
        db.sql("INSERT INTO daily_plan_items (id, plan_id, task_id, position, created_at) VALUES (?, ?, ?, 0, ?)")
                .params(id, planId, taskId, NOW).update();
        return id;
    }
}
