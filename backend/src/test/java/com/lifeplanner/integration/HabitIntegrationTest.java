package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /habits: daily habits and their done days. Each test uses its own database. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/habits-${random.uuid}.db")
@AutoConfigureMockMvc
class HabitIntegrationTest {

    @Autowired
    MockMvc mvc;

    String habit(String title) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/habits").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.doneDates.length()").value(0)).andReturn().getResponse().getContentAsString(), "$.id");
    }

    String week(boolean archived) throws Exception {
        return mvc.perform(get("/api/v1/habits?from=2026-11-07&to=2026-11-13" + (archived ? "&archived=true" : "")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void ticksAndUnticksDaysIdempotentlyAndListsThemPerRange() throws Exception {
        String read = habit("مطالعه");
        String sport = habit("ورزش");
        mvc.perform(put("/api/v1/habits/" + read + "/checks/2026-11-08")).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/habits/" + read + "/checks/2026-11-08")).andExpect(status().isNoContent()); // again: no-op
        mvc.perform(put("/api/v1/habits/" + read + "/checks/2026-11-07")).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/habits/" + read + "/checks/2026-11-20")).andExpect(status().isNoContent()); // outside the range
        mvc.perform(put("/api/v1/habits/" + sport + "/checks/2026-11-10")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/habits/" + sport + "/checks/2026-11-10")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/habits/" + sport + "/checks/2026-11-10")).andExpect(status().isNoContent());
        String body = week(false);
        List<String> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids.indexOf(read)).isLessThan(ids.indexOf(sport)); // creation order
        assertThat(JsonPath.<List<List<String>>>read(body, "$[?(@.id == '" + read + "')].doneDates")).containsExactly(List.of("2026-11-07", "2026-11-08"));
        assertThat(JsonPath.<List<List<String>>>read(body, "$[?(@.id == '" + sport + "')].doneDates")).containsExactly(List.of());
    }

    @Test
    void archivingHidesTheHabitKeepsItsDaysAndBlocksTicks() throws Exception {
        String h = habit("زبان");
        mvc.perform(put("/api/v1/habits/" + h + "/checks/2026-11-09")).andExpect(status().isNoContent());
        mvc.perform(patch("/api/v1/habits/" + h).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" زبان ۱۵ دقیقه \",\"archived\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("زبان ۱۵ دقیقه")).andExpect(jsonPath("$.archivedAt").isNotEmpty());
        assertThat(JsonPath.<List<String>>read(week(false), "$[*].id")).doesNotContain(h);
        assertThat(JsonPath.<List<List<String>>>read(week(true), "$[?(@.id == '" + h + "')].doneDates")).containsExactly(List.of("2026-11-09"));
        mvc.perform(put("/api/v1/habits/" + h + "/checks/2026-11-10")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("habit_archived"));
        mvc.perform(patch("/api/v1/habits/" + h).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").doesNotExist());
        assertThat(JsonPath.<List<String>>read(week(false), "$[*].id")).contains(h);
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/api/v1/habits").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}")).andExpect(status().isBadRequest());
        String h = habit("x");
        mvc.perform(patch("/api/v1/habits/" + h).contentType(MediaType.APPLICATION_JSON).content("{\"title\":null}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").value("required"));
        mvc.perform(patch("/api/v1/habits/" + h).contentType(MediaType.APPLICATION_JSON).content("{\"doneDates\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/habits/" + h + "/checks/2026-02-30")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/habits/00000000-0000-0000-0000-000000000000/checks/2026-11-10")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/habits?from=2026-11-10&to=2026-11-09")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/habits?from=2026-01-01&to=2026-12-31")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").value("range_too_long"));
    }

    @Test
    void anotherUsersHabitIsInvisible() throws Exception {
        String h = habit("mine");
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other-habit@test.local\",\"password\":\"password-123\"}"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(put("/api/v1/habits/" + h + "/checks/2026-11-10").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/habits/" + h).header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"archived\":true}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/habits?from=2026-11-07&to=2026-11-13").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
