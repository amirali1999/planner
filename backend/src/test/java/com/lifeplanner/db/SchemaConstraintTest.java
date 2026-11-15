package com.lifeplanner.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static com.lifeplanner.db.TestRows.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Database-level invariants of V1, exercised through the application's pooled DataSource. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SchemaConstraintTest {

    @Autowired
    JdbcClient db;

    // ---- foreign keys -------------------------------------------------------------------------

    @Test
    void taskWithUnknownGoalIsRejected() {
        assertThatThrownBy(() -> TestRows.task(db, TestRows.id()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("FOREIGN KEY");
    }

    @Test
    void taskWithUnknownFocusItemIsRejected() {
        assertThatThrownBy(() -> db.sql("""
                INSERT INTO tasks (id, title, focus_item_id, status, created_at, updated_at)
                VALUES (?, 't', ?, 'not_started', ?, ?)""").params(TestRows.id(), TestRows.id(), NOW, NOW).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("FOREIGN KEY");
    }

    @Test
    void taskWithoutGoalOrFocusItemIsAllowed() {
        String taskId = TestRows.task(db, null);
        assertThat(db.sql("SELECT status FROM tasks WHERE id = ?").param(taskId).query(String.class).single())
                .isEqualTo("not_started");
    }

    @Test
    void referencedGoalCannotBeDeletedNoCascade() {
        String goalId = TestRows.goal(db);
        TestRows.keyResult(db, goalId, "20", "80", "1");
        assertThatThrownBy(() -> db.sql("DELETE FROM goals WHERE id = ?").param(goalId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("FOREIGN KEY");
    }

    // ---- goals ----------------------------------------------------------------------------------

    @Test
    void goalCannotBeItsOwnParent() {
        String goalId = TestRows.goal(db);
        assertThatThrownBy(() -> db.sql("UPDATE goals SET parent_goal_id = id WHERE id = ?").param(goalId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("CHECK");
    }

    @Test
    void standaloneAndChildGoalsAreAllowed() {
        String parent = TestRows.goal(db);
        String child = TestRows.goal(db, parent);
        assertThat(db.sql("SELECT parent_goal_id FROM goals WHERE id = ?").param(child).query(String.class).single())
                .isEqualTo(parent);
    }

    @Test
    void unknownGoalStatusIsRejected() {
        String goalId = TestRows.goal(db);
        assertThatThrownBy(() -> db.sql("UPDATE goals SET status = 'completed' WHERE id = ?").param(goalId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("CHECK");
    }

    @Test
    void impossibleOrMalformedDatesAreRejected() {
        String goalId = TestRows.goal(db);
        for (String bad : new String[]{"2026-02-30", "2026-13-01", "2026-1-01", "1405-08-12x", "2026/11/03"}) {
            assertThatThrownBy(() -> db.sql("UPDATE goals SET deadline = ? WHERE id = ?").params(bad, goalId).update())
                    .as(bad).isInstanceOf(DataAccessException.class);
        }
        db.sql("UPDATE goals SET deadline = '2028-02-29' WHERE id = ?").param(goalId).update();
    }

    @Test
    void periodEndBeforeStartIsRejected() {
        String goalId = TestRows.goal(db);
        assertThatThrownBy(() -> db.sql("UPDATE goals SET period_end = '2026-09-22' WHERE id = ?").param(goalId).update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void nonCanonicalTimestampIsRejected() {
        String goalId = TestRows.goal(db);
        for (String bad : new String[]{"2026-11-03T12:00:00Z", "2026-11-03 12:00:00.000", "2026-11-03T12:00:00.000+03:30"}) {
            assertThatThrownBy(() -> db.sql("UPDATE goals SET updated_at = ? WHERE id = ?").params(bad, goalId).update())
                    .as(bad).isInstanceOf(DataAccessException.class);
        }
    }

    // ---- key results & check-ins ------------------------------------------------------------------

    @Test
    void goalMayHaveMultipleKeyResults() {
        String goalId = TestRows.goal(db);
        TestRows.keyResult(db, goalId, "20", "80", "1");
        TestRows.keyResult(db, goalId, "100", "60", "3");
        assertThat(db.sql("SELECT count(*) FROM key_results WHERE goal_id = ?").param(goalId)
                .query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void decimalsRoundTripExactly() {
        String goalId = TestRows.goal(db);
        String kr = TestRows.keyResult(db, goalId, "0.1", "-0.000001", "0.333333");
        assertThat(db.sql("SELECT baseline || '|' || target || '|' || weight FROM key_results WHERE id = ?")
                .param(kr).query(String.class).single()).isEqualTo("0.1|-0.000001|0.333333");
    }

    @Test
    void baselineEqualToTargetIsRejected() {
        String goalId = TestRows.goal(db);
        assertThatThrownBy(() -> TestRows.keyResult(db, goalId, "20", "20", "1"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("CHECK");
    }

    @Test
    void nonPositiveOrMalformedWeightIsRejected() {
        String goalId = TestRows.goal(db);
        for (String bad : new String[]{"0", "-1", "0.0", "abc", "1e3", "1.2.3"}) {
            assertThatThrownBy(() -> TestRows.keyResult(db, goalId, "20", "80", bad))
                    .as(bad).isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void nonDecimalMeasuresAreRejected() {
        String goalId = TestRows.goal(db);
        for (String bad : new String[]{"NaN", "Infinity", "1e5", "--1", "1-", ""}) {
            assertThatThrownBy(() -> TestRows.keyResult(db, goalId, bad, "80", "1"))
                    .as(bad).isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> db.sql("""
                INSERT INTO key_results (id, goal_id, name, baseline, target, weight, created_at, updated_at)
                VALUES (?, ?, 'blob', X'3230', '80', '1', ?, ?)""").params(TestRows.id(), goalId, NOW, NOW).update())
                .as("BLOB is never a decimal").isInstanceOf(DataAccessException.class);
    }

    @Test
    void numericLiteralIsCoercedToTextByColumnAffinity() {
        // TEXT affinity converts numbers before CHECK runs, so the stored value is still decimal text, never REAL.
        String goalId = TestRows.goal(db);
        String id = TestRows.id();
        db.sql("""
                INSERT INTO key_results (id, goal_id, name, baseline, target, weight, created_at, updated_at)
                VALUES (?, ?, 'real', 20.5, 80, 1, ?, ?)""").params(id, goalId, NOW, NOW).update();
        assertThat(db.sql("SELECT typeof(baseline) || ':' || baseline FROM key_results WHERE id = ?")
                .param(id).query(String.class).single()).isEqualTo("text:20.5");
    }

    @Test
    void checkInsAreAppendOnly() {
        String kr = TestRows.keyResult(db, TestRows.goal(db), "20", "60", "1");
        String checkIn = TestRows.checkIn(db, kr, "40");
        TestRows.checkIn(db, kr, "45"); // a correction is a new row

        assertThatThrownBy(() -> db.sql("UPDATE key_result_checkins SET value = '41' WHERE id = ?").param(checkIn).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> db.sql("DELETE FROM key_result_checkins WHERE id = ?").param(checkIn).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(db.sql("SELECT count(*) FROM key_result_checkins WHERE key_result_id = ?").param(kr)
                .query(Integer.class).single()).isEqualTo(2);
    }

    // ---- tasks ----------------------------------------------------------------------------------

    @Test
    void taskHasOnlyThreeStatuses() {
        String taskId = TestRows.task(db, null);
        assertThatThrownBy(() -> db.sql("UPDATE tasks SET status = 'blocked' WHERE id = ?").param(taskId).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("CHECK");
        db.sql("UPDATE tasks SET status = 'doing', blocked_reason = 'منتظر داده' WHERE id = ?").param(taskId).update();
    }

    @Test
    void estimatedMinutesMustBeNonNegativeIntegerOrNull() {
        String taskId = TestRows.task(db, null);
        db.sql("UPDATE tasks SET estimated_minutes = 0 WHERE id = ?").param(taskId).update();
        assertThatThrownBy(() -> db.sql("UPDATE tasks SET estimated_minutes = -1 WHERE id = ?").param(taskId).update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> db.sql("UPDATE tasks SET estimated_minutes = 1.5 WHERE id = ?").param(taskId).update())
                .isInstanceOf(DataAccessException.class);
    }

    // ---- daily plans ------------------------------------------------------------------------------

    @Test
    void dailyPlanDateIsUniquePerUser() {
        String a = TestRows.user(db, "plan-a@test.local");
        String b = TestRows.user(db, "plan-b@test.local");
        TestRows.dailyPlan(db, "2031-01-01", a);
        assertThatThrownBy(() -> TestRows.dailyPlan(db, "2031-01-01", a))
                .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("UNIQUE");
        TestRows.dailyPlan(db, "2031-01-01", b); // another account may plan the same day
    }

    @Test
    void userEmailIsUniqueAndNormalized() {
        TestRows.user(db, "unique@test.local");
        assertThatThrownBy(() -> TestRows.user(db, "unique@test.local")).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> TestRows.user(db, "Upper@Test.local")).hasMessageContaining("CHECK");
    }

    @Test
    void sameTaskTwiceInOnePlanIsRejectedButAllowedOnAnotherDate() {
        String taskId = TestRows.task(db, null);
        String plan = TestRows.dailyPlan(db, "2031-02-01");
        TestRows.planItem(db, plan, taskId);
        assertThatThrownBy(() -> TestRows.planItem(db, plan, taskId))
                .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("UNIQUE");

        String otherDay = TestRows.dailyPlan(db, "2031-02-02");
        TestRows.planItem(db, otherDay, taskId);
    }

    @Test
    void planItemWithUnknownTaskIsRejected() {
        String plan = TestRows.dailyPlan(db, "2031-03-01");
        assertThatThrownBy(() -> TestRows.planItem(db, plan, TestRows.id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("FOREIGN KEY");
    }
}
