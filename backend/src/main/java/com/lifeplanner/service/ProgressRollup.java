package com.lifeplanner.service;

import com.lifeplanner.model.enums.OutcomeSource;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hierarchical progress (docs/DOMAIN_RULES.md), pure and in-memory:
 * <ul>
 *   <li><b>Outcome</b>: weighted mean of the goal's OWN active KRs if it has any; otherwise the unweighted mean of its
 *       direct active children's defined Outcomes (recursively, unrounded); otherwise null. Own KRs and children are
 *       never mixed.</li>
 *   <li><b>Execution</b>: done / active over the distinct active tasks whose EFFECTIVE goal lies in the goal's active
 *       subtree. Each task has exactly one effective goal and each subtree goal is visited once, so a task is counted
 *       once per aggregate (direct + focus link, or several daily plans, never double).</li>
 *   <li><b>Active</b> children/descendants exclude archived and dropped goals; excluding a goal prunes its whole branch
 *       from its ancestors' rollups (its own numbers are still computed when asked).</li>
 *   <li>Cycle-safe: corrupt parent cycles are cut by an in-progress path and visited sets.</li>
 * </ul>
 * Outcome and Execution are never combined.
 */
public final class ProgressRollup {

    /** A goal as seen by the rollup. {@code countsInRollups}: not archived and not dropped. */
    public record Node(String id, String parentId, boolean countsInRollups) {
    }

    /** Active and done task counts of tasks whose effective goal is exactly this goal. */
    public record TaskCounts(int active, int done) {
        static final TaskCounts NONE = new TaskCounts(0, 0);
    }

    /**
     * @param outcome            unrounded, null = not measured
     * @param contributingChildren direct children whose Outcome entered the child rollup (0 for own KRs)
     * @param childIds           direct active children (shown for transparency even when own KRs decide)
     * @param subtreeGoalCount   goals whose tasks entered Execution (the goal itself + active descendants)
     */
    public record Result(BigDecimal outcome, OutcomeSource source, int contributingChildren, List<String> childIds,
                         BigDecimal execution, int activeTasks, int doneTasks, int subtreeGoalCount) {
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<String>> activeChildren = new HashMap<>();
    private final Map<String, BigDecimal> ownOutcome;
    private final Map<String, TaskCounts> taskCounts;
    private final Map<String, BigDecimal> outcomeMemo = new HashMap<>();
    private final Map<String, OutcomeSource> sourceMemo = new HashMap<>();
    private final Map<String, Integer> contributingMemo = new HashMap<>();

    /**
     * @param ownOutcome  weighted outcome of each goal's own active KRs (only goals that have active KRs)
     * @param taskCounts  counts per effective goal
     */
    public ProgressRollup(Collection<Node> goals, Map<String, BigDecimal> ownOutcome, Map<String, TaskCounts> taskCounts) {
        goals.forEach(n -> nodes.put(n.id(), n));
        for (Node n : goals) {
            if (n.parentId() != null && !n.parentId().equals(n.id()) && n.countsInRollups() && nodes.containsKey(n.parentId())) {
                activeChildren.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n.id());
            }
        }
        this.ownOutcome = ownOutcome;
        this.taskCounts = taskCounts;
    }

    public Result resultFor(String goalId) {
        BigDecimal outcome = outcome(goalId, new HashSet<>());
        Execution execution = execution(goalId);
        return new Result(outcome, sourceMemo.get(goalId), contributingMemo.getOrDefault(goalId, 0),
                List.copyOf(activeChildren.getOrDefault(goalId, List.of())),
                ProgressCalculator.execution(execution.active, execution.done), execution.active, execution.done,
                execution.goals);
    }

    private BigDecimal outcome(String goalId, Set<String> inProgress) {
        if (sourceMemo.containsKey(goalId)) {
            return outcomeMemo.get(goalId);
        }
        BigDecimal own = ownOutcome.get(goalId);
        if (own != null) {
            remember(goalId, own, OutcomeSource.OWN_KEY_RESULTS, 0);
            return own;
        }
        inProgress.add(goalId);
        List<BigDecimal> childValues = new ArrayList<>();
        for (String child : activeChildren.getOrDefault(goalId, List.of())) {
            if (inProgress.contains(child)) {
                continue; // corrupt cycle: never loop
            }
            BigDecimal v = outcome(child, inProgress);
            if (v != null) {
                childValues.add(v);
            }
        }
        inProgress.remove(goalId);
        if (childValues.isEmpty()) {
            remember(goalId, null, OutcomeSource.NOT_MEASURED, 0);
            return null;
        }
        BigDecimal sum = childValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(childValues.size()), MathContext.DECIMAL128);
        remember(goalId, mean, OutcomeSource.CHILD_ROLLUP, childValues.size());
        return mean;
    }

    private void remember(String goalId, BigDecimal outcome, OutcomeSource source, int contributing) {
        outcomeMemo.put(goalId, outcome);
        sourceMemo.put(goalId, source);
        contributingMemo.put(goalId, contributing);
    }

    private record Execution(int active, int done, int goals) {
    }

    /** Iterative DFS over the active subtree; each goal (and therefore each task) is counted once. */
    private Execution execution(String goalId) {
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>(List.of(goalId));
        int active = 0;
        int done = 0;
        while (!stack.isEmpty()) {
            String id = stack.pop();
            if (!visited.add(id)) {
                continue;
            }
            TaskCounts c = taskCounts.getOrDefault(id, TaskCounts.NONE);
            active += c.active();
            done += c.done();
            activeChildren.getOrDefault(id, List.of()).forEach(stack::push);
        }
        return new Execution(active, done, visited.size());
    }
}
