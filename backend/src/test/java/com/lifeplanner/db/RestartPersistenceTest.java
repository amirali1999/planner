package com.lifeplanner.db;

import com.lifeplanner.PlannerApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Full application restart against the same file: data survives and V1 is recorded exactly once. */
class RestartPersistenceTest {

    @TempDir
    Path dir;

    private ConfigurableApplicationContext start(Path dbFile) {
        return new SpringApplicationBuilder(PlannerApplication.class)
                .web(WebApplicationType.NONE)
                // Command-line arg: must outrank the test config/application.yml (builder .properties() would not).
                .run("--planner.datasource.path=" + dbFile);
    }

    @Test
    void dataSurvivesRestartAndMigrationsRunOnce() {
        Path dbFile = dir.resolve("nested/data/planner.db");
        String goalId;
        String taskId;
        try (ConfigurableApplicationContext ctx = start(dbFile)) {
            JdbcClient db = ctx.getBean(JdbcClient.class);
            goalId = TestRows.goal(db);
            taskId = TestRows.task(db, goalId);
            TestRows.checkIn(db, TestRows.keyResult(db, goalId, "20", "60", "1"), "40");
        }
        assertThat(Files.exists(dbFile)).as("parent directories are created").isTrue();

        try (ConfigurableApplicationContext ctx = start(dbFile)) {
            JdbcClient db = ctx.getBean(JdbcClient.class);
            assertThat(db.sql("SELECT goal_id FROM tasks WHERE id = ?").param(taskId).query(String.class).single())
                    .isEqualTo(goalId);
            assertThat(db.sql("SELECT count(*) FROM key_result_checkins").query(Integer.class).single()).isEqualTo(1);
            assertThat(db.sql("SELECT count(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL")
                    .query(Integer.class).single()).isEqualTo(MigrationCount.APPLIED);
        }
    }
}
