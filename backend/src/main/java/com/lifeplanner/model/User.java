package com.lifeplanner.model;

import java.time.Instant;

/** An account. {@code email} is stored normalized (trimmed, lower-case); the password only as a BCrypt hash. */
public record User(String id, String email, String passwordHash, String displayName, Instant createdAt, Instant updatedAt) {
}
