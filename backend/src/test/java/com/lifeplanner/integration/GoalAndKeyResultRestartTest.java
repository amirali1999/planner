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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP against a real server, stopped and restarted on the same SQLite file. */
class GoalAndKeyResultRestartTest {

    @TempDir
    Path dir;

    private final HttpClient http = HttpClient.newHttpClient();
    private final TestAuth auth = new TestAuth(http);

    private ConfigurableApplicationContext start(Path db) {
        return new SpringApplicationBuilder(PlannerApplication.class)
                .run("--planner.datasource.path=" + db, "--server.port=0");
    }

    private static String base(ConfigurableApplicationContext ctx) {
        return "http://127.0.0.1:" + ctx.getEnvironment().getProperty("local.server.port") + "/api/v1";
    }

    private HttpResponse<String> send(String method, String url, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Authorization", auth.bearer(url)).header("Content-Type", "application/json");
        b = json == null ? b.method(method, HttpRequest.BodyPublishers.noBody())
                : b.method(method, HttpRequest.BodyPublishers.ofString(json));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void goalWithTwoKeyResultsAndCheckInsSurvivesRestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String goalId;
        String krA;
        try (ConfigurableApplicationContext ctx = start(db)) {
            String api = base(ctx);
            HttpResponse<String> goal = send("POST", api + "/goals", """
                    {"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21"}""");
            assertThat(goal.statusCode()).isEqualTo(201);
            goalId = JsonPath.read(goal.body(), "$.id");
            krA = JsonPath.read(send("POST", api + "/goals/" + goalId + "/key-results", """
                    {"name":"Recall@5","unit":"%","baseline":20,"target":60}""").body(), "$.id");
            String krB = JsonPath.read(send("POST", api + "/goals/" + goalId + "/key-results", """
                    {"name":"Latency","unit":"ms","baseline":100,"target":60,"weight":3}""").body(), "$.id");
            assertThat(send("POST", api + "/key-results/" + krA + "/check-ins", """
                    {"value":40,"measuredAt":"2026-11-03T12:00:00Z"}""").statusCode()).isEqualTo(201);
            assertThat(send("POST", api + "/key-results/" + krB + "/check-ins", """
                    {"value":80,"measuredAt":"2026-11-03T12:00:00Z"}""").statusCode()).isEqualTo(201);
        }

        try (ConfigurableApplicationContext ctx = start(db)) {
            String api = base(ctx);
            HttpResponse<String> list = send("GET", api + "/goals/" + goalId + "/key-results", null);
            assertThat(list.statusCode()).isEqualTo(200);
            List<String> names = JsonPath.read(list.body(), "$[*].name");
            List<Double> scores = JsonPath.read(list.body(), "$[*].score");
            assertThat(names).containsExactly("Recall@5", "Latency");
            assertThat(scores).containsExactly(50.0, 50.0);
            assertThat(JsonPath.<List<?>>read(send("GET", api + "/key-results/" + krA + "/check-ins", null).body(), "$"))
                    .hasSize(1);
        }
    }
}
