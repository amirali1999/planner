package com.lifeplanner.integration;

import com.lifeplanner.PlannerApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Due date (ISO date-only) and completion survive a real restart unchanged. */
class MilestoneRestartTest {

    @TempDir
    Path dir;

    private final HttpClient http = HttpClient.newHttpClient();
    private final TestAuth auth = new TestAuth(http);
    private String api;

    private ConfigurableApplicationContext start(Path db) {
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(PlannerApplication.class)
                .run("--planner.datasource.path=" + db, "--server.port=0");
        api = "http://127.0.0.1:" + ctx.getEnvironment().getProperty("local.server.port") + "/api/v1";
        return ctx;
    }

    private String send(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api + path)).header("Authorization", auth.bearer(api + path)).header("Content-Type", "application/json");
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).as(method + " " + path + " → " + r.body()).isLessThan(300);
        return r.body();
    }

    @Test
    void milestonesSurviveRestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String goalId;
        String before;
        try (ConfigurableApplicationContext ignored = start(db)) {
            goalId = JsonPath.read(send("POST", "/goals", """
                    {"title":"g","periodType":"monthly","periodStart":"2026-10-23","periodEnd":"2026-11-21"}"""), "$.id");
            String m = JsonPath.read(send("POST", "/goals/" + goalId + "/milestones", """
                    {"title":"تحویل نسخه اول","dueDate":"2026-11-21"}"""), "$.id");
            send("POST", "/goals/" + goalId + "/milestones", "{\"title\":\"بدون موعد\"}");
            send("PATCH", "/milestones/" + m, "{\"completed\":true}");
            before = send("GET", "/goals/" + goalId + "/milestones", null);
        }
        try (ConfigurableApplicationContext ignored = start(db)) {
            String after = send("GET", "/goals/" + goalId + "/milestones", null);
            assertThat(after).isEqualTo(before);
            assertThat((String) JsonPath.read(after, "$[0].dueDate")).isEqualTo("2026-11-21");
            assertThat((Boolean) JsonPath.read(after, "$[0].completed")).isTrue();
        }
    }
}
