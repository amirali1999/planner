package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/** Accounts. Not user-scoped (it is how the user is found); used by authentication only. */
@Repository
public class UserRepository {

    private static final String COLUMNS = "id, email, password_hash, display_name, created_at, updated_at";
    /** Root tables whose rows existed before accounts did (user_id NULL) and are claimed by the first account. */
    private static final List<String> OWNED_ROOT_TABLES = List.of("goals", "tasks", "focus_items", "inbox_items", "daily_plans");

    private static final RowMapper<User> MAPPER = (rs, n) -> new User(rs.getString("id"), rs.getString("email"),
            rs.getString("password_hash"), rs.getString("display_name"),
            UtcTimestamps.fromDb(rs.getString("created_at")), UtcTimestamps.fromDb(rs.getString("updated_at")));

    private final JdbcClient db;

    public UserRepository(JdbcClient db) {
        this.db = db;
    }

    public void insert(User u) {
        db.sql("INSERT INTO users (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)")
                .params(u.id(), u.email(), u.passwordHash(), u.displayName(), UtcTimestamps.toDb(u.createdAt()),
                        UtcTimestamps.toDb(u.updatedAt()))
                .update();
    }

    public Optional<User> findByEmail(String normalizedEmail) {
        return db.sql("SELECT " + COLUMNS + " FROM users WHERE email = ?").param(normalizedEmail).query(MAPPER).optional();
    }

    public Optional<User> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM users WHERE id = ?").param(id).query(MAPPER).optional();
    }

    public int count() {
        return db.sql("SELECT count(*) FROM users").query(Integer.class).single();
    }

    /**
     * Gives every unowned row (data created before authentication existed) to {@code userId}. Children (key results,
     * check-ins, milestones, history, plan items) follow their parents. Returns the number of claimed root rows.
     */
    public int claimUnownedData(String userId) {
        int claimed = 0;
        for (String table : OWNED_ROOT_TABLES) { // fixed identifiers, no user input
            claimed += db.sql("UPDATE " + table + " SET user_id = ? WHERE user_id IS NULL").param(userId).update();
        }
        return claimed;
    }
}
