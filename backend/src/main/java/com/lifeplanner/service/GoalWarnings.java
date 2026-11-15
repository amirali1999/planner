package com.lifeplanner.service;

import com.lifeplanner.model.Goal;
import com.lifeplanner.model.srv.GoalWarningSrv;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Date-consistency warnings (docs/DOMAIN_RULES.md: "Date mismatch is a warning, not silent correction").
 * Pure function: no dates are ever adjusted. A boundary week crossing a month is not an exception.
 */
public final class GoalWarnings {

    private GoalWarnings() {
    }

    /**
     * @param parent   the Goal's parent or null
     * @param children the Goal's non-archived direct children
     */
    public static List<GoalWarningSrv> compute(Goal goal, Goal parent, List<Goal> children) {
        List<GoalWarningSrv> warnings = new ArrayList<>();
        if (parent != null && !contains(parent, goal)) {
            warnings.add(new GoalWarningSrv(GoalWarningSrv.PERIOD_OUTSIDE_PARENT,
                    "بازه این هدف کاملاً داخل بازه هدف والد نیست.",
                    Map.of("parentGoalId", parent.id(),
                            "parentPeriodStart", parent.periodStart().toString(),
                            "parentPeriodEnd", parent.periodEnd().toString())));
        }
        if (goal.deadline() != null
                && (goal.deadline().isBefore(goal.periodStart()) || goal.deadline().isAfter(goal.periodEnd()))) {
            warnings.add(new GoalWarningSrv(GoalWarningSrv.DEADLINE_OUTSIDE_PERIOD,
                    "موعد این هدف خارج از بازه آن است.", Map.of("deadline", goal.deadline().toString())));
        }
        for (Goal child : children) {
            if (!contains(goal, child)) {
                warnings.add(new GoalWarningSrv(GoalWarningSrv.CHILD_PERIOD_OUTSIDE,
                        "بازه یکی از زیرهدف‌ها کاملاً داخل بازه این هدف نیست.",
                        Map.of("childGoalId", child.id(), "childTitle", child.title())));
            }
        }
        return warnings;
    }

    private static boolean contains(Goal outer, Goal inner) {
        return !inner.periodStart().isBefore(outer.periodStart()) && !inner.periodEnd().isAfter(outer.periodEnd());
    }
}
