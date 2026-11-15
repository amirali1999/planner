package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "New order of ALL items of one daily plan, top first (index 0 becomes position 0).")
public record ReorderPlanItemsVO(
        @Schema(description = "Ids of every item of the plan (also items whose task is archived) exactly once, in the new order.",
                example = "[\"0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10\", \"7c1a2b3c-4d5e-4f60-8a9b-0c1d2e3f4a5b\"]")
        @ArraySchema(arraySchema = @Schema(description = "Ids of every item of the plan (also items whose task is archived) exactly once, in the new order."))
        List<String> itemIds) {
}
