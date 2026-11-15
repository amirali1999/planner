package com.lifeplanner.model;

import java.time.Instant;
import java.time.LocalDate;

/** One plan per local calendar date (Gregorian ISO). Not a Weekly Goal and not a weekly plan. */
public record DailyPlan(String id, LocalDate localDate, Instant createdAt, Instant updatedAt) {
}
