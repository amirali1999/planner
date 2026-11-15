package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-02: Task ↔ Focus Item links and unique effective goal attribution (direct goal wins). */
@SpringBootTest
@AutoConfigureMockMvc
class TaskAttributionIntegrationTest {

    static final String MISSING = "00000000-0000-0000-0000-000000000000";

    @Autowired
    MockMvc mvc;

    String goalA;
    String goalB;
    String focusB; // focus item linked to goal B

    @BeforeEach
    void setUp() throws Exception {
        goalA = goal("A");
        goalB = goal("B");
        focusB = focus("پروژه B", goalB);
    }

    static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    String goal(String title) throws Exception {
        return JsonPath.read(body(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"%s","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}""".formatted(title)))
                .andExpect(status().isCreated())), "$.id");
    }

    String focus(String title, String goalId) throws Exception {
        String goalPart = goalId == null ? "" : ",\"goalId\":\"" + goalId + "\"";
        return JsonPath.read(body(mvc.perform(post("/api/v1/focus-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"kind\":\"project\"" + goalPart + "}")).andExpect(status().isCreated())), "$.id");
    }

    ResultActions postTask(String goalId, String focusItemId, String status) throws Exception {
        StringBuilder json = new StringBuilder("{\"title\":\"t\",\"status\":\"" + status + "\"");
        if (goalId != null) json.append(",\"goalId\":\"").append(goalId).append('"');
        if (focusItemId != null) json.append(",\"focusItemId\":\"").append(focusItemId).append('"');
        return mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(json.append('}').toString()));
    }

    String task(String goalId, String focusItemId, String status) throws Exception {
        return JsonPath.read(body(postTask(goalId, focusItemId, status).andExpect(status().isCreated())), "$.id");
    }

    List<String> goalTasks(String goalId, String query) throws Exception {
        return JsonPath.read(body(mvc.perform(get("/api/v1/goals/" + goalId + "/tasks" + query)).andExpect(status().isOk())), "$[*].id");
    }

    ResultActions patchJson(String path, String json) throws Exception {
        return mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---- the four attribution combinations ----------------------------------------------------------

    @Test
    void neitherRelation() throws Exception {
        String t = task(null, null, "not_started");
        mvc.perform(get("/api/v1/tasks/" + t))
                .andExpect(jsonPath("$.effectiveGoalId").doesNotExist())
                .andExpect(jsonPath("$.effectiveGoalSource").doesNotExist())
                .andExpect(jsonPath("$.warnings.length()").value(0));
        assertThat(goalTasks(goalA, "")).doesNotContain(t);
    }

    @Test
    void directOnlyAndFocusOnly() throws Exception {
        String direct = task(goalA, null, "not_started");
        String viaFocus = task(null, focusB, "not_started");
        mvc.perform(get("/api/v1/tasks/" + direct)).andExpect(jsonPath("$.effectiveGoalId").value(goalA))
                .andExpect(jsonPath("$.effectiveGoalSource").value("direct"));
        mvc.perform(get("/api/v1/tasks/" + viaFocus))
                .andExpect(jsonPath("$.goalId").doesNotExist())
                .andExpect(jsonPath("$.focusItemId").value(focusB))
                .andExpect(jsonPath("$.focusItemGoalId").value(goalB))
                .andExpect(jsonPath("$.effectiveGoalId").value(goalB))
                .andExpect(jsonPath("$.effectiveGoalSource").value("focus_item"));
        assertThat(goalTasks(goalA, "")).containsExactly(direct);
        assertThat(goalTasks(goalB, "")).containsExactly(viaFocus);
    }

    @Test
    void bothRelationsToTheSameGoalAppearOnceAndCountOnce() throws Exception {
        String both = task(goalB, focusB, "done");          // vector 7
        String viaFocus = task(null, focusB, "not_started");
        assertThat(goalTasks(goalB, "")).containsExactly(both, viaFocus);
        mvc.perform(get("/api/v1/tasks/" + both)).andExpect(jsonPath("$.warnings.length()").value(0));
        mvc.perform(get("/api/v1/goals/" + goalB + "/progress"))
                .andExpect(jsonPath("$.activeTaskCount").value(2))
                .andExpect(jsonPath("$.doneTaskCount").value(1))
                .andExpect(jsonPath("$.executionProgress").value(50.0));
    }

    @Test
    void acceptanceDirectAWithFocusOnBCountsForAOnlyWithWarning() throws Exception {
        String t = task(goalA, focusB, "done");             // vector 8
        mvc.perform(get("/api/v1/tasks/" + t))
                .andExpect(jsonPath("$.effectiveGoalId").value(goalA))
                .andExpect(jsonPath("$.effectiveGoalSource").value("direct"))
                .andExpect(jsonPath("$.warnings[0].code").value("goal_differs_from_focus_item"))
                .andExpect(jsonPath("$.warnings[0].details.focusItemGoalId").value(goalB));
        assertThat(goalTasks(goalA, "")).containsExactly(t);
        assertThat(goalTasks(goalB, "")).doesNotContain(t);
        mvc.perform(get("/api/v1/goals/" + goalA + "/progress"))
                .andExpect(jsonPath("$.activeTaskCount").value(1)).andExpect(jsonPath("$.executionProgress").value(100.0));
        mvc.perform(get("/api/v1/goals/" + goalB + "/progress"))
                .andExpect(jsonPath("$.activeTaskCount").value(0)).andExpect(jsonPath("$.executionProgress").doesNotExist());
        // the tree uses the same counts
        String tree = body(mvc.perform(get("/api/v1/goals/tree")));
        assertThat((List<Integer>) JsonPath.read(tree, "$..[?(@.id == '" + goalA + "')].activeTaskCount")).containsExactly(1);
        assertThat((List<Integer>) JsonPath.read(tree, "$..[?(@.id == '" + goalB + "')].activeTaskCount")).containsExactly(0);
    }

    // ---- changing relations ---------------------------------------------------------------------------

    @Test
    void movingTheFocusItemsGoalOnlyMovesTasksWithoutDirectGoalAndNeverRewritesTaskGoalId() throws Exception {
        String goalC = goal("C");
        String viaFocus = task(null, focusB, "not_started");
        String direct = task(goalA, focusB, "not_started");
        patchJson("/api/v1/focus-items/" + focusB, "{\"goalId\":\"" + goalC + "\"}").andExpect(status().isOk());

        assertThat(goalTasks(goalC, "")).containsExactly(viaFocus);
        assertThat(goalTasks(goalB, "")).isEmpty();
        assertThat(goalTasks(goalA, "")).containsExactly(direct);
        mvc.perform(get("/api/v1/tasks/" + viaFocus)).andExpect(jsonPath("$.goalId").doesNotExist())
                .andExpect(jsonPath("$.effectiveGoalId").value(goalC));
        mvc.perform(get("/api/v1/tasks/" + direct)).andExpect(jsonPath("$.goalId").value(goalA))
                .andExpect(jsonPath("$.warnings[0].details.focusItemGoalId").value(goalC));

        // unlinking the focus item from any goal leaves the focus-only task unattributed
        patchJson("/api/v1/focus-items/" + focusB, "{\"goalId\":null}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/tasks/" + viaFocus)).andExpect(jsonPath("$.effectiveGoalId").doesNotExist());
    }

    @Test
    void taskPatchSetsAndClearsFocusItem() throws Exception {
        String t = task(null, null, "not_started");
        patchJson("/api/v1/tasks/" + t, "{\"focusItemId\":\"" + focusB + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.focusItemId").value(focusB)).andExpect(jsonPath("$.effectiveGoalId").value(goalB));
        patchJson("/api/v1/tasks/" + t, "{\"title\":\"renamed\"}").andExpect(jsonPath("$.focusItemId").value(focusB));
        patchJson("/api/v1/tasks/" + t, "{\"focusItemId\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.focusItemId").doesNotExist()).andExpect(jsonPath("$.effectiveGoalId").doesNotExist());
        patchJson("/api/v1/tasks/" + t, "{\"focusItemId\":\"" + MISSING + "\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.focusItemId").value(MISSING));
    }

    // ---- archive handling ---------------------------------------------------------------------------

    @Test
    void archivedTasksAreExcludedFromListAndExecutionButReachable() throws Exception {
        String active = task(null, focusB, "done");
        String archived = task(null, focusB, "not_started");
        patchJson("/api/v1/tasks/" + archived, "{\"archived\":true}").andExpect(status().isOk());
        assertThat(goalTasks(goalB, "")).containsExactly(active);
        assertThat(goalTasks(goalB, "?archived=all")).containsExactly(active, archived);
        assertThat(goalTasks(goalB, "?archived=true")).containsExactly(archived);
        mvc.perform(get("/api/v1/goals/" + goalB + "/progress"))
                .andExpect(jsonPath("$.activeTaskCount").value(1)).andExpect(jsonPath("$.executionProgress").value(100.0));
    }

    @Test
    void archivedFocusItemKeepsAttributingButRefusesNewLinks() throws Exception {
        String t = task(null, focusB, "not_started");
        patchJson("/api/v1/focus-items/" + focusB, "{\"archived\":true}").andExpect(status().isOk());
        assertThat(goalTasks(goalB, "")).containsExactly(t);
        patchJson("/api/v1/tasks/" + t, "{\"status\":\"doing\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.focusItemId").value(focusB));
        postTask(null, focusB, "not_started").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("focus_item_archived"));
        String other = task(null, null, "not_started");
        patchJson("/api/v1/tasks/" + other, "{\"focusItemId\":\"" + focusB + "\"}").andExpect(status().isConflict());
    }

    @Test
    void archivedGoalStillListsItsTasks() throws Exception {
        String t = task(null, focusB, "done");
        mvc.perform(post("/api/v1/goals/" + goalB + "/archive")).andExpect(status().isOk());
        assertThat(goalTasks(goalB, "")).containsExactly(t);
    }

    // ---- filters and errors ---------------------------------------------------------------------------

    @Test
    void filtersAndErrors() throws Exception {
        String done = task(null, focusB, "done");
        String open = task(goalB, null, "not_started");
        assertThat(goalTasks(goalB, "?status=done")).containsExactly(done);
        List<String> byFocus = JsonPath.read(body(mvc.perform(get("/api/v1/tasks?focusItemId=" + focusB))), "$[*].id");
        assertThat(byFocus).containsExactly(done);
        List<String> directOnly = JsonPath.read(body(mvc.perform(get("/api/v1/tasks?goalId=" + goalB))), "$[*].id");
        assertThat(directOnly).containsExactly(open); // /tasks?goalId stays the direct link
        mvc.perform(get("/api/v1/goals/" + MISSING + "/tasks")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/goals/" + goalB + "/tasks?status=blocked")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/goals/" + goalB + "/tasks?archived=maybe")).andExpect(status().isBadRequest());
    }
}
