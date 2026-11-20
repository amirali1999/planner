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
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual daily activity logs: CRUD, ordering, overlap and per-user isolation. */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/activity-logs-${random.uuid}.db")
@AutoConfigureMockMvc
class ActivityLogIntegrationTest {

    static final AtomicInteger DAY = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    static LocalDate freshDay() {
        return LocalDate.of(2070, 1, 1).plusDays(3L * DAY.getAndIncrement());
    }

    static String body(LocalDate date, String title, String category, int start, int end) {
        return """
                {"date":"%s","title":"%s","category":"%s","startMinute":%d,"endMinute":%d}
                """.formatted(date, title, category, start, end);
    }

    ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/activity-logs").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createsListsAndOrdersActualActivities() throws Exception {
        LocalDate day = freshDay();
        create(body(day, "  مطالعه فصل چهارم  ", "study", 600, 690)).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andExpect(jsonPath("$.title").value("مطالعه فصل چهارم"))
                .andExpect(jsonPath("$.category").value("study"));
        create(body(day, "صبحانه", "personal", 480, 510)).andExpect(status().isCreated());
        create(body(day.plusDays(1), "روز دیگر", "work", 480, 510)).andExpect(status().isCreated());

        String json = mvc.perform(get("/api/v1/activity-logs").param("date", day.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<Integer>) JsonPath.read(json, "$[*].startMinute")).containsExactly(480, 600);
    }

    @Test
    void validatesClosedFiveMinuteIntervalsAndRejectsOverlap() throws Exception {
        LocalDate day = freshDay();
        String first = JsonPath.read(create(body(day, "کار", "work", 540, 600)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        create(body(day, "هم‌پوشان", "study", 570, 630)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("activity_log_overlap"))
                .andExpect(jsonPath("$.details.activityLogId").value(first));
        create(body(day, "چسبیده", "rest", 600, 630)).andExpect(status().isCreated());
        create(body(day, "گام نامعتبر", "work", -1, 660)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.startMinute").value("invalid_value"));
        create(body(day, "پایان نامعتبر", "work", 700, 700)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.endMinute").value("must_be_after_startMinute"));
        create(body(day, " ", "work", 720, 780)).andExpect(status().isBadRequest());
        create(body(day, "دسته", "other", 720, 780)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/activity-logs").param("date", "2026-02-30")).andExpect(status().isBadRequest());
    }

    @Test
    void editsAndExplicitlyDeletesAnActivity() throws Exception {
        LocalDate day = freshDay();
        String id = JsonPath.read(create(body(day, "اول", "work", 600, 660)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/v1/activity-logs/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(day, "ویرایش‌شده", "personal", 615, 690)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("ویرایش‌شده"))
                .andExpect(jsonPath("$.startMinute").value(615));
        mvc.perform(delete("/api/v1/activity-logs/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/activity-logs/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void linksOwnTaskAndTimerIsIsolatedFromCompletedLogsUntilStopped() throws Exception {
        LocalDate day = freshDay();
        String task = JsonPath.read(mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"linked\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        String input = body(day, "task work", "work", 601, 662).trim();
        input = input.substring(0,input.length()-1) + ",\"taskId\":\"" + task + "\"}";
        create(input).andExpect(status().isCreated()).andExpect(jsonPath("$.taskId").value(task));
        mvc.perform(post("/api/v1/activity-timer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"invalid\",\"timeZone\":\"UTC\",\"plannedMinutes\":121}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_error"));
        mvc.perform(post("/api/v1/activity-timer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"timer\",\"timeZone\":\"UTC\",\"plannedMinutes\":25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plannedMinutes").value(25));
        mvc.perform(post("/api/v1/activity-timer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"second\",\"timeZone\":\"UTC\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/activity-logs").param("date",day.toString())).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/v1/activity-timer/stop")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/v1/activity-timer/stop")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherUsersActivityLogsAreInvisibleAndUnlinkable() throws Exception {
        LocalDate day = freshDay();
        String mine = JsonPath.read(create(body(day, "مال من", "work", 600, 660)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String token = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other-log@test.local\",\"password\":\"password-123\"}"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/activity-logs").param("date", day.toString()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/v1/activity-logs/" + mine).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(day, "دستکاری", "work", 700, 760)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/activity-logs/" + mine).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/activity-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(day, "مال او", "rest", 600, 660)))
                .andExpect(status().isCreated());
    }

    @Test
    void timerAndManualLogsRejectEachOthersIntervalsAndTimerCanBeCancelled() throws Exception {
        String token = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"timer-overlap-" + DAY.incrementAndGet() + "@test.local\",\"password\":\"password-123\"}"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        String zoneName = List.of("UTC", "Asia/Tehran", "Pacific/Kiritimati", "Pacific/Honolulu").stream()
                .filter(candidate -> {
                    int hour = Instant.now().atZone(ZoneId.of(candidate)).getHour();
                    return hour >= 2 && hour <= 21;
                }).findFirst().orElseThrow();
        var localNow = Instant.now().atZone(ZoneId.of(zoneName));
        LocalDate day = localNow.toLocalDate();
        String existing = JsonPath.read(mvc.perform(post("/api/v1/activity-logs")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(body(day, "existing", "work", 0, 1440)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/activity-timer").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"blocked timer\",\"timeZone\":\"" + zoneName + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("activity_log_overlap"));

        mvc.perform(delete("/api/v1/activity-logs/" + existing).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        String startedAt = JsonPath.read(mvc.perform(post("/api/v1/activity-timer")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"running\",\"timeZone\":\"" + zoneName + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.startedAt");
        var started = Instant.parse(startedAt).atZone(ZoneId.of(zoneName));
        int minute = started.getHour() * 60 + started.getMinute();
        int end = Math.min(1440, minute + 1);
        mvc.perform(post("/api/v1/activity-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(started.toLocalDate(), "blocked log", "work", minute, end)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("activity_timer_overlap"));

        mvc.perform(delete("/api/v1/activity-timer").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/activity-timer").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$").doesNotExist());
        mvc.perform(post("/api/v1/activity-logs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(started.toLocalDate(), "allowed after cancel", "work", minute, end)))
                .andExpect(status().isCreated());
    }
}
