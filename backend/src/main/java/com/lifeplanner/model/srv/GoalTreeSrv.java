package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Forest of your goals. Every goal appears exactly once.")
public record GoalTreeSrv(@Schema(description = "The archive filter that was applied.", example = "false", allowableValues = {"false", "true", "all"}) String archived, @Schema(description = "Number of goals in the forest.", example = "5") int goalCount, @Schema(description = "Root goals: standalone goals and goals whose parent is filtered out.") List<GoalTreeNodeSrv> roots) {
}
