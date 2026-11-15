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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KeyResultApiIntegrationTest {

    static final String MISSING = "00000000-0000-0000-0000-000000000000";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String goalId;

    @BeforeEach
    void createGoal() throws Exception {
        String body = mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        goalId = JsonPath.read(body, "$.id");
    }

    ResultActions postKr(String goal, String json) throws Exception {
        return mvc.perform(post("/api/v1/goals/" + goal + "/key-results").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String createKr(String json) throws Exception {
        return JsonPath.read(postKr(goalId, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions postCheckIn(String krId, String json) throws Exception {
        return mvc.perform(post("/api/v1/key-results/" + krId + "/check-ins").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---- create / read --------------------------------------------------------------------------

    @Test
    void newKrStartsAtBaselineWithExplicitZeroScore() throws Exception {
        postKr(goalId, """
                {"name":"Recall@5","unit":"%","baseline":20,"target":80}""")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/key-results/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.name").value("Recall@5"))
                .andExpect(jsonPath("$.unit").value("%"))
                .andExpect(jsonPath("$.baseline").value(20))
                .andExpect(jsonPath("$.target").value(80))
                .andExpect(jsonPath("$.weight").value(1))
                .andExpect(jsonPath("$.current").value(20))
                .andExpect(jsonPath("$.currentSource").value("baseline"))
                .andExpect(jsonPath("$.currentMeasuredAt").doesNotExist())
                .andExpect(jsonPath("$.score").value(0.0));
    }

    @Test
    void goalSupportsMultipleKeyResults() throws Exception {
        String a = createKr("""
                {"name":"Recall@5","baseline":20,"target":80,"weight":1}""");
        String b = createKr("""
                {"name":"Latency ms","unit":"ms","baseline":900,"target":300,"weight":3}""");
        mvc.perform(get("/api/v1/goals/" + goalId + "/key-results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(a))
                .andExpect(jsonPath("$[1].id").value(b))
                .andExpect(jsonPath("$[1].weight").value(3));
        mvc.perform(get("/api/v1/key-results/" + b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Latency ms"));
    }

    @Test
    void decimalsAreExactAndCanonical() throws Exception {
        String id = createKr("""
                {"name":"n","baseline":0.10,"target":0.5,"weight":0.333333}""");
        mvc.perform(get("/api/v1/key-results/" + id))
                .andExpect(jsonPath("$.baseline").value(0.1))
                .andExpect(jsonPath("$.weight").value(0.333333));
        String raw = mvc.perform(get("/api/v1/key-results/" + id)).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(raw).contains("\"baseline\":0.1,").doesNotContain("E-");
    }

    @Test
    void missingGoalOrKrIs404() throws Exception {
        postKr(MISSING, """
                {"name":"n","baseline":0,"target":1}""")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
        mvc.perform(get("/api/v1/goals/" + MISSING + "/key-results")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/key-results/" + MISSING)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.keyResultId").value(MISSING));
        mvc.perform(get("/api/v1/key-results/" + MISSING + "/check-ins")).andExpect(status().isNotFound());
        postCheckIn(MISSING, """
                {"value":1,"measuredAt":"2026-11-03T12:00:00Z"}""").andExpect(status().isNotFound());
    }

    // ---- KR validation --------------------------------------------------------------------------

    @Test
    void targetEqualToBaselineIs400() throws Exception {
        postKr(goalId, """
                {"name":"n","baseline":20,"target":20.00}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.target").value("equals_baseline"));
    }

    @Test
    void nonPositiveWeightIs400() throws Exception {
        for (String w : new String[]{"0", "-1", "0.000"}) {
            postKr(goalId, """
                    {"name":"n","baseline":0,"target":1,"weight":%s}""".formatted(w))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.weight").value("must_be_positive"));
        }
    }

    @Test
    void nonNumericOrNonFiniteNumbersAre400() throws Exception {
        for (String bad : new String[]{"\"20\"", "\"abc\"", "NaN", "Infinity", "true", "null"}) {
            postKr(goalId, """
                    {"name":"n","baseline":%s,"target":80}""".formatted(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation_error"));
        }
        postKr(goalId, """
                {"name":"n","baseline":0,"target":80,"weight":"abc"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.weight").value("invalid_value"));
    }

    @Test
    void precisionBeyondStorageIs400NotRounded() throws Exception {
        postKr(goalId, """
                {"name":"n","baseline":0.0000001,"target":1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.baseline").value("precision_exceeded"));
        postKr(goalId, """
                {"name":"n","baseline":0,"target":1e400}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.target").value("precision_exceeded"));
    }

    @Test
    void missingNameIs400() throws Exception {
        postKr(goalId, """
                {"baseline":0,"target":1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").value("required"));
    }

    @Test
    void archivedGoalRejectsNewKrWith409() throws Exception {
        db.sql("UPDATE goals SET archived_at = '2026-11-03T12:00:00.000Z' WHERE id = ?").param(goalId).update();
        postKr(goalId, """
                {"name":"n","baseline":0,"target":1}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("goal_archived"));
    }

    // ---- check-ins ------------------------------------------------------------------------------

    @Test
    void decreasingKrVector2ScoresFifty() throws Exception {
        String id = createKr("""
                {"name":"Latency","unit":"ms","baseline":100,"target":60}""");
        postCheckIn(id, """
                {"value":80,"measuredAt":"2026-11-03T12:00:00Z","note":"evaluation set v1"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyResultId").value(id))
                .andExpect(jsonPath("$.value").value(80))
                .andExpect(jsonPath("$.measuredAt").value("2026-11-03T12:00:00Z"))
                .andExpect(jsonPath("$.note").value("evaluation set v1"));
        mvc.perform(get("/api/v1/key-results/" + id))
                .andExpect(jsonPath("$.current").value(80))
                .andExpect(jsonPath("$.currentSource").value("check_in"))
                .andExpect(jsonPath("$.currentMeasuredAt").value("2026-11-03T12:00:00Z"))
                .andExpect(jsonPath("$.score").value(50.0));
    }

    @Test
    void increasingKrVector1AndClamping() throws Exception {
        String id = createKr("""
                {"name":"n","baseline":20,"target":60}""");
        postCheckIn(id, """
                {"value":40,"measuredAt":"2026-11-01T00:00:00Z"}""").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/key-results/" + id)).andExpect(jsonPath("$.score").value(50.0));
        postCheckIn(id, """
                {"value":95,"measuredAt":"2026-11-02T00:00:00Z"}""").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/key-results/" + id)).andExpect(jsonPath("$.score").value(100.0));
        postCheckIn(id, """
                {"value":-3,"measuredAt":"2026-11-03T00:00:00Z"}""").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/key-results/" + id)).andExpect(jsonPath("$.score").value(0.0))
                .andExpect(jsonPath("$.current").value(-3));
    }

    @Test
    void latestMeasuredAtDrivesCurrentNotInsertionOrder() throws Exception {
        String id = createKr("""
                {"name":"n","baseline":0,"target":3}""");
        postCheckIn(id, """
                {"value":2,"measuredAt":"2026-11-03T12:00:00Z"}""").andExpect(status().isCreated());
        // back-dated measurement entered later must not become current
        postCheckIn(id, """
                {"value":1,"measuredAt":"2026-11-01T08:00:00+03:30","note":"late entry"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.measuredAt").value("2026-11-01T04:30:00Z"));
        mvc.perform(get("/api/v1/key-results/" + id))
                .andExpect(jsonPath("$.current").value(2))
                .andExpect(jsonPath("$.score").value(66.67));
        mvc.perform(get("/api/v1/key-results/" + id + "/check-ins"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].value").value(1))
                .andExpect(jsonPath("$[1].value").value(2));
    }

    @Test
    void tieOnMeasuredAtFallsBackToCreatedAtThenId() throws Exception {
        String id = createKr("""
                {"name":"n","baseline":0,"target":100}""");
        String sql = "INSERT INTO key_result_checkins (id, key_result_id, value, measured_at, created_at) VALUES (?, ?, ?, ?, ?)";
        String m = "2026-11-03T12:00:00.000Z";
        db.sql(sql).params("aaaaaaaa-0000-0000-0000-000000000000", id, "10", m, "2026-11-03T13:00:00.000Z").update();
        db.sql(sql).params("bbbbbbbb-0000-0000-0000-000000000000", id, "20", m, "2026-11-03T12:30:00.000Z").update();
        mvc.perform(get("/api/v1/key-results/" + id)).andExpect(jsonPath("$.current").value(10));
        db.sql(sql).params("cccccccc-0000-0000-0000-000000000000", id, "30", m, "2026-11-03T13:00:00.000Z").update();
        mvc.perform(get("/api/v1/key-results/" + id)).andExpect(jsonPath("$.current").value(30));
    }

    @Test
    void checkInsAreAppendOnlyOverApi() throws Exception {
        String id = createKr("""
                {"name":"n","baseline":0,"target":10}""");
        String checkInId = JsonPath.read(postCheckIn(id, """
                {"value":5,"measuredAt":"2026-11-03T12:00:00Z"}""").andReturn().getResponse().getContentAsString(), "$.id");
        String path = "/api/v1/key-results/" + id + "/check-ins";
        mvc.perform(delete(path)).andExpect(status().isMethodNotAllowed());
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch(path + "/" + checkInId)).andExpect(status().isNotFound());
        mvc.perform(delete(path + "/" + checkInId)).andExpect(status().isNotFound());
        // a correction is a new row
        postCheckIn(id, """
                {"value":4,"measuredAt":"2026-11-03T12:00:01Z","note":"correction"}""").andExpect(status().isCreated());
        mvc.perform(get(path)).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(checkInId))
                .andExpect(jsonPath("$[0].value").value(5));
    }

    @Test
    void invalidCheckInsAre400() throws Exception {
        String id = createKr("""
                {"name":"n","baseline":0,"target":10}""");
        postCheckIn(id, """
                {"measuredAt":"2026-11-03T12:00:00Z"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.value").value("required"));
        postCheckIn(id, """
                {"value":1}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.measuredAt").value("required"));
        postCheckIn(id, """
                {"value":1,"measuredAt":"2026-11-03"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.measuredAt").value("invalid_value"));
        postCheckIn(id, """
                {"value":"1","measuredAt":"2026-11-03T12:00:00Z"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.value").value("invalid_value"));
        postCheckIn(id, """
                {"value":1.1234567,"measuredAt":"2026-11-03T12:00:00Z"}""")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.value").value("precision_exceeded"));
        postCheckIn(id, """
                {"value":1,"measuredAt":"+10000-01-01T00:00:00Z"}""")
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/key-results/" + id + "/check-ins")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void archivedKrRejectsCheckInAndIsHiddenOnlyFromDefaultList() throws Exception {
        String id = createKr("""
                {"name":"n","baseline":0,"target":10}""");
        db.sql("UPDATE key_results SET archived_at = '2026-11-03T12:00:00.000Z' WHERE id = ?").param(id).update();
        postCheckIn(id, """
                {"value":1,"measuredAt":"2026-11-03T12:00:00Z"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("key_result_archived"));
        mvc.perform(get("/api/v1/goals/" + goalId + "/key-results")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/goals/" + goalId + "/key-results?includeArchived=true"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].archivedAt").value("2026-11-03T12:00:00Z"));
        mvc.perform(get("/api/v1/key-results/" + id)).andExpect(status().isOk());
    }
}
