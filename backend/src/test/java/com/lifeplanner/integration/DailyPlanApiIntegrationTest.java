package com.lifeplanner.integration;

import com.lifeplanner.model.DailyPlanItem;
import com.lifeplanner.repository.DailyPlanRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DailyPlanApiIntegrationTest {

    static final String MISSING = "00000000-0000-0000-0000-000000000000";
    /** Each test uses its own dates; the context (and DB) is shared. */
    static final AtomicInteger DAY = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    @Autowired
    DailyPlanRepository repository;

    static String freshDate() {
        return java.time.LocalDate.of(2040, 1, 1).plusDays(DAY.getAndIncrement()).toString();
    }

    String createTask(String json) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions addItem(String date, String taskId) throws Exception {
        return mvc.perform(post("/api/v1/plans/daily/" + date + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"" + taskId + "\"}"));
    }

    int count(String table) {
        return db.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    // ---- GET is a pure read -----------------------------------------------------------------------

    @Test
    void getMissingPlanReturnsEmptyAndWritesNothing() throws Exception {
        String date = freshDate();
        int plansBefore = count("daily_plans");
        int itemsBefore = count("daily_plan_items");
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/v1/plans/daily").param("date", date))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date").value(date))
                    .andExpect(jsonPath("$.exists").value(false))
                    .andExpect(jsonPath("$.planId").doesNotExist())
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.completionPercent").doesNotExist());
        }
        assertThat(count("daily_plans")).isEqualTo(plansBefore);
        assertThat(count("daily_plan_items")).isEqualTo(itemsBefore);
    }

    @Test
    void getExistingPlanDoesNotTouchIt() throws Exception {
        String date = freshDate();
        mvc.perform(put("/api/v1/plans/daily/" + date)).andExpect(status().isCreated());
        String before = db.sql("SELECT updated_at FROM daily_plans WHERE local_date = ?").param(date).query(String.class).single();
        mvc.perform(get("/api/v1/plans/daily").param("date", date)).andExpect(jsonPath("$.exists").value(true));
        assertThat(db.sql("SELECT updated_at FROM daily_plans WHERE local_date = ?").param(date).query(String.class).single())
                .isEqualTo(before);
    }

    @Test
    void invalidDatesAre400() throws Exception {
        for (String bad : new String[]{"2026-02-30", "2026-13-01", "2026-11-3", "2026-11-03+03:30", "2026-11-03T00:00:00Z", "today"}) {
            mvc.perform(get("/api/v1/plans/daily").param("date", bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.date").value("invalid_value"));
            mvc.perform(put("/api/v1/plans/daily/" + bad)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/plans/daily")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.date").value("required"));
    }

    // ---- PUT is idempotent ------------------------------------------------------------------------

    @Test
    void repeatedPutReturnsSamePlanAndOneRow() throws Exception {
        String date = freshDate();
        String first = JsonPath.read(mvc.perform(put("/api/v1/plans/daily/" + date))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(date))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.completionPercent").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.planId");
        mvc.perform(put("/api/v1/plans/daily/" + date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(first));
        assertThat(db.sql("SELECT count(*) FROM daily_plans WHERE local_date = ?").param(date).query(Integer.class).single())
                .isEqualTo(1);
        // date-only round trip: stored exactly as sent, no timezone shift
        assertThat(db.sql("SELECT local_date FROM daily_plans WHERE id = ?").param(first).query(String.class).single())
                .isEqualTo(date);
    }

    // ---- items ------------------------------------------------------------------------------------

    @Test
    void addingTaskCreatesPlanAndReferencesTheSameTaskRow() throws Exception {
        String date = freshDate();
        String taskId = createTask("""
                {"title":"مطالعه فصل دوم"}""");
        int tasksBefore = count("tasks");
        addItem(date, taskId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(0))
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.task.status").value("not_started"));
        assertThat(count("tasks")).isEqualTo(tasksBefore);

        // status changed through the Task API shows up in the plan: one record, not a copy
        mvc.perform(patch("/api/v1/tasks/" + taskId).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"done\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/plans/daily").param("date", date))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].task.id").value(taskId))
                .andExpect(jsonPath("$.items[0].task.status").value("done"))
                .andExpect(jsonPath("$.completionPercent").value(100.0));
    }

    @Test
    void addingToTodayCanAtomicallyPutTheTaskOnItsWeekToo() throws Exception {
        String date = freshDate();
        String taskId = createTask("{\"title\":\"today and week\"}");
        String saturday = LocalDate.parse(date).with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).toString();

        mvc.perform(post("/api/v1/plans/daily/" + date + "/items").param("addToWeek", "true")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":\"" + taskId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.task.id").value(taskId));

        mvc.perform(get("/api/v1/plans/daily").param("date", date))
                .andExpect(jsonPath("$.items[0].task.id").value(taskId));
        mvc.perform(get("/api/v1/plans/weekly/" + saturday + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].task.id").value(taskId));
    }

    @Test
    void sameTaskTwiceOnOneDateIs409ButAllowedOnAnotherDate() throws Exception {
        String date = freshDate();
        String taskId = createTask("""
                {"title":"t"}""");
        addItem(date, taskId).andExpect(status().isCreated());
        addItem(date, taskId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_plan_task"))
                .andExpect(jsonPath("$.details.taskId").value(taskId));
        assertThat(db.sql("""
                SELECT count(*) FROM daily_plan_items i JOIN daily_plans p ON p.id = i.plan_id
                WHERE p.local_date = ? AND i.task_id = ?""").params(date, taskId).query(Integer.class).single()).isEqualTo(1);
        addItem(freshDate(), taskId).andExpect(status().isCreated());
    }

    @Test
    void itemErrors() throws Exception {
        String date = freshDate();
        addItem(date, MISSING).andExpect(status().isNotFound()).andExpect(jsonPath("$.details.taskId").value(MISSING));
        mvc.perform(post("/api/v1/plans/daily/" + date + "/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.taskId").value("required"));
        String archived = createTask("""
                {"title":"old"}""");
        mvc.perform(patch("/api/v1/tasks/" + archived).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
                .andExpect(status().isOk());
        addItem(date, archived).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("task_archived"));
        // failed adds do not leave a plan behind
        mvc.perform(get("/api/v1/plans/daily").param("date", date)).andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    void completionCountsActiveTasksAndKeepsArchivedItemsVisible() throws Exception {
        String date = freshDate();
        String a = createTask("{\"title\":\"a\",\"status\":\"done\"}");
        String b = createTask("{\"title\":\"b\"}");
        String c = createTask("{\"title\":\"c\",\"status\":\"done\"}");
        addItem(date, a).andExpect(jsonPath("$.position").value(0));
        addItem(date, b).andExpect(jsonPath("$.position").value(1));
        addItem(date, c).andExpect(jsonPath("$.position").value(2));
        mvc.perform(get("/api/v1/plans/daily").param("date", date))
                .andExpect(jsonPath("$.activeTaskCount").value(3))
                .andExpect(jsonPath("$.doneTaskCount").value(2))
                .andExpect(jsonPath("$.completionPercent").value(66.67));
        mvc.perform(patch("/api/v1/tasks/" + c).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"));
        mvc.perform(get("/api/v1/plans/daily").param("date", date))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[2].task.archivedAt").isString())
                .andExpect(jsonPath("$.activeTaskCount").value(2))
                .andExpect(jsonPath("$.doneTaskCount").value(1))
                .andExpect(jsonPath("$.completionPercent").value(50.0));
    }

    @Test
    void uniqueViolationIsTranslatedToDuplicateKeyException() throws Exception {
        String date = freshDate();
        String taskId = createTask("{\"title\":\"t\"}");
        addItem(date, taskId).andExpect(status().isCreated());
        String planId = db.sql("SELECT id FROM daily_plans WHERE local_date = ?").param(date).query(String.class).single();
        assertThatThrownBy(() -> repository.insertItem(new DailyPlanItem(java.util.UUID.randomUUID().toString(), planId, taskId, 9,
                Instant.parse("2026-11-03T12:00:00Z"))))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
