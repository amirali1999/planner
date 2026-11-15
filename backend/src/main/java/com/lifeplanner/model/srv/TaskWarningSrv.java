package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Non-blocking notice on a task (e.g. direct goal differs from the focus item's goal). */
@Schema(description = "Non-blocking notice on a task.")
public record TaskWarningSrv(@Schema(description = "Warning code.", example = "goal_differs_from_focus_item", allowableValues = {"goal_differs_from_focus_item"}) String code, @Schema(description = "Persian explanation.") String message, @Schema(description = "Context, e.g. goalId and focusItemGoalId.") Map<String, Object> details) {
}
