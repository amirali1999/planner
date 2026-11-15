package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M-01: milestone lifecycle, filters, archived goals, and independence from Outcome/Execution. */
@SpringBootTest
@AutoConfigureMockMvc
class MilestoneApiIntegrationTest {

    static final String MISSING = "00000000-0000-0000-0000-000000000000";

    @Autowired
    MockMvc mvc;

    String goalId;

    @BeforeEach
    void goal() throws Exception {
        goalId = JsonPath.read(body(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"g","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}"""))), "$.id");
    }

    static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/goals/" + goalId + "/milestones").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String milestone(String title, String due) throws Exception {
        String duePart = due == null ? "" : ",\"dueDate\":\"" + due + "\"";
        return JsonPath.read(body(create("{\"title\":\"" + title + "\"" + duePart + "}").andExpect(status().isCreated())), "$.id");
    }

    ResultActions patchMs(String id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/milestones/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    List<String> titles(String query) throws Exception {
        return JsonPath.read(body(mvc.perform(get("/api/v1/goals/" + goalId + "/milestones" + query)).andExpect(status().isOk())), "$[*].title");
    }

    @Test
    void createAndEmptyList() throws Exception {
        mvc.perform(get("/api/v1/goals/" + goalId + "/milestones")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        create("""
                {"title":"  نسخه اول  ","description":"d","dueDate":"2026-11-20"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("نسخه اول"))
                .andExpect(jsonPath("$.goalId").value(goalId))
                .andExpect(jsonPath("$.dueDate").value("2026-11-20"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.archivedAt").doesNotExist());
        create("{\"title\":\"بدون موعد\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.dueDate").doesNotExist());
    }

    @Test
    void validation() throws Exception {
        create("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        create("{\"title\":\" \"}").andExpect(status().isBadRequest());
        create("{\"title\":\"x\",\"dueDate\":\"2026-02-30\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.dueDate").value("invalid_value"));
        create("{\"title\":\"x\",\"completed\":true}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.completed").value("unknown_field"));
        String id = milestone("x", null);
        patchMs(id, "{\"completedAt\":\"2026-11-03T12:00:00Z\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.completedAt").value("not_editable"));
        patchMs(id, "{\"completed\":\"yes\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.completed").value("invalid_value"));
        patchMs(id, "{\"completed\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.completed").value("required"));
        patchMs(id, "{\"title\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        mvc.perform(get("/api/v1/goals/" + goalId + "/milestones?status=late")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/goals/" + goalId + "/milestones?status=overdue")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.date").value("required"));
    }

    @Test
    void completeUncompleteEditAndClear() throws Exception {
        String id = milestone("دموی اول", "2026-11-20");
        String completedAt = JsonPath.read(body(patchMs(id, "{\"completed\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))), "$.completedAt");
        assertThat(completedAt).isNotNull();
        patchMs(id, "{\"completed\":true}").andExpect(jsonPath("$.completedAt").value(completedAt)); // idempotent
        patchMs(id, "{\"completed\":false}").andExpect(jsonPath("$.completed").value(false)).andExpect(jsonPath("$.completedAt").doesNotExist());
        patchMs(id, "{\"title\":\"دموی نهایی\",\"dueDate\":\"2026-12-01\",\"description\":\"شرح\"}")
                .andExpect(jsonPath("$.title").value("دموی نهایی")).andExpect(jsonPath("$.dueDate").value("2026-12-01"));
        patchMs(id, "{\"dueDate\":null,\"description\":null}")
                .andExpect(jsonPath("$.dueDate").doesNotExist()).andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.title").value("دموی نهایی"));
    }

    @Test
    void filtersAndStableOrder() throws Exception {
        milestone("بدون موعد", null);
        milestone("گذشته", "2026-10-01");
        milestone("امروز", "2026-11-04");
        milestone("آینده", "2026-12-01");
        String done = milestone("انجام‌شده گذشته", "2026-09-30");
        patchMs(done, "{\"completed\":true}").andExpect(status().isOk());

        assertThat(titles("")).containsExactly("انجام‌شده گذشته", "گذشته", "امروز", "آینده", "بدون موعد");
        assertThat(titles("?status=overdue&date=2026-11-04")).containsExactly("گذشته");     // completed ones are never overdue
        assertThat(titles("?status=upcoming&date=2026-11-04")).containsExactly("امروز", "آینده");
        assertThat(titles("?status=open")).containsExactly("گذشته", "امروز", "آینده", "بدون موعد");
        assertThat(titles("?status=completed")).containsExactly("انجام‌شده گذشته");
    }

    @Test
    void archivedMilestonesAreKeptAndReadOnlyUntilRestored() throws Exception {
        String id = milestone("قدیمی", "2026-10-01");
        patchMs(id, "{\"completed\":true}");
        patchMs(id, "{\"archived\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isString())
                .andExpect(jsonPath("$.completed").value(true));
        assertThat(titles("")).doesNotContain("قدیمی");
        assertThat(titles("?archived=true")).containsExactly("قدیمی");
        assertThat(titles("?archived=all")).contains("قدیمی");
        patchMs(id, "{\"title\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("milestone_archived"));
        patchMs(id, "{\"archived\":false,\"title\":\"بازگشته\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").doesNotExist()).andExpect(jsonPath("$.title").value("بازگشته"));
    }

    @Test
    void archivedGoalIsReadOnlyButListable() throws Exception {
        String id = milestone("m", null);
        mvc.perform(post("/api/v1/goals/" + goalId + "/archive")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/goals/" + goalId + "/milestones")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        create("{\"title\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_archived"));
        patchMs(id, "{\"completed\":true}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_archived"));
    }

    @Test
    void unknownIdsAre404() throws Exception {
        mvc.perform(get("/api/v1/goals/" + MISSING + "/milestones")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/goals/" + MISSING + "/milestones").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
        patchMs(MISSING, "{\"completed\":true}").andExpect(status().isNotFound()).andExpect(jsonPath("$.details.milestoneId").value(MISSING));
    }

    @Test
    void completingMilestonesNeverChangesOutcomeExecutionOrKrScore() throws Exception {
        String kr = JsonPath.read(body(mvc.perform(post("/api/v1/goals/" + goalId + "/key-results").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"kr\",\"baseline\":20,\"target\":60}"))), "$.id");
        mvc.perform(post("/api/v1/key-results/" + kr + "/check-ins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":40,\"measuredAt\":\"2026-11-03T12:00:00Z\"}")).andExpect(status().isCreated());
        for (String s : new String[]{"done", "done", "done", "doing"}) {
            mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"t\",\"status\":\"" + s + "\",\"goalId\":\"" + goalId + "\"}")).andExpect(status().isCreated());
        }
        String progressBefore = body(mvc.perform(get("/api/v1/goals/" + goalId + "/progress")));
        String krBefore = body(mvc.perform(get("/api/v1/key-results/" + kr)));
        assertThat((Double) JsonPath.read(progressBefore, "$.outcomeProgress")).isEqualTo(50.0);
        assertThat((Double) JsonPath.read(progressBefore, "$.executionProgress")).isEqualTo(75.0);

        String a = milestone("a", "2026-11-10");
        String b = milestone("b", null);
        patchMs(a, "{\"completed\":true}").andExpect(status().isOk());
        patchMs(b, "{\"completed\":true}").andExpect(status().isOk());

        assertThat(body(mvc.perform(get("/api/v1/goals/" + goalId + "/progress")))).isEqualTo(progressBefore);
        assertThat(body(mvc.perform(get("/api/v1/key-results/" + kr)))).isEqualTo(krBefore);
        String tree = body(mvc.perform(get("/api/v1/goals/tree")));
        assertThat(tree).doesNotContain("milestone");
    }
}
