package com.lifeplanner.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.lifeplanner.model.NoteLimits;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "planner.datasource.path=target/test-db/task-notes-${random.uuid}.db")
@AutoConfigureMockMvc
class TaskNoteIntegrationTest {

    private static final String TASKS = "/api/v1/tasks";
    private static final String NOTES = "/api/v1/task-notes";
    private static final String COUNTS = NOTES + "/counts";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void completedTaskHasItsOwnNotesAndCount() throws Exception {
        String task = task(Map.of("title", "مرور ویدیو", "status", "done", "description", "شرح تسک"));
        String otherTask = task(Map.of("title", "ویدیوی بعد"));
        mvc.perform(get(taskNotes(task))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        add(task, "   ").andExpect(status().isBadRequest());
        add(task, "x".repeat(NoteLimits.BODY_MAX + 1)).andExpect(status().isBadRequest());

        var creation = add(task, "  خط اول\nخط دوم  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").value(task))
                .andExpect(jsonPath("$.body").value("خط اول\nخط دوم"));
        String note = field(creation, "$.id");
        String createdAt = field(creation, "$.createdAt");
        creation.andExpect(header().string(HttpHeaders.LOCATION, notePath(note)));
        var secondCreation = add(task, "مثال‌ها").andExpect(status().isCreated());
        String otherNote = field(secondCreation, "$.id");
        String otherCreatedAt = field(secondCreation, "$.createdAt");
        // Compare the stored timestamps, with the ID as a tie breaker when both notes land in one millisecond.
        int comparison = Instant.parse(createdAt).compareTo(Instant.parse(otherCreatedAt));
        boolean secondIsFirst = comparison < 0 || (comparison == 0 && note.compareTo(otherNote) < 0);
        mvc.perform(get(taskNotes(task))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(secondIsFirst ? otherNote : note, secondIsFirst ? note : otherNote)));
        mvc.perform(get(taskNotes(otherTask))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(COUNTS)).andExpect(status().isOk()).andExpect(jsonPath(countFor(task), contains(2)));

        replace(note, " بازنویسی ").andExpect(status().isOk()).andExpect(jsonPath("$.body").value("بازنویسی"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));
        mvc.perform(delete(notePath(otherNote))).andExpect(status().isNoContent());
        mvc.perform(get(COUNTS)).andExpect(status().isOk()).andExpect(jsonPath(countFor(task), contains(1)));
        mvc.perform(get(TASKS + "/" + task)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done")).andExpect(jsonPath("$.description").value("شرح تسک"));
    }

    @Test
    void anotherUserCannotListCountCreateEditOrDeleteOwnedNotes() throws Exception {
        String task = task(Map.of("title", "تسک خصوصی"));
        String note = field(add(task, "یادداشت خصوصی").andExpect(status().isCreated()), "$.id");
        String token = field(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "task-notes-other@test.local", "password", "password-123"))))
                .andExpect(status().isCreated()), "$.accessToken");
        String auth = "Bearer " + token;

        mvc.perform(get(taskNotes(task)).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound());
        mvc.perform(post(taskNotes(task)).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "x")))).andExpect(status().isNotFound());
        mvc.perform(put(notePath(note)).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", "x")))).andExpect(status().isNotFound());
        mvc.perform(delete(notePath(note)).header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isNotFound());
        mvc.perform(get(COUNTS).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(taskNotes(task))).andExpect(status().isOk()).andExpect(jsonPath("$[0].body").value("یادداشت خصوصی"));
    }

    @Test
    void archivingKeepsNotesReadOnlyAndPermanentDeletionRemovesThem() throws Exception {
        String task = task(Map.of("title", "کار بایگانی"));
        String note = field(add(task, "یادداشت ماندگار").andExpect(status().isCreated()), "$.id");

        archiveTask(task, true);
        mvc.perform(get(taskNotes(task))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(note));
        mvc.perform(get(COUNTS)).andExpect(status().isOk()).andExpect(jsonPath("$[*].taskId", not(hasItem(task))));
        add(task, "x").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("task_archived"));
        replace(note, "x").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("task_archived"));
        mvc.perform(delete(notePath(note))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("task_archived"));

        archiveTask(task, false);
        mvc.perform(get(COUNTS)).andExpect(status().isOk()).andExpect(jsonPath(countFor(task), contains(1)));
        archiveTask(task, true);
        mvc.perform(delete(TASKS + "/" + task)).andExpect(status().isOk());
        mvc.perform(get(taskNotes(task))).andExpect(status().isNotFound());
        replace(note, "x").andExpect(status().isNotFound());
    }

    @Test
    void deletingAnArchivedFocusItemAlsoRemovesNotesOfItsActiveTasks() throws Exception {
        String item = field(mvc.perform(post("/api/v1/focus-items").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "پروژه", "kind", "project")))).andExpect(status().isCreated()), "$.id");
        String task = task(Map.of("title", "تسک پروژه", "focusItemId", item));
        String note = field(add(task, "یادداشت تسک").andExpect(status().isCreated()), "$.id");
        mvc.perform(patch("/api/v1/focus-items/" + item).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("archived", true)))).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/focus-items/" + item)).andExpect(status().isOk());
        mvc.perform(get(taskNotes(task))).andExpect(status().isNotFound());
        replace(note, "x").andExpect(status().isNotFound());
    }

    private String task(Map<String, String> body) throws Exception {
        return field(mvc.perform(post(TASKS).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()), "$.id");
    }

    private void archiveTask(String task, boolean archived) throws Exception {
        mvc.perform(patch(TASKS + "/" + task).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("archived", archived)))).andExpect(status().isOk());
    }

    private ResultActions add(String task, String body) throws Exception {
        return mvc.perform(post(taskNotes(task)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", body))));
    }

    private ResultActions replace(String note, String body) throws Exception {
        return mvc.perform(put(notePath(note)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("body", body))));
    }

    private String json(Map<String, ?> body) throws Exception {
        return mapper.writeValueAsString(body);
    }

    private static String field(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    private static String taskNotes(String task) {
        return TASKS + "/" + task + "/notes";
    }

    private static String notePath(String note) {
        return NOTES + "/" + note;
    }

    private static String countFor(String task) {
        return "$[?(@.taskId == '" + task + "')].count";
    }
}
