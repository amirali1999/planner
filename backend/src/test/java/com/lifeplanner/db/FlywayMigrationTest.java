package com.lifeplanner.db;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteException;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real migrations against isolated temporary SQLite files (no Spring context). */
class FlywayMigrationTest {

    @TempDir
    Path dir;

    private static HikariDataSource open(Path file) {
        return SqliteDataSources.create(file, Duration.ofSeconds(2), 2);
    }

    private static Flyway flyway(DataSource ds) {
        return Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
    }

    @Test
    void freshDatabaseGetsAllDemo0TablesExactlyOnce() {
        try (HikariDataSource ds = open(dir.resolve("fresh.db"))) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED);
            assertThat(flyway(ds).migrate().migrationsExecuted).isZero();

            JdbcClient db = JdbcClient.create(ds);
            List<String> tables = db.sql("""
                    SELECT name FROM sqlite_master
                    WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> 'flyway_schema_history'
                    ORDER BY name""").query(String.class).list();
            assertThat(tables).containsExactly(
                    "activity_logs", "activity_timers", "daily_plan_items", "daily_plans", "focus_item_notes", "focus_items", "goal_history", "goals", "habit_checks", "habits", "inbox_items",
                    "key_result_checkins", "key_results", "milestones", "refresh_tokens", "signing_keys", "task_notes", "tasks", "time_blocks", "users", "weekly_plan_items");
            assertThat(db.sql("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = 1")
                    .query(Integer.class).single()).isEqualTo(1);
            // v1 single-KPI model must not come back.
            assertThat(db.sql("SELECT count(*) FROM sqlite_master WHERE name = 'goal_kpis'")
                    .query(Integer.class).single()).isZero();
        }
    }

    @Test
    void reopeningPopulatedDatabaseKeepsDataAndAppliesNothing() {
        Path file = dir.resolve("upgrade.db");
        String goalId;
        try (HikariDataSource ds = open(file)) {
            flyway(ds).migrate();
            goalId = TestRows.goal(JdbcClient.create(ds));
        }
        try (HikariDataSource ds = open(file)) {
            Flyway again = flyway(ds);
            again.validate();
            assertThat(again.migrate().migrationsExecuted).isZero();
            assertThat(JdbcClient.create(ds).sql("SELECT title FROM goals WHERE id = ?")
                    .param(goalId).query(String.class).single()).isEqualTo("یادگیری RAG");
        }
    }

    @Test
    void populatedV1DatabaseUpgradesToLatestWithoutDataLoss() {
        Path file = dir.resolve("v1-user.db");
        String goalId;
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true)
                    .target("1").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            goalId = TestRows.goal(db);
            TestRows.checkIn(db, TestRows.keyResult(db, goalId, "20", "60", "1"), "40");
            TestRows.planItem(db, TestRows.dailyPlan(db, "2026-11-03"), TestRows.task(db, goalId));
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 1); // V2..latest
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT title FROM goals WHERE id = ?").param(goalId).query(String.class).single()).isEqualTo("یادگیری RAG");
            assertThat(db.sql("SELECT count(*) FROM key_result_checkins").query(Integer.class).single()).isEqualTo(1);
            assertThat(db.sql("SELECT count(*) FROM daily_plan_items").query(Integer.class).single()).isEqualTo(1);
            assertThat(db.sql("SELECT count(*) FROM goal_history").query(Integer.class).single()).isZero();
            assertThat(db.sql("PRAGMA foreign_key_check").query((rs, n) -> rs.getString(1)).list()).isEmpty();
        }
    }

    @Test
    void populatedV2HistoryIsKeptByTheV3RebuildAndStaysAppendOnly() {
        Path file = dir.resolve("v2-user.db");
        String goalId;
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true)
                    .target("2").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            goalId = TestRows.goal(db);
            String sql = "INSERT INTO goal_history (id, goal_id, event_type, before_json, after_json, created_at) VALUES (?, ?, ?, ?, ?, ?)";
            db.sql(sql).params(TestRows.id(), goalId, "status_changed", "{\"status\":\"not_started\"}", "{\"status\":\"doing\"}", TestRows.NOW).update();
            db.sql(sql).params(TestRows.id(), goalId, "archived", "{\"archivedAt\":null}", "{\"archivedAt\":\"x\"}", TestRows.NOW).update();
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 2); // V3..latest
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT event_type FROM goal_history WHERE goal_id = ? ORDER BY rowid").param(goalId).query(String.class).list())
                    .containsExactly("status_changed", "archived");
            assertThat(db.sql("SELECT count(*) FROM goal_history WHERE key_result_id IS NOT NULL OR note IS NOT NULL")
                    .query(Integer.class).single()).isZero();
            assertThatThrownBy(() -> db.sql("DELETE FROM goal_history").update()).hasMessageContaining("append-only");
            assertThatThrownBy(() -> db.sql("UPDATE goal_history SET note = 'x'").update()).hasMessageContaining("append-only");
            // a KR event must name its KR
            assertThatThrownBy(() -> db.sql("INSERT INTO goal_history (id, goal_id, event_type, before_json, after_json, created_at) VALUES (?, ?, 'key_result_changed', '{}', '{}', ?)")
                    .params(TestRows.id(), goalId, TestRows.NOW).update()).hasMessageContaining("CHECK");
            assertThat(db.sql("PRAGMA foreign_key_check").query((rs, n) -> rs.getString(1)).list()).isEmpty();
        }
    }

    @Test
    void populatedV5DatabaseGetsOwnershipColumnsAndKeepsEveryRow() {
        Path file = dir.resolve("v5-user.db");
        String goalId;
        String planId;
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true)
                    .target("5").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            goalId = TestRows.goal(db);
            String task = TestRows.task(db, goalId);
            planId = TestRows.dailyPlan(db, "2026-11-03");
            TestRows.planItem(db, planId, task);
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 5); // V6..latest
            JdbcClient db = JdbcClient.create(ds);
            // rows kept, unowned until the first account claims them
            assertThat(db.sql("SELECT user_id FROM goals WHERE id = ?").param(goalId).query(String.class).optional()).isEmpty();
            assertThat(db.sql("SELECT count(*) FROM goals WHERE id = ? AND user_id IS NULL").param(goalId).query(Integer.class).single()).isEqualTo(1);
            assertThat(db.sql("SELECT count(*) FROM daily_plans WHERE id = ?").param(planId).query(Integer.class).single()).isEqualTo(1);
            assertThat(db.sql("SELECT count(*) FROM daily_plan_items WHERE plan_id = ?").param(planId).query(Integer.class).single()).isEqualTo(1);
            // the rebuilt child table points at the rebuilt parent
            assertThat(db.sql("SELECT \"table\" FROM pragma_foreign_key_list('daily_plan_items') WHERE \"from\" = 'plan_id'")
                    .query(String.class).single()).isEqualTo("daily_plans");
            assertThat(db.sql("PRAGMA foreign_key_check").query((rs, n) -> rs.getString(1)).list()).isEmpty();
        }
    }

    @Test
    void v7RanksExistingActiveGoalsInDisplayOrderPerOwner() {
        Path file = dir.resolve("v6-user.db");
        String annual, monthlyEarly, monthlyLate, archived;
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).target("6").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            String sql = "INSERT INTO goals (id, title, period_type, period_start, period_end, created_at, updated_at, archived_at) VALUES (?, 'g', ?, ?, ?, ?, ?, ?)";
            monthlyLate = TestRows.id();
            db.sql(sql).params(monthlyLate, "monthly", "2026-10-23", "2026-11-21", TestRows.NOW, TestRows.NOW, null).update();
            annual = TestRows.id();
            db.sql(sql).params(annual, "annual", "2026-03-21", "2027-03-20", TestRows.NOW, TestRows.NOW, null).update();
            monthlyEarly = TestRows.id();
            db.sql(sql).params(monthlyEarly, "monthly", "2026-09-23", "2026-10-22", TestRows.NOW, TestRows.NOW, null).update();
            archived = TestRows.id();
            db.sql(sql).params(archived, "monthly", "2026-01-01", "2026-01-31", TestRows.NOW, TestRows.NOW, TestRows.NOW).update();
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 6); // V7..latest
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT id FROM goals WHERE priority IS NOT NULL ORDER BY priority").query(String.class).list())
                    .containsExactly(annual, monthlyEarly, monthlyLate);
            assertThat(db.sql("SELECT priority FROM goals WHERE id = ?").param(archived).query(Integer.class).optional()).isEmpty();
        }
    }

    @Test
    void v8NumbersExistingFocusItemsPerGoalInCreationOrder() {
        Path file = dir.resolve("v7-user.db");
        String goalA, goalB, a1, a2, b1, free;
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).target("7").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            goalA = TestRows.goal(db);
            goalB = TestRows.goal(db);
            String sql = "INSERT INTO focus_items (id, title, kind, goal_id, status, created_at, updated_at) VALUES (?, 't', 'other', ?, 'not_started', ?, ?)";
            a2 = TestRows.id();
            db.sql(sql).params(a2, goalA, "2026-11-02T00:00:00.000Z", TestRows.NOW).update();
            a1 = TestRows.id();
            db.sql(sql).params(a1, goalA, "2026-11-01T00:00:00.000Z", TestRows.NOW).update();
            b1 = TestRows.id();
            db.sql(sql).params(b1, goalB, "2026-11-03T00:00:00.000Z", TestRows.NOW).update();
            free = TestRows.id();
            db.sql(sql).params(free, null, "2026-11-01T00:00:00.000Z", TestRows.NOW).update();
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 7);
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT id FROM focus_items WHERE goal_id = ? ORDER BY position").param(goalA).query(String.class).list())
                    .containsExactly(a1, a2);
            assertThat(db.sql("SELECT position FROM focus_items WHERE id = ?").param(b1).query(Integer.class).single()).isEqualTo(1);
            assertThat(db.sql("SELECT count(*) FROM focus_items WHERE id = ? AND position IS NULL").param(free).query(Integer.class).single()).isEqualTo(1);
        }
    }

    @Test
    void v9GivesTasksWithoutPriorityTheDefaultLow() {
        Path file = dir.resolve("v8-user.db");
        String none, high;
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).target("8").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            none = TestRows.task(db, null);
            high = TestRows.task(db, null);
            db.sql("UPDATE tasks SET priority = 'high' WHERE id = ?").param(high).update();
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 8);
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT priority FROM tasks WHERE id = ?").param(none).query(String.class).single()).isEqualTo("low");
            assertThat(db.sql("SELECT priority FROM tasks WHERE id = ?").param(high).query(String.class).single()).isEqualTo("high");
        }
    }

    @Test
    void v11BackfillsCompletedAtOfDoneTasksOnly() {
        Path file = dir.resolve("v10-user.db");
        String open, done;
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).target("10").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            open = TestRows.task(db, null);
            done = TestRows.task(db, null);
            db.sql("UPDATE tasks SET status = 'done', updated_at = '2026-11-08T10:15:00.000Z' WHERE id = ?").param(done).update();
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 10);
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT completed_at FROM tasks WHERE id = ?").param(done).query(String.class).single())
                    .isEqualTo("2026-11-08T10:15:00.000Z");
            assertThat(db.sql("SELECT count(*) FROM tasks WHERE id = ? AND completed_at IS NULL").param(open).query(Integer.class).single())
                    .isEqualTo(1);
            assertThatThrownBy(() -> db.sql("UPDATE tasks SET completed_at = 'yesterday' WHERE id = ?").param(done).update())
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void editedAppliedMigrationIsRejectedNotReapplied() {
        Path file = dir.resolve("tampered.db");
        try (HikariDataSource ds = open(file)) {
            flyway(ds).migrate();
            JdbcClient.create(ds).sql("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'").update();
            assertThatThrownBy(() -> flyway(ds).migrate()).isInstanceOf(FlywayException.class);
        }
    }

    @Test
    void v17PreservesPopulatedV16LogsAndAcceptsMinutePrecision() {
        Path file = dir.resolve("v16-log.db");
        String owner = TestRows.id();
        String log = TestRows.id();
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).target("16").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            db.sql("INSERT INTO users(id,email,password_hash,created_at,updated_at) VALUES (?,?,?,?,?)")
                    .params(owner,"upgrade@test.local","hash",TestRows.NOW,TestRows.NOW).update();
            db.sql("INSERT INTO activity_logs(id,user_id,local_date,title,category,start_minute,end_minute,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)")
                    .params(log,owner,"2026-11-14","keep","work",600,660,TestRows.NOW,TestRows.NOW).update();
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 16);
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT title FROM activity_logs WHERE id=?").param(log).query(String.class).single()).isEqualTo("keep");
            db.sql("UPDATE activity_logs SET start_minute=601 WHERE id=?").param(log).update();
            assertThat(db.sql("PRAGMA foreign_key_check").query((rs,n)->rs.getString(1)).list()).isEmpty();
        }
    }

    @Test
    void v18PreservesRunningTimerAndAddsBoundedPomodoroDuration() {
        Path file = dir.resolve("v17-timer.db");
        String owner = TestRows.id();
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).target("17").load().migrate();
            JdbcClient db = JdbcClient.create(ds);
            db.sql("INSERT INTO users(id,email,password_hash,created_at,updated_at) VALUES (?,?,?,?,?)")
                    .params(owner,"pomodoro-upgrade@test.local","hash",TestRows.NOW,TestRows.NOW).update();
            db.sql("INSERT INTO activity_timers(user_id,title,started_at,time_zone) VALUES (?,?,?,?)")
                    .params(owner,"keep running",TestRows.NOW,"Asia/Tehran").update();
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 17);
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT title FROM activity_timers WHERE user_id=?").param(owner).query(String.class).single())
                    .isEqualTo("keep running");
            assertThat(db.sql("SELECT count(*) FROM activity_timers WHERE user_id=? AND planned_minutes IS NULL")
                    .param(owner).query(Integer.class).single()).isEqualTo(1);
            db.sql("UPDATE activity_timers SET planned_minutes=25 WHERE user_id=?").param(owner).update();
            assertThatThrownBy(() -> db.sql("UPDATE activity_timers SET planned_minutes=121 WHERE user_id=?")
                    .param(owner).update()).isInstanceOf(Exception.class);
        }
    }

    @Test
    void v19PreservesExistingTasksAndCreatesNotesWithEnforcedTaskOwnershipLink() {
        Path file = dir.resolve("v18-tasks.db");
        String taskId;
        try (HikariDataSource ds = open(file)) {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(true).target("18").load().migrate();
            taskId = TestRows.task(JdbcClient.create(ds), null);
        }
        String noteId = TestRows.id();
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isEqualTo(MigrationCount.APPLIED - 18);
            JdbcClient db = JdbcClient.create(ds);
            assertThat(db.sql("SELECT title FROM tasks WHERE id = ?")
                    .param(taskId).query(String.class).single()).isEqualTo("مطالعه فصل دوم");
            String insert = "INSERT INTO task_notes (id, task_id, body, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
            assertThatThrownBy(() -> db.sql(insert).params(noteId, TestRows.id(), "یادداشت", TestRows.NOW, TestRows.NOW)
                    .update()).hasRootCauseInstanceOf(SQLiteException.class).hasMessageContaining("FOREIGN KEY constraint failed");
            assertThat(db.sql(insert).params(noteId, taskId, "یادداشت", TestRows.NOW, TestRows.NOW).update()).isEqualTo(1);
        }
        try (HikariDataSource ds = open(file)) {
            assertThat(flyway(ds).migrate().migrationsExecuted).isZero();
            assertThat(JdbcClient.create(ds).sql("SELECT body FROM task_notes WHERE id = ?")
                    .param(noteId).query(String.class).single()).isEqualTo("یادداشت");
        }
    }

    @Test
    void cleanIsDisabled() {
        try (HikariDataSource ds = open(dir.resolve("noclean.db"))) {
            Flyway flyway = flyway(ds);
            flyway.migrate();
            assertThatThrownBy(flyway::clean).isInstanceOf(FlywayException.class);
        }
    }
}
