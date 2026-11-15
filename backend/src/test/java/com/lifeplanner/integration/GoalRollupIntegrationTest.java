package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G-05 over the real API: outcome rollup, subtree execution with unique tasks, archive rules, consistent views. */
@SpringBootTest
@AutoConfigureMockMvc
class GoalRollupIntegrationTest {

    static final AtomicInteger YEAR = new AtomicInteger(2070);

    @Autowired
    MockMvc mvc;

    String postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    String goal(String type, int year, String parent) throws Exception {
        String range = switch (type) {
            case "annual" -> "\"periodStart\":\"%d-03-21\",\"periodEnd\":\"%d-03-20\"".formatted(year, year + 1);
            case "quarterly" -> "\"periodStart\":\"%d-09-23\",\"periodEnd\":\"%d-12-21\"".formatted(year, year);
            default -> "\"periodStart\":\"%d-10-23\",\"periodEnd\":\"%d-11-21\"".formatted(year, year);
        };
        String parentPart = parent == null ? "" : ",\"parentGoalId\":\"" + parent + "\"";
        return JsonPath.read(postJson("/api/v1/goals", "{\"title\":\"" + type + "\",\"periodType\":\"" + type + "\"," + range + parentPart + "}"), "$.id");
    }

    /** KR 0→100 with a check-in at {@code value}: score = value. */
    void krAt(String goalId, int value) throws Exception {
        String kr = JsonPath.read(postJson("/api/v1/goals/" + goalId + "/key-results", "{\"name\":\"kr\",\"baseline\":0,\"target\":100}"), "$.id");
        postJson("/api/v1/key-results/" + kr + "/check-ins", "{\"value\":" + value + ",\"measuredAt\":\"2026-11-03T12:00:00Z\"}");
    }

    String task(String goalId, String focusItemId, String status) throws Exception {
        StringBuilder json = new StringBuilder("{\"title\":\"t\",\"status\":\"" + status + "\"");
        if (goalId != null) json.append(",\"goalId\":\"").append(goalId).append('"');
        if (focusItemId != null) json.append(",\"focusItemId\":\"").append(focusItemId).append('"');
        return JsonPath.read(postJson("/api/v1/tasks", json.append('}').toString()), "$.id");
    }

