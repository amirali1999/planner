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

/** A standalone and a goal-linked Focus Item survive a real restart; no goal is created implicitly. */
class FocusItemRestartTest {

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
    void focusItemsSurviveRestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String before;
        try (ConfigurableApplicationContext ignored = start(db)) {
            String goalId = JsonPath.read(send("POST", "/goals", """
                    {"title":"g","periodType":"monthly","periodStart":"2026-10-23","periodEnd":"2026-11-21"}"""), "$.id");
            send("POST", "/focus-items", "{\"title\":\"کتاب مستقل\",\"kind\":\"book\"}");
            send("POST", "/focus-items", "{\"title\":\"پروژه مرتبط\",\"kind\":\"project\",\"goalId\":\"" + goalId + "\"}");
            before = send("GET", "/focus-items", null);
        }
        try (ConfigurableApplicationContext ctx = start(db)) {
            assertThat(send("GET", "/focus-items", null)).isEqualTo(before);
            assertThat(ctx.getBean(JdbcClient.class).sql("SELECT count(*) FROM goals").query(Integer.class).single()).isEqualTo(1);
        }
    }
}
