package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PUT /plans/daily/{date}/items/order: full reorder of one day's plan (today Kanban board). */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/plan-order-${random.uuid}.db")
@AutoConfigureMockMvc
class DailyPlanOrderIntegrationTest {

    static final AtomicInteger DAY = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    static String freshDate() {
        return java.time.LocalDate.of(2041, 1, 1).plusDays(DAY.getAndIncrement()).toString();
    }

    String task(String title) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    String plan(String date, String taskId) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/plans/daily/" + date + "/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"" + taskId + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    static String body(String... ids) {
        return "{\"itemIds\":[" + String.join(",", java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    ResultActions order(String date, String... ids) throws Exception {
        return mvc.perform(put("/api/v1/plans/daily/" + date + "/items/order").contentType(MediaType.APPLICATION_JSON).content(body(ids)));
    }

    List<String> titles(String date) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/plans/daily?date=" + date)).andReturn().getResponse().getContentAsString(), "$.items[*].task.title");
    }

    @Test
    void reordersAllItemsAndKeepsTheOrderForLaterReads() throws Exception {
        String d = freshDate();
        String a = plan(d, task("a")), b = plan(d, task("b")), c = plan(d, task("c"));
        order(d, c, a, b).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(c)).andExpect(jsonPath("$.items[0].position").value(0))
                .andExpect(jsonPath("$.items[2].id").value(b)).andExpect(jsonPath("$.items[2].position").value(2));
        assertThat(titles(d)).containsExactly("c", "a", "b");
        plan(d, task("d")); // new items are appended
        assertThat(titles(d)).containsExactly("c", "a", "b", "d");
    }

    @Test
    void itemsOfArchivedTasksMustBeListedToo() throws Exception {
        String d = freshDate();
        String archivedTask = task("old");
        String a = plan(d, archivedTask), b = plan(d, task("b"));
        mvc.perform(patch("/api/v1/tasks/" + archivedTask).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
                .andExpect(status().isOk());
        order(d, b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.itemIds").value("must_list_all_items_of_plan"));
        order(d, b, a).andExpect(status().isOk());
        assertThat(titles(d)).containsExactly("b", "old");
    }

    @Test
    void rejectsPartialDuplicateForeignOrEmptyLists() throws Exception {
        String d = freshDate(), other = freshDate();
        String a = plan(d, task("a")), b = plan(d, task("b"));
        String foreign = plan(other, task("x"));
        order(d, a).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.itemIds").value("must_list_all_items_of_plan"));
        order(d, a, a, b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.itemIds").value("duplicate"));
        order(d, a, foreign).andExpect(status().isBadRequest());
        order(d).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.itemIds").value("required"));
        mvc.perform(put("/api/v1/plans/daily/" + d + "/items/order").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(titles(d)).containsExactly("a", "b"); // nothing changed
        assertThat(titles(other)).containsExactly("x");
    }

    @Test
    void aDateWithoutPlanIs404AndNoPlanIsCreated() throws Exception {
        String d = freshDate();
        order(d, "00000000-0000-0000-0000-000000000000").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/plans/daily?date=" + d)).andExpect(jsonPath("$.exists").value(false));
        order("2041-02-30", "x").andExpect(status().isBadRequest());
    }

    @Test
    void anotherUsersPlanIsInvisible() throws Exception {
        String d = freshDate();
        String a = plan(d, task("a"));
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other-plan@test.local\",\"password\":\"password-123\"}")).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(put("/api/v1/plans/daily/" + d + "/items/order").header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON).content(body(a))).andExpect(status().isNotFound());
        assertThat(titles(d)).containsExactly("a");
    }
}
