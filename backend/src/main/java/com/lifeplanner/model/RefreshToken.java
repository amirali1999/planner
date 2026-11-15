package com.lifeplanner.model;

import java.time.Instant;

/**
 * Stored refresh token (only its SHA-256 hash). Tokens rotated from one login form a family that shares the absolute
 * expiry of that login.
 */
public record RefreshToken(String id, String userId, String familyId, String tokenHash, Instant expiresAt,
                           Instant createdAt, Instant revokedAt, String revokedReason) {

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
