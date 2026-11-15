package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** completedAt on tasks and GET /plans/daily/{date}/carry-over («تخته امروز»: unfinished tasks of earlier days). */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/carry-over-${random.uuid}.db")
@AutoConfigureMockMvc
class CarryOverIntegrationTest {

    /** Each test gets its own block of ten days far from the others, so earlier tests never leak into later ones. */
    static final AtomicInteger BLOCK = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    static LocalDate freshDay() {
        return LocalDate.of(2050, 1, 1).plusYears(BLOCK.getAndIncrement());
    }

    String task(String json) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    void plan(LocalDate date, String taskId) throws Exception {
        mvc.perform(post("/api/v1/plans/daily/" + date + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"" + taskId + "\"}")).andExpect(status().isCreated());
    }

    String patchTask(String id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/tasks/" + id).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    String carry(LocalDate date, String query) throws Exception {
        return mvc.perform(get("/api/v1/plans/daily/" + date + "/carry-over" + query))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    List<String> ids(String json) {
        return JsonPath.read(json, "$[*].task.id");
    }

    @Test
    void completedAtFollowsTheStatus() throws Exception {
        String id = task("{\"title\":\"c\"}");
        assertThat((Object) JsonPath.read(patchTask(id, "{\"status\":\"doing\"}"), "$.completedAt")).isNull();
        String done = JsonPath.read(patchTask(id, "{\"status\":\"done\"}"), "$.completedAt");
        assertThat(done).isNotNull();
        // other edits of a done task keep the original completion time
        assertThat((String) JsonPath.read(patchTask(id, "{\"title\":\"c2\"}"), "$.completedAt")).isEqualTo(done);
        assertThat((Object) JsonPath.read(patchTask(id, "{\"status\":\"not_started\"}"), "$.completedAt")).isNull();
        // a task created as done is completed when created
        String created = mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"d\",\"status\":\"done\"}"))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(created, "$.completedAt")).isEqualTo(JsonPath.read(created, "$.createdAt"));
    }

    @Test
    void unfinishedTasksOfEarlierDaysAreCarriedOverWithTheirLatestDay() throws Exception {
        LocalDate today = freshDay();
        String open = task("{\"title\":\"open\"}");
        plan(today.minusDays(3), open);
        plan(today.minusDays(1), open);
        String doing = task("{\"title\":\"doing\",\"status\":\"doing\"}");
        plan(today.minusDays(2), doing);
        String done = task("{\"title\":\"done\"}");
        plan(today.minusDays(1), done);
        patchTask(done, "{\"status\":\"done\"}");
        String alsoToday = task("{\"title\":\"already today\"}");
        plan(today.minusDays(1), alsoToday);
        plan(today, alsoToday);
        String archived = task("{\"title\":\"archived\"}");
        plan(today.minusDays(1), archived);
        patchTask(archived, "{\"archived\":true}");
        String future = task("{\"title\":\"later\"}");
        plan(today.plusDays(1), future);
        String rescheduled = task("{\"title\":\"rescheduled\"}");
        plan(today.minusDays(2), rescheduled);
        plan(today.plusDays(1), rescheduled);

        String json = carry(today, "");
        assertThat(ids(json)).containsExactly(doing, open); // oldest latest-day first
        assertThat(ids(json)).doesNotContain(rescheduled); // a future plan supersedes carry-over from the old day
        assertThat((String) JsonPath.read(json, "$[1].plannedOn")).isEqualTo(today.minusDays(1).toString());
        assertThat((String) JsonPath.read(json, "$[0].task.status")).isEqualTo("doing");
        // nothing was written by the read
        mvc.perform(get("/api/v1/plans/daily?date=" + today)).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void finishedTasksAppearOnlyInsideTheRequestedWindow() throws Exception {
        LocalDate today = freshDay();
        String done = task("{\"title\":\"done\"}");
        plan(today.minusDays(2), done);
        String completedAt = JsonPath.read(patchTask(done, "{\"status\":\"done\"}"), "$.completedAt");
        Instant at = Instant.parse(completedAt);
        assertThat(ids(carry(today, ""))).doesNotContain(done);
        assertThat(ids(carry(today, "?completedFrom=" + at.minusSeconds(60) + "&completedTo=" + at.plusSeconds(60)))).contains(done);
        assertThat(ids(carry(today, "?completedFrom=" + at.plusSeconds(1) + "&completedTo=" + at.plusSeconds(3600)))).doesNotContain(done);
        assertThat(ids(carry(today, "?completedFrom=" + at.minusSeconds(3600) + "&completedTo=" + at))).doesNotContain(done); // end exclusive
    }

    @Test
    void invalidParametersAre400() throws Exception {
        mvc.perform(get("/api/v1/plans/daily/2050-13-01/carry-over")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/plans/daily/2050-01-01/carry-over?completedFrom=2050-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.completedTo").value("required"));
        mvc.perform(get("/api/v1/plans/daily/2050-01-01/carry-over?completedFrom=yesterday&completedTo=2050-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.completedFrom").value("invalid_value"));
        mvc.perform(get("/api/v1/plans/daily/2050-01-01/carry-over?completedFrom=2050-01-02T00:00:00Z&completedTo=2050-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.completedTo").value("must_be_after_completedFrom"));
    }
}
