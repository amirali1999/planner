package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DELETE of archived goals / focus items / tasks: archive-first rule, owner-chosen cascades, append-only elsewhere. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/archive-delete-${random.uuid}.db")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ArchiveDeletionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String goal(String title, String type, String start, String end, String parent) throws Exception {
        String p = parent == null ? "" : ",\"parentGoalId\":\"" + parent + "\"";
        return id(postJson("/api/v1/goals", "{\"title\":\"" + title + "\",\"periodType\":\"" + type + "\",\"periodStart\":\"" + start + "\",\"periodEnd\":\"" + end + "\"" + p + "}"));
    }

    String annual(String title) throws Exception {
        return goal(title, "annual", "2026-03-21", "2027-03-20", null);
    }

    String item(String title, String goalId) throws Exception {
        String g = goalId == null ? "" : ",\"goalId\":\"" + goalId + "\"";
        return id(postJson("/api/v1/focus-items", "{\"title\":\"" + title + "\",\"kind\":\"other\"" + g + "}"));
    }

    String task(String title, String goalId, String focusItemId) throws Exception {
        String extra = (goalId == null ? "" : ",\"goalId\":\"" + goalId + "\"") + (focusItemId == null ? "" : ",\"focusItemId\":\"" + focusItemId + "\"");
        return id(postJson("/api/v1/tasks", "{\"title\":\"" + title + "\"" + extra + "}"));
    }

    void archive(String path) throws Exception {
        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
    }

    int count(String sql, Object... params) {
        return db.sql(sql).params(params).query(Integer.class).single();
    }

    @Test
    void activeEntitiesCannotBeDeletedAndUnknownIdsAre404() throws Exception {
        String g = annual("g");
        String f = item("f", null);
        String t = task("t", null, null);
        mvc.perform(delete("/api/v1/goals/" + g)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_archived"));
        mvc.perform(delete("/api/v1/focus-items/" + f)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/tasks/" + t)).andExpect(status().isConflict());
        String missing = "00000000-0000-0000-0000-000000000000";
        mvc.perform(delete("/api/v1/goals/" + missing)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/focus-items/" + missing)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/tasks/" + missing)).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM goals")).isEqualTo(1);
    }

    @Test
    void deletingAnArchivedTaskRemovesItFromDailyPlans() throws Exception {
        String t = task("t", null, null), keep = task("keep", null, null);
        for (String x : new String[] {t, keep}) {
            postJson("/api/v1/plans/daily/2026-11-10/items", "{\"taskId\":\"" + x + "\"}").andExpect(status().isCreated());
        }
        archive("/api/v1/tasks/" + t);
        mvc.perform(delete("/api/v1/tasks/" + t)).andExpect(status().isOk()).andExpect(jsonPath("$.tasks").value(1));
        mvc.perform(get("/api/v1/tasks/" + t)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/plans/daily?date=2026-11-10")).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].task.title").value("keep"));
    }

    @Test
    void deletingAnArchivedFocusItemDeletesItsTasks() throws Exception {
        String f = item("f", null), other = item("other", null);
        task("a", null, f);
        String archivedTask = task("b", null, f);
        archive("/api/v1/tasks/" + archivedTask);
        String keep = task("keep", null, other);
        archive("/api/v1/focus-items/" + f);
        mvc.perform(delete("/api/v1/focus-items/" + f)).andExpect(status().isOk())
                .andExpect(jsonPath("$.focusItems").value(1)).andExpect(jsonPath("$.tasks").value(2));
        assertThat(count("SELECT count(*) FROM tasks")).isEqualTo(1);
        mvc.perform(get("/api/v1/tasks/" + keep)).andExpect(status().isOk());
    }

    @Test
    void deletingAnArchivedGoalRemovesEverythingBelowItButKeepsChildGoals() throws Exception {
        String g = annual("g"), other = annual("other");
        String child = goal("child", "quarterly", "2026-09-23", "2026-12-21", g);
        String kr = id(postJson("/api/v1/goals/" + g + "/key-results", "{\"name\":\"kr\",\"baseline\":0,\"target\":10}"));
        postJson("/api/v1/key-results/" + kr + "/check-ins", "{\"value\":4,\"measuredAt\":\"2026-11-01T10:00:00Z\"}").andExpect(status().isCreated());
        postJson("/api/v1/goals/" + g + "/milestones", "{\"title\":\"m\"}").andExpect(status().isCreated());
        String f = item("f", g);
        task("direct", g, null);
        task("via item", null, f);
        task("item task, other goal", other, f); // its focus item goes, so it goes too
        String keep = task("keep", other, null);
        String childTask = task("child task", child, null);
        mvc.perform(post("/api/v1/goals/" + g + "/archive")).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/goals/" + g)).andExpect(status().isOk())
                .andExpect(jsonPath("$.goals").value(1)).andExpect(jsonPath("$.focusItems").value(1))
                .andExpect(jsonPath("$.tasks").value(3)).andExpect(jsonPath("$.detachedChildGoals").value(1));

        mvc.perform(get("/api/v1/goals/" + g)).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM key_results WHERE goal_id = ?", g)).isZero();
        assertThat(count("SELECT count(*) FROM key_result_checkins")).isZero();
        assertThat(count("SELECT count(*) FROM milestones")).isZero();
        assertThat(count("SELECT count(*) FROM goal_history WHERE goal_id = ?", g)).isZero();
        assertThat(count("SELECT count(*) FROM focus_items")).isZero();
        mvc.perform(get("/api/v1/tasks/" + keep)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tasks/" + childTask)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/goals/" + child)).andExpect(status().isOk()).andExpect(jsonPath("$.parentGoalId").doesNotExist());
        assertThat(count("SELECT count(*) FROM goal_history WHERE goal_id = ? AND event_type = 'parent_changed'", child)).isEqualTo(1);
    }

    @Test
    void historyAndCheckInsOfActiveGoalsStayAppendOnly() throws Exception {
        String g = annual("g");
        String kr = id(postJson("/api/v1/goals/" + g + "/key-results", "{\"name\":\"kr\",\"baseline\":0,\"target\":10}"));
        postJson("/api/v1/key-results/" + kr + "/check-ins", "{\"value\":4,\"measuredAt\":\"2026-11-01T10:00:00Z\"}").andExpect(status().isCreated());
        mvc.perform(post("/api/v1/goals/" + g + "/archive")).andExpect(status().isOk()); // writes an "archived" history row
        mvc.perform(post("/api/v1/goals/" + g + "/unarchive")).andExpect(status().isOk());
        assertThatThrownBy(() -> db.sql("DELETE FROM key_result_checkins").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> db.sql("DELETE FROM goal_history").update()).hasMessageContaining("append-only");
    }

    @Test
    void anotherUsersArchivedTaskIsInvisible() throws Exception {
        String t = task("mine", null, null);
        archive("/api/v1/tasks/" + t);
        String other = JsonPath.read(postJson("/api/v1/auth/register", "{\"email\":\"other-del@test.local\",\"password\":\"password-123\"}")
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(delete("/api/v1/tasks/" + t).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks/" + t)).andExpect(status().isOk());
    }
}
