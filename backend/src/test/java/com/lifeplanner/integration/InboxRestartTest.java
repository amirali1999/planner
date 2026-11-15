package com.lifeplanner.integration;

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

/** A captured note (including markup and newlines) is returned identically after a real restart. */
class InboxRestartTest {

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
    void capturedNotesSurviveRestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String before;
        try (ConfigurableApplicationContext ignored = start(db)) {
            send("POST", "/inbox-items", "{\"rawText\":\"ایده: <b>نوشتن</b> پست درباره RAG\\nخط دوم\"}");
            send("POST", "/inbox-items", "{\"rawText\":\"تماس با استاد\"}");
            before = send("GET", "/inbox-items", null);
        }
        try (ConfigurableApplicationContext ignored = start(db)) {
            assertThat(send("GET", "/inbox-items", null)).isEqualTo(before).contains("<b>نوشتن</b>").contains("\\n");
        }
    }
}
