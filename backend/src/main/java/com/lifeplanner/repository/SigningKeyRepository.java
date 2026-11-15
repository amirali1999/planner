package com.lifeplanner.repository;

import com.lifeplanner.db.UtcTimestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * The HMAC key for access tokens: 256 random bits created on first start and kept in the database, so issued tokens
 * survive a restart and the key never lives in source code or configuration files.
 */
@Repository
public class SigningKeyRepository {

    public record SigningKey(String id, byte[] secret) {
    }

    private final JdbcClient db;

    public SigningKeyRepository(JdbcClient db) {
        this.db = db;
    }

    public SigningKey getOrCreate(Instant now) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        db.sql("""
                INSERT INTO signing_keys (id, algorithm, secret, created_at)
                SELECT ?, 'HS256', ?, ? WHERE NOT EXISTS (SELECT 1 FROM signing_keys)""")
                .params(UUID.randomUUID().toString(), Base64.getUrlEncoder().withoutPadding().encodeToString(secret),
                        UtcTimestamps.toDb(now))
                .update();
        return db.sql("SELECT id, secret FROM signing_keys ORDER BY created_at, id LIMIT 1")
                .query((rs, n) -> new SigningKey(rs.getString("id"), Base64.getUrlDecoder().decode(rs.getString("secret"))))
                .single();
    }
}
