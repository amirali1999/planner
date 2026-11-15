package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GoalApiIntegrationTest {

    static final String VALID = """
            {"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21","deadline":"2026-12-21"}""";

    @Autowired
    MockMvc mvc;

    ResultActions postGoal(String json) throws Exception {
        return mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String createGoal(String json) throws Exception {
        String body = postGoal(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void createReturns201WithDefaultsAndLocation() throws Exception {
        postGoal(VALID)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/goals/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.title").value("یادگیری RAG"))
                .andExpect(jsonPath("$.status").value("not_started"))
                .andExpect(jsonPath("$.periodType").value("quarterly"))
                .andExpect(jsonPath("$.periodStart").value("2026-09-23"))
                .andExpect(jsonPath("$.periodEnd").value("2026-12-21"))
                .andExpect(jsonPath("$.deadline").value("2026-12-21"))
                .andExpect(jsonPath("$.parentGoalId").doesNotExist())
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.combinedProgress").doesNotExist());
    }

    @Test
    void createdGoalIsReadableByIdAndInList() throws Exception {
        String id = createGoal("""
                {"title":"  ماهانه  ","description":"d","periodType":"monthly","periodStart":"2026-10-23","periodEnd":"2026-11-21","status":"doing"}""");
        mvc.perform(get("/api/v1/goals/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("ماهانه"))
                .andExpect(jsonPath("$.status").value("doing"))
                .andExpect(jsonPath("$.deadline").doesNotExist());
        mvc.perform(get("/api/v1/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id)))
                .andExpect(jsonPath("$[0].warnings").doesNotExist()); // lists omit warnings
    }

    @Test
    void unknownGoalIs404() throws Exception {
        mvc.perform(get("/api/v1/goals/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message").value("هدف پیدا نشد."));
    }

    @Test
    void missingOrBlankTitleIs400() throws Exception {
        postGoal("""
                {"periodType":"weekly","periodStart":"2026-10-31","periodEnd":"2026-11-06"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.title").value("required"));
        postGoal("""
                {"title":"   ","periodType":"weekly","periodStart":"2026-10-31","periodEnd":"2026-11-06"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").value("required"));
    }

    @Test
    void unknownPeriodTypeOrStatusIs400() throws Exception {
        postGoal(VALID.replace("quarterly", "daily"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.periodType").value("invalid_value"));
        postGoal(VALID.replace("}", ",\"status\":\"completed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").value("invalid_value"));
    }

    @Test
    void malformedOrImpossibleDatesAre400() throws Exception {
        for (String bad : new String[]{"2026-02-30", "2026-13-01", "2026-9-23", "1405-07-01T00:00:00Z", "23/09/2026"}) {
            postGoal(VALID.replace("\"periodStart\":\"2026-09-23\"", "\"periodStart\":\"" + bad + "\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.periodStart").value("invalid_value"));
        }
        postGoal(VALID.replace("\"periodStart\":\"2026-09-23\"", "\"periodStart\":20260923"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void periodEndBeforeStartIs400() throws Exception {
        postGoal(VALID.replace("\"periodEnd\":\"2026-12-21\"", "\"periodEnd\":\"2026-09-22\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.periodEnd").value("before_period_start"));
    }

    @Test
    void sameDayPeriodIsAllowed() throws Exception {
        postGoal("""
                {"title":"t","periodType":"weekly","periodStart":"2026-10-31","periodEnd":"2026-10-31"}""")
                .andExpect(status().isCreated());
    }

    @Test
    void unknownFieldIs400() throws Exception {
        postGoal(VALID.replace("}", ",\"combinedProgress\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.combinedProgress").value("unknown_field"));
    }

    @Test
    void parentMustExistAndBeStrictlyCoarser() throws Exception {
        String annual = createGoal("""
                {"title":"سالانه","periodType":"annual","periodStart":"2026-03-21","periodEnd":"2027-03-20"}""");
        // skipping a level (annual -> monthly) is allowed
        mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"ماهانه","periodType":"monthly","periodStart":"2026-10-23","periodEnd":"2026-11-21","parentGoalId":"%s"}"""
                        .formatted(annual)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentGoalId").value(annual));
        // same granularity is not coarser (G-02: stable 409 conflict code)
        postGoal("""
                {"title":"x","periodType":"annual","periodStart":"2026-03-21","periodEnd":"2027-03-20","parentGoalId":"%s"}"""
                .formatted(annual))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("incompatible_period_granularity"));
        postGoal(VALID.replace("}", ",\"parentGoalId\":\"00000000-0000-0000-0000-000000000000\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.parentGoalId").value("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void wrongContentTypeOrMalformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/v1/goals").contentType(MediaType.TEXT_PLAIN).content(VALID))
                .andExpect(status().isUnsupportedMediaType());
        postGoal("{\"title\":").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }
}
