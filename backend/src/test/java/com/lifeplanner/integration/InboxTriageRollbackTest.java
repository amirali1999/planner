package com.lifeplanner.integration;

import com.lifeplanner.config.TestAuthConfig;
import com.lifeplanner.repository.InboxRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The created entity and the "processed" mark commit together or not at all. */
@SpringBootTest
@AutoConfigureMockMvc
class InboxTriageRollbackTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    @MockitoSpyBean
    InboxRepository inbox;

    @org.springframework.beans.factory.annotation.Autowired
    com.lifeplanner.repository.UserRepository users;

    String capture() throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/inbox-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rawText\":\"x\"}")).andReturn().getResponse().getContentAsString(), "$.id");
    }

    int tasks() {
        return db.sql("SELECT count(*) FROM tasks").query(Integer.class).single();
    }

    @Test
    void failureWhileMarkingProcessedRollsBackTheCreatedTask() throws Exception {
        String id = capture();
        doThrow(new IllegalStateException("disk full")).when(inbox).markProcessed(eq(id), anyString(), any(), any(), any());
        int before = tasks();
        mvc.perform(post("/api/v1/inbox-items/" + id + "/process").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"task\",\"task\":{\"title\":\"t\"}}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("internal_error"));
        assertThat(tasks()).isEqualTo(before);
        mvc.perform(get("/api/v1/inbox-items/" + id)).andExpect(jsonPath("$.status").value("unprocessed"));
    }

    @Test
    void concurrentProcessingIs409AndRollsBackTheSecondEntity() throws Exception {
        String id = capture();
        // simulate another request processing the item between our read and our conditional update
        doReturn(false).when(inbox).markProcessed(eq(id), anyString(), any(), any(), any());
        int before = tasks();
        mvc.perform(post("/api/v1/inbox-items/" + id + "/process").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"task\",\"task\":{\"title\":\"t\"}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("inbox_item_already_processed"));
        assertThat(tasks()).isEqualTo(before);
    }

    @Test
    void conditionalUpdateRefusesAlreadyProcessedRows() throws Exception {
        String id = capture();
        String owner = users.findByEmail(TestAuthConfig.EMAIL).orElseThrow().id();
        assertThat(TestAuthConfig.runAs(owner, () -> inbox.markProcessed(id, "reference", null, null, java.time.Instant.parse("2026-11-08T12:00:00Z")))).isTrue();
        assertThat(TestAuthConfig.runAs(owner, () -> inbox.markProcessed(id, "discard", null, null, java.time.Instant.parse("2026-11-08T12:00:01Z")))).isFalse();
        // another account cannot touch it at all (scoped update)
        assertThat(TestAuthConfig.runAs("00000000-0000-0000-0000-000000000000",
                () -> inbox.markProcessed(id, "discard", null, null, java.time.Instant.parse("2026-11-08T12:00:02Z")))).isFalse();
        mvc.perform(get("/api/v1/inbox-items/" + id)).andExpect(jsonPath("$.decision").value("reference"));
    }
}
