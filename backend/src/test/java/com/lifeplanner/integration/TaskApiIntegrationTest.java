package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest {

    static final String MISSING = "00000000-0000-0000-0000-000000000000";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String goalId;

    @BeforeEach
    void createGoal() throws Exception {
        goalId = JsonPath.read(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions postTask(String json) throws Exception {
        return mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String createTask(String json) throws Exception {
        return JsonPath.read(postTask(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions patchTask(String id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/tasks/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    int rowCount(String id) {
        return db.sql("SELECT count(*) FROM tasks WHERE id = ?").param(id).query(Integer.class).single();
    }

    // ---- create ---------------------------------------------------------------------------------

    @Test
    void standaloneTaskGetsDefaults() throws Exception {
        postTask("""
                {"title":"  مطالعه فصل دوم  "}""")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/tasks/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.title").value("مطالعه فصل دوم"))
                .andExpect(jsonPath("$.status").value("not_started"))
                .andExpect(jsonPath("$.goalId").doesNotExist())
                .andExpect(jsonPath("$.focusItemId").doesNotExist())
                .andExpect(jsonPath("$.priority").value("low")) // default priority
                .andExpect(jsonPath("$.estimatedMinutes").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist())
                .andExpect(jsonPath("$.blockedReason").doesNotExist())
                .andExpect(jsonPath("$.archivedAt").doesNotExist());
    }

    @Test
    void goalLinkedTaskWithAllFields() throws Exception {
        String id = createTask("""
                {"title":"پیاده‌سازی retriever","description":"d","goalId":"%s","status":"doing","priority":"high",
                 "estimatedMinutes":90,"dueDate":"2026-11-10","blockedReason":"منتظر داده"}""".formatted(goalId));
        mvc.perform(get("/api/v1/tasks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.status").value("doing"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.estimatedMinutes").value(90))
                .andExpect(jsonPath("$.dueDate").value("2026-11-10"))
                .andExpect(jsonPath("$.blockedReason").value("منتظر داده"));
        assertThat(rowCount(id)).isEqualTo(1);
    }

    @Test
    void zeroEstimateIsDistinctFromNull() throws Exception {
        String zero = createTask("""
                {"title":"t","estimatedMinutes":0}""");
        mvc.perform(get("/api/v1/tasks/" + zero)).andExpect(jsonPath("$.estimatedMinutes").value(0));
        String none = createTask("""
                {"title":"t","estimatedMinutes":null}""");
        mvc.perform(get("/api/v1/tasks/" + none)).andExpect(jsonPath("$.estimatedMinutes").doesNotExist());
    }

    @Test
    void unknownGoalIs404AndNoGoalIsCreated() throws Exception {
        int goalsBefore = db.sql("SELECT count(*) FROM goals").query(Integer.class).single();
        postTask("""
                {"title":"t","goalId":"%s"}""".formatted(MISSING))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.details.goalId").value(MISSING));
        assertThat(db.sql("SELECT count(*) FROM goals").query(Integer.class).single()).isEqualTo(goalsBefore);
    }

    @Test
    void archivedGoalCannotReceiveNewTasks() throws Exception {
        db.sql("UPDATE goals SET archived_at = '2026-11-03T12:00:00.000Z' WHERE id = ?").param(goalId).update();
        postTask("""
                {"title":"t","goalId":"%s"}""".formatted(goalId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("goal_archived"));
    }

    @Test
    void createValidation() throws Exception {
        postTask("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        postTask("""
                {"title":"t","status":"blocked"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.status").value("invalid_value"));
        postTask("""
                {"title":"t","priority":"urgent"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.priority").value("invalid_value"));
        postTask("""
                {"title":"t","estimatedMinutes":-1}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.estimatedMinutes").value("must_not_be_negative"));
        postTask("""
                {"title":"t","estimatedMinutes":1.5}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.estimatedMinutes").value("invalid_value"));
        postTask("""
                {"title":"t","estimatedMinutes":"30"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.estimatedMinutes").value("invalid_value"));
        postTask("""
                {"title":"t","dueDate":"2026-02-30"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.dueDate").value("invalid_value"));
        postTask("""
                {"title":"t","focusItemId":"%s"}""".formatted(MISSING))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.details.focusItemId").value(MISSING)); // F-02: real field
        postTask("""
                {"title":"t","plannedDate":"2026-11-03"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.plannedDate").value("unknown_field"));
    }

    // ---- read -----------------------------------------------------------------------------------

    @Test
    void missingTaskIs404() throws Exception {
        mvc.perform(get("/api/v1/tasks/" + MISSING)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.taskId").value(MISSING));
        patchTask(MISSING, """
                {"status":"done"}""").andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByStatusGoalAndArchive() throws Exception {
        String linked = createTask("""
                {"title":"linked","goalId":"%s","status":"done"}""".formatted(goalId));
        String standalone = createTask("""
                {"title":"standalone"}""");
        String archived = createTask("""
                {"title":"archived","goalId":"%s"}""".formatted(goalId));
        patchTask(archived, """
                {"archived":true}""").andExpect(status().isOk());

        mvc.perform(get("/api/v1/tasks?goalId=" + goalId))
                .andExpect(jsonPath("$[*].id", hasItem(linked)))
                .andExpect(jsonPath("$[*].id", not(hasItem(archived))))
                .andExpect(jsonPath("$[*].id", not(hasItem(standalone))));
        mvc.perform(get("/api/v1/tasks?archived=true&goalId=" + goalId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(archived));
        mvc.perform(get("/api/v1/tasks?archived=all&goalId=" + goalId)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/tasks?status=done"))
                .andExpect(jsonPath("$[*].id", hasItem(linked)))
                .andExpect(jsonPath("$[*].status", not(hasItem("not_started"))));
        mvc.perform(get("/api/v1/tasks?status=blocked")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").value("invalid_value"));
        mvc.perform(get("/api/v1/tasks?archived=maybe")).andExpect(status().isBadRequest());
    }

    // ---- update ---------------------------------------------------------------------------------

    @Test
    void statusMovesFreelyAmongThreeStatuses() throws Exception {
        String id = createTask("""
                {"title":"t"}""");
        for (String s : new String[]{"doing", "done", "not_started", "done", "doing"}) {
            patchTask(id, """
                    {"status":"%s"}""".formatted(s)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(s));
        }
        patchTask(id, """
                {"status":"blocked"}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").value("invalid_value"));
        patchTask(id, """
                {"status":null}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").value("required"));
        mvc.perform(get("/api/v1/tasks/" + id)).andExpect(jsonPath("$.status").value("doing"));
        assertThat(rowCount(id)).isEqualTo(1);
    }

    @Test
    void patchLeavesAbsentFieldsAndClearsExplicitNulls() throws Exception {
        String id = createTask("""
                {"title":"t","goalId":"%s","priority":"low","estimatedMinutes":30,"dueDate":"2026-11-10","blockedReason":"x"}"""
                .formatted(goalId));
        String before = mvc.perform(get("/api/v1/tasks/" + id)).andReturn().getResponse().getContentAsString();

        patchTask(id, """
                {"title":"t2"}""")
                .andExpect(jsonPath("$.title").value("t2"))
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.priority").value("low"))
                .andExpect(jsonPath("$.estimatedMinutes").value(30))
                .andExpect(jsonPath("$.dueDate").value("2026-11-10"))
                .andExpect(jsonPath("$.blockedReason").value("x"))
                .andExpect(jsonPath("$.createdAt").value((String) JsonPath.read(before, "$.createdAt")));

        patchTask(id, """
                {"goalId":null,"estimatedMinutes":null,"dueDate":null,"blockedReason":null}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("t2"))
                .andExpect(jsonPath("$.goalId").doesNotExist())
                .andExpect(jsonPath("$.priority").value("low")) // untouched by the other clears
                .andExpect(jsonPath("$.estimatedMinutes").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist())
                .andExpect(jsonPath("$.blockedReason").doesNotExist());

        patchTask(id, """
                {"goalId":"%s","estimatedMinutes":0,"dueDate":"2026-12-01"}""".formatted(goalId))
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.estimatedMinutes").value(0))
                .andExpect(jsonPath("$.dueDate").value("2026-12-01"));
    }

    @Test
    void patchValidation() throws Exception {
        String id = createTask("""
                {"title":"t"}""");
        patchTask(id, """
                {"title":"  "}""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        patchTask(id, """
                {"title":null}""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        patchTask(id, """
                {"estimatedMinutes":-5}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.estimatedMinutes").value("must_not_be_negative"));
        patchTask(id, """
                {"dueDate":"2026-13-01"}""").andExpect(status().isBadRequest());
        patchTask(id, """
                {"goalId":"%s"}""".formatted(MISSING)).andExpect(status().isNotFound());
        patchTask(id, """
                {"id":"other"}""").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.id").value("unknown_field"));
        patchTask(id, """
                {"archived":null}""").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tasks/" + id)).andExpect(jsonPath("$.title").value("t"));
    }

    @Test
    void archivedTaskRejectsEditsUntilRestored() throws Exception {
        String id = createTask("""
                {"title":"t"}""");
        String archivedAt = JsonPath.read(patchTask(id, """
                {"archived":true}""").andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.archivedAt");
        assertThat(archivedAt).isNotNull();
        patchTask(id, """
                {"archived":true}""").andExpect(jsonPath("$.archivedAt").value(archivedAt)); // idempotent
        patchTask(id, """
                {"status":"done"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("task_archived"));
        mvc.perform(get("/api/v1/tasks/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("not_started"));
        patchTask(id, """
                {"archived":false,"status":"done"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.status").value("done"));
    }

    @Test
    void existingLinkToArchivedGoalSurvivesUnrelatedEdits() throws Exception {
        String id = createTask("""
                {"title":"t","goalId":"%s"}""".formatted(goalId));
        db.sql("UPDATE goals SET archived_at = '2026-11-03T12:00:00.000Z' WHERE id = ?").param(goalId).update();
        patchTask(id, """
                {"status":"done"}""").andExpect(status().isOk()).andExpect(jsonPath("$.goalId").value(goalId));
    }

    @Test
    void anActiveTaskCannotBeDeleted() throws Exception { // only archived tasks can be deleted (ArchiveDeletionIntegrationTest)
        String id = createTask("""
                {"title":"t"}""");
        mvc.perform(delete("/api/v1/tasks/" + id)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_archived"));
        assertThat(rowCount(id)).isEqualTo(1);
    }

    @Test
    void everyTaskHasAPriorityLowByDefaultAndItCannotBeCleared() throws Exception {
        String id = JsonPath.read(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"p\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.priority").value("low"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        for (String p : new String[] {"medium", "high", "low"}) {
            patchTask(id, "{\"priority\":\"" + p + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.priority").value(p));
        }
        patchTask(id, "{\"priority\":null}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.priority").value("required"));
        mvc.perform(get("/api/v1/tasks/" + id)).andExpect(jsonPath("$.priority").value("low"));
    }
}
