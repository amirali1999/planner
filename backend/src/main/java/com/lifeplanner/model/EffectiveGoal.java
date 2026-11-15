package com.lifeplanner.model;

import com.lifeplanner.model.enums.EffectiveGoalSource;

/**
 * Effective Goal of a Task (docs/DOMAIN_RULES.md "Task & attribution"): the direct {@code task.goalId} if set,
 * otherwise the linked Focus Item's goal, otherwise none. When both are set and differ, the direct goal wins and the
 * disagreement is reported as a non-blocking warning (a legitimate exception, never an error).
 */
public record EffectiveGoal(String goalId, EffectiveGoalSource source, boolean conflictsWithFocusItem) {

    public static EffectiveGoal resolve(String directGoalId, String focusItemGoalId) {
        if (directGoalId != null) {
            return new EffectiveGoal(directGoalId, EffectiveGoalSource.DIRECT, focusItemGoalId != null && !focusItemGoalId.equals(directGoalId));
        }
        if (focusItemGoalId != null) {
            return new EffectiveGoal(focusItemGoalId, EffectiveGoalSource.FOCUS_ITEM, false);
        }
        return new EffectiveGoal(null, null, false);
    }
}
