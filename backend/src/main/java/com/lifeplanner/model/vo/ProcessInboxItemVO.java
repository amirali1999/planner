package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.enums.InboxDecision;
import jakarta.validation.constraints.NotNull;

/**
 * Triage request. Exactly the payload of the chosen decision: {@code task} for "task", {@code goal} for "goal",
 * {@code focusItem} for "focus_item"; none for reference/someday/discard. Payloads are validated like their own
 * create endpoints (errors are reported as e.g. {@code task.title}). Only the payload matching the decision is
 * validated, after checking that the right one is present (see InboxService).
 */
@Schema(description = "Triage of an unprocessed inbox item. Send exactly the payload of the chosen decision: task for \"task\", goal for \"goal\", focusItem for \"focus_item\", none for reference/someday/discard. The entity is created and the item marked processed in one transaction.")
public record ProcessInboxItemVO(
        @NotNull(message = "required") @Schema(description = "What the note becomes.", example = "task") InboxDecision decision,
        @Schema(description = "Required when decision=task, forbidden otherwise; validated like POST /tasks (errors as task.<field>).") CreateTaskVO task,
        @Schema(description = "Required when decision=goal, forbidden otherwise; validated like POST /goals (errors as goal.<field>).") CreateGoalVO goal,
        @Schema(description = "Required when decision=focus_item, forbidden otherwise; validated like POST /focus-items.") CreateFocusItemVO focusItem) {
}
