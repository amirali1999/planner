package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
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

/** POST /plans/daily/{date}/tasks/{taskId}/move — drag-and-drop between calendar days. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/plan-move-${random.uuid}.db")
@AutoConfigureMockMvc
class DailyPlanMoveIntegrationTest {

    @Autowired
    MockMvc mvc;

    String task(String title) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    void plan(String date, String taskId) throws Exception {
        mvc.perform(post("/api/v1/plans/daily/" + date + "/items").contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":\"" + taskId + "\"}"))
                .andExpect(status().isCreated());
    }

    ResultActions move(String from, String taskId, String to) throws Exception {
        return mvc.perform(post("/api/v1/plans/daily/" + from + "/tasks/" + taskId + "/move").contentType(MediaType.APPLICATION_JSON)
                .content("{\"toDate\":\"" + to + "\"}"));
    }

    List<String> titles(String date) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/plans/daily?date=" + date)).andReturn().getResponse().getContentAsString(), "$.items[*].task.title");
    }

    @Test
    void movesAPlannedTaskToAnotherDayAndCreatesThePlanIfNeeded() throws Exception {
        String a = task("a");
        String b = task("b");
        plan("2071-03-01", a);
        plan("2071-03-01", b);
        move("2071-03-01", a, "2071-03-04").andExpect(status().isOk()).andExpect(jsonPath("$.task.id").value(a));
        assertThat(titles("2071-03-01")).containsExactly("b");
        assertThat(titles("2071-03-04")).containsExactly("a");
        // already on the target: it just leaves the source
        plan("2071-03-01", a);
        move("2071-03-01", a, "2071-03-04").andExpect(status().isOk());
        assertThat(titles("2071-03-01")).containsExactly("b");
        assertThat(titles("2071-03-04")).containsExactly("a");
        // same day: no change; task fields untouched
        move("2071-03-04", a, "2071-03-04").andExpect(status().isOk());
        mvc.perform(get("/api/v1/tasks/" + a)).andExpect(jsonPath("$.status").value("not_started")).andExpect(jsonPath("$.dueDate").doesNotExist());
    }

    @Test
    void rejectsUnplannedArchivedForeignAndBadInput() throws Exception {
        String t = task("t");
        move("2071-04-01", t, "2071-04-02").andExpect(status().isNotFound());
        plan("2071-04-01", t);
        mvc.perform(post("/api/v1/plans/daily/2071-04-01/tasks/" + t + "/move").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other-move@test.local\",\"password\":\"password-123\"}")).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/v1/plans/daily/2071-04-01/tasks/" + t + "/move").header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"toDate\":\"2071-04-02\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/tasks/" + t).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
        move("2071-04-01", t, "2071-04-02").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("task_archived"));
    }
}
