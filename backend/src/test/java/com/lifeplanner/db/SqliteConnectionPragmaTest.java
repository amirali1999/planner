package com.lifeplanner.db;

import com.lifeplanner.config.PlannerProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SqliteConnectionPragmaTest {

    @Autowired
    HikariDataSource dataSource;

    @Autowired
    PlannerProperties properties;

    @TempDir
    Path dir;

    @Test
    void testsNeverUseTheProductionDatabase() {
        Path used = properties.datasource().file().toAbsolutePath().normalize();
        assertThat(used.toString()).contains("target" + java.io.File.separator + "test-db");
        assertThat(used.getFileName().toString()).isNotEqualTo("planner.db");
    }

    @Test
    void everyPooledConnectionEnforcesForeignKeysAndBusyTimeout() throws SQLException {
        int size = dataSource.getMaximumPoolSize();
        List<Connection> held = new ArrayList<>();
        try {
            // Hold the whole pool at once so every physical connection is inspected, not just one reused one.
            for (int i = 0; i < size; i++) {
                held.add(dataSource.getConnection());
            }
            for (Connection c : held) {
                assertConnectionEnforcesInvariants(c);
            }
        } finally {
            for (Connection c : held) {
                c.close();
            }
        }

        // Connections created after eviction get the same pragmas.
        dataSource.getHikariPoolMXBean().softEvictConnections();
        try (Connection fresh = dataSource.getConnection()) {
            assertConnectionEnforcesInvariants(fresh);
        }
    }

    private static void assertConnectionEnforcesInvariants(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            assertThat(pragma(st, "foreign_keys")).isEqualTo(1);
            assertThat(pragma(st, "busy_timeout")).isEqualTo(5000);
            // Behavior, not just the flag: an orphan task must be rejected on this very connection.
            assertThatThrownBy(() -> st.executeUpdate("""
                    INSERT INTO tasks (id, title, goal_id, status, created_at, updated_at)
                    VALUES ('00000000-0000-0000-0000-00000000dead', 't', '00000000-0000-0000-0000-000000000000',
                            'not_started', '2026-11-03T12:00:00.000Z', '2026-11-03T12:00:00.000Z')"""))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("FOREIGN KEY");
        }
    }

    private static int pragma(Statement st, String name) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA " + name)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void busyTimeoutIsBoundedNotInfinite() throws SQLException {
        try (HikariDataSource ds = SqliteDataSources.create(dir.resolve("busy.db"), Duration.ofMillis(300), 2);
             Connection writer = ds.getConnection();
             Connection blocked = ds.getConnection()) {
            try (Statement st = writer.createStatement()) {
                st.execute("CREATE TABLE t (x INTEGER)");
                st.execute("BEGIN IMMEDIATE");
                st.execute("INSERT INTO t VALUES (1)");
            }
            long start = System.nanoTime();
            assertThatThrownBy(() -> {
                try (Statement st = blocked.createStatement()) {
                    st.execute("INSERT INTO t VALUES (2)");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("SQLITE_BUSY");
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertThat(elapsedMs).isBetween(250L, 4000L);
            try (Statement st = writer.createStatement()) {
                st.execute("ROLLBACK");
            }
        }
    }
}
