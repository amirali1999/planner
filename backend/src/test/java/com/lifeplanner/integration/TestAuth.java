package com.lifeplanner.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real-HTTP tests: an {@code Authorization} header for the test account on the server behind {@code url}. Registers the
 * account on first use and logs in afterwards (e.g. after a restart, with the same database).
 */
public final class TestAuth {

    private final HttpClient http;
    private final Map<String, String> byServer = new ConcurrentHashMap<>();

    public TestAuth(HttpClient http) {
        this.http = http;
    }

    public static final String EMAIL = "owner@test.local";
    public static final String PASSWORD = "test-password-1";
    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    /** Per test instance (never shared between servers that might reuse a port with another database). */
    public String bearer(String url) {
        String base = url.substring(0, url.indexOf("/api/v1") + "/api/v1".length());
        return byServer.computeIfAbsent(base, b -> "Bearer " + obtain(http, b));
    }

    private static String obtain(HttpClient http, String base) {
        try {
            String body = "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
            HttpResponse<String> r = post(http, base + "/auth/register", body);
            if (r.statusCode() == 409) {
                r = post(http, base + "/auth/login", body);
            }
            Matcher m = TOKEN.matcher(r.body());
            if (r.statusCode() / 100 != 2 || !m.find()) {
                throw new IllegalStateException("test login failed: " + r.statusCode() + " " + r.body());
            }
            return m.group(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpResponse<String> post(HttpClient http, String url, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
