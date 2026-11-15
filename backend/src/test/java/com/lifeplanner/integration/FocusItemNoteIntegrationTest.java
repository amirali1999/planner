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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notes of a focus item: plain text kept with the item (not tasks). */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/focus-notes-${random.uuid}.db")
@AutoConfigureMockMvc
class FocusItemNoteIntegrationTest {

    @Autowired
    MockMvc mvc;

    String item(String title) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/focus-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"kind\":\"book\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions addNote(String itemId, String body) throws Exception {
        return mvc.perform(post("/api/v1/focus-items/" + itemId + "/notes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}"));
    }

    String note(String itemId, String body) throws Exception {
        return JsonPath.read(addNote(itemId, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void addsListsEditsAndDeletesNotesNewestFirstWithoutTouchingTasks() throws Exception {
        String book = item("کتاب RAG");
        String first = note(book, "  فصل ۱ خلاصه  ");
        Thread.sleep(5);
        String second = note(book, "فصل ۲: مثال‌ها");
        String body = mvc.perform(get("/api/v1/focus-items/" + book + "/notes")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$[*].id")).containsExactly(second, first);
        assertThat(JsonPath.<List<String>>read(body, "$[*].body")).containsExactly("فصل ۲: مثال‌ها", "فصل ۱ خلاصه");
        mvc.perform(put("/api/v1/focus-item-notes/" + first).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"فصل ۱ — بازنویسی\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("فصل ۱ — بازنویسی"));
        String counts = mvc.perform(get("/api/v1/focus-item-notes/counts")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(counts, "$[?(@.focusItemId == '" + book + "')].count")).containsExactly(2);
        mvc.perform(delete("/api/v1/focus-item-notes/" + second)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/focus-items/" + book + "/notes")).andExpect(jsonPath("$.length()").value(1));
        // a note is not a task
        mvc.perform(get("/api/v1/tasks?archived=all&focusItemId=" + book)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void validatesAndProtectsArchivedAndForeignItems() throws Exception {
        String item = item("پروژه");
        addNote(item, "   ").andExpect(status().isBadRequest());
        addNote("00000000-0000-0000-0000-000000000000", "x").andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/focus-item-notes/00000000-0000-0000-0000-000000000000").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"x\"}")).andExpect(status().isNotFound());
        String n = note(item, "قبل از بایگانی");
        mvc.perform(patch("/api/v1/focus-items/" + item).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/focus-items/" + item + "/notes")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        addNote(item, "بعد").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("focus_item_archived"));
        mvc.perform(delete("/api/v1/focus-item-notes/" + n)).andExpect(status().isConflict());
        // permanently deleting the archived item removes its notes
        mvc.perform(delete("/api/v1/focus-items/" + item)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/focus-item-notes/" + n).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}")).andExpect(status().isNotFound());

        String mine = item("مال من");
        String mineNote = note(mine, "خصوصی");
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other-notes@test.local\",\"password\":\"password-123\"}")).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/focus-items/" + mine + "/notes").header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/focus-item-notes/" + mineNote).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/focus-item-notes/counts").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.length()").value(0));
    }
}
