package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** K-01: KR edit (with recalibration audit), archive, history, weighted outcome breakdown. */
@SpringBootTest
@AutoConfigureMockMvc
class KeyResultManagementIntegrationTest {

    static final String MISSING = "00000000-0000-0000-0000-000000000000";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String goalId;

    @BeforeEach
    void goal() throws Exception {
        goalId = JsonPath.read(body(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"g","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}"""))), "$.id");
    }

    static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    String kr(String baseline, String target, String weight) throws Exception {
        return JsonPath.read(body(mvc.perform(post("/api/v1/goals/" + goalId + "/key-results").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"kr\",\"baseline\":%s,\"target\":%s,\"weight\":%s}".formatted(baseline, target, weight)))
                .andExpect(status().isCreated())), "$.id");
    }

    void checkIn(String kr, String value, String measuredAt) throws Exception {
        mvc.perform(post("/api/v1/key-results/" + kr + "/check-ins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":%s,\"measuredAt\":\"%s\"}".formatted(value, measuredAt))).andExpect(status().isCreated());
    }

    ResultActions patchKr(String kr, String json) throws Exception {
        return mvc.perform(patch("/api/v1/key-results/" + kr).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    int historyRows(String kr) {
        return db.sql("SELECT count(*) FROM goal_history WHERE key_result_id = ?").param(kr).query(Integer.class).single();
    }

    @Test
    void acceptanceWeightedOutcomeAndArchiveKeepsHistoryReadable() throws Exception {
        String full = kr("0", "10", "1");
        checkIn(full, "10", "2026-11-03T12:00:00Z");          // 100%
        String zero = kr("0", "10", "3");
        checkIn(zero, "0", "2026-11-03T12:00:00Z");           // 0%
        mvc.perform(get("/api/v1/goals/" + goalId + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(25.0))
                .andExpect(jsonPath("$.keyResults", hasSize(2)))
                .andExpect(jsonPath("$.keyResults[0].keyResultId").value(full))
                .andExpect(jsonPath("$.keyResults[0].score").value(100.0))
                .andExpect(jsonPath("$.keyResults[0].weightPercent").value(25.0))
                .andExpect(jsonPath("$.keyResults[0].contribution").value(25.0))
                .andExpect(jsonPath("$.keyResults[1].weightPercent").value(75.0))
                .andExpect(jsonPath("$.keyResults[1].contribution").value(0.0))
                .andExpect(jsonPath("$.combinedProgress").doesNotExist());

        String archivedAt = JsonPath.read(body(mvc.perform(post("/api/v1/key-results/" + zero + "/archive")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"دیگر معیار مناسبی نیست\"}"))
                .andExpect(status().isOk())), "$.archivedAt");
        mvc.perform(post("/api/v1/key-results/" + zero + "/archive")).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").value(archivedAt)); // idempotent, no body needed

        mvc.perform(get("/api/v1/goals/" + goalId + "/progress"))
                .andExpect(jsonPath("$.outcomeProgress").value(100.0))
                .andExpect(jsonPath("$.keyResultsCount").value(1))
                .andExpect(jsonPath("$.keyResults", hasSize(1)));
        // history stays readable
        mvc.perform(get("/api/v1/key-results/" + zero)).andExpect(status().isOk()).andExpect(jsonPath("$.current").value(0));
        mvc.perform(get("/api/v1/key-results/" + zero + "/check-ins")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/key-results/" + zero + "/history"))
                .andExpect(jsonPath("$[*].eventType").value(org.hamcrest.Matchers.contains("key_result_created", "key_result_archived")))
                .andExpect(jsonPath("$[1].note").value("دیگر معیار مناسبی نیست"));
        mvc.perform(get("/api/v1/goals/" + goalId + "/key-results")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/goals/" + goalId + "/key-results?includeArchived=true")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void renameNeedsNoNoteAndNoOpWritesNothing() throws Exception {
        String id = kr("0", "10", "1");
        int before = historyRows(id);
        patchKr(id, "{\"name\":\"kr\"}").andExpect(status().isOk());
        assertThat(historyRows(id)).isEqualTo(before);
        patchKr(id, "{\"name\":\"  Recall@5 \",\"unit\":\"%\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Recall@5")).andExpect(jsonPath("$.unit").value("%"));
        mvc.perform(get("/api/v1/key-results/" + id + "/history"))
                .andExpect(jsonPath("$[1].eventType").value("key_result_changed"))
                .andExpect(jsonPath("$[1].before.name").value("kr"))
                .andExpect(jsonPath("$[1].after.name").value("Recall@5"))
                .andExpect(jsonPath("$[1].note").doesNotExist());
        patchKr(id, "{\"unit\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.unit").doesNotExist());
    }

    @Test
    void recalibrationRequiresNoteKeepsCheckInsAndReDerivesScore() throws Exception {
        String id = kr("20", "60", "1");
        checkIn(id, "40", "2026-11-03T12:00:00Z");
        mvc.perform(get("/api/v1/key-results/" + id)).andExpect(jsonPath("$.score").value(50.0));

        patchKr(id, "{\"target\":100}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.note").value("required_for_recalibration"));
        patchKr(id, "{\"weight\":2,\"note\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.note").value("required_for_recalibration"));
        patchKr(id, "{\"target\":60.00,\"note\":\"x\"}").andExpect(status().isOk()); // numerically unchanged → no-op
        assertThat(historyRows(id)).isEqualTo(1);

        patchKr(id, "{\"target\":100,\"weight\":2,\"note\":\"هدف واقع‌بینانه‌تر\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value(100))
                .andExpect(jsonPath("$.weight").value(2))
                .andExpect(jsonPath("$.current").value(40))
                .andExpect(jsonPath("$.score").value(25.0)); // (40-20)/(100-20)
        mvc.perform(get("/api/v1/key-results/" + id + "/check-ins"))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].value").value(40));
        mvc.perform(get("/api/v1/key-results/" + id + "/history"))
                .andExpect(jsonPath("$[1].eventType").value("key_result_changed"))
                .andExpect(jsonPath("$[1].before.target").value("60"))
                .andExpect(jsonPath("$[1].after.target").value("100"))
                .andExpect(jsonPath("$[1].before.weight").value("1"))
                .andExpect(jsonPath("$[1].after.weight").value("2"))
                .andExpect(jsonPath("$[1].note").value("هدف واقع‌بینانه‌تر"));
    }

    @Test
    void invalidStructureIs400AndChangesNothing() throws Exception {
        String id = kr("20", "60", "1");
        for (String w : new String[]{"0", "-1", "0.0"}) {
            patchKr(id, "{\"weight\":%s,\"note\":\"n\"}".formatted(w)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.weight").value("must_be_positive"));
        }
        patchKr(id, "{\"weight\":null,\"note\":\"n\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.weight").value("required"));
        patchKr(id, "{\"target\":20,\"note\":\"n\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.target").value("equals_baseline"));
        patchKr(id, "{\"baseline\":60,\"note\":\"n\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.baseline").value("equals_baseline"));
        patchKr(id, "{\"baseline\":\"30\",\"note\":\"n\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.baseline").value("invalid_value"));
        patchKr(id, "{\"baseline\":0.1234567,\"note\":\"n\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.baseline").value("precision_exceeded"));
        patchKr(id, "{\"name\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.name").value("required"));
        patchKr(id, "{\"score\":100}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.score").value("not_editable"));
        patchKr(id, "{\"goalId\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.goalId").value("not_editable"));
        patchKr(id, "{\"color\":\"red\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.color").value("unknown_field"));
        mvc.perform(get("/api/v1/key-results/" + id)).andExpect(jsonPath("$.baseline").value(20)).andExpect(jsonPath("$.target").value(60))
                .andExpect(jsonPath("$.weight").value(1));
        assertThat(historyRows(id)).isEqualTo(1);
    }

    @Test
    void archivedKrAndArchivedGoalAreReadOnly() throws Exception {
        String id = kr("0", "10", "1");
        mvc.perform(post("/api/v1/key-results/" + id + "/archive")).andExpect(status().isOk());
        patchKr(id, "{\"name\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("key_result_archived"));

        String other = kr("0", "10", "1");
        mvc.perform(post("/api/v1/goals/" + goalId + "/archive")).andExpect(status().isOk());
        patchKr(other, "{\"name\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_archived"));
        mvc.perform(post("/api/v1/key-results/" + other + "/archive")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("goal_archived"));
    }

    @Test
    void unknownKrIs404() throws Exception {
        patchKr(MISSING, "{\"name\":\"x\"}").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/key-results/" + MISSING + "/archive")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/key-results/" + MISSING + "/history")).andExpect(status().isNotFound());
    }

    @Test
    void latestCheckInStaysDeterministicAcrossRecalibration() throws Exception {
        String id = kr("0", "100", "1");
        checkIn(id, "70", "2026-11-03T12:00:00Z");
        checkIn(id, "30", "2026-11-01T12:00:00Z"); // back-dated: not current
        patchKr(id, "{\"target\":200,\"note\":\"دو برابر\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.current").value(70))
                .andExpect(jsonPath("$.currentMeasuredAt").value("2026-11-03T12:00:00Z"))
                .andExpect(jsonPath("$.score").value(35.0));
    }
}
