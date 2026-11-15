package com.lifeplanner.service;

import com.lifeplanner.model.enums.OutcomeSource;
import com.lifeplanner.service.ProgressRollup.Node;
import com.lifeplanner.service.ProgressRollup.Result;
import com.lifeplanner.service.ProgressRollup.TaskCounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ProgressRollupTest {

    static Node active(String id, String parent) {
        return new Node(id, parent, true);
    }

    static Node excluded(String id, String parent) {
        return new Node(id, parent, false);
    }

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static Result run(List<Node> nodes, Map<String, BigDecimal> own, Map<String, TaskCounts> counts, String goal) {
        return new ProgressRollup(nodes, own, counts).resultFor(goal);
    }

    // ---- Outcome ------------------------------------------------------------------------------------

    @Test
    void vector5ParentWithoutKrAveragesChildren40And80To60() {
        Result r = run(List.of(active("p", null), active("a", "p"), active("b", "p")),
                Map.of("a", d("40"), "b", d("80")), Map.of(), "p");
        assertThat(r.outcome()).isEqualByComparingTo("60");
        assertThat(r.source()).isEqualTo(OutcomeSource.CHILD_ROLLUP);
        assertThat(r.contributingChildren()).isEqualTo(2);
    }

    @Test
    void ownKrsWinAndChildrenStaySeparate() {
        Result r = run(List.of(active("p", null), active("a", "p"), active("b", "p")),
                Map.of("p", d("90"), "a", d("40"), "b", d("80")), Map.of(), "p");
        assertThat(r.outcome()).isEqualByComparingTo("90");
        assertThat(r.source()).isEqualTo(OutcomeSource.OWN_KEY_RESULTS);
        assertThat(r.contributingChildren()).isZero();
        assertThat(r.childIds()).containsExactly("a", "b"); // still listed for transparency
    }

    @Test
    void vector6NothingMeasuredIsNullNotZero() {
        Result r = run(List.of(active("p", null), active("a", "p")), Map.of(), Map.of(), "p");
        assertThat(r.outcome()).isNull();
        assertThat(r.source()).isEqualTo(OutcomeSource.NOT_MEASURED);
        assertThat(r.execution()).isNull();
        assertThat(r.activeTasks()).isZero();
    }

    @ParameterizedTest(name = "children {0} → {1}")
    @CsvSource(delimiter = ';', value = {
            "40,80;60",
            "100,0,0,0;25",          // equal child weights (not KR weights)
            "33.333333,66.666667;50",
            "0;0",                   // a measured 0 is a value, not "missing"
            "100;100",
    })
    void childMeanIsUnweighted(String childValues, String expected) {
        List<Node> nodes = new ArrayList<>(List.of(active("p", null)));
        Map<String, BigDecimal> own = new HashMap<>();
        String[] values = childValues.split(",");
        for (int i = 0; i < values.length; i++) {
            nodes.add(active("c" + i, "p"));
            own.put("c" + i, d(values[i]));
        }
        assertThat(ProgressCalculator.round(run(nodes, own, Map.of(), "p").outcome())).isEqualByComparingTo(expected);
    }

    @Test
    void unmeasuredChildrenAreSkippedNotCountedAsZero() {
        Result r = run(List.of(active("p", null), active("a", "p"), active("b", "p")), Map.of("a", d("40")), Map.of(), "p");
        assertThat(r.outcome()).isEqualByComparingTo("40");
        assertThat(r.contributingChildren()).isEqualTo(1);
    }

    @Test
    void rollupIsRecursiveAcrossLevels() {
        // annual → quarterly (no KR) → two monthly with KRs 20 and 60; annual's other child has KR 100
        List<Node> nodes = List.of(active("y", null), active("q", "y"), active("m1", "q"), active("m2", "q"), active("q2", "y"));
        Result y = run(nodes, Map.of("m1", d("20"), "m2", d("60"), "q2", d("100")), Map.of(), "y");
        assertThat(y.outcome()).isEqualByComparingTo("70"); // mean(q=40, q2=100)
    }

    @Test
    void krVectorsFeedOwnOutcome() {
        // vector 1 (increasing) and 2 (decreasing) as own KR of two children → parent 50
        BigDecimal v1 = KeyResultScore.compute(d("20"), d("60"), d("40"));
        BigDecimal v2 = KeyResultScore.compute(d("100"), d("60"), d("80"));
        Result r = run(List.of(active("p", null), active("a", "p"), active("b", "p")), Map.of("a", v1, "b", v2), Map.of(), "p");
        assertThat(r.outcome()).isEqualByComparingTo("50");
    }

    // ---- Execution ----------------------------------------------------------------------------------

    @Test
    void executionSumsDistinctTasksOverTheActiveSubtree() {
        List<Node> nodes = List.of(active("p", null), active("a", "p"), active("g", "a"));
        Map<String, TaskCounts> counts = Map.of("p", new TaskCounts(2, 1), "a", new TaskCounts(1, 1), "g", new TaskCounts(1, 1));
        Result p = run(nodes, Map.of(), counts, "p");
        assertThat(p.activeTasks()).isEqualTo(4);
        assertThat(p.doneTasks()).isEqualTo(3);
        assertThat(p.execution()).isEqualByComparingTo("75");
        assertThat(p.subtreeGoalCount()).isEqualTo(3);
        Result a = run(nodes, Map.of(), counts, "a"); // each ancestor counts its subtree independently
        assertThat(a.activeTasks()).isEqualTo(2);
        assertThat(a.execution()).isEqualByComparingTo("100");
    }

    // ---- archived / dropped ---------------------------------------------------------------------------

    @Test
    void archivedOrDroppedChildIsPrunedWithItsWholeBranch() {
        List<Node> nodes = List.of(active("p", null), excluded("x", "p"), active("xg", "x"), active("ok", "p"));
        Map<String, BigDecimal> own = Map.of("x", d("0"), "xg", d("0"), "ok", d("80"));
        Map<String, TaskCounts> counts = Map.of("x", new TaskCounts(5, 0), "xg", new TaskCounts(5, 0), "ok", new TaskCounts(1, 1));
        Result p = run(nodes, own, counts, "p");
        assertThat(p.outcome()).isEqualByComparingTo("80");
        assertThat(p.activeTasks()).isEqualTo(1);
        assertThat(p.childIds()).containsExactly("ok");
        // the excluded goal itself still computes its own numbers when asked
        Result x = run(nodes, own, counts, "x");
        assertThat(x.outcome()).isEqualByComparingTo("0");
        assertThat(x.activeTasks()).isEqualTo(10);
    }

    // ---- corrupt data and scale -----------------------------------------------------------------------

    @Test
    void parentCyclesTerminate() {
        List<Node> nodes = List.of(active("a", "b"), active("b", "a"), active("s", "s"));
        Map<String, TaskCounts> counts = Map.of("a", new TaskCounts(1, 1), "b", new TaskCounts(1, 0), "s", new TaskCounts(1, 1));
        Result a = run(nodes, Map.of("b", d("50")), counts, "a");
        assertThat(a.activeTasks()).isEqualTo(2); // a and b, each once
        assertThat(a.outcome()).isEqualByComparingTo("50");
        Result s = run(nodes, Map.of(), counts, "s"); // self-parent
        assertThat(s.activeTasks()).isEqualTo(1);
        assertThat(s.outcome()).isNull();
    }

    @Test
    void deepChainAndLargeTreeAreFast() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            List<Node> chain = new ArrayList<>();
            Map<String, TaskCounts> counts = new HashMap<>();
            for (int i = 0; i < 1000; i++) {
                chain.add(active("n" + i, i == 0 ? null : "n" + (i - 1)));
                counts.put("n" + i, new TaskCounts(1, i % 2));
            }
            Result root = run(chain, Map.of("n999", d("30")), counts, "n0");
            assertThat(root.outcome()).isEqualByComparingTo("30"); // propagated up 999 levels
            assertThat(root.activeTasks()).isEqualTo(1000);
            assertThat(root.doneTasks()).isEqualTo(500);

            List<Node> wide = new ArrayList<>(List.of(active("r", null)));
            Map<String, BigDecimal> own = new HashMap<>();
            for (int i = 0; i < 5000; i++) {
                wide.add(active("w" + i, "r"));
                own.put("w" + i, d(i % 2 == 0 ? "0" : "100"));
            }
            ProgressRollup rollup = new ProgressRollup(wide, own, Map.of());
            assertThat(rollup.resultFor("r").outcome()).isEqualByComparingTo("50");
            for (int i = 0; i < 5000; i++) {
                rollup.resultFor("w" + i); // memoized: every node cheap
            }
        });
    }
}
