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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G-02: optional hierarchy, skipped levels, cycle protection, granularity, date-mismatch warnings. */
@SpringBootTest
@AutoConfigureMockMvc
class GoalHierarchyIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String create(String type, String start, String end, String parent) throws Exception {
        String parentPart = parent == null ? "" : ",\"parentGoalId\":\"" + parent + "\"";
        return JsonPath.read(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"%s","periodType":"%s","periodStart":"%s","periodEnd":"%s"%s}""".formatted(type, type, start, end, parentPart)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    // Jalali 1405: year 2026-03-21..2027-03-20, Q3 (Mehr–Azar) 2026-09-23..2026-12-21, Aban 2026-10-23..2026-11-21
    String annual() throws Exception { return create("annual", "2026-03-21", "2027-03-20", null); }
    String quarterly(String parent) throws Exception { return create("quarterly", "2026-09-23", "2026-12-21", parent); }
    String monthly(String parent) throws Exception { return create("monthly", "2026-10-23", "2026-11-21", parent); }
    String weekly(String parent) throws Exception { return create("weekly", "2026-10-31", "2026-11-06", parent); }

    ResultActions setParent(String goal, String parent) throws Exception {
        String value = parent == null ? "null" : "\"" + parent + "\"";
        return mvc.perform(patch("/api/v1/goals/" + goal).contentType(MediaType.APPLICATION_JSON).content("{\"parentGoalId\":" + value + "}"));
    }

    @Test
    void standaloneMonthlyGoalIsValid() throws Exception {
        String m = monthly(null);
        mvc.perform(get("/api/v1/goals/" + m)).andExpect(jsonPath("$.parentGoalId").doesNotExist())
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }

    @Test
    void annualToMonthlySkippingQuarterIsAllowed() throws Exception {
        String a = annual();
        String m = monthly(null);
        setParent(m, a).andExpect(status().isOk()).andExpect(jsonPath("$.parentGoalId").value(a))
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }

    @Test
    void fullFourLevelChainViaPatch() throws Exception {
        String a = annual();
        String q = quarterly(null);
        String m = monthly(null);
        String w = weekly(null);
        setParent(q, a).andExpect(status().isOk());
        setParent(m, q).andExpect(status().isOk());
        setParent(w, m).andExpect(status().isOk());
        mvc.perform(get("/api/v1/goals/" + w)).andExpect(jsonPath("$.parentGoalId").value(m));
        assertThat(db.sql("SELECT event_type FROM goal_history WHERE goal_id = ?").param(w).query(String.class).list())
                .containsExactly("parent_changed");
    }

    @Test
    void selfParentIs409Cycle() throws Exception {
        String m = monthly(null);
        setParent(m, m).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_cycle"));
    }

    @Test
    void deepCycleIs409EvenThroughSeveralLevels() throws Exception {
        String a = annual();
        String q = quarterly(a);
        String m = monthly(q);
        String w = weekly(m);
        // annual under its own great-grandchild would close a loop a → q → m → w → a
        setParent(a, w).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_cycle"))
                .andExpect(jsonPath("$.details.parentGoalId").value(w));
        setParent(q, m).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_cycle"));
        mvc.perform(get("/api/v1/goals/" + a)).andExpect(jsonPath("$.parentGoalId").doesNotExist());
    }

    @Test
    void reverseOrEqualGranularityIs409() throws Exception {
        String m = monthly(null);
        String a = annual();
        setParent(a, m).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("incompatible_period_granularity"))
                .andExpect(jsonPath("$.details.parentPeriodType").value("monthly"))
                .andExpect(jsonPath("$.details.periodType").value("annual"));
        String m2 = monthly(null);
        setParent(m2, m).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("incompatible_period_granularity"));
    }

    @Test
    void parentCanBeRemovedAndChanged() throws Exception {
        String a1 = annual();
        String a2 = annual();
        String m = monthly(a1);
        setParent(m, a2).andExpect(status().isOk()).andExpect(jsonPath("$.parentGoalId").value(a2));
        setParent(m, null).andExpect(status().isOk()).andExpect(jsonPath("$.parentGoalId").doesNotExist());
        assertThat(db.sql("SELECT after_json FROM goal_history WHERE goal_id = ? AND event_type = 'parent_changed' ORDER BY created_at, rowid")
                .param(m).query(String.class).list()).containsExactly("{\"parentGoalId\":\"" + a2 + "\"}", "{\"parentGoalId\":null}");
    }

    @Test
    void unknownOrArchivedParentIsRejected() throws Exception {
        String m = monthly(null);
        setParent(m, "00000000-0000-0000-0000-000000000000").andExpect(status().isNotFound());
        String a = annual();
        mvc.perform(post("/api/v1/goals/" + a + "/archive")).andExpect(status().isOk());
        setParent(m, a).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("parent_goal_archived"));
    }

    @Test
    void childOfArchivedParentKeepsLinkAndCanBeDetached() throws Exception {
        String a = annual();
        String m = monthly(a);
        mvc.perform(post("/api/v1/goals/" + a + "/archive")).andExpect(status().isOk());
        // editing an unrelated field keeps the (now archived) parent link: no silent change
        mvc.perform(patch("/api/v1/goals/" + m).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"doing\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentGoalId").value(a));
        setParent(m, null).andExpect(status().isOk()).andExpect(jsonPath("$.parentGoalId").doesNotExist());
    }

    @Test
    void dateMismatchWithParentIsANonBlockingWarning() throws Exception {
        String m = monthly(null);
        // cross-month boundary week: Saturday 30 Aban .. Friday 6 Azar
        String w = create("weekly", "2026-11-21", "2026-11-27", null);
        setParent(w, m).andExpect(status().isOk())
                .andExpect(jsonPath("$.parentGoalId").value(m))
                .andExpect(jsonPath("$.warnings[0].code").value("period_outside_parent"))
                .andExpect(jsonPath("$.warnings[0].details.parentGoalId").value(m));
        mvc.perform(get("/api/v1/goals/" + m))
                .andExpect(jsonPath("$.warnings[0].code").value("child_period_outside"))
                .andExpect(jsonPath("$.warnings[0].details.childGoalId").value(w));
        // creating with a mismatching parent also succeeds with a warning
        mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"x","periodType":"weekly","periodStart":"2026-12-05","periodEnd":"2026-12-11","parentGoalId":"%s"}""".formatted(m)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0].code").value("period_outside_parent"));
    }

    @Test
    void shorteningParentWarnsAndNeverMovesChildDates() throws Exception {
        String q = quarterly(null);
        String m = monthly(q);
        mvc.perform(patch("/api/v1/goals/" + q).contentType(MediaType.APPLICATION_JSON).content("{\"periodEnd\":\"2026-11-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodEnd").value("2026-11-01"))
                .andExpect(jsonPath("$.warnings[0].code").value("child_period_outside"))
                .andExpect(jsonPath("$.warnings[0].details.childGoalId").value(m));
        mvc.perform(get("/api/v1/goals/" + m))
                .andExpect(jsonPath("$.periodStart").value("2026-10-23"))
                .andExpect(jsonPath("$.periodEnd").value("2026-11-21"))
                .andExpect(jsonPath("$.warnings[0].code").value("period_outside_parent"));
    }

    @Test
    void deadlineOutsidePeriodWarns() throws Exception {
        String m = monthly(null);
        mvc.perform(patch("/api/v1/goals/" + m).contentType(MediaType.APPLICATION_JSON).content("{\"deadline\":\"2026-12-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadline").value("2026-12-30"))
                .andExpect(jsonPath("$.warnings[0].code").value("deadline_outside_period"));
    }
}
