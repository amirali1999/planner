package com.lifeplanner.e2e;

import com.lifeplanner.PlannerApplication;
import com.lifeplanner.integration.TestAuth;
import com.lifeplanner.db.MigrationCount;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demo 0 release gate: the full Goal → multi-KR → check-in → Task → Today → Done → Dashboard path over real
 * HTTP against a real server and an isolated SQLite file, then a restart. Also checks no-write GETs (file bytes),
 * null vs zero, the error contract, migrations, FK and integrity checks, and loopback binding.
 */
class DemoWorkflowE2ETest {

    static final String DAY = "2026-11-03";         // Tuesday 12 Aban 1405
    static final String EMPTY_DAY = "2026-11-10";

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

    private HttpResponse<String> call(String method, String path, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api + path)).header("Authorization", auth.bearer(api + path));
        if (json != null) {
            b.header("Content-Type", "application/json");
        }
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.body()).as("no blended progress anywhere").doesNotContainIgnoringCase("combined");
        return response;
    }

    private String ok(String method, String path, String json) throws Exception {
        HttpResponse<String> r = call(method, path, json);
        assertThat(r.statusCode()).as(method + " " + path + " → " + r.body()).isBetween(200, 201);
        return r.body();
    }

    private static String id(String body) {
        return JsonPath.read(body, "$.id");
    }

    private static Object at(String body, String path) {
        return JsonPath.read(body, path);
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    @Test
    void demoZeroWorkflowSurvivesRestart() throws Exception {
        Path db = dir.resolve("data/planner.db");
        Map<String, String> snapshot = new LinkedHashMap<>();
        List<String> readPaths = new ArrayList<>();
        String goal;
        String krA;

        try (ConfigurableApplicationContext ctx = start(db)) {
            InetAddress bound = ctx.getBean(org.springframework.boot.autoconfigure.web.ServerProperties.class).getAddress();
            assertThat(bound.isLoopbackAddress()).as("loopback only").isTrue();
            assertThat(ok("GET", "/health", null)).isEqualTo("{\"status\":\"UP\",\"apiVersion\":\"v1\"}");

            // Goal with two weighted KRs (one increasing, one decreasing)
            goal = id(ok("POST", "/goals", """
                    {"title":"یادگیری RAG","periodType":"quarterly","periodStart":"2026-09-23","periodEnd":"2026-12-21","deadline":"2026-12-21","status":"doing"}"""));
            String unmeasured = id(ok("POST", "/goals", """
                    {"title":"هدف بدون سنجه","periodType":"monthly","periodStart":"2026-10-23","periodEnd":"2026-11-21"}"""));
            krA = id(ok("POST", "/goals/" + goal + "/key-results", """
                    {"name":"Recall@5","unit":"%","baseline":20,"target":60,"weight":1}"""));
            String krB = id(ok("POST", "/goals/" + goal + "/key-results", """
                    {"name":"Latency","unit":"ms","baseline":100,"target":60,"weight":3}"""));
            assertThat(at(ok("GET", "/key-results/" + krA, null), "$.score")).isEqualTo(0.0); // baseline = explicit 0%

            // Append-only check-ins: a back-dated entry does not become current
            ok("POST", "/key-results/" + krA + "/check-ins", """
                    {"value":40,"measuredAt":"2026-11-03T08:00:00Z","note":"evaluation set v1"}""");
            ok("POST", "/key-results/" + krA + "/check-ins", """
                    {"value":25,"measuredAt":"2026-11-01T08:00:00Z","note":"late entry of an older measurement"}""");
            ok("POST", "/key-results/" + krB + "/check-ins", """
                    {"value":70,"measuredAt":"2026-11-03T09:00:00Z"}""");
            assertThat(at(ok("GET", "/key-results/" + krA, null), "$.current")).isEqualTo(40);
            assertThat(at(ok("GET", "/key-results/" + krA, null), "$.score")).isEqualTo(50.0);
            assertThat(at(ok("GET", "/key-results/" + krB, null), "$.score")).isEqualTo(75.0);
            assertThat(call("DELETE", "/key-results/" + krA + "/check-ins", null).statusCode()).isEqualTo(405);

            // Tasks: four linked to the goal, one standalone
            List<String> linked = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                linked.add(id(ok("POST", "/tasks", """
                        {"title":"کار %d","goalId":"%s","estimatedMinutes":%d}""".formatted(i, goal, i == 4 ? 0 : 30))));
            }
            String standalone = id(ok("POST", "/tasks", """
                    {"title":"خرید کتاب","dueDate":"2026-11-05"}"""));

            // Today plan: three existing tasks; repeat add rejected
            assertThat(at(ok("GET", "/plans/daily?date=" + DAY, null), "$.exists")).isEqualTo(false);
            for (String t : List.of(linked.get(0), linked.get(3), standalone)) {
                ok("POST", "/plans/daily/" + DAY + "/items", "{\"taskId\":\"" + t + "\"}");
            }
            HttpResponse<String> dup = call("POST", "/plans/daily/" + DAY + "/items", "{\"taskId\":\"" + standalone + "\"}");
            assertThat(dup.statusCode()).isEqualTo(409);
            assertThat(at(dup.body(), "$.code")).isEqualTo("duplicate_plan_task");

            // Before anything is done: measured zeros, not nulls
            String progressBefore = ok("GET", "/goals/" + goal + "/progress", null);
            assertThat(at(progressBefore, "$.outcomeProgress")).isEqualTo(68.75); // (50×1 + 75×3) / 4
            assertThat(at(progressBefore, "$.executionProgress")).isEqualTo(0.0);
            assertThat(at(ok("GET", "/dashboard/today?date=" + DAY, null), "$.today.completionPercent")).isEqualTo(0.0);

            // Done: three linked tasks (two via Tasks, one via the same Task API from Today) + the standalone one
            for (String t : List.of(linked.get(0), linked.get(1), linked.get(2), standalone)) {
                ok("PATCH", "/tasks/" + t, "{\"status\":\"done\"}");
            }

            String progress = ok("GET", "/goals/" + goal + "/progress", null);
            assertThat(at(progress, "$.outcomeProgress")).isEqualTo(68.75);
            assertThat(at(progress, "$.outcomeSource")).isEqualTo("own_key_results");
            assertThat(at(progress, "$.keyResultsCount")).isEqualTo(2);
            assertThat(at(progress, "$.executionProgress")).isEqualTo(75.0);
            assertThat(at(progress, "$.activeTaskCount")).isEqualTo(4);
            assertThat(at(progress, "$.doneTaskCount")).isEqualTo(3);

            String unmeasuredProgress = ok("GET", "/goals/" + unmeasured + "/progress", null);
            assertThat((Object) at(unmeasuredProgress, "$.outcomeProgress")).isNull();
            assertThat(at(unmeasuredProgress, "$.outcomeSource")).isEqualTo("not_measured");
            assertThat((Object) at(unmeasuredProgress, "$.executionProgress")).isNull();

            String dashboard = ok("GET", "/dashboard/today?date=" + DAY, null);
            assertThat(at(dashboard, "$.today.plannedCount")).isEqualTo(3);
            assertThat(at(dashboard, "$.today.doneTaskCount")).isEqualTo(2);
            assertThat(at(dashboard, "$.today.completionPercent")).isEqualTo(66.67);
            assertThat(JsonPath.<List<String>>read(dashboard, "$.goals[*].goalId")).containsExactly(goal, unmeasured);
            assertThat(at(dashboard, "$.goals[0].outcomeProgress")).isEqualTo(68.75);
            assertThat(at(dashboard, "$.goals[0].executionProgress")).isEqualTo(75.0);
            assertThat((Object) at(dashboard, "$.goals[1].outcomeProgress")).isNull();
            assertThat((Object) at(dashboard, "$.goals[1].executionProgress")).isNull();
            assertThat((Object) at(ok("GET", "/dashboard/today?date=" + EMPTY_DAY, null), "$.today.completionPercent")).isNull();
            assertThat(at(ok("GET", "/goals/" + goal, null), "$.status")).isEqualTo("doing"); // never auto-changed

            // Error contract: sanitized {code, message, details?}
            HttpResponse<String> missing = call("GET", "/tasks/00000000-0000-0000-0000-000000000000", null);
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(JsonPath.<Map<String, Object>>read(missing.body(), "$").keySet()).containsOnly("code", "message", "details");
            HttpResponse<String> invalid = call("POST", "/goals/" + goal + "/key-results", """
                    {"name":"x","baseline":5,"target":5}""");
            assertThat(invalid.statusCode()).isEqualTo(400);
            assertThat(at(invalid.body(), "$.details.target")).isEqualTo("equals_baseline");
            assertThat(call("GET", "/nope", null).body()).doesNotContain("trace").doesNotContain("Exception");

            // Every read endpoint, recorded for the restart comparison and the no-write check
            readPaths.addAll(List.of("/goals", "/goals/" + goal, "/goals/" + goal + "/key-results",
                    "/goals/" + goal + "/key-results?includeArchived=true", "/key-results/" + krA,
                    "/key-results/" + krA + "/check-ins", "/key-results/" + krB, "/tasks", "/tasks?archived=all",
                    "/tasks/" + standalone, "/plans/daily?date=" + DAY, "/plans/daily?date=" + EMPTY_DAY,
                    "/goals/" + goal + "/progress", "/goals/" + unmeasured + "/progress",
                    "/dashboard/today?date=" + DAY, "/dashboard/today?date=" + EMPTY_DAY, "/health"));
            String before = sha256(db);
            for (String path : readPaths) {
                snapshot.put(path, ok("GET", path, null));
            }
            assertThat(sha256(db)).as("GET endpoints must not write").isEqualTo(before);
            // Sensitivity check: a real write (unrelated date) does change the file, so the check above is meaningful.
            ok("PUT", "/plans/daily/2026-12-01", null);
            assertThat(sha256(db)).isNotEqualTo(before);
        }

        try (ConfigurableApplicationContext ctx = start(db)) {
            for (Map.Entry<String, String> e : snapshot.entrySet()) {
                assertThat(ok("GET", e.getKey(), null)).as("after restart: " + e.getKey()).isEqualTo(e.getValue());
            }
            JdbcClient jdbc = ctx.getBean(JdbcClient.class);
            assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL")
                    .query(Integer.class).single()).as("migrations applied exactly once").isEqualTo(MigrationCount.APPLIED);
            assertThat(jdbc.sql("PRAGMA foreign_key_check").query((rs, n) -> rs.getString(1)).list()).isEmpty();
            assertThat(jdbc.sql("PRAGMA integrity_check").query(String.class).single()).isEqualTo("ok");
            assertThat(jdbc.sql("SELECT count(*) FROM key_result_checkins WHERE key_result_id = ?").param(krA)
                    .query(Integer.class).single()).isEqualTo(2);
            assertThat(jdbc.sql("SELECT count(*) FROM tasks").query(Integer.class).single()).isEqualTo(5);
            assertThat(jdbc.sql("SELECT local_date FROM daily_plans ORDER BY local_date").query(String.class).list())
                    .containsExactly(DAY, "2026-12-01");
        }
    }
}
