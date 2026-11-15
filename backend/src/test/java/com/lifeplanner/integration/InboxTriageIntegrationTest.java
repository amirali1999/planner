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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** I-02: triage of inbox items, one transaction per decision, provenance kept, no reprocessing. */
@SpringBootTest
@AutoConfigureMockMvc
class InboxTriageIntegrationTest {

    static final String MISSING = "00000000-0000-0000-0000-000000000000";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String capture(String text) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/inbox-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawText\":\"" + text + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions process(String id, String json) throws Exception {
        return mvc.perform(post("/api/v1/inbox-items/" + id + "/process").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    int count(String table) {
        return db.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    void assertStillUnprocessed(String id) throws Exception {
        mvc.perform(get("/api/v1/inbox-items/" + id))
                .andExpect(jsonPath("$.status").value("unprocessed"))
                .andExpect(jsonPath("$.decision").doesNotExist())
                .andExpect(jsonPath("$.createdEntityId").doesNotExist());
    }

    @Test
    void acceptanceConvertToTaskKeepsProvenanceAndRefusesSecondConversion() throws Exception {
        String id = capture("مطالعه فصل دوم کتاب");
        int tasksBefore = count("tasks");
        String body = process(id, """
                {"decision":"task","task":{"title":"مطالعه فصل دوم","status":"not_started","estimatedMinutes":45}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inboxItemId").value(id))
                .andExpect(jsonPath("$.status").value("processed"))
                .andExpect(jsonPath("$.decision").value("task"))
                .andExpect(jsonPath("$.createdEntityType").value("task"))
                .andExpect(jsonPath("$.createdEntityId").isString())
                .andExpect(jsonPath("$.processedAt").isString())
                .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(body, "$.createdEntityId");
        assertThat(count("tasks")).isEqualTo(tasksBefore + 1);
        mvc.perform(get("/api/v1/tasks/" + taskId)).andExpect(jsonPath("$.title").value("مطالعه فصل دوم"))
                .andExpect(jsonPath("$.estimatedMinutes").value(45));
        // provenance: the original text is kept with the link to what it became
        mvc.perform(get("/api/v1/inbox-items/" + id))
                .andExpect(jsonPath("$.rawText").value("مطالعه فصل دوم کتاب"))
                .andExpect(jsonPath("$.status").value("processed"))
                .andExpect(jsonPath("$.createdEntityType").value("task"))
                .andExpect(jsonPath("$.createdEntityId").value(taskId));

        process(id, """
                {"decision":"task","task":{"title":"again","status":"not_started"}}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("inbox_item_already_processed"))
                .andExpect(jsonPath("$.details.createdEntityId").value(taskId));
        process(id, "{\"decision\":\"discard\"}").andExpect(status().isConflict());
        assertThat(count("tasks")).isEqualTo(tasksBefore + 1);
    }

    @Test
    void convertToGoalAndFocusItem() throws Exception {
        String g = capture("یادگیری RAG");
        String goalId = JsonPath.read(process(g, """
                {"decision":"goal","goal":{"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdEntityType").value("goal"))
                .andReturn().getResponse().getContentAsString(), "$.createdEntityId");
        mvc.perform(get("/api/v1/goals/" + goalId)).andExpect(jsonPath("$.status").value("not_started"));

        String f = capture("کتاب DDIA");
        String focusId = JsonPath.read(process(f, """
                {"decision":"focus_item","focusItem":{"title":"کتاب DDIA","kind":"book","goalId":"%s"}}""".formatted(goalId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdEntityType").value("focus_item"))
                .andReturn().getResponse().getContentAsString(), "$.createdEntityId");
        mvc.perform(get("/api/v1/focus-items/" + focusId)).andExpect(jsonPath("$.goalId").value(goalId));
    }

    @Test
    void referenceSomedayAndDiscardCreateNothingAndStayReadable() throws Exception {
        int goals = count("goals");
        int tasks = count("tasks");
        int focus = count("focus_items");
        String ref = capture("لینک مقاله مرجع");
        String some = capture("شاید یک روز سفر");
        String gone = capture("ایده بی‌ربط");
        for (String[] c : new String[][]{{ref, "reference"}, {some, "someday"}, {gone, "discard"}}) {
            process(c[0], "{\"decision\":\"" + c[1] + "\"}").andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value(c[1]))
                    .andExpect(jsonPath("$.createdEntityType").doesNotExist())
                    .andExpect(jsonPath("$.createdEntityId").doesNotExist());
        }
        assertThat(count("goals")).isEqualTo(goals);
        assertThat(count("tasks")).isEqualTo(tasks);
        assertThat(count("focus_items")).isEqualTo(focus);

        List<String> queue = ids("/api/v1/inbox-items");
        assertThat(queue).doesNotContain(ref, some, gone);
        assertThat(ids("/api/v1/inbox-items?status=processed&decision=reference")).contains(ref).doesNotContain(some, gone);
        assertThat(ids("/api/v1/inbox-items?status=processed&decision=someday")).contains(some).doesNotContain(ref);
        assertThat(ids("/api/v1/inbox-items?status=processed&decision=discard")).contains(gone);
        mvc.perform(get("/api/v1/inbox-items/" + gone)).andExpect(jsonPath("$.rawText").value("ایده بی‌ربط"))
                .andExpect(jsonPath("$.decision").value("discard"));
        mvc.perform(get("/api/v1/inbox-items?status=processed&decision=task,goal")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/inbox-items?decision=trash")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.decision").value("invalid_value"));
    }

    List<String> ids(String url) throws Exception {
        return JsonPath.read(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[*].id");
    }

    @Test
    void invalidRequestsAre400AndLeaveTheItemUnprocessed() throws Exception {
        String id = capture("x");
        int tasks = count("tasks");
        process(id, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.decision").value("required"));
        process(id, "{\"decision\":\"later\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.decision").value("invalid_value"));
        process(id, "{\"decision\":\"task\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.task").value("required"));
        process(id, "{\"decision\":\"reference\",\"task\":{\"title\":\"t\"}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.task").value("not_allowed_for_decision"));
        process(id, "{\"decision\":\"task\",\"goal\":{\"title\":\"g\"},\"task\":{\"title\":\"t\"}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.goal").value("not_allowed_for_decision"));
        process(id, "{\"decision\":\"task\",\"task\":{\"title\":\" \"}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['task.title']").value("required"));
        process(id, "{\"decision\":\"task\",\"task\":{\"title\":\"t\",\"color\":\"red\"}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['task.color']").value("unknown_field"));
        process(id, "{\"decision\":\"goal\",\"goal\":{\"title\":\"g\",\"periodType\":\"weekly\",\"periodStart\":\"2026-11-06\",\"periodEnd\":\"2026-10-31\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.periodEnd").value("before_period_start"));
        assertStillUnprocessed(id);
        assertThat(count("tasks")).isEqualTo(tasks);
    }

    @Test
    void failedCreationCreatesNothingAndKeepsItemUnprocessed() throws Exception {
        String id = capture("کار برای هدفی که وجود ندارد");
        int tasks = count("tasks");
        process(id, """
                {"decision":"task","task":{"title":"t","goalId":"%s"}}""".formatted(MISSING))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.details.goalId").value(MISSING));
        assertStillUnprocessed(id);
        assertThat(count("tasks")).isEqualTo(tasks);
        // the same item can then be processed correctly
        process(id, "{\"decision\":\"task\",\"task\":{\"title\":\"t\"}}").andExpect(status().isOk());
    }

    @Test
    void unknownItemIs404() throws Exception {
        process(MISSING, "{\"decision\":\"discard\"}").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
    }
}
