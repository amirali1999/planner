package com.lifeplanner.controller;

import com.lifeplanner.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ProbeController {
        record Body(@NotBlank String title) {
        }

        @GetMapping("/probe/not-found")
        void notFound() {
            throw new ApiException.NotFoundException("هدف پیدا نشد.", Map.of("goalId", "x"));
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new ApiException.ConflictException("duplicate_plan_task", "تکراری است.", null);
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail /Users/someone/data/planner.db");
        }

        @GetMapping("/probe/date")
        String date(@RequestParam LocalDate date) {
            return date.toString();
        }

        @PostMapping("/probe/body")
        String body(@Valid @RequestBody Body body) {
            return body.title();
        }
    }

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundKeepsMessageAndDetails() throws Exception {
        mvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message").value("هدف پیدا نشد."))
                .andExpect(jsonPath("$.details.goalId").value("x"));
    }

    @Test
    void conflictUsesSpecificCodeAndOmitsNullDetails() throws Exception {
        mvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("duplicate_plan_task"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void unexpectedErrorIsSanitized() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformedDateIs400() throws Exception {
        mvc.perform(get("/probe/date").param("date", "2026-13-40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.date").value("invalid_value"));
    }

    @Test
    void missingParamIs400() throws Exception {
        mvc.perform(get("/probe/date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.date").value("required"));
    }

    @Test
    void beanValidationFailureListsField() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.details.title").exists());
    }

    @Test
    void malformedJsonIs400WithoutParserDetails() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(content().string(not(containsString("Jackson"))));
    }
}
