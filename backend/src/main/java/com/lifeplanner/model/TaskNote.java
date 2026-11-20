package com.lifeplanner.model;

import java.time.Instant;

public record TaskNote(String id, String taskId, String body, Instant createdAt, Instant updatedAt) {
}
