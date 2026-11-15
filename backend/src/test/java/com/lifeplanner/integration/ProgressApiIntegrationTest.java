package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProgressApiIntegrationTest {

    /** Each dashboard test owns a 10-year block of far-future dates so goals from other tests never match. */
    static final AtomicInteger YEAR = new AtomicInteger(2050);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String post(String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path) // FQN: helper post() shadows the static import
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    void patchJson(String path, String json) throws Exception {
        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());
    }

    String goal(int year, String status) throws Exception {
        return JsonPath.read(post("/api/v1/goals", """
                {"title":"g","periodType":"annual","periodStart":"%d-03-21","periodEnd":"%d-03-20","status":"%s"}"""
                .formatted(year, year + 1, status)), "$.id");
    }

    String goal() throws Exception {
        return goal(2026, "doing");
    }

    String kr(String goalId, String baseline, String target, String weight) throws Exception {
        return JsonPath.read(post("/api/v1/goals/" + goalId + "/key-results", """
                {"name":"kr","baseline":%s,"target":%s,"weight":%s}""".formatted(baseline, target, weight)), "$.id");
    }

    void checkIn(String krId, String value) throws Exception {
        post("/api/v1/key-results/" + krId + "/check-ins", """
                {"value":%s,"measuredAt":"2026-11-03T12:00:00Z"}""".formatted(value));
    }

    String task(String goalId, String status) throws Exception {
        String goalPart = goalId == null ? "" : ",\"goalId\":\"" + goalId + "\"";
        return JsonPath.read(post("/api/v1/tasks", """
                {"title":"t","status":"%s"%s}""".formatted(status, goalPart)), "$.id");
    }

    // ---- /goals/{id}/progress -----------------------------------------------------------------------

    @Test
    void acceptanceKr50AndThreeOfFourTasksDone() throws Exception {
        String g = goal();
        checkIn(kr(g, "20", "60", "1"), "40");
        task(g, "done");
        task(g, "done");
        task(g, "done");
        task(g, "doing");
        mvc.perform(get("/api/v1/goals/" + g + "/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId").value(g))
                .andExpect(jsonPath("$.outcomeProgress").value(50.0))
                .andExpect(jsonPath("$.outcomeSource").value("own_key_results"))
                .andExpect(jsonPath("$.keyResultsCount").value(1))
                .andExpect(jsonPath("$.measuredAt").value("2026-11-03T12:00:00Z"))
                .andExpect(jsonPath("$.executionProgress").value(75.0))
                .andExpect(jsonPath("$.activeTaskCount").value(4))
                .andExpect(jsonPath("$.doneTaskCount").value(3))
                .andExpect(jsonPath("$.combinedProgress").doesNotExist())
                .andExpect(content().string(not(containsString("62.5"))));
    }

    @Test
    void noKrAndNoTasksAreNullNotZero() throws Exception {
        String g = goal();
        mvc.perform(get("/api/v1/goals/" + g + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").doesNotExist())
                .andExpect(jsonPath("$.outcomeSource").value("not_measured"))
                .andExpect(jsonPath("$.keyResultsCount").value(0))
                .andExpect(jsonPath("$.measuredAt").doesNotExist())
                .andExpect(jsonPath("$.executionProgress").doesNotExist())
                .andExpect(jsonPath("$.activeTaskCount").value(0));
    }

    @Test
    void baselineOnlyKrIsExplicitZeroAndUndoneTasksAreZero() throws Exception {
        String g = goal();
        kr(g, "20", "80", "1");
        task(g, "not_started");
        mvc.perform(get("/api/v1/goals/" + g + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(0.0))
                .andExpect(jsonPath("$.outcomeSource").value("own_key_results"))
                .andExpect(jsonPath("$.measuredAt").doesNotExist())
                .andExpect(jsonPath("$.executionProgress").value(0.0));
    }

    @Test
    void decreasingAndWeightedKrs() throws Exception {
        String g = goal();
        checkIn(kr(g, "100", "60", "1"), "80"); // 50
        mvc.perform(get("/api/v1/goals/" + g + "/progress")).andExpect(jsonPath("$.outcomeProgress").value(50.0));

        String weighted = goal();
        checkIn(kr(weighted, "0", "10", "1"), "10"); // 100, weight 1
        kr(weighted, "5", "1", "3");                  // baseline only: 0, weight 3
        mvc.perform(get("/api/v1/goals/" + weighted + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(25.0))
                .andExpect(jsonPath("$.keyResultsCount").value(2));
    }

    @Test
    void archivedKrsAndTasksAndOtherTasksAreExcluded() throws Exception {
        String g = goal();
        String archivedKr = kr(g, "0", "10", "1");
        checkIn(archivedKr, "0");
        checkIn(kr(g, "0", "10", "1"), "10");
        db.sql("UPDATE key_results SET archived_at = '2026-11-03T12:00:00.000Z' WHERE id = ?").param(archivedKr).update();
        task(g, "done");
        String archivedTask = task(g, "not_started");
        patchJson("/api/v1/tasks/" + archivedTask, "{\"archived\":true}");
        task(null, "not_started");      // standalone: belongs to no goal
        task(goal(), "not_started");    // another goal's task
        mvc.perform(get("/api/v1/goals/" + g + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(100.0))
                .andExpect(jsonPath("$.keyResultsCount").value(1))
                .andExpect(jsonPath("$.executionProgress").value(100.0))
                .andExpect(jsonPath("$.activeTaskCount").value(1));
    }

    @Test
    void fullProgressNeverChangesGoalStatus() throws Exception {
        String g = goal(2026, "not_started");
        checkIn(kr(g, "0", "1", "1"), "1");
        task(g, "done");
        mvc.perform(get("/api/v1/goals/" + g + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(100.0))
                .andExpect(jsonPath("$.executionProgress").value(100.0));
        mvc.perform(get("/api/v1/goals/" + g)).andExpect(jsonPath("$.status").value("not_started"));
    }

    @Test
    void missingGoalIs404() throws Exception {
        mvc.perform(get("/api/v1/goals/00000000-0000-0000-0000-000000000000/progress"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
    }

    // ---- /dashboard/today ---------------------------------------------------------------------------

    @Test
    void emptyDayHasNullCompletionAndCreatesNoPlan() throws Exception {
        int year = YEAR.getAndAdd(10);
        String date = year + "-06-01";
        int plansBefore = db.sql("SELECT count(*) FROM daily_plans").query(Integer.class).single();
        mvc.perform(get("/api/v1/dashboard/today").param("date", date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(date))
                .andExpect(jsonPath("$.today.planExists").value(false))
                .andExpect(jsonPath("$.today.plannedCount").value(0))
                .andExpect(jsonPath("$.today.completionPercent").doesNotExist())
                .andExpect(jsonPath("$.goals.length()").value(0));
        assertThat(db.sql("SELECT count(*) FROM daily_plans").query(Integer.class).single()).isEqualTo(plansBefore);
    }

    @Test
    void dashboardShowsTodayCountsAndTwoSeparateGoalMeasurements() throws Exception {
        int year = YEAR.getAndAdd(10);
        String date = year + "-06-01";
        String active = goal(year, "doing");
        checkIn(kr(active, "20", "60", "1"), "40");
        String t1 = task(active, "done");
        task(active, "done");
        task(active, "done");
        String t4 = task(active, "not_started");
        String onHold = goal(year, "on_hold");          // still shown, status visible
        goal(year, "done");                             // finished by the user: hidden
        goal(year, "dropped");                          // hidden
        String archived = goal(year, "doing");
        db.sql("UPDATE goals SET archived_at = '2026-11-03T12:00:00.000Z' WHERE id = ?").param(archived).update();
        goal(year + 1, "doing");                        // period does not contain the date
        post("/api/v1/plans/daily/" + date + "/items", "{\"taskId\":\"" + t1 + "\"}");
        post("/api/v1/plans/daily/" + date + "/items", "{\"taskId\":\"" + t4 + "\"}");

        mvc.perform(get("/api/v1/dashboard/today").param("date", date))
                .andExpect(jsonPath("$.today.planExists").value(true))
                .andExpect(jsonPath("$.today.plannedCount").value(2))
                .andExpect(jsonPath("$.today.doneTaskCount").value(1))
                .andExpect(jsonPath("$.today.completionPercent").value(50.0))
                .andExpect(jsonPath("$.goals.length()").value(2))
                .andExpect(jsonPath("$.goals[0].goalId").value(active))
                .andExpect(jsonPath("$.goals[0].outcomeProgress").value(50.0))
                .andExpect(jsonPath("$.goals[0].executionProgress").value(75.0))
                .andExpect(jsonPath("$.goals[0].combinedProgress").doesNotExist())
                .andExpect(jsonPath("$.goals[1].goalId").value(onHold))
                .andExpect(jsonPath("$.goals[1].status").value("on_hold"))
                .andExpect(jsonPath("$.goals[1].outcomeProgress").doesNotExist())
                .andExpect(jsonPath("$.goals[1].executionProgress").doesNotExist())
                .andExpect(content().string(not(containsString("62.5"))));
    }

    @Test
    void dashboardDateValidation() throws Exception {
        mvc.perform(get("/api/v1/dashboard/today")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.date").value("required"));
        mvc.perform(get("/api/v1/dashboard/today").param("date", "2026-02-30")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.date").value("invalid_value"));
    }
}
