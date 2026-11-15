package com.lifeplanner.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keeps the OpenAPI document complete: every /api/v1 endpoint is documented with a summary and description; every
 * parameter, request body and response has a description; every schema and every field has a description; error
 * responses use the ApiErrorSrv body; public endpoints need no token, all others need the bearer token.
 * A new endpoint or field without documentation fails here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    static final Set<String> PUBLIC = Set.of(
            "GET /api/v1/health", "POST /api/v1/auth/register", "POST /api/v1/auth/login",
            "POST /api/v1/auth/refresh", "POST /api/v1/auth/logout");

    @Autowired
    MockMvc mvc;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    JsonNode doc;

    @BeforeEach
    void load() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        doc = new ObjectMapper().readTree(json);
        // build artifact for review / client generation: target/openapi.json
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/openapi.json"),
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(doc));
    }

    static String blankOr(JsonNode node, String field) {
        return node == null || node.path(field).asText("").isBlank() ? null : node.path(field).asText();
    }

    @Test
    void documentIsOpenApi31WithBearerSecurity() {
        assertThat(doc.path("openapi").asText()).startsWith("3.1");
        assertThat(doc.at("/info/title").asText()).isNotBlank();
        assertThat(doc.at("/info/description").asText()).contains("Authentication").contains("Errors");
        assertThat(doc.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(doc.at("/components/securitySchemes/bearerAuth/bearerFormat").asText()).isEqualTo("JWT");
        assertThat(doc.at("/components/schemas/ApiErrorSrv").isMissingNode()).isFalse();
    }

    @Test
    void everyEndpointIsDocumented() {
        Set<String> implemented = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, method) -> {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/")) {
                    continue;
                }
                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                    implemented.add(m.name() + " " + pattern);
                }
            }
        });
        Set<String> documented = new TreeSet<>();
        doc.path("paths").fields().forEachRemaining(p -> p.getValue().fieldNames()
                .forEachRemaining(m -> documented.add(m.toUpperCase() + " " + p.getKey())));
        assertThat(implemented).hasSizeGreaterThan(40);
        assertThat(documented).containsExactlyInAnyOrderElementsOf(implemented);
    }

    @Test
    void everyOperationParameterBodyAndResponseIsDescribed() {
        List<String> problems = new ArrayList<>();
        doc.path("paths").fields().forEachRemaining(p -> p.getValue().fields().forEachRemaining(m -> {
            String id = m.getKey().toUpperCase() + " " + p.getKey();
            JsonNode op = m.getValue();
            if (blankOr(op, "summary") == null) problems.add(id + ": summary");
            if (blankOr(op, "description") == null) problems.add(id + ": description");
            if (!op.path("tags").isArray() || op.path("tags").isEmpty()) problems.add(id + ": tag");
            for (JsonNode param : op.path("parameters")) {
                if (blankOr(param, "description") == null) problems.add(id + ": parameter " + param.path("name").asText());
            }
            if (op.has("requestBody")) {
                if (blankOr(op.get("requestBody"), "description") == null) problems.add(id + ": requestBody description");
                if (op.at("/requestBody/content/application~1json/schema").isMissingNode()) problems.add(id + ": requestBody schema");
            }
            boolean hasSuccess = false;
            for (Iterator<Map.Entry<String, JsonNode>> it = op.path("responses").fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> r = it.next();
                if (blankOr(r.getValue(), "description") == null) problems.add(id + ": response " + r.getKey());
                if (r.getKey().startsWith("2")) {
                    hasSuccess = true;
                }
                if (r.getKey().matches("[45]\\d\\d") && !r.getValue().at("/content/application~1json/schema/$ref").asText()
                        .endsWith("/ApiErrorSrv")) {
                    problems.add(id + ": error " + r.getKey() + " without ApiErrorSrv body");
                }
            }
            if (!hasSuccess) problems.add(id + ": no 2xx response");
            if (!op.path("responses").has("500")) problems.add(id + ": no 500");
            boolean isPublic = op.has("security") && op.get("security").isEmpty();
            if (PUBLIC.contains(id) != isPublic) problems.add(id + ": security should be " + (PUBLIC.contains(id) ? "none" : "bearer"));
            if (!isPublic && !op.path("responses").has("401")) problems.add(id + ": no 401");
        }));
        assertThat(problems).isEmpty();
    }

    @Test
    void everySchemaAndEveryFieldIsDescribed() {
        List<String> problems = new ArrayList<>();
        doc.path("components").path("schemas").fields().forEachRemaining(s -> {
            JsonNode schema = s.getValue();
            if (blankOr(schema, "description") == null) problems.add(s.getKey());
            schema.path("properties").fields().forEachRemaining(prop -> {
                if (blankOr(prop.getValue(), "description") == null) problems.add(s.getKey() + "." + prop.getKey());
            });
        });
        assertThat(problems).isEmpty();
    }

    @Test
    void enumsAreDocumentedWithTheirWireCodes() {
        JsonNode create = doc.at("/components/schemas/CreateGoalVO/properties");
        assertThat(create.at("/status/enum").toString()).isEqualTo("[\"not_started\",\"doing\",\"done\",\"on_hold\",\"dropped\"]");
        assertThat(create.at("/periodType/enum").toString()).isEqualTo("[\"annual\",\"quarterly\",\"monthly\",\"weekly\"]");
        assertThat(doc.at("/components/schemas/TaskSrv/properties/status/enum").toString())
                .isEqualTo("[\"not_started\",\"doing\",\"done\"]");
        assertThat(doc.toString()).doesNotContain("combinedProgress").doesNotContain("NOT_STARTED");
    }

    @Test
    void swaggerUiIsServedLocallyWithoutToken() throws Exception {
        mvc.perform(get("/swagger-ui/index.html").header("Authorization", "")).andExpect(status().isOk());
    }
}
