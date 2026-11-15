package com.lifeplanner.db;

/** Number of versioned Flyway migrations in src/main/resources/db/migration. Bump when adding V<n>__*.sql. */
public final class MigrationCount {

    public static final int APPLIED = 17;

    private MigrationCount() {
    }
}
