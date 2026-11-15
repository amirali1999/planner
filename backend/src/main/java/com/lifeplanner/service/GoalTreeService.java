package com.lifeplanner.service;

import com.lifeplanner.model.Goal;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.srv.GoalProgressSrv;
import com.lifeplanner.model.srv.GoalTreeNodeSrv;
import com.lifeplanner.model.srv.GoalTreeSrv;
import com.lifeplanner.repository.GoalRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the goal forest in memory from one goal query plus two progress queries (no N+1).
 * Every included goal appears exactly once. Cycle-safe: traversal uses a visited set, and any goal not reachable from
 * a root (possible only with corrupt data) is added as a root instead of disappearing.
 */
@Service
public class GoalTreeService {

    private static final Map<String, Integer> COARSE_FIRST = Map.of("annual", 0, "quarterly", 1, "monthly", 2, "weekly", 3);

    /** Stable sibling order: period start, coarser period first, creation time, id. */
    static final Comparator<Goal> ORDER = Comparator.comparing(Goal::periodStart)
            .thenComparing(g -> COARSE_FIRST.get(g.periodType().code()))
            .thenComparing(Goal::createdAt)
            .thenComparing(Goal::id);

    private final GoalRepository goals;
    private final ProgressService progress;

    public GoalTreeService(GoalRepository goals, ProgressService progress) {
        this.goals = goals;
        this.progress = progress;
    }

    public GoalTreeSrv tree(ArchiveFilter archive, String archivedParam) {
        List<Goal> included = new ArrayList<>(goals.find(archive));
        included.sort(ORDER);
        Map<String, Goal> byId = new LinkedHashMap<>();
        included.forEach(g -> byId.put(g.id(), g));
        Map<String, List<Goal>> childrenOf = new HashMap<>();
        List<Goal> roots = new ArrayList<>();
        for (Goal g : included) {
            if (g.parentGoalId() != null && byId.containsKey(g.parentGoalId()) && !g.parentGoalId().equals(g.id())) {
                childrenOf.computeIfAbsent(g.parentGoalId(), k -> new ArrayList<>()).add(g);
            } else {
                roots.add(g);
            }
        }
        Map<String, GoalProgressSrv> measured = progress.forGoals(byId.keySet());

        Set<String> visited = new HashSet<>();
        List<GoalTreeNodeSrv> rootNodes = new ArrayList<>();
        for (Goal root : roots) {
            rootNodes.add(build(root, byId, childrenOf, measured, visited));
        }
        // Corrupt data only (e.g. a manual cycle a→b→a): keep such goals visible, once, as roots.
        for (Goal g : included) {
            if (!visited.contains(g.id())) {
                rootNodes.add(build(g, byId, childrenOf, measured, visited));
            }
        }
        return new GoalTreeSrv(archivedParam, visited.size(), rootNodes);
    }

    private GoalTreeNodeSrv build(Goal goal, Map<String, Goal> byId, Map<String, List<Goal>> childrenOf,
                               Map<String, GoalProgressSrv> measured, Set<String> visited) {
        visited.add(goal.id());
        List<GoalTreeNodeSrv> children = new ArrayList<>();
        for (Goal child : childrenOf.getOrDefault(goal.id(), List.of())) {
            if (!visited.contains(child.id())) {
                children.add(build(child, byId, childrenOf, measured, visited));
            }
        }
        GoalProgressSrv p = measured.get(goal.id());
        boolean detached = goal.parentGoalId() != null && !byId.containsKey(goal.parentGoalId());
        return new GoalTreeNodeSrv(goal.id(), goal.title(), goal.status().code(), goal.periodType().code(),
                goal.periodStart(), goal.periodEnd(), goal.deadline(), goal.parentGoalId(), detached, goal.archivedAt(),
                p.outcomeProgress(), p.outcomeSource(), p.keyResultsCount(), p.contributingChildCount(),
                p.executionProgress(), p.activeTaskCount(), p.doneTaskCount(), children);
    }
}
