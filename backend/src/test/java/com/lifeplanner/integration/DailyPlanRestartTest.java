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

/** After a real restart, the same date yields exactly the same plan, item order and task statuses. */
class DailyPlanRestartTest {

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

    private String send(String method, String url, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Authorization", auth.bearer(url)).header("Content-Type", "application/json");
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).as(method + " " + url + " → " + r.body()).isLessThan(300);
        return r.body();
    }

    @Test
    void planAndStatusesSurviveRestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String date = "2026-11-03";
        String planBefore;
        try (ConfigurableApplicationContext ctx = start(db)) {
            String api = api(ctx);
            String a = JsonPath.read(send("POST", api + "/tasks", "{\"title\":\"a\"}"), "$.id");
            String b = JsonPath.read(send("POST", api + "/tasks", "{\"title\":\"b\"}"), "$.id");
            send("POST", api + "/plans/daily/" + date + "/items", "{\"taskId\":\"" + a + "\"}");
            send("POST", api + "/plans/daily/" + date + "/items", "{\"taskId\":\"" + b + "\"}");
            send("PATCH", api + "/tasks/" + b, "{\"status\":\"done\"}");
            planBefore = send("GET", api + "/plans/daily?date=" + date, null);
        }
        try (ConfigurableApplicationContext ctx = start(db)) {
            String planAfter = send("GET", api(ctx) + "/plans/daily?date=" + date, null);
            assertThat(planAfter).isEqualTo(planBefore);
            assertThat(JsonPath.<java.util.List<String>>read(planAfter, "$.items[*].task.status")).containsExactly("not_started", "done");
            assertThat((Double) JsonPath.read(planAfter, "$.completionPercent")).isEqualTo(50.0);
        }
    }
}
