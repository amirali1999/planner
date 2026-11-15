package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G-06: goal list filters (status, periodType, archive, due window, overdue), stable pagination, manual status. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/goal-filter-${random.uuid}.db")
@AutoConfigureMockMvc
// a fresh context = a fresh random DB file per test, so filter results are exact (no shared goals, no deletes)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GoalFilterIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient db;

    String postJson(String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    String goal(String title, String status, String periodType, String deadline) throws Exception {
        String range = switch (periodType) {
            case "annual" -> "\"periodStart\":\"2026-03-21\",\"periodEnd\":\"2027-03-20\"";
            case "quarterly" -> "\"periodStart\":\"2026-09-23\",\"periodEnd\":\"2026-12-21\"";
            case "weekly" -> "\"periodStart\":\"2026-11-07\",\"periodEnd\":\"2026-11-13\"";
            default -> "\"periodStart\":\"2026-10-23\",\"periodEnd\":\"2026-11-21\"";
        };
        String dl = deadline == null ? "" : ",\"deadline\":\"" + deadline + "\"";
        return JsonPath.read(postJson("/api/v1/goals", "{\"title\":\"" + title + "\",\"status\":\"" + status
                + "\",\"periodType\":\"" + periodType + "\"," + range + dl + "}"), "$.id");
    }

    List<String> titles(String query) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/goals?" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[*].title");
    }

    void seedAllStatuses() throws Exception {
        goal("ns", "not_started", "monthly", null);
        goal("doing", "doing", "monthly", null);
        goal("doing-q", "doing", "quarterly", null);
        goal("done", "done", "monthly", null);
        goal("hold", "on_hold", "annual", null);
        goal("dropped", "dropped", "weekly", null);
        String archived = goal("doing-archived", "doing", "monthly", null);
        mvc.perform(post("/api/v1/goals/" + archived + "/archive")).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "status={0} → {1}")
    @CsvSource(delimiter = ';', value = {
            "not_started;ns",
            "doing;doing,doing-q",
            "done;done",
            "on_hold;hold",
            "dropped;dropped",
            "on_hold,dropped;hold,dropped",
            "doing&status=doing&status=done;doing,doing-q,done", // repeated + duplicate value → still distinct rows
    })
    void everyStatusFiltersExactlyAndDistinct(String status, String expected) throws Exception {
        seedAllStatuses();
        List<String> got = titles("status=" + status);
        assertThat(got).containsExactlyInAnyOrder(expected.split(","));
        assertThat(new HashSet<>(got)).hasSameSizeAs(got);
    }

    @Test
    void statusCombinesWithArchiveAndPeriodType() throws Exception {
        seedAllStatuses();
        assertThat(titles("status=doing&archived=true")).containsExactly("doing-archived");
        assertThat(titles("status=doing&archived=all")).containsExactlyInAnyOrder("doing", "doing-q", "doing-archived");
        assertThat(titles("status=doing&periodType=quarterly")).containsExactly("doing-q");
        assertThat(titles("periodType=annual,weekly")).containsExactlyInAnyOrder("hold", "dropped");
        assertThat(titles("")).hasSize(6).doesNotContain("doing-archived"); // default: active only, all statuses
        assertThat(titles("status=&periodType=")).hasSize(6); // empty value = no filter (as a cleared form field sends)
        mvc.perform(get("/api/v1/goals?status=doing")).andExpect(header().string("X-Total-Count", "2"));
    }

    @ParameterizedTest(name = "{0} → 400 {1}")
    @CsvSource({
            "status=finished,status",
            "status=doing&status=bogus,status",
            "status=DOING,status",
            "periodType=yearly,periodType",
            "archived=maybe,archived",
            "deadlineFrom=2026-13-01,deadlineFrom",
            "deadlineTo=2026-11-9,deadlineTo",
            "overdueOn=1403-08-18x,overdueOn",
            "sort=title,sort",
            "limit=0,limit",
            "limit=201,limit",
            "limit=abc,limit",
            "offset=-1,offset",
    })
    void invalidParametersAre400WithFieldCode(String query, String field) throws Exception {
        mvc.perform(get("/api/v1/goals?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details." + field).exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void reversedDueWindowIsRejected() throws Exception {
        mvc.perform(get("/api/v1/goals?deadlineFrom=2026-11-10&deadlineTo=2026-11-09"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.deadlineTo").value("before_deadline_from"));
    }

    @Test
    void deadlineBoundariesAreDateOnlyAndInclusive() throws Exception {
        goal("yesterday", "doing", "monthly", "2026-11-08");
        goal("today", "doing", "monthly", "2026-11-09");
        goal("tomorrow", "not_started", "monthly", "2026-11-10");
        goal("hold-late", "on_hold", "monthly", "2026-11-01");
        goal("done-late", "done", "monthly", "2026-11-01");
        goal("dropped-late", "dropped", "monthly", "2026-11-01");
        goal("no-deadline", "doing", "monthly", null);

        // overdue = deadline strictly before the client's date, status still open; due today is not overdue
        assertThat(titles("overdueOn=2026-11-09")).containsExactlyInAnyOrder("yesterday", "hold-late");
        assertThat(titles("overdueOn=2026-11-10")).containsExactlyInAnyOrder("yesterday", "today", "hold-late");
        assertThat(titles("overdueOn=2026-11-08")).containsExactly("hold-late");
        // inclusive window; goals without deadline never match a window
        assertThat(titles("deadlineFrom=2026-11-09&deadlineTo=2026-11-09")).containsExactly("today");
        assertThat(titles("deadlineFrom=2026-11-09")).containsExactlyInAnyOrder("today", "tomorrow");
        assertThat(titles("deadlineTo=2026-11-08")).containsExactlyInAnyOrder("yesterday", "hold-late", "done-late", "dropped-late");
        // deadline ordering: dated by deadline, undated last
        assertThat(titles("sort=deadline").getLast()).isEqualTo("no-deadline");
        assertThat(titles("sort=deadline&status=doing")).containsExactly("yesterday", "today", "no-deadline");
    }

    @Test
    void paginationIsStableAndComplete() throws Exception {
        // identical period start for all → ties broken by created_at then id
        for (int i = 0; i < 7; i++) {
            goal("g" + i, i % 2 == 0 ? "doing" : "not_started", "monthly", null);
        }
        List<String> all = titles("");
        List<String> paged = new ArrayList<>();
        for (int offset = 0; offset < 9; offset += 3) {
            MvcResult r = mvc.perform(get("/api/v1/goals?limit=3&offset=" + offset))
                    .andExpect(header().string("X-Total-Count", "7")).andReturn();
            paged.addAll(JsonPath.read(r.getResponse().getContentAsString(), "$[*].title"));
        }
        assertThat(paged).containsExactlyElementsOf(all).hasSize(7);
        assertThat(titles("limit=3&offset=3")).isEqualTo(titles("limit=3&offset=3")); // repeatable
        assertThat(titles("limit=5&offset=100")).isEmpty();
        assertThat(titles("status=doing&limit=2&offset=2")).hasSize(2);
        mvc.perform(get("/api/v1/goals?status=doing&limit=2")).andExpect(header().string("X-Total-Count", "4"));
    }

    @Test
    void goalStaysDoingAtOutcome100AndExecution100() throws Exception {
        String g = goal("measured", "doing", "monthly", null);
        String kr = JsonPath.read(postJson("/api/v1/goals/" + g + "/key-results", "{\"name\":\"k\",\"baseline\":0,\"target\":10}"), "$.id");
        postJson("/api/v1/key-results/" + kr + "/check-ins", "{\"value\":12,\"measuredAt\":\"2026-11-09T08:00:00Z\"}");
        String task = JsonPath.read(postJson("/api/v1/tasks", "{\"title\":\"t\",\"goalId\":\"" + g + "\"}"), "$.id");
        mvc.perform(patch("/api/v1/tasks/" + task).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"done\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/goals/" + g + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(100.0))
                .andExpect(jsonPath("$.executionProgress").value(100.0));
        mvc.perform(get("/api/v1/goals/" + g)).andExpect(jsonPath("$.status").value("doing"));
        assertThat(titles("status=doing")).containsExactly("measured");
        assertThat(titles("status=done")).isEmpty();
        assertThat(db.sql("SELECT count(*) FROM goal_history WHERE goal_id = ? AND event_type = 'status_changed'")
                .param(g).query(Integer.class).single()).isZero();

        // and the opposite: manual done does not touch the measured numbers
        String other = goal("manual", "doing", "monthly", null);
        String kr2 = JsonPath.read(postJson("/api/v1/goals/" + other + "/key-results", "{\"name\":\"k\",\"baseline\":0,\"target\":10}"), "$.id");
        postJson("/api/v1/key-results/" + kr2 + "/check-ins", "{\"value\":4,\"measuredAt\":\"2026-11-09T08:00:00Z\"}");
        mvc.perform(patch("/api/v1/goals/" + other).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"done\"}"))
                .andExpect(jsonPath("$.status").value("done"));
        mvc.perform(get("/api/v1/goals/" + other + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(40.0))
                .andExpect(jsonPath("$.executionProgress").doesNotExist());
    }

    @Test
    void everyStatusCanBeSetManuallyAndUnknownIsRejected() throws Exception {
        String g = goal("s", "not_started", "monthly", null);
        for (String s : List.of("doing", "on_hold", "dropped", "done", "not_started")) {
            mvc.perform(patch("/api/v1/goals/" + g).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(s));
        }
        mvc.perform(patch("/api/v1/goals/" + g).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"blocked\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/goals/" + g + "/archive")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/goals/" + g).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"doing\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_archived"));
    }

    @Test
    void listReadsNeverWrite() throws Exception {
        String g = goal("r", "doing", "monthly", "2026-11-01");
        String before = db.sql("SELECT updated_at FROM goals WHERE id = ?").param(g).query(String.class).single();
        titles("overdueOn=2026-11-09&sort=deadline&limit=1");
        mvc.perform(get("/api/v1/goals/progress").param("ids", g)).andExpect(status().isOk());
        assertThat(db.sql("SELECT updated_at FROM goals WHERE id = ?").param(g).query(String.class).single()).isEqualTo(before);
        assertThat(db.sql("SELECT status FROM goals WHERE id = ?").param(g).query(String.class).single()).isEqualTo("doing");
    }

    @Test
    void batchProgressMatchesSingleProgress() throws Exception {
        String a = goal("a", "doing", "monthly", null);
        String b = goal("b", "on_hold", "monthly", null);
        String kr = JsonPath.read(postJson("/api/v1/goals/" + a + "/key-results", "{\"name\":\"k\",\"baseline\":0,\"target\":10}"), "$.id");
        postJson("/api/v1/key-results/" + kr + "/check-ins", "{\"value\":5,\"measuredAt\":\"2026-11-09T08:00:00Z\"}");
        mvc.perform(get("/api/v1/goals/progress").param("ids", b + "," + a + "," + b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].goalId").value(b))
                .andExpect(jsonPath("$[0].outcomeProgress").doesNotExist())
                .andExpect(jsonPath("$[1].goalId").value(a))
                .andExpect(jsonPath("$[1].outcomeProgress").value(50.0));
        mvc.perform(get("/api/v1/goals/progress").param("ids", a, "missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.details.goalId").value("missing"));
        mvc.perform(get("/api/v1/goals/progress")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/goals/progress").param("ids", String.join(",", java.util.Collections.nCopies(1, "x")) + ",,"))
                .andExpect(status().isBadRequest());
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 201; i++) many.add("id" + i);
        mvc.perform(get("/api/v1/goals/progress").param("ids", String.join(",", many)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.ids").value("too_many"));
    }

    @Test
    void dashboardInclusionRulesByStatusAndArchive() throws Exception {
        for (String s : List.of("not_started", "doing", "on_hold", "done", "dropped")) {
            goal("dash-" + s, s, "monthly", null);
        }
        String archived = goal("dash-archived", "doing", "monthly", null);
        mvc.perform(post("/api/v1/goals/" + archived + "/archive")).andExpect(status().isOk());
        String dash = mvc.perform(get("/api/v1/dashboard/today").param("date", "2026-11-09")).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(dash, "$.goals[*].title"))
                .containsExactlyInAnyOrder("dash-not_started", "dash-doing", "dash-on_hold");
    }
}
