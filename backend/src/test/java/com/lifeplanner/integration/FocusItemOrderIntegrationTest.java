package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Order of focus items inside a goal: per-goal append, full reorder, no moving between goals, relink appends. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/focus-order-${random.uuid}.db")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FocusItemOrderIntegrationTest {

    @Autowired
    MockMvc mvc;

    String goal(String t) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content(
                "{\"title\":\"" + t + "\",\"periodType\":\"monthly\",\"periodStart\":\"2026-10-23\",\"periodEnd\":\"2026-11-21\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    String item(String t, String goalId) throws Exception {
        String g = goalId == null ? "" : ",\"goalId\":\"" + goalId + "\"";
        return JsonPath.read(mvc.perform(post("/api/v1/focus-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + t + "\",\"kind\":\"other\"" + g + "}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Titles of the goal's active items in position order (what the board shows). */
    List<String> board(String goalId) throws Exception {
        List<Map<String, Object>> items = JsonPath.read(mvc.perform(get("/api/v1/focus-items?goalId=" + goalId))
                .andReturn().getResponse().getContentAsString(), "$");
        return items.stream().sorted(Comparator.comparing(m -> (Integer) m.get("position")))
                .map(m -> (String) m.get("title")).toList();
    }

    String order(String... ids) {
        return "{\"focusItemIds\":[" + String.join(",", java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    @Test
    void itemsAreAppendedPerGoalAndReorderedWithinTheirGoal() throws Exception {
        String g1 = goal("g1"), g2 = goal("g2");
        String a = item("a", g1), b = item("b", g1), c = item("c", g1);
        item("x", g2);
        mvc.perform(get("/api/v1/focus-items/" + item("free", null))).andExpect(jsonPath("$.position").doesNotExist());
        mvc.perform(get("/api/v1/focus-items/" + item("y", g2))).andExpect(jsonPath("$.position").value(2)); // own counter per goal
        assertThat(board(g1)).containsExactly("a", "b", "c");

        mvc.perform(put("/api/v1/goals/" + g1 + "/focus-items/order").contentType(MediaType.APPLICATION_JSON).content(order(c, a, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(c)).andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[2].id").value(b)).andExpect(jsonPath("$[2].position").value(3));
        assertThat(board(g1)).containsExactly("c", "a", "b");
        assertThat(board(g2)).containsExactly("x", "y"); // other goal untouched
        item("d", g1);
        assertThat(board(g1)).containsExactly("c", "a", "b", "d");
    }

    @Test
    void itemsCannotBeMovedBetweenGoalsThroughTheOrderEndpoint() throws Exception {
        String g1 = goal("g1"), g2 = goal("g2");
        String a = item("a", g1), b = item("b", g1);
        String foreign = item("x", g2);
        String archived = item("old", g1);
        mvc.perform(patch("/api/v1/focus-items/" + archived).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
                .andExpect(status().isOk());
        for (String body : List.of(order(a, b, foreign), order(a), order(a, a, b), order(a, b, archived), order(), "{}")) {
            mvc.perform(put("/api/v1/goals/" + g1 + "/focus-items/order").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation_error"))
                    .andExpect(jsonPath("$.details.focusItemIds").exists());
        }
        // putting g1's items into g2's order is just as impossible
        mvc.perform(put("/api/v1/goals/" + g2 + "/focus-items/order").contentType(MediaType.APPLICATION_JSON).content(order(foreign, a)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/goals/00000000-0000-0000-0000-000000000000/focus-items/order").contentType(MediaType.APPLICATION_JSON)
                .content(order(a, b))).andExpect(status().isNotFound());
        assertThat(board(g1)).containsExactly("a", "b");
        assertThat(board(g2)).containsExactly("x");
    }

    @Test
    void relinkingAnItemAppendsItToTheNewGoalAndUnlinkingClearsThePosition() throws Exception {
        String g1 = goal("g1"), g2 = goal("g2");
        String a = item("a", g1);
        item("b", g1);
        item("x", g2);
        mvc.perform(patch("/api/v1/focus-items/" + a).contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":\"" + g2 + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.position").value(2));
        assertThat(board(g2)).containsExactly("x", "a");
        mvc.perform(patch("/api/v1/focus-items/" + a).contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.position").doesNotExist());
        // editing anything else keeps the position
        String b2 = item("c", g1);
        mvc.perform(patch("/api/v1/focus-items/" + b2).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"done\"}"))
                .andExpect(jsonPath("$.position").value(3));
    }
}
