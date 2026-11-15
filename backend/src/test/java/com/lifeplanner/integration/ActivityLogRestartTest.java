package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import com.lifeplanner.PlannerApplication;
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

/** An actual activity remains available after a real backend restart. */
class ActivityLogRestartTest {

    @TempDir
    Path dir;

    private final HttpClient http = HttpClient.newHttpClient();
    private final TestAuth auth = new TestAuth(http);

    private ConfigurableApplicationContext start(Path db) {
        return new SpringApplicationBuilder(PlannerApplication.class).run("--planner.datasource.path=" + db, "--server.port=0");
    }

    private static String api(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port") + "/api/v1";
    }

    private HttpResponse<String> send(String method, String url, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Authorization", auth.bearer(url));
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void activitySurvivesRestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String id;
        try (ConfigurableApplicationContext context = start(db)) {
            HttpResponse<String> created = send("POST", api(context) + "/activity-logs", """
                    {"date":"2026-11-14","title":"کار واقعی","category":"work","startMinute":600,"endMinute":690}
                    """);
            assertThat(created.statusCode()).isEqualTo(201);
            id = JsonPath.read(created.body(), "$.id");
            assertThat(send("POST", api(context) + "/activity-timer", """
                    {"title":"تایمر ماندگار","timeZone":"Asia/Tehran"}
                    """).statusCode()).isEqualTo(200);
        }
        try (ConfigurableApplicationContext context = start(db)) {
            HttpResponse<String> listed = send("GET", api(context) + "/activity-logs?date=2026-11-14", null);
            assertThat(listed.statusCode()).isEqualTo(200);
            assertThat((String) JsonPath.read(listed.body(), "$[0].id")).isEqualTo(id);
            assertThat((String) JsonPath.read(listed.body(), "$[0].title")).isEqualTo("کار واقعی");
            assertThat((String) JsonPath.read(send("GET", api(context) + "/activity-timer", null).body(), "$.title"))
                    .isEqualTo("تایمر ماندگار");
        }
    }
}
