package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Manual goal priority (goals board drag and drop): append, full reorder, archive/restore, sort, validation. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/goal-priority-${random.uuid}.db")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GoalPriorityIntegrationTest {

    @Autowired
    MockMvc mvc;

    String goal(String title) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"title\":\"" + title + "\",\"periodType\":\"monthly\",\"periodStart\":\"2026-10-23\",\"periodEnd\":\"2026-11-21\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.priority").isNumber())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    List<String> byPriority() throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/goals?sort=priority")).andReturn().getResponse().getContentAsString(), "$[*].title");
    }

    List<Integer> priorities() throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/goals?sort=priority")).andReturn().getResponse().getContentAsString(), "$[*].priority");
    }

    String order(String... ids) {
        return "{\"goalIds\":[" + String.join(",", java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    @Test
    void newGoalsAreAppendedAndAFullReorderIsStored() throws Exception {
        String a = goal("a"), b = goal("b"), c = goal("c");
        assertThat(byPriority()).containsExactly("a", "b", "c");
        assertThat(priorities()).containsExactly(1, 2, 3);

        mvc.perform(put("/api/v1/goals/priority").contentType(MediaType.APPLICATION_JSON).content(order(c, a, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(c)).andExpect(jsonPath("$[0].priority").value(1))
                .andExpect(jsonPath("$[2].id").value(b)).andExpect(jsonPath("$[2].priority").value(3));
        assertThat(byPriority()).containsExactly("c", "a", "b");
        mvc.perform(get("/api/v1/goals/" + a)).andExpect(jsonPath("$.priority").value(2));
        goal("d");
        assertThat(byPriority()).containsExactly("c", "a", "b", "d");
    }

    @Test
    void editingAGoalKeepsItsPriority() throws Exception {
        goal("a");
        String b = goal("b");
        mvc.perform(patch("/api/v1/goals/" + b).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"b2\",\"status\":\"doing\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priority").value(2));
    }

    @Test
    void archivingClosesTheGapAndRestoringAppends() throws Exception {
        String a = goal("a"), b = goal("b");
        goal("c");
        mvc.perform(post("/api/v1/goals/" + a + "/archive")).andExpect(status().isOk()).andExpect(jsonPath("$.priority").doesNotExist());
        assertThat(byPriority()).containsExactly("b", "c");
        assertThat(priorities()).containsExactly(1, 2);
        mvc.perform(post("/api/v1/goals/" + a + "/unarchive")).andExpect(status().isOk()).andExpect(jsonPath("$.priority").value(3));
        assertThat(byPriority()).containsExactly("b", "c", "a");
        // archived goals sort last and have no priority
        mvc.perform(post("/api/v1/goals/" + b + "/archive")).andExpect(status().isOk());
        assertThat(JsonPath.<List<String>>read(mvc.perform(get("/api/v1/goals?sort=priority&archived=all")).andReturn().getResponse()
                .getContentAsString(), "$[*].title")).containsExactly("c", "a", "b");
    }

    @Test
    void incompleteDuplicateOrForeignListsAreRejectedAndChangeNothing() throws Exception {
        String a = goal("a"), b = goal("b");
        String archived = goal("x");
        mvc.perform(post("/api/v1/goals/" + archived + "/archive")).andExpect(status().isOk());
        for (String[] bad : List.of(new String[] {a}, new String[] {a, a}, new String[] {b, a, archived},
                new String[] {b, a, "00000000-0000-0000-0000-000000000000"}, new String[] {})) {
            mvc.perform(put("/api/v1/goals/priority").contentType(MediaType.APPLICATION_JSON).content(order(bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation_error"))
                    .andExpect(jsonPath("$.details.goalIds").exists());
        }
        mvc.perform(put("/api/v1/goals/priority").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        assertThat(byPriority()).containsExactly("a", "b");
    }

    @Test
    void eachUserHasTheirOwnOrder() throws Exception {
        String mine = goal("mine");
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other@test.local\",\"password\":\"password-123\"}")).andReturn().getResponse().getContentAsString(), "$.accessToken");
        String theirs = JsonPath.read(mvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"theirs\",\"periodType\":\"monthly\",\"periodStart\":\"2026-10-23\",\"periodEnd\":\"2026-11-21\"}"))
                .andExpect(jsonPath("$.priority").value(1)).andReturn().getResponse().getContentAsString(), "$.id");
        // another user's goal cannot be part of my order
        mvc.perform(put("/api/v1/goals/priority").contentType(MediaType.APPLICATION_JSON).content(order(mine, theirs)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/goals/priority").contentType(MediaType.APPLICATION_JSON).content(order(mine))).andExpect(status().isOk());
    }
}
