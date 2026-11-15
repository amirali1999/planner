package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "New priority order of ALL your active goals, highest priority first (index 0 becomes priority 1).")
public record ReorderGoalsVO(
        @Schema(description = "Ids of every active goal exactly once, in the new order. Archived goals are not included.",
                example = "[\"0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10\", \"7c1a2b3c-4d5e-4f60-8a9b-0c1d2e3f4a5b\"]")
        @ArraySchema(arraySchema = @Schema(description = "Ids of every active goal exactly once, in the new order. Archived goals are not included."))
        List<String> goalIds) {
}
