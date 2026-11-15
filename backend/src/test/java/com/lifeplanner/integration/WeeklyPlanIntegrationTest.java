package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /plans/weekly/{weekStart}/items: tasks put on a week without a day. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/weekly-plan-${random.uuid}.db")
@AutoConfigureMockMvc
class WeeklyPlanIntegrationTest {

    static final AtomicInteger WEEK = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    /** A Saturday (2061-01-01 is a Saturday). */
    static LocalDate freshWeek() {
        return LocalDate.of(2061, 1, 1).plusWeeks(WEEK.getAndIncrement());
    }

    String task(String title) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions add(LocalDate week, String taskId) throws Exception {
        return mvc.perform(post("/api/v1/plans/weekly/" + week + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"" + taskId + "\"}"));
    }

    List<String> titles(LocalDate week) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/plans/weekly/" + week + "/items")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[*].task.title");
    }

    @Test
    void putsTasksOnAWeekOnceWithoutADay() throws Exception {
        LocalDate w = freshWeek();
        String a = task("a");
        add(w, a).andExpect(status().isCreated()).andExpect(jsonPath("$.weekStart").value(w.toString()))
                .andExpect(jsonPath("$.task.id").value(a));
        add(w, task("b")).andExpect(status().isCreated());
        add(w, a).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("duplicate_plan_task"));
        add(w.plusWeeks(1), a).andExpect(status().isCreated()); // another week is fine
        assertThat(titles(w)).containsExactly("a", "b");
        // no daily plan was created
        mvc.perform(get("/api/v1/plans/daily?date=" + w)).andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    void weekMustStartOnSaturdayAndTaskMustBeOwnAndActive() throws Exception {
        LocalDate w = freshWeek();
        mvc.perform(get("/api/v1/plans/weekly/" + w.plusDays(1) + "/items")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.weekStart").value("must_be_saturday"));
        add(w.plusDays(2), task("x")).andExpect(status().isBadRequest());
        add(w, "00000000-0000-0000-0000-000000000000").andExpect(status().isNotFound());
        String old = task("old");
        mvc.perform(patch("/api/v1/tasks/" + old).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
        add(w, old).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("task_archived"));
        mvc.perform(post("/api/v1/plans/weekly/" + w + "/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void itemsGoWithAPermanentlyDeletedTaskAndStayPrivate() throws Exception {
        LocalDate w = freshWeek();
        String t = task("gone");
        add(w, t).andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/tasks/" + t).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
        assertThat(titles(w)).containsExactly("gone"); // archived tasks are still listed (the UI filters)
        mvc.perform(delete("/api/v1/tasks/" + t)).andExpect(status().isOk());
        assertThat(titles(w)).isEmpty();

        add(w, task("mine")).andExpect(status().isCreated());
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other-weekly@test.local\",\"password\":\"password-123\"}")).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/plans/weekly/" + w + "/items").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void removesEveryPlanningEntryInTheWeekWithoutChangingTheTaskOrOtherWeeks() throws Exception {
        LocalDate w = freshWeek();
        String t = task("remove from this week");
        LocalDate unrelatedDay = w.plusDays(1000);
        mvc.perform(patch("/api/v1/tasks/" + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dueDate\":\"" + w.plusDays(2) + "\"}"))
                .andExpect(status().isOk());
        add(w, t).andExpect(status().isCreated());
        for (LocalDate day : List.of(w.plusDays(1), w.plusDays(4), unrelatedDay)) {
            mvc.perform(post("/api/v1/plans/daily/" + day + "/items").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"taskId\":\"" + t + "\"}"))
                    .andExpect(status().isCreated());
        }

        mvc.perform(delete("/api/v1/plans/weekly/" + w + "/tasks/" + t)).andExpect(status().isNoContent());

        assertThat(titles(w)).isEmpty();
        mvc.perform(get("/api/v1/plans/daily?date=" + w.plusDays(1))).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/plans/daily?date=" + w.plusDays(4))).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/plans/daily?date=" + unrelatedDay)).andExpect(jsonPath("$.items[0].task.id").value(t));
        mvc.perform(get("/api/v1/tasks/" + t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(w.plusDays(2).toString()));
        mvc.perform(delete("/api/v1/plans/weekly/" + w + "/tasks/" + t)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/plans/weekly/" + w.plusDays(1) + "/tasks/" + t)).andExpect(status().isBadRequest());
    }
}
