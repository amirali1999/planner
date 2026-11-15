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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** I-01: quick capture with raw text only; nothing inferred; safe storage of arbitrary input. */
@SpringBootTest
@AutoConfigureMockMvc
class InboxApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    ResultActions capture(String rawTextJson) throws Exception {
        return mvc.perform(post("/api/v1/inbox-items").contentType(MediaType.APPLICATION_JSON).content("{\"rawText\":" + rawTextJson + "}"));
    }

    static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    int count(String table) {
        return db.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    @Test
    void captureStoresTextOnlyWithNoGoalOrTask() throws Exception {
        int goals = count("goals");
        int tasks = count("tasks");
        int focus = count("focus_items");
        capture(json("  خرید کتاب RAG تا جمعه، اولویت بالا\nبرای هدف یادگیری  "))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/inbox-items/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.rawText").value("خرید کتاب RAG تا جمعه، اولویت بالا\nبرای هدف یادگیری"))
                .andExpect(jsonPath("$.status").value("unprocessed"))
                .andExpect(jsonPath("$.decision").doesNotExist())
                .andExpect(jsonPath("$.createdEntityType").doesNotExist())
                .andExpect(jsonPath("$.createdEntityId").doesNotExist())
                .andExpect(jsonPath("$.processedAt").doesNotExist())
                .andExpect(jsonPath("$.capturedAt").isString())
                .andExpect(jsonPath("$.createdAt").isString());
        // the words "goal", "Friday", "priority" are NOT interpreted
        assertThat(count("goals")).isEqualTo(goals);
        assertThat(count("tasks")).isEqualTo(tasks);
        assertThat(count("focus_items")).isEqualTo(focus);
    }

    @Test
    void emptyOrBlankOrMissingTextIs400() throws Exception {
        for (String bad : new String[]{"\"\"", "\"   \"", "\"\\n\\t \"", "null"}) {
            capture(bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation_error"))
                    .andExpect(jsonPath("$.message").value("متن یادداشت نمی‌تواند خالی باشد."))
                    .andExpect(jsonPath("$.details.rawText").value("required"));
        }
        mvc.perform(post("/api/v1/inbox-items").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.rawText").value("required"));
        capture("123").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.rawText").value("invalid_value"));
    }

    @Test
    void lengthLimitCountsCharactersNotBytes() throws Exception {
        String max = "😀".repeat(10) + "ا".repeat(3990);            // 4000 code points (emoji = 1)
        capture(json(max)).andExpect(status().isCreated());
        capture(json(max + "ب")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.rawText").value("too_long"))
                .andExpect(jsonPath("$.details.maxLength").value(4000))
                .andExpect(jsonPath("$.details.length").value(4001))
                .andExpect(jsonPath("$.message").value("متن یادداشت حداکثر ۴۰۰۰ نویسه می‌تواند باشد."));
        // outer whitespace does not count
        capture(json("   " + "x".repeat(4000) + "   ")).andExpect(status().isCreated());
    }

    @Test
    void markupIsStoredAndReturnedVerbatimNeverInterpreted() throws Exception {
        String xss = "<script>alert('x')</script><img src=x onerror=\"alert(1)\"> & <b>bold</b> {{7*7}} ${7*7}";
        String id = JsonPath.read(capture(json(xss)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/inbox-items/" + id)).andExpect(jsonPath("$.rawText").value(xss));
        assertThat(db.sql("SELECT raw_text FROM inbox_items WHERE id = ?").param(id).query(String.class).single()).isEqualTo(xss);
        // JSON response: never served as HTML
        String contentType = mvc.perform(get("/api/v1/inbox-items/" + id)).andReturn().getResponse().getContentType();
        assertThat(contentType).startsWith("application/json");
    }

    @Test
    void onlyRawTextIsAccepted() throws Exception {
        for (String extra : new String[]{"goalId", "dueDate", "priority", "status", "decision"}) {
            mvc.perform(post("/api/v1/inbox-items").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rawText\":\"x\",\"" + extra + "\":\"y\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details." + extra).value("unknown_field"));
        }
    }

    @Test
    void queueListsUnprocessedNewestFirstAndFilters() throws Exception {
        String first = JsonPath.read(capture(json("اول")).andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(capture(json("دوم")).andReturn().getResponse().getContentAsString(), "$.id");
        List<String> queue = JsonPath.read(mvc.perform(get("/api/v1/inbox-items")).andReturn().getResponse().getContentAsString(), "$[*].id");
        assertThat(queue.indexOf(second)).isLessThan(queue.indexOf(first));
        List<String> processed = JsonPath.read(mvc.perform(get("/api/v1/inbox-items?status=processed")).andReturn().getResponse().getContentAsString(), "$[*].id");
        assertThat(processed).doesNotContain(first, second);
        List<String> all = JsonPath.read(mvc.perform(get("/api/v1/inbox-items?status=all")).andReturn().getResponse().getContentAsString(), "$[*].id");
        assertThat(all).contains(first, second);
        mvc.perform(get("/api/v1/inbox-items?status=done")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/inbox-items/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void databaseRefusesAnUnprocessedItemWithADecisionOrEntity() {
        String sql = """
                INSERT INTO inbox_items (id, raw_text, status, decision, created_entity_type, created_entity_id, captured_at, created_at, updated_at)
                VALUES (?, 'x', 'unprocessed', ?, ?, ?, '2026-11-08T12:00:00.000Z', '2026-11-08T12:00:00.000Z', '2026-11-08T12:00:00.000Z')""";
        assertThatThrownBy(() -> db.sql(sql).params(java.util.UUID.randomUUID().toString(), "task", null, null).update())
                .hasMessageContaining("CHECK");
        assertThatThrownBy(() -> db.sql(sql).params(java.util.UUID.randomUUID().toString(), null, "task",
                "00000000-0000-0000-0000-000000000001").update()).hasMessageContaining("CHECK");
    }
}
