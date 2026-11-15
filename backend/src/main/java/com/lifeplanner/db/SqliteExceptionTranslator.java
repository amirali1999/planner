package com.lifeplanner.db;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import java.sql.SQLException;

/**
 * Spring has no SQLite error-code mapping, so every SQLite failure would surface as UncategorizedSQLException.
 * This maps Xerial result codes to Spring's hierarchy so services can react to specific failures
 * (e.g. a UNIQUE race → 409). Returns null for anything else, letting JdbcTemplate fall back.
 */
public class SqliteExceptionTranslator implements SQLExceptionTranslator {

    @Override
    public DataAccessException translate(String task, String sql, SQLException ex) {
        if (!(ex instanceof SQLiteException sqlite)) {
            return null;
        }
        SQLiteErrorCode code = sqlite.getResultCode();
        String message = task + "; " + ex.getMessage();
        if (code == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE || code == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY) {
            return new DuplicateKeyException(message, ex);
        }
        String name = code.name();
        if (name.startsWith("SQLITE_CONSTRAINT")) {
            return new DataIntegrityViolationException(message, ex);
        }
        if (name.startsWith("SQLITE_BUSY") || name.startsWith("SQLITE_LOCKED")) {
            return new CannotAcquireLockException(message, ex);
        }
        return null;
    }
}
