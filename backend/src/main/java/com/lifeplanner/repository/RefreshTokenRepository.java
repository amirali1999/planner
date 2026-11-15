package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.model.RefreshToken;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;

/** Hashed refresh tokens. Rows are revoked, never deleted, so reuse of an old token stays detectable. */
@Repository
public class RefreshTokenRepository {

    private static final String COLUMNS = "id, user_id, family_id, token_hash, expires_at, created_at, revoked_at, revoked_reason";

    private static final RowMapper<RefreshToken> MAPPER = (rs, n) -> new RefreshToken(rs.getString("id"),
            rs.getString("user_id"), rs.getString("family_id"), rs.getString("token_hash"),
            UtcTimestamps.fromDb(rs.getString("expires_at")), UtcTimestamps.fromDb(rs.getString("created_at")),
            UtcTimestamps.fromDb(rs.getString("revoked_at")), rs.getString("revoked_reason"));

    private final JdbcClient db;

    public RefreshTokenRepository(JdbcClient db) {
        this.db = db;
    }

    public void insert(RefreshToken t) {
        db.sql("INSERT INTO refresh_tokens (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)")
                .params(t.id(), t.userId(), t.familyId(), t.tokenHash(), UtcTimestamps.toDb(t.expiresAt()),
                        UtcTimestamps.toDb(t.createdAt()))
                .update();
    }

    public Optional<RefreshToken> findByHash(String tokenHash) {
        return db.sql("SELECT " + COLUMNS + " FROM refresh_tokens WHERE token_hash = ?").param(tokenHash).query(MAPPER).optional();
    }

    /**
     * Revokes one still-active token. Conditional, so of two concurrent refreshes with the same token only one wins
     * (returns true); the other sees false.
     */
    public boolean revokeIfActive(String id, String reason, Instant at) {
        return db.sql("UPDATE refresh_tokens SET revoked_at = ?, revoked_reason = ? WHERE id = ? AND revoked_at IS NULL")
                .params(UtcTimestamps.toDb(at), reason, id).update() == 1;
    }

    /** Revokes every still-active token of a login session (logout, or reuse of a rotated token). */
    public int revokeFamily(String familyId, String reason, Instant at) {
        return db.sql("UPDATE refresh_tokens SET revoked_at = ?, revoked_reason = ? WHERE family_id = ? AND revoked_at IS NULL")
                .params(UtcTimestamps.toDb(at), reason, familyId).update();
    }
}
