package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.KeyResult;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.GoalStatus;
import com.lifeplanner.model.srv.ChildOutcomeSrv;
import com.lifeplanner.model.srv.GoalProgressSrv;
import com.lifeplanner.model.srv.KeyResultContributionSrv;
import com.lifeplanner.repository.GoalRepository;
import com.lifeplanner.repository.KeyResultRepository;
import com.lifeplanner.repository.TaskRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Loads everything progress needs in three queries (all goals, active KRs with latest check-in, task counts per
 * effective goal) and delegates the hierarchy to {@link ProgressRollup}. Used by the progress endpoint, the dashboard
 * and the tree, so all three always agree. Read-only; never changes Goal status.
 */
@Service
public class ProgressService {

    private final GoalService goals;
    private final GoalRepository goalRepository;
    private final KeyResultRepository keyResults;
    private final TaskRepository tasks;

    public ProgressService(GoalService goals, GoalRepository goalRepository, KeyResultRepository keyResults, TaskRepository tasks) {
        this.goals = goals;
        this.goalRepository = goalRepository;
        this.keyResults = keyResults;
        this.tasks = tasks;
    }

    /** 404 if the goal does not exist. */
    public GoalProgressSrv forGoal(String goalId) {
        return forGoal(goals.get(goalId));
    }

    public GoalProgressSrv forGoal(Goal goal) {
        return forGoals(List.of(goal.id())).get(goal.id());
    }

    public static final int MAX_BATCH = 200;

    /**
     * Progress of several goals in one call (e.g. one page of the goal list). 400 for an empty/oversized request,
     * 404 naming the first unknown id. Order and duplicates of the request are normalised (first occurrence wins).
     */
    public List<GoalProgressSrv> batch(List<String> goalIds) {
        List<String> ids = goalIds == null ? List.of() : goalIds.stream().map(String::trim).distinct().toList();
        if (ids.isEmpty() || ids.size() > MAX_BATCH || ids.stream().anyMatch(String::isEmpty)) {
            throw new ApiException.ValidationException("فهرست شناسه‌ها باید بین ۱ تا ۲۰۰ شناسه معتبر باشد.",
                    Map.of("ids", ids.isEmpty() || ids.stream().anyMatch(String::isEmpty) ? "required" : "too_many"));
        }
        Map<String, GoalProgressSrv> byId = forGoals(ids);
        for (String id : ids) {
            if (byId.get(id) == null) {
                throw new ApiException.NotFoundException("هدف پیدا نشد.", Map.of("goalId", id));
            }
        }
        return ids.stream().map(byId::get).toList();
    }

    public Map<String, GoalProgressSrv> forGoals(Collection<String> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        List<Goal> all = goalRepository.find(ArchiveFilter.ALL);
        List<String> allIds = all.stream().map(Goal::id).toList();
        Map<String, List<KeyResult>> krsByGoal = keyResults.findActiveByGoals(allIds).stream()
                .collect(Collectors.groupingBy(KeyResult::goalId));
        Map<String, BigDecimal> ownOutcome = new HashMap<>();
        krsByGoal.forEach((goalId, krs) -> ownOutcome.put(goalId, ProgressCalculator.outcome(krs.stream()
                .map(kr -> new ProgressCalculator.WeightedScore(kr.score(), kr.weight())).toList())));
        Map<String, ProgressRollup.TaskCounts> counts = new HashMap<>();
        tasks.countActiveByGoals(allIds).forEach((goalId, c) -> counts.put(goalId, new ProgressRollup.TaskCounts(c.active(), c.done())));

        ProgressRollup rollup = new ProgressRollup(all.stream()
                .map(g -> new ProgressRollup.Node(g.id(), g.parentGoalId(), !g.isArchived() && g.status() != GoalStatus.DROPPED))
                .toList(), ownOutcome, counts);

        Map<String, GoalProgressSrv> result = new LinkedHashMap<>();
        java.util.Set<String> known = new java.util.HashSet<>(allIds);
        for (String id : goalIds) {
            if (!known.contains(id)) {
                continue; // unknown ids are simply absent from the map
            }
            result.put(id, toProgress(id, rollup, krsByGoal.getOrDefault(id, List.of())));
        }
        return result;
    }

    private static GoalProgressSrv toProgress(String goalId, ProgressRollup rollup, List<KeyResult> ownKrs) {
        ProgressRollup.Result r = rollup.resultFor(goalId);
        BigDecimal totalWeight = ownKrs.stream().map(KeyResult::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<KeyResultContributionSrv> breakdown = ownKrs.stream()
                .map(kr -> new KeyResultContributionSrv(kr.id(), kr.name(),
                        ProgressCalculator.round(kr.score()), kr.weight(),
                        ProgressCalculator.round(ProgressCalculator.weightShare(kr.weight(), totalWeight)),
                        ProgressCalculator.round(ProgressCalculator.contribution(kr.score(), kr.weight(), totalWeight))))
                .toList();
        Instant measuredAt = ownKrs.stream().map(KeyResult::latestMeasuredAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        List<ChildOutcomeSrv> children = r.childIds().stream()
                .map(childId -> {
                    ProgressRollup.Result c = rollup.resultFor(childId);
                    return new ChildOutcomeSrv(childId, ProgressCalculator.round(c.outcome()), c.source().code());
                })
                .toList();
        return new GoalProgressSrv(goalId, ProgressCalculator.round(r.outcome()), r.source().code(), ownKrs.size(),
                r.contributingChildren(), measuredAt,
                ProgressCalculator.round(r.execution()), r.activeTasks(), r.doneTasks(), breakdown, children, r.subtreeGoalCount());
    }
}
