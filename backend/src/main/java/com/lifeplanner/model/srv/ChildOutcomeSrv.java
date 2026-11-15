package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * A direct active child's Outcome, always listed for transparency. When the parent has its own KRs these do NOT
 * enter the parent's Outcome; with {@code outcomeSource = child_rollup} the defined ones are averaged equally.
 */
@Schema(description = "Outcome of a direct active child goal. Listed for transparency; it enters the parent's Outcome only when the parent has no own KRs (child_rollup).")
public record ChildOutcomeSrv(@Schema(description = "Child goal id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId, @Schema(description = "Child Outcome 0..100 or null when not measured.", example = "40") BigDecimal outcomeProgress, @Schema(description = "Where outcomeProgress comes from: own_key_results, child_rollup (no own KR; mean of children) or not_measured.", example = "own_key_results", allowableValues = {"own_key_results", "child_rollup", "not_measured"}) String outcomeSource) {
}
