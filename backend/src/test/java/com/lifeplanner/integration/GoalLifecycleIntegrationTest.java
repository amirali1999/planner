package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G-01: edit, archive/restore, archived filter, goal_history. */
@SpringBootTest
@AutoConfigureMockMvc
class GoalLifecycleIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    String create(String json) throws Exception {
        return JsonPath.read(body(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())), "$.id");
    }

    String quarterly() throws Exception {
        return create("""
                {"title":"q","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}""");
    }

    ResultActions patchGoal(String id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/goals/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Goal-level lifecycle events only (KR events share the table since V3). */
    List<Map<String, Object>> history(String goalId) {
        return db.sql("SELECT event_type, before_json, after_json FROM goal_history WHERE goal_id = ? AND key_result_id IS NULL ORDER BY created_at, rowid")
                .param(goalId).query().listOfRows();
    }

    @Test
    void patchEditsFieldsAndRecordsHistoryPerCategory() throws Exception {
        String id = quarterly();
        patchGoal(id, """
                {"title":"  یادگیری RAG  ","description":"d","status":"doing","periodEnd":"2026-12-20","deadline":"2026-12-15"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("یادگیری RAG"))
                .andExpect(jsonPath("$.status").value("doing"))
                .andExpect(jsonPath("$.periodEnd").value("2026-12-20"))
                .andExpect(jsonPath("$.deadline").value("2026-12-15"))
                .andExpect(jsonPath("$.periodType").value("quarterly"))
                .andExpect(jsonPath("$.warnings.length()").value(0));
        List<Map<String, Object>> rows = history(id);
        assertThat(rows).extracting(r -> r.get("event_type")).containsExactly("status_changed", "details_changed");
        assertThat((String) rows.get(0).get("before_json")).isEqualTo("{\"status\":\"not_started\"}");
        assertThat((String) rows.get(0).get("after_json")).isEqualTo("{\"status\":\"doing\"}");
        assertThat((String) rows.get(1).get("after_json"))
                .contains("\"title\":\"یادگیری RAG\"", "\"periodEnd\":\"2026-12-20\"", "\"deadline\":\"2026-12-15\"")
                .doesNotContain("status");

        // clearing optional fields; absent fields untouched
        patchGoal(id, """
                {"deadline":null,"description":null}""")
                .andExpect(jsonPath("$.deadline").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.title").value("یادگیری RAG"));
    }

    @Test
    void noOpPatchWritesNothing() throws Exception {
        String id = quarterly();
        String before = body(mvc.perform(get("/api/v1/goals/" + id)));
        patchGoal(id, """
                {"title":"q","status":"not_started"}""").andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value((String) JsonPath.read(before, "$.updatedAt")));
        assertThat(history(id)).isEmpty();
    }

    @Test
    void invalidUpdatesAre400AndChangeNothing() throws Exception {
        String id = quarterly();
        patchGoal(id, "{\"title\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        patchGoal(id, "{\"status\":\"finished\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.status").value("invalid_value"));
        patchGoal(id, "{\"status\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.status").value("required"));
        patchGoal(id, "{\"periodEnd\":\"2026-09-01\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.periodEnd").value("before_period_start"));
        patchGoal(id, "{\"periodStart\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.periodStart").value("required"));
        patchGoal(id, "{\"periodStart\":\"2026-02-30\"}").andExpect(status().isBadRequest());
        patchGoal(id, "{\"periodType\":\"monthly\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.periodType").value("not_editable"));
        patchGoal(id, "{\"archivedAt\":null}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.archivedAt").value("not_editable"));
        patchGoal(id, "{\"combinedProgress\":1}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.combinedProgress").value("unknown_field"));
        patchGoal("00000000-0000-0000-0000-000000000000", "{\"title\":\"x\"}").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/goals/" + id)).andExpect(jsonPath("$.title").value("q")).andExpect(jsonPath("$.periodEnd").value("2026-12-21"));
        assertThat(history(id)).isEmpty();
    }

    @Test
    void archivePreservesKrsTasksCheckInsAndChildren() throws Exception {
        String parent = create("""
                {"title":"سالانه","periodType":"annual","periodStart":"2026-03-21","periodEnd":"2027-03-20"}""");
        String child = create("""
                {"title":"فصلی","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21","parentGoalId":"%s"}""".formatted(parent));
        String kr = JsonPath.read(body(mvc.perform(post("/api/v1/goals/" + parent + "/key-results").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"kr\",\"baseline\":0,\"target\":10}"))), "$.id");
        mvc.perform(post("/api/v1/key-results/" + kr + "/check-ins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":5,\"measuredAt\":\"2026-11-03T12:00:00Z\"}")).andExpect(status().isCreated());
        String task = JsonPath.read(body(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t\",\"status\":\"doing\",\"goalId\":\"" + parent + "\"}"))), "$.id");

        String archivedAt = JsonPath.read(body(mvc.perform(post("/api/v1/goals/" + parent + "/archive"))
                .andExpect(status().isOk())), "$.archivedAt");
        mvc.perform(post("/api/v1/goals/" + parent + "/archive"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").value(archivedAt)); // idempotent

        mvc.perform(get("/api/v1/goals/" + child))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.parentGoalId").value(parent));
        mvc.perform(get("/api/v1/tasks/" + task))
                .andExpect(jsonPath("$.status").value("doing"))
                .andExpect(jsonPath("$.goalId").value(parent))
                .andExpect(jsonPath("$.archivedAt").doesNotExist());
        mvc.perform(get("/api/v1/key-results/" + kr)).andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.current").value(5));
        mvc.perform(get("/api/v1/key-results/" + kr + "/check-ins")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/goals/" + parent + "/progress")).andExpect(jsonPath("$.outcomeProgress").value(50.0));
        assertThat(history(parent)).extracting(r -> r.get("event_type")).containsExactly("archived");

        // archived goals: hidden by default, visible with an explicit filter
        mvc.perform(get("/api/v1/goals")).andExpect(jsonPath("$[*].id", not(hasItem(parent)))).andExpect(jsonPath("$[*].id", hasItem(child)));
        mvc.perform(get("/api/v1/goals?archived=true")).andExpect(jsonPath("$[*].id", hasItem(parent))).andExpect(jsonPath("$[*].id", not(hasItem(child))));
        mvc.perform(get("/api/v1/goals?archived=all")).andExpect(jsonPath("$[*].id", hasItem(parent))).andExpect(jsonPath("$[*].id", hasItem(child)));
        mvc.perform(get("/api/v1/goals?archived=maybe")).andExpect(status().isBadRequest());

        // archived goal is read-only until restored
        patchGoal(parent, "{\"title\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_archived"));
        mvc.perform(post("/api/v1/goals/" + parent + "/unarchive")).andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").doesNotExist());
        mvc.perform(post("/api/v1/goals/" + parent + "/unarchive")).andExpect(status().isOk());
        assertThat(history(parent)).extracting(r -> r.get("event_type")).containsExactly("archived", "unarchived");
        patchGoal(parent, "{\"title\":\"x\"}").andExpect(status().isOk());
    }

    @Test
    void historyIsAppendOnly() throws Exception {
        String id = quarterly();
        patchGoal(id, "{\"status\":\"doing\"}").andExpect(status().isOk());
        assertThatThrownBy(() -> db.sql("UPDATE goal_history SET event_type = 'archived' WHERE goal_id = ?").param(id).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> db.sql("DELETE FROM goal_history WHERE goal_id = ?").param(id).update())
                .hasMessageContaining("append-only");
    }

    @Test
    void archiveOfUnknownGoalIs404() throws Exception {
        mvc.perform(post("/api/v1/goals/00000000-0000-0000-0000-000000000000/archive")).andExpect(status().isNotFound());
    }
}
