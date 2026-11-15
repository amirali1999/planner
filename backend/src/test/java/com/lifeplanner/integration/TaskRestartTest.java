package com.lifeplanner.integration;

import com.lifeplanner.PlannerApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Done status of standalone and goal-linked tasks survives a real server restart; one row each. */
class TaskRestartTest {

    @TempDir
    Path dir;

    private final HttpClient http = HttpClient.newHttpClient();
    private final TestAuth auth = new TestAuth(http);

    private ConfigurableApplicationContext start(Path db) {
        return new SpringApplicationBuilder(PlannerApplication.class).run("--planner.datasource.path=" + db, "--server.port=0");
    }

    private static String api(ConfigurableApplicationContext ctx) {
        return "http://127.0.0.1:" + ctx.getEnvironment().getProperty("local.server.port") + "/api/v1";
    }

    private HttpResponse<String> send(String method, String url, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Authorization", auth.bearer(url)).header("Content-Type", "application/json");
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void doneSurvivesRestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String standalone;
        String linked;
        try (ConfigurableApplicationContext ctx = start(db)) {
            String api = api(ctx);
            String goalId = JsonPath.read(send("POST", api + "/goals", """
                    {"title":"g","periodType":"weekly","periodStart":"2026-10-31","periodEnd":"2026-11-06"}""").body(), "$.id");
            standalone = JsonPath.read(send("POST", api + "/tasks", """
                    {"title":"standalone"}""").body(), "$.id");
            linked = JsonPath.read(send("POST", api + "/tasks", """
                    {"title":"linked","goalId":"%s"}""".formatted(goalId)).body(), "$.id");
            assertThat(send("PATCH", api + "/tasks/" + standalone, """
                    {"status":"done"}""").statusCode()).isEqualTo(200);
            assertThat(send("PATCH", api + "/tasks/" + linked, """
                    {"status":"done"}""").statusCode()).isEqualTo(200);
        }
        try (ConfigurableApplicationContext ctx = start(db)) {
            String api = api(ctx);
            assertThat((String) JsonPath.read(send("GET", api + "/tasks/" + standalone, null).body(), "$.status")).isEqualTo("done");
            assertThat((String) JsonPath.read(send("GET", api + "/tasks/" + linked, null).body(), "$.status")).isEqualTo("done");
            assertThat(ctx.getBean(JdbcClient.class).sql("SELECT count(*) FROM tasks").query(Integer.class).single()).isEqualTo(2);
        }
    }
}
