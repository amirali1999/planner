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

/** The signing key and refresh tokens live in the database: tokens issued before a restart still work after it. */
class AuthRestartTest {

    @TempDir
    Path dir;

    private final HttpClient http = HttpClient.newHttpClient();

    private ConfigurableApplicationContext start(Path db) {
        return new SpringApplicationBuilder(PlannerApplication.class).run("--planner.datasource.path=" + db, "--server.port=0");
    }

    private static String api(ConfigurableApplicationContext ctx) {
        return "http://127.0.0.1:" + ctx.getEnvironment().getProperty("local.server.port") + "/api/v1";
    }

    private HttpResponse<String> send(String method, String url, String json, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        if (headers.length > 0) {
            b.headers(headers);
        }
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String refreshCookie(HttpResponse<String> r) {
        String h = r.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith("planner_refresh=")).findFirst().orElseThrow();
        return h.substring(0, h.indexOf(';'));
    }

    @Test
    void accessAndRefreshTokensSurviveARestart() throws Exception {
        Path db = dir.resolve("planner.db");
        String access;
        String cookie;
        String goalId;
        try (ConfigurableApplicationContext ctx = start(db)) {
            HttpResponse<String> reg = send("POST", api(ctx) + "/auth/register", "{\"email\":\"r@test.local\",\"password\":\"password-123\"}");
            assertThat(reg.statusCode()).isEqualTo(201);
            access = JsonPath.read(reg.body(), "$.accessToken");
            cookie = refreshCookie(reg);
            goalId = JsonPath.read(send("POST", api(ctx) + "/goals", """
                    {"title":"g","periodType":"weekly","periodStart":"2026-10-31","periodEnd":"2026-11-06"}""",
                    "Authorization", "Bearer " + access).body(), "$.id");
        }
        try (ConfigurableApplicationContext ctx = start(db)) {
            assertThat(send("GET", api(ctx) + "/goals/" + goalId, null, "Authorization", "Bearer " + access).statusCode()).isEqualTo(200);
            HttpResponse<String> refreshed = send("POST", api(ctx) + "/auth/refresh", null, "Cookie", cookie);
            assertThat(refreshed.statusCode()).isEqualTo(200);
            String newAccess = JsonPath.read(refreshed.body(), "$.accessToken");
            assertThat(send("GET", api(ctx) + "/goals", null, "Authorization", "Bearer " + newAccess).body()).contains(goalId);
            assertThat(send("GET", api(ctx) + "/goals", null).statusCode()).isEqualTo(401);
        }
    }
}
