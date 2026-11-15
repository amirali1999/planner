package com.lifeplanner.service;

import com.lifeplanner.model.Goal;
import com.lifeplanner.model.enums.GoalStatus;
import com.lifeplanner.model.enums.PeriodType;
import com.lifeplanner.model.srv.GoalWarningSrv;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalWarningsTest {

    static final Instant T = Instant.parse("2026-11-03T12:00:00Z");

    static Goal goal(String id, PeriodType type, String start, String end, String deadline, String parent) {
        return new Goal(id, id, null, type, LocalDate.parse(start), LocalDate.parse(end),
                deadline == null ? null : LocalDate.parse(deadline), GoalStatus.DOING, parent, null, T, T);
    }

    static List<String> codes(List<GoalWarningSrv> warnings) {
        return warnings.stream().map(GoalWarningSrv::code).toList();
    }

    // Aban 1405 = 2026-10-23 .. 2026-11-21
    final Goal aban = goal("aban", PeriodType.MONTHLY, "2026-10-23", "2026-11-21", null, null);

    @Test
    void containedChildAndStandaloneGoalHaveNoWarnings() {
        Goal week = goal("w", PeriodType.WEEKLY, "2026-10-31", "2026-11-06", null, "aban");
        assertThat(GoalWarnings.compute(week, aban, List.of())).isEmpty();
        assertThat(GoalWarnings.compute(aban, null, List.of(week))).isEmpty();
    }

    @Test
    void crossMonthWeekIsAWarningNotAnError() {
        // Saturday 30 Aban .. Friday 6 Azar: crosses the month boundary; boundary weeks are not exempt.
        Goal week = goal("w", PeriodType.WEEKLY, "2026-11-21", "2026-11-27", null, "aban");
        assertThat(codes(GoalWarnings.compute(week, aban, List.of()))).containsExactly(GoalWarningSrv.PERIOD_OUTSIDE_PARENT);
        List<GoalWarningSrv> parentSide = GoalWarnings.compute(aban, null, List.of(week));
        assertThat(codes(parentSide)).containsExactly(GoalWarningSrv.CHILD_PERIOD_OUTSIDE);
        assertThat(parentSide.get(0).details()).containsEntry("childGoalId", "w");
    }

    @Test
    void deadlineOutsidePeriodWarns() {
        Goal g = goal("g", PeriodType.MONTHLY, "2026-10-23", "2026-11-21", "2026-12-01", null);
        assertThat(codes(GoalWarnings.compute(g, null, List.of()))).containsExactly(GoalWarningSrv.DEADLINE_OUTSIDE_PERIOD);
        Goal onEnd = goal("g", PeriodType.MONTHLY, "2026-10-23", "2026-11-21", "2026-11-21", null);
        assertThat(GoalWarnings.compute(onEnd, null, List.of())).isEmpty();
    }

    @Test
    void identicalRangesAreContained() {
        Goal sameRange = goal("q", PeriodType.QUARTERLY, "2026-10-23", "2026-11-21", null, "y");
        Goal year = goal("y", PeriodType.ANNUAL, "2026-10-23", "2026-11-21", null, null);
        assertThat(GoalWarnings.compute(sameRange, year, List.of())).isEmpty();
    }
}
