package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /time-blocks (weekly time-box): create, list, move/resize, overlap, task links, delete. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/time-blocks-${random.uuid}.db")
@AutoConfigureMockMvc
class TimeBlockIntegrationTest {

    static final AtomicInteger WEEK = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;

    static LocalDate freshDay() {
        return LocalDate.of(2060, 1, 1).plusWeeks(3L * WEEK.getAndIncrement()); // 3 weeks apart: tests never share days
    }

    ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/time-blocks").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String id(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    ResultActions edit(String id, String json) throws Exception {
        return mvc.perform(patch("/api/v1/time-blocks/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String task(String title) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    static String block(LocalDate d, int start, int end, String extra) {
        return "{\"date\":\"" + d + "\",\"startMinute\":" + start + ",\"endMinute\":" + end + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    @Test
    void createsListsAndOrdersBlocksOfARange() throws Exception {
        LocalDate d = freshDay();
        String t = task("پیاده‌سازی ماژول");
        create(block(d, 600, 780, "\"taskId\":\"" + t + "\",\"color\":\"blue\"")).andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.task.title").value("پیاده‌سازی ماژول"))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.color").value("blue"));
        create(block(d, 480, 540, "\"title\":\"  آماده‌سازی روز \"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("آماده‌سازی روز")).andExpect(jsonPath("$.color").value("teal"))
                .andExpect(jsonPath("$.task").doesNotExist());
        create(block(d.plusDays(1), 0, 1440, "\"title\":\"all day\"")).andExpect(status().isCreated());
        create(block(d.plusDays(7), 60, 120, "\"title\":\"next week\"")).andExpect(status().isCreated());
        String json = mvc.perform(get("/api/v1/time-blocks?from=" + d + "&to=" + d.plusDays(6)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> starts = JsonPath.read(json, "$[*].startMinute");
        assertThat(starts).containsExactly(480, 600, 0);
    }

    @Test
    void rejectsInvalidMinutesTitlesAndRanges() throws Exception {
        LocalDate d = freshDay();
        create(block(d, 600, 600, "\"title\":\"x\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.endMinute").value("must_be_after_startMinute"));
        create(block(d, 601, 660, "\"title\":\"x\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.startMinute").value("invalid_value"));
        create(block(d, 1400, 1445, "\"title\":\"x\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.endMinute").value("invalid_value"));
        create(block(d, 600, 660, "\"title\":\"   \"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").value("required"));
        create(block(d, 600, 660, "\"title\":\"x\",\"color\":\"orange\"")).andExpect(status().isBadRequest());
        create("{\"startMinute\":600,\"endMinute\":660,\"title\":\"x\"}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/time-blocks?from=" + d + "&to=" + d.minusDays(1))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/time-blocks?from=" + d + "&to=" + d.plusDays(42))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.to").value("range_too_long"));
    }

    @Test
    void blocksOfADayNeverOverlapButMayTouch() throws Exception {
        LocalDate d = freshDay();
        String a = id(create(block(d, 540, 600, "\"title\":\"a\"")).andExpect(status().isCreated()));
        create(block(d, 570, 630, "\"title\":\"b\"")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("time_block_overlap")).andExpect(jsonPath("$.details.timeBlockId").value(a));
        String b = id(create(block(d, 600, 660, "\"title\":\"b\"")).andExpect(status().isCreated())); // touching is fine
        create(block(d.plusDays(1), 540, 600, "\"title\":\"other day\"")).andExpect(status().isCreated());
        // move/resize: overlap is checked against the others, not against itself
        edit(b, "{\"startMinute\":590}").andExpect(status().isConflict());
        edit(b, "{\"startMinute\":615,\"endMinute\":720}").andExpect(status().isOk())
                .andExpect(jsonPath("$.startMinute").value(615)).andExpect(jsonPath("$.endMinute").value(720));
        edit(a, "{\"date\":\"" + d.plusDays(2) + "\",\"startMinute\":0,\"endMinute\":30}").andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(d.plusDays(2).toString()));
    }

    @Test
    void patchRulesForTitleTaskAndColour() throws Exception {
        LocalDate d = freshDay();
        String t = task("t");
        String b = id(create(block(d, 60, 120, "\"title\":\"own\"")));
        edit(b, "{\"title\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.title").value("required"));
        edit(b, "{\"taskId\":\"" + t + "\",\"title\":null,\"color\":\"rose\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(t)).andExpect(jsonPath("$.title").doesNotExist()).andExpect(jsonPath("$.color").value("rose"));
        edit(b, "{\"color\":null}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.color").value("required"));
        edit(b, "{\"id\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.id").value("not_editable"));
        edit(b, "{\"note\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.note").value("unknown_field"));
        edit("00000000-0000-0000-0000-000000000000", "{\"color\":\"teal\"}").andExpect(status().isNotFound());
        // the block never changes the task
        mvc.perform(get("/api/v1/tasks/" + t)).andExpect(jsonPath("$.status").value("not_started"));
    }

    @Test
    void taskLinksMustBeOwnAndActiveAndBlocksGoWithAPermanentlyDeletedTask() throws Exception {
        LocalDate d = freshDay();
        create(block(d, 60, 120, "\"taskId\":\"00000000-0000-0000-0000-000000000000\"")).andExpect(status().isNotFound());
        String archived = task("old");
        mvc.perform(patch("/api/v1/tasks/" + archived).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
        create(block(d, 60, 120, "\"taskId\":\"" + archived + "\"")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("task_archived"));

        String t = task("linked");
        String b = id(create(block(d, 200, 260, "\"taskId\":\"" + t + "\"")).andExpect(status().isCreated()));
        mvc.perform(patch("/api/v1/tasks/" + t).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
        // a block of an archived task is still listed (UI hides it) and no longer blocks its time
        mvc.perform(get("/api/v1/time-blocks").param("from", d.toString()).param("to", d.toString()))
                .andExpect(jsonPath("$[0].task.archivedAt").exists());
        create(block(d, 200, 260, "\"title\":\"free again\"")).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/tasks/" + t)).andExpect(status().isOk());
        edit(b, "{\"color\":\"teal\"}").andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersBlocksAreInvisible() throws Exception {
        LocalDate d = freshDay();
        String b = id(create(block(d, 60, 120, "\"title\":\"mine\"")));
        String other = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other-blocks@test.local\",\"password\":\"password-123\"}")).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/time-blocks?from=" + d + "&to=" + d).header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(patch("/api/v1/time-blocks/" + b).header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"color\":\"rose\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/time-blocks/" + b).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        // the other user may use the same time: overlap is per user
        mvc.perform(post("/api/v1/time-blocks").header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                .content(block(d, 60, 120, "\"title\":\"theirs\""))).andExpect(status().isCreated());
    }

    @Test
    void deleteRemovesTheBlockOnly() throws Exception {
        LocalDate d = freshDay();
        String t = task("keep");
        String b = id(create(block(d, 60, 120, "\"taskId\":\"" + t + "\"")));
        mvc.perform(delete("/api/v1/time-blocks/" + b)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/time-blocks/" + b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tasks/" + t)).andExpect(status().isOk());
    }
}
