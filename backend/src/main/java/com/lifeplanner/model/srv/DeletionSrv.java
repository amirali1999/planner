package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "What a permanent deletion removed.")
public record DeletionSrv(
        @Schema(description = "Goals deleted (0 or 1).", example = "1") int goals,
        @Schema(description = "Focus items deleted (the item itself, or the goal's items).", example = "2") int focusItems,
        @Schema(description = "Tasks deleted (also removed from every daily plan).", example = "7") int tasks,
        @Schema(description = "Child goals that were kept and are now standalone (parent link removed, recorded in their history).", example = "1") int detachedChildGoals) {
}
