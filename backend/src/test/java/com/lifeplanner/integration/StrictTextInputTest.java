package com.lifeplanner.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Text fields never silently accept numbers or booleans (e.g. "title": 123). */
@SpringBootTest
@AutoConfigureMockMvc
class StrictTextInputTest {

    @Autowired
    MockMvc mvc;

    @Test
    void numbersAndBooleansAreNotCoercedIntoText() throws Exception {
        String[][] cases = {
                {"/api/v1/tasks", "{\"title\":123}", "title"},
                {"/api/v1/tasks", "{\"title\":true}", "title"},
                {"/api/v1/focus-items", "{\"title\":1.5,\"kind\":\"book\"}", "title"},
                {"/api/v1/inbox-items", "{\"rawText\":123}", "rawText"},
                {"/api/v1/goals", "{\"title\":7,\"periodType\":\"weekly\",\"periodStart\":\"2026-10-31\",\"periodEnd\":\"2026-11-06\"}", "title"},
        };
        for (String[] c : cases) {
            mvc.perform(post(c[0]).contentType(MediaType.APPLICATION_JSON).content(c[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation_error"))
                    .andExpect(jsonPath("$.details." + c[2]).value("invalid_value"));
        }
    }
}
