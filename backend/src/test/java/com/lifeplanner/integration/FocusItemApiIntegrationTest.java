package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F-01: Focus Items with optional Goal link, kinds, archive retention. */
@SpringBootTest
@AutoConfigureMockMvc
class FocusItemApiIntegrationTest {

    static final String MISSING = "00000000-0000-0000-0000-000000000000";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    String goalId;

    @BeforeEach
    void goal() throws Exception {
        goalId = JsonPath.read(body(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}"""))), "$.id");
    }

    static String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/focus-items").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String item(String json) throws Exception {
        return JsonPath.read(body(create(json).andExpect(status().isCreated())), "$.id");
    }

    ResultActions patchItem(String id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/focus-items/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void standaloneAndGoalLinkedItems() throws Exception {
        int goalsBefore = db.sql("SELECT count(*) FROM goals").query(Integer.class).single();
        create("""
                {"title":"  کتاب Designing Data-Intensive Apps ","kind":"book"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("کتاب Designing Data-Intensive Apps"))
                .andExpect(jsonPath("$.kind").value("book"))
                .andExpect(jsonPath("$.status").value("not_started"))
                .andExpect(jsonPath("$.goalId").doesNotExist())
                .andExpect(jsonPath("$.archivedAt").doesNotExist());
        String linked = item("""
                {"title":"پروژه retriever","kind":"project","description":"d","goalId":"%s","status":"doing"}""".formatted(goalId));
        mvc.perform(get("/api/v1/focus-items/" + linked))
                .andExpect(jsonPath("$.goalId").value(goalId)).andExpect(jsonPath("$.status").value("doing"))
                .andExpect(jsonPath("$.description").value("d"));
        assertThat(db.sql("SELECT count(*) FROM goals").query(Integer.class).single()).isEqualTo(goalsBefore);
    }

    @Test
    void allKindsValidOthersRejected() throws Exception {
        for (String kind : new String[]{"project", "book", "course", "area", "other"}) {
            create("{\"title\":\"x\",\"kind\":\"" + kind + "\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value(kind));
        }
        create("{\"title\":\"x\",\"kind\":\"habit\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.kind").value("invalid_value"));
        create("{\"title\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.kind").value("required"));
        create("{\"kind\":\"book\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        create("{\"title\":\"x\",\"kind\":\"book\",\"status\":\"blocked\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").value("invalid_value"));
        create("{\"title\":\"x\",\"kind\":\"book\",\"goalIds\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.goalIds").value("unknown_field")); // many-to-many is not part of MVP v0
    }

    @Test
    void missingOrArchivedGoalIsRejectedAndNoGoalIsCreated() throws Exception {
        int goalsBefore = db.sql("SELECT count(*) FROM goals").query(Integer.class).single();
        create("{\"title\":\"x\",\"kind\":\"book\",\"goalId\":\"" + MISSING + "\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.details.goalId").value(MISSING));
        assertThat(db.sql("SELECT count(*) FROM goals").query(Integer.class).single()).isEqualTo(goalsBefore);
        mvc.perform(post("/api/v1/goals/" + goalId + "/archive")).andExpect(status().isOk());
        create("{\"title\":\"x\",\"kind\":\"book\",\"goalId\":\"" + goalId + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_archived"));
        mvc.perform(get("/api/v1/focus-items/" + MISSING)).andExpect(status().isNotFound());
        patchItem(MISSING, "{\"title\":\"x\"}").andExpect(status().isNotFound());
    }

    @Test
    void archivingTheGoalKeepsItsFocusItemsAndTheirLink() throws Exception {
        String id = item("{\"title\":\"دوره\",\"kind\":\"course\",\"goalId\":\"" + goalId + "\"}");
        mvc.perform(post("/api/v1/goals/" + goalId + "/archive")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/focus-items/" + id))
                .andExpect(jsonPath("$.archivedAt").doesNotExist())
                .andExpect(jsonPath("$.goalId").value(goalId));
        mvc.perform(get("/api/v1/focus-items")).andExpect(jsonPath("$[*].id", hasItem(id)));
        // unrelated edits keep the existing link to the archived goal; unlinking is allowed
        patchItem(id, "{\"status\":\"doing\"}").andExpect(status().isOk()).andExpect(jsonPath("$.goalId").value(goalId));
        patchItem(id, "{\"goalId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.goalId").doesNotExist());
        patchItem(id, "{\"goalId\":\"" + goalId + "\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("goal_archived"));
    }

    @Test
    void patchEditsLinksAndValidates() throws Exception {
        String id = item("{\"title\":\"x\",\"kind\":\"other\",\"description\":\"d\"}");
        patchItem(id, "{\"title\":\"حوزه سلامت\",\"kind\":\"area\",\"goalId\":\"" + goalId + "\",\"status\":\"done\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("حوزه سلامت")).andExpect(jsonPath("$.kind").value("area"))
                .andExpect(jsonPath("$.goalId").value(goalId)).andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.description").value("d"));
        patchItem(id, "{\"description\":null}").andExpect(jsonPath("$.description").doesNotExist());
        patchItem(id, "{\"kind\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.kind").value("required"));
        patchItem(id, "{\"kind\":\"habit\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.kind").value("invalid_value"));
        patchItem(id, "{\"title\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        patchItem(id, "{\"archivedAt\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.archivedAt").value("not_editable"));
        patchItem(id, "{\"goalId\":\"" + MISSING + "\"}").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/focus-items/" + id)).andExpect(jsonPath("$.title").value("حوزه سلامت")).andExpect(jsonPath("$.goalId").value(goalId));
    }

    @Test
    void archiveRetentionFiltersAndRestore() throws Exception {
        String id = item("{\"title\":\"بایگانی‌شونده\",\"kind\":\"project\",\"goalId\":\"" + goalId + "\"}");
        String archivedAt = JsonPath.read(body(patchItem(id, "{\"archived\":true}").andExpect(status().isOk())), "$.archivedAt");
        patchItem(id, "{\"archived\":true}").andExpect(jsonPath("$.archivedAt").value(archivedAt));
        mvc.perform(get("/api/v1/focus-items")).andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mvc.perform(get("/api/v1/focus-items?archived=true")).andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/v1/focus-items/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.goalId").value(goalId));
        patchItem(id, "{\"title\":\"x\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("focus_item_archived"));
        patchItem(id, "{\"archived\":false,\"status\":\"doing\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").doesNotExist()).andExpect(jsonPath("$.status").value("doing"));
        assertThat(db.sql("SELECT count(*) FROM focus_items WHERE id = ?").param(id).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void listFilters() throws Exception {
        String a = item("{\"title\":\"a\",\"kind\":\"book\",\"goalId\":\"" + goalId + "\"}");
        String b = item("{\"title\":\"b\",\"kind\":\"course\",\"goalId\":\"" + goalId + "\",\"status\":\"done\"}");
        item("{\"title\":\"c\",\"kind\":\"book\"}");
        List<String> byGoal = JsonPath.read(body(mvc.perform(get("/api/v1/focus-items?goalId=" + goalId))), "$[*].id");
        assertThat(byGoal).containsExactly(a, b);
        List<String> books = JsonPath.read(body(mvc.perform(get("/api/v1/focus-items?goalId=" + goalId + "&kind=book"))), "$[*].id");
        assertThat(books).containsExactly(a);
        List<String> done = JsonPath.read(body(mvc.perform(get("/api/v1/focus-items?goalId=" + goalId + "&status=done"))), "$[*].id");
        assertThat(done).containsExactly(b);
        mvc.perform(get("/api/v1/focus-items?kind=habit")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.kind").value("invalid_value"));
        mvc.perform(get("/api/v1/focus-items?archived=maybe")).andExpect(status().isBadRequest());
    }
}
