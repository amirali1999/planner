package com.lifeplanner.integration;

import com.lifeplanner.PlannerApplication;
import com.lifeplanner.db.MigrationCount;
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

/** Edits, parent links and archive state survive a real restart (V1 → V2 migrations applied once). */
class GoalEditRestartTest {

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
    void editsSurviveRestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String annual;
        String monthly;
        String before;
        try (ConfigurableApplicationContext ignored = start(db)) {
            annual = JsonPath.read(send("POST", "/goals", """
                    {"title":"سالانه","periodType":"annual","periodStart":"2026-03-21","periodEnd":"2027-03-20"}"""), "$.id");
            monthly = JsonPath.read(send("POST", "/goals", """
                    {"title":"ماهانه","periodType":"monthly","periodStart":"2026-10-23","periodEnd":"2026-11-21"}"""), "$.id");
            send("PATCH", "/goals/" + monthly, """
                    {"title":"آبان","status":"doing","parentGoalId":"%s","deadline":"2026-11-20"}""".formatted(annual));
            send("POST", "/goals/" + annual + "/archive", null);
            before = send("GET", "/goals/" + monthly, null);
        }
        try (ConfigurableApplicationContext ctx = start(db)) {
            assertThat(send("GET", "/goals/" + monthly, null)).isEqualTo(before);
            assertThat((String) JsonPath.read(send("GET", "/goals/" + annual, null), "$.archivedAt")).isNotNull();
            assertThat(ctx.getBean(org.springframework.jdbc.core.simple.JdbcClient.class)
                    .sql("SELECT count(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL")
                    .query(Integer.class).single()).isEqualTo(MigrationCount.APPLIED);
        }
    }
}
