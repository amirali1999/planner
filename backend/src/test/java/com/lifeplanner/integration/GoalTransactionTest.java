package com.lifeplanner.integration;

import com.lifeplanner.repository.GoalHistoryRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** If writing the audit row fails, the goal change itself must roll back (one transaction). */
@SpringBootTest
@AutoConfigureMockMvc
class GoalTransactionTest {

    @Autowired
    MockMvc mvc;

    @MockitoSpyBean
    GoalHistoryRepository history;

    @Test
    void failedHistoryWriteRollsBackEditAndArchive() throws Exception {
        String id = JsonPath.read(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"original","periodType":"monthly","periodStart":"2026-10-23","periodEnd":"2026-11-21"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        doThrow(new IllegalStateException("disk full")).when(history).append(anyString(), anyString(), anyString(), anyString(), any());

        mvc.perform(patch("/api/v1/goals/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"changed\",\"status\":\"doing\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"));
        mvc.perform(post("/api/v1/goals/" + id + "/archive")).andExpect(status().isInternalServerError());

        mvc.perform(get("/api/v1/goals/" + id))
                .andExpect(jsonPath("$.title").value("original"))
                .andExpect(jsonPath("$.status").value("not_started"))
                .andExpect(jsonPath("$.archivedAt").doesNotExist());
    }
}
