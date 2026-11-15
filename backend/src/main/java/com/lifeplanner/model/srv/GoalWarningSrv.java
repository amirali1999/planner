package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Non-blocking notice returned with a Goal (e.g. date mismatch with its parent). Never prevents a save. */
@Schema(description = "Non-blocking notice about a goal's dates; the save has happened.")
public record GoalWarningSrv(@Schema(description = "Warning code.", example = "deadline_outside_period", allowableValues = {"period_outside_parent", "child_period_outside", "deadline_outside_period"}) String code, @Schema(description = "Persian explanation.") String message, @Schema(description = "Context such as parentGoalId, childGoalId, childTitle.") Map<String, Object> details) {

    public static final String PERIOD_OUTSIDE_PARENT = "period_outside_parent";
    public static final String CHILD_PERIOD_OUTSIDE = "child_period_outside";
    public static final String DEADLINE_OUTSIDE_PERIOD = "deadline_outside_period";
}
