package com.lifeplanner.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corrupt data (a parent cycle written outside the API) must neither hang the tree nor drop goals.
 * Own database so the corrupt rows cannot affect other tests.
 */
@SpringBootTest(properties = "planner.datasource.path=target/test-db/cycle-${random.uuid}.db")
@AutoConfigureMockMvc
class GoalTreeCycleSafetyTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    @Test
    void cycleInDataTerminatesAndShowsEachGoalExactlyOnce() throws Exception {
        String sql = """
                INSERT INTO goals (id, title, period_type, period_start, period_end, parent_goal_id, created_at, updated_at)
                VALUES (?, ?, 'monthly', '2026-10-23', '2026-11-21', NULL, '2026-11-03T12:00:00.000Z', '2026-11-03T12:00:00.000Z')""";
        String a = "aaaaaaaa-0000-0000-0000-000000000000";
        String b = "bbbbbbbb-0000-0000-0000-000000000000";
        db.sql(sql).params(a, "a").update();
        db.sql(sql).params(b, "b").update();
        db.sql("UPDATE goals SET parent_goal_id = ? WHERE id = ?").params(b, a).update();
        db.sql("UPDATE goals SET parent_goal_id = ? WHERE id = ?").params(a, b).update();

        String body = mvc.perform(get("/api/v1/goals/tree")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> allIds = JsonPath.read(body, "$..id");
        assertThat(allIds).containsExactlyInAnyOrder(a, b);
        assertThat((Integer) JsonPath.read(body, "$.goalCount")).isEqualTo(2);
    }
}
