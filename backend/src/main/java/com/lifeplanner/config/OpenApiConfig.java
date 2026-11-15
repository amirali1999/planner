package com.lifeplanner.config;

import com.lifeplanner.model.srv.ApiErrorSrv;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.1 description of the v1 API ({@code /v3/api-docs}) and Swagger UI ({@code /swagger-ui.html}), both served
 * by this backend (no CDN). Operation/field documentation lives on the controllers and on the VO/Srv records; this
 * class adds what is common to every operation: security scheme, the error body, and the standard error responses.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    static final String ERROR_SCHEMA = "ApiErrorSrv";

    private static final String API_DESCRIPTION = """
            REST API of the **Personal Life Planner** (single local backend, multi-user, Persian UI).

            ## Conventions
            * **Prefix** `/api/v1`, JSON in **camelCase**, IDs are UUID strings.
            * **Date-only** fields are Gregorian ISO `YYYY-MM-DD` (never shifted by a time zone; Jalali is a UI concern).
              **Timestamps** are UTC ISO-8601 (`2026-11-09T08:30:00.000Z`).
            * **Decimals** are JSON numbers, kept exact (no float rounding); progress values are rounded to 2 decimals.
            * **Strict input**: unknown JSON fields, wrong JSON types (e.g. `"title": 123`, `"weight": "2"`), malformed dates
              and unknown enum codes are rejected with `400`.
            * **PATCH** bodies are partial: a field that is **absent** stays unchanged, an explicit **`null`** clears it
              (where the field is optional), a value sets it.
            * **Archive first**: goals, KRs, tasks, focus items and milestones are archived, not deleted; check-ins and history
              are append-only. Only from the archive can a goal, focus item or task be deleted permanently
              (`DELETE`, 409 `not_archived` for active ones). `GET` requests never write.
            * **Outcome progress** (from Key Results) and **Execution progress** (from tasks) are always reported separately
              and are never averaged into one number. `null` means *not measured*, which is different from `0`.

            ## Errors
            Every non-2xx response has the body `{code, message, details?}` (schema `ApiErrorSrv`): `code` is a stable English
            machine code, `message` a Persian user-facing text, `details` maps field names to field-level codes
            (`required`, `invalid_value`, `too_long`, …). Status codes: `400` validation, `401` authentication,
            `404` unknown **or another user's** resource, `409` conflict (code says which), `429` too many login attempts,
            `500` unexpected (no stack traces).

            ## Authentication
            1. `POST /api/v1/auth/register` or `POST /api/v1/auth/login` → JSON with `accessToken` + an httpOnly
               **refresh cookie** (`planner_refresh`, `SameSite=Strict`, path `/api/v1/auth`).
            2. Call every other endpoint with `Authorization: Bearer <accessToken>` (RFC 6750). In Swagger UI use
               **Authorize** and paste the token (without the `Bearer ` prefix).
            3. On `401` call `POST /api/v1/auth/refresh` (the browser sends the cookie) to get a new access token; the cookie
               is rotated on every refresh. `POST /api/v1/auth/logout` ends the session.

            Access tokens are JWTs (HS256, `typ: at+jwt`, RFC 7519/9068). Each user only ever sees their own data.
            """;

    @Bean
    OpenAPI plannerOpenApi(PlannerProperties properties) {
        Components components = new Components()
                .addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token from /auth/login, /auth/register or /auth/refresh (JWT, `typ: at+jwt`)."))
                .addSecuritySchemes("refreshCookie", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name(properties.auth().cookieName())
                        .description("httpOnly refresh-token cookie set by the server; only used by /auth/refresh and /auth/logout."));
        return new OpenAPI()
                .info(new Info().title("Personal Life Planner API").version("v1").description(API_DESCRIPTION))
                .servers(List.of(new Server().url("/").description("This backend (loopback only)")))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .tags(List.of(
                        tag("Authentication", "Accounts and tokens: register, login, refresh, logout, current user."),
                        tag("Goals", "Goals (annual/quarterly/monthly/weekly) with hierarchy, status, deadline, archive, filters and paging."),
                        tag("Progress & tree", "Read-only views: goal tree, Outcome/Execution progress (single and batch)."),
                        tag("Key results", "Measurable Key Results of a goal, recalibration with audit history, append-only check-ins."),
                        tag("Milestones", "Delivery checkpoints of a goal (not KRs; never affect progress)."),
                        tag("Tasks", "Tasks with three statuses, optional direct goal and focus item (effective goal attribution)."),
                        tag("Focus items", "Projects, books, courses, areas the user works on; optionally linked to one goal."),
                        tag("Inbox", "Quick capture of raw notes and their explicit triage into task/goal/focus item/reference/someday/discard."),
                        tag("Daily plans", "Which existing tasks are planned for a calendar date."),
                        tag("Weekly plans", "Tasks put on a Saturday–Friday week without a day (membership only; no capacity or commitments)."),
                        tag("Time blocks", "Weekly time-box: blocks of planned time on a local day, optionally linked to a task (never change the task)."),
                        tag("Focus item notes", "Free-text notes kept with a focus item (not tasks)."),
                        tag("Habits", "Daily habits and the local days they were done (tick/untick per day)."),
                        tag("Dashboard", "Today summary and the active goals with their separate Outcome/Execution."),
                        tag("System", "Health check.")));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    /**
     * Standard error responses on every operation, so each controller only declares its operation-specific ones:
     * 400 when the operation takes input, 401 when it needs a token, 500 always; every error response gets the
     * {@code ApiErrorSrv} body. Also documents the RFC 6750 challenge on 401 and {@code Retry-After} on 429.
     */
    @Bean
    OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            // registered here (after springdoc dropped unreferenced schemas) because only this customizer refers to it
            ModelConverters.getInstance().read(ApiErrorSrv.class).forEach(openApi.getComponents()::addSchemas);
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> addErrorResponses(op)));
        };
    }

    private static void addErrorResponses(io.swagger.v3.oas.models.Operation op) {
        {
            ApiResponses responses = op.getResponses();
            boolean secured = op.getSecurity() == null || !op.getSecurity().isEmpty();
            // a cookie read by the server (refresh/logout) is not client input that can fail validation
            boolean takesInput = op.getRequestBody() != null || (op.getParameters() != null
                    && op.getParameters().stream().anyMatch(p -> !"cookie".equals(p.getIn())));
            if (takesInput) {
                responses.putIfAbsent("400", new ApiResponse().description(
                        "Invalid input: malformed JSON, unknown field, wrong JSON type, invalid date/enum/number or a "
                                + "violated rule. `code=validation_error`, `details` names the fields."));
            }
            if (secured) {
                responses.putIfAbsent("401", new ApiResponse().description(
                        "Missing, malformed, forged or expired access token (`code=unauthorized` or `invalid_token`)."));
                responses.get("401").addHeaderObject("WWW-Authenticate", new Header()
                        .description("RFC 6750 challenge, e.g. `Bearer error=\"invalid_token\", …`")
                        .schema(new StringSchema()));
            }
            responses.putIfAbsent("500", new ApiResponse().description("Unexpected server error (`code=internal_error`); no internals exposed."));
            if (responses.containsKey("429")) {
                responses.get("429").addHeaderObject("Retry-After", new Header()
                        .description("Seconds until another login attempt is allowed.").schema(new StringSchema()));
            }
            responses.forEach((code, response) -> {
                if (code.startsWith("4") || code.startsWith("5")) {
                    response.setContent(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
                }
            });
        }
    }
}