    String progress(String goalId) throws Exception {
        return mvc.perform(get("/api/v1/goals/" + goalId + "/progress")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void acceptanceChildren40And80RollUpTo60() throws Exception {
        int y = YEAR.getAndAdd(5);
        String parent = goal("annual", y, null);
        String a = goal("quarterly", y, parent);
        String b = goal("monthly", y, parent);
        krAt(a, 40);
        krAt(b, 80);
        mvc.perform(get("/api/v1/goals/" + parent + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(60.0))
                .andExpect(jsonPath("$.outcomeSource").value("child_rollup"))
                .andExpect(jsonPath("$.keyResultsCount").value(0))
                .andExpect(jsonPath("$.contributingChildCount").value(2))
                .andExpect(jsonPath("$.childOutcomes.length()").value(2))
                .andExpect(jsonPath("$.executionProgress").doesNotExist())
                .andExpect(jsonPath("$.combinedProgress").doesNotExist());
    }

    @Test
    void acceptanceOwnKr90WinsAndChildrenAreShownSeparately() throws Exception {
        int y = YEAR.getAndAdd(5);
        String parent = goal("annual", y, null);
        String child = goal("quarterly", y, parent);
        krAt(parent, 90);
        krAt(child, 10);
        mvc.perform(get("/api/v1/goals/" + parent + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(90.0))
                .andExpect(jsonPath("$.outcomeSource").value("own_key_results"))
                .andExpect(jsonPath("$.contributingChildCount").value(0))
                .andExpect(jsonPath("$.childOutcomes[0].goalId").value(child))
                .andExpect(jsonPath("$.childOutcomes[0].outcomeProgress").value(10.0))
                .andExpect(jsonPath("$.childOutcomes[0].outcomeSource").value("own_key_results"));
        mvc.perform(get("/api/v1/goals/" + child + "/progress")).andExpect(jsonPath("$.outcomeProgress").value(10.0));
    }

    @Test
    void acceptanceTaskLinkedTwiceViaDifferentPathsCountsOnce() throws Exception {
        int y = YEAR.getAndAdd(5);
        String parent = goal("annual", y, null);
        String child = goal("quarterly", y, parent);
        String focusOnParent = JsonPath.read(postJson("/api/v1/focus-items",
                "{\"title\":\"f\",\"kind\":\"project\",\"goalId\":\"" + parent + "\"}"), "$.id");
        String t = task(child, focusOnParent, "done");       // direct child + focus on parent → effective child, once
        task(null, focusOnParent, "not_started");             // focus-only → parent
        // the same task on two daily plans is still one task
        postJson("/api/v1/plans/daily/" + y + "-10-24/items", "{\"taskId\":\"" + t + "\"}");
        postJson("/api/v1/plans/daily/" + y + "-10-25/items", "{\"taskId\":\"" + t + "\"}");

        mvc.perform(get("/api/v1/goals/" + parent + "/progress"))
                .andExpect(jsonPath("$.activeTaskCount").value(2))
                .andExpect(jsonPath("$.doneTaskCount").value(1))
                .andExpect(jsonPath("$.executionProgress").value(50.0))
                .andExpect(jsonPath("$.subtreeGoalCount").value(2))
                .andExpect(jsonPath("$.outcomeProgress").doesNotExist())
                .andExpect(jsonPath("$.outcomeSource").value("not_measured"));
        mvc.perform(get("/api/v1/goals/" + child + "/progress"))
                .andExpect(jsonPath("$.activeTaskCount").value(1)).andExpect(jsonPath("$.executionProgress").value(100.0));
        // the parent's own task list stays its direct effective list (subtree aggregation is for progress only)
        List<String> parentTasks = JsonPath.read(mvc.perform(get("/api/v1/goals/" + parent + "/tasks")).andReturn().getResponse().getContentAsString(), "$[*].id");
        assertThat(parentTasks).hasSize(1).doesNotContain(t);
    }

    @Test
    void archivedAndDroppedChildrenArePrunedWithTheirDescendants() throws Exception {
        int y = YEAR.getAndAdd(5);
        String parent = goal("annual", y, null);
        String kept = goal("quarterly", y, parent);
        String archived = goal("quarterly", y, parent);
        String underArchived = goal("monthly", y, archived);
        String dropped = goal("monthly", y, parent);
        krAt(kept, 80);
        krAt(archived, 0);
        krAt(underArchived, 0);
        krAt(dropped, 0);
        task(kept, null, "done");
        task(archived, null, "not_started");
        task(underArchived, null, "not_started");
        task(dropped, null, "not_started");
        mvc.perform(post("/api/v1/goals/" + archived + "/archive")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/goals/" + dropped).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"dropped\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/goals/" + parent + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(80.0))
                .andExpect(jsonPath("$.activeTaskCount").value(1))
                .andExpect(jsonPath("$.executionProgress").value(100.0))
                .andExpect(jsonPath("$.childOutcomes.length()").value(1));
        // the archived goal still reports its own numbers (its subtree includes its active child)
        mvc.perform(get("/api/v1/goals/" + archived + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(0.0))
                .andExpect(jsonPath("$.activeTaskCount").value(2));
    }

    @Test
    void progressTreeAndDashboardAgree() throws Exception {
        int y = YEAR.getAndAdd(5);
        String parent = goal("annual", y, null);
        String a = goal("quarterly", y, parent);
        String b = goal("monthly", y, parent);
        krAt(a, 40);
        krAt(b, 80);
        task(a, null, "done");
        task(b, null, "not_started");
        String p = progress(parent);
        assertThat((Double) JsonPath.read(p, "$.outcomeProgress")).isEqualTo(60.0);
        assertThat((Double) JsonPath.read(p, "$.executionProgress")).isEqualTo(50.0);

        String tree = mvc.perform(get("/api/v1/goals/tree")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Double>>read(tree, "$..[?(@.id == '" + parent + "')].outcomeProgress")).containsExactly(60.0);
        assertThat(JsonPath.<List<String>>read(tree, "$..[?(@.id == '" + parent + "')].outcomeSource")).containsExactly("child_rollup");
        assertThat(JsonPath.<List<Double>>read(tree, "$..[?(@.id == '" + parent + "')].executionProgress")).containsExactly(50.0);
        assertThat(JsonPath.<List<Integer>>read(tree, "$..[?(@.id == '" + parent + "')].contributingChildCount")).containsExactly(2);

        String dash = mvc.perform(get("/api/v1/dashboard/today").param("date", y + "-11-01")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Double>>read(dash, "$.goals[?(@.goalId == '" + parent + "')].outcomeProgress")).containsExactly(60.0);
        assertThat(JsonPath.<List<Double>>read(dash, "$.goals[?(@.goalId == '" + parent + "')].executionProgress")).containsExactly(50.0);
        assertThat(JsonPath.<List<String>>read(dash, "$.goals[?(@.goalId == '" + parent + "')].outcomeSource")).containsExactly("child_rollup");
        assertThat(JsonPath.<List<Integer>>read(dash, "$.goals[?(@.goalId == '" + parent + "')].contributingChildCount")).containsExactly(2);
        assertThat(dash).doesNotContainIgnoringCase("combined");
    }
}
