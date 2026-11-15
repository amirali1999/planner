package com.lifeplanner.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Builds the pooled SQLite DataSource. The {@link SQLiteConfig} pragmas are applied by the driver
 * every time it opens a physical connection, so every pooled connection enforces foreign keys and
 * has a bounded busy timeout (SQLite resets both per connection; a one-off startup PRAGMA is not enough).
 * <p>
 * Never deletes or truncates an existing database file; it only creates the parent directory.
 */
public final class SqliteDataSources {

    private SqliteDataSources() {
    }

    public static HikariDataSource create(Path dbPath, Duration busyTimeout, int maxPoolSize) {
        if (busyTimeout.isNegative() || busyTimeout.isZero() || busyTimeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("busyTimeout must be positive and bounded");
        }
        if (maxPoolSize < 1) {
            throw new IllegalArgumentException("maxPoolSize must be >= 1");
        }
        Path file = dbPath.toAbsolutePath().normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create database directory " + file.getParent(), e);
        }

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout((int) busyTimeout.toMillis());

        SQLiteDataSource sqlite = new SQLiteDataSource(config);
        sqlite.setUrl("jdbc:sqlite:" + file);

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("planner-sqlite");
        hikari.setDataSource(sqlite);
        hikari.setMaximumPoolSize(maxPoolSize);
        hikari.setMinimumIdle(1);
        return new HikariDataSource(hikari);
    }
}
