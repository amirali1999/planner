package com.lifeplanner.service;

import com.lifeplanner.model.Goal;
import com.lifeplanner.model.srv.DailyPlanSrv;
import com.lifeplanner.model.srv.DashboardTodaySrv;
import com.lifeplanner.model.srv.GoalCardSrv;
import com.lifeplanner.model.srv.GoalProgressSrv;
import com.lifeplanner.model.srv.TodaySummarySrv;
import com.lifeplanner.repository.GoalRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

/** Read-only composition of existing calculations; it computes nothing of its own. */
@Service
public class DashboardService {

    private final DailyPlanService dailyPlans;
    private final GoalRepository goals;
    private final ProgressService progress;

    public DashboardService(DailyPlanService dailyPlans, GoalRepository goals, ProgressService progress) {
        this.dailyPlans = dailyPlans;
        this.goals = goals;
        this.progress = progress;
    }

    public DashboardTodaySrv today(LocalDate date) {
        DailyPlanSrv plan = dailyPlans.view(date); // pure read: never creates a plan
        TodaySummarySrv summary = new TodaySummarySrv(plan.exists(), plan.items().size(), plan.activeTaskCount(),
                plan.doneTaskCount(), plan.completionPercent());
        List<Goal> active = goals.findActiveOn(date);
        java.util.Map<String, GoalProgressSrv> byGoal = progress.forGoals(active.stream().map(Goal::id).toList());
        List<GoalCardSrv> cards = active.stream().map(g -> card(g, byGoal.get(g.id()))).toList();
        return new DashboardTodaySrv(date, summary, cards);
    }

    private static GoalCardSrv card(Goal goal, GoalProgressSrv p) {
        return new GoalCardSrv(goal.id(), goal.title(), goal.status().code(), goal.periodType().code(),
                goal.periodStart(), goal.periodEnd(), p.outcomeProgress(), p.outcomeSource(), p.keyResultsCount(),
                p.contributingChildCount(), p.measuredAt(), p.executionProgress(), p.activeTaskCount(), p.doneTaskCount());
    }
}
