package com.lifeplanner.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** G-04: GET /goals/tree — independent roots, nesting, stable order, archive filters, per-node progress. */
@SpringBootTest
@AutoConfigureMockMvc
class GoalTreeIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient db;

    final ObjectMapper json = new ObjectMapper();

    String create(String title, String type, String start, String end, String parent) throws Exception {
        String parentPart = parent == null ? "" : ",\"parentGoalId\":\"" + parent + "\"";
        return JsonPath.read(mvc.perform(post("/api/v1/goals").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"%s","periodType":"%s","periodStart":"%s","periodEnd":"%s"%s}""".formatted(title, type, start, end, parentPart)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    JsonNode tree(String archived) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/goals/tree").param("archived", archived))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    static JsonNode find(JsonNode nodes, String id) {
        for (JsonNode n : nodes) {
            if (n.get("id").asText().equals(id)) {
                return n;
            }
            JsonNode inChild = find(n.get("children"), id);
            if (inChild != null) {
                return inChild;
            }
        }
        return null;
    }

    static List<String> ids(JsonNode nodes) {
        List<String> out = new ArrayList<>();
        nodes.forEach(n -> out.add(n.get("id").asText()));
        return out;
    }

    static void collect(JsonNode nodes, List<String> out) {
        nodes.forEach(n -> {
            out.add(n.get("id").asText());
            collect(n.get("children"), out);
        });
    }

    @Test
    void standaloneMonthlyGoalIsARootWithUnmeasuredProgress() throws Exception {
        String m = create("ماهانه مستقل", "monthly", "2026-10-23", "2026-11-21", null);
        JsonNode t = tree("false");
        assertThat(ids(t.get("roots"))).contains(m);
        JsonNode node = find(t.get("roots"), m);
        assertThat(node.get("detachedFromParent").asBoolean()).isFalse();
        assertThat(node.get("children")).isEmpty();
        assertThat(node.get("outcomeProgress").isNull()).isTrue();
        assertThat(node.get("outcomeSource").asText()).isEqualTo("not_measured");
        assertThat(node.get("executionProgress").isNull()).isTrue();
    }

    @Test
    void multiLevelAndSkippedLevelTreesAreNavigable() throws Exception {
        String a = create("سال", "annual", "2026-03-21", "2027-03-20", null);
        String q = create("فصل سوم", "quarterly", "2026-09-23", "2026-12-21", a);
        String m = create("آبان", "monthly", "2026-10-23", "2026-11-21", q);
        String w = create("هفته", "weekly", "2026-10-31", "2026-11-06", m);
        String skip = create("آذر (مستقیم زیر سال)", "monthly", "2026-11-22", "2026-12-21", a);
        JsonNode roots = tree("false").get("roots");
        JsonNode annual = find(roots, a);
        assertThat(ids(roots)).contains(a).doesNotContain(q, m, w, skip);
        assertThat(ids(annual.get("children"))).containsExactly(q, skip); // q starts earlier
        assertThat(ids(find(roots, q).get("children"))).containsExactly(m);
        assertThat(ids(find(roots, m).get("children"))).containsExactly(w);
    }

    @Test
    void everyGoalAppearsExactlyOnce() throws Exception {
        create("x", "annual", "2026-03-21", "2027-03-20", null);
        JsonNode t = tree("all");
        List<String> all = new ArrayList<>();
        collect(t.get("roots"), all);
        int total = db.sql("SELECT count(*) FROM goals").query(Integer.class).single();
        assertThat(all).hasSize(total).doesNotHaveDuplicates();
        assertThat(t.get("goalCount").asInt()).isEqualTo(total);
    }

    @Test
    void siblingsAreOrderedByStartThenCoarserPeriodThenCreation() throws Exception {
        String parent = create("والد", "annual", "2030-03-21", "2031-03-20", null);
        String late = create("دیرتر", "monthly", "2030-05-22", "2030-06-21", parent);
        String monthSameStart = create("ماه", "monthly", "2030-03-21", "2030-04-20", parent);
        String quarterSameStart = create("فصل", "quarterly", "2030-03-21", "2030-06-21", parent);
        String week = create("هفته", "weekly", "2030-03-23", "2030-03-29", parent);
        assertThat(ids(find(tree("false").get("roots"), parent).get("children")))
                .containsExactly(quarterSameStart, monthSameStart, week, late);
        // stable: repeated reads give the same order
        assertThat(tree("false").toString()).isEqualTo(tree("false").toString());
    }

    @Test
    void archiveFiltersKeepActiveChildrenVisibleAsDetachedRoots() throws Exception {
        String parent = create("والد بایگانی", "annual", "2026-03-21", "2027-03-20", null);
        String child = create("فرزند فعال", "monthly", "2026-10-23", "2026-11-21", parent);
        mvc.perform(post("/api/v1/goals/" + parent + "/archive")).andExpect(status().isOk());

        JsonNode active = tree("false").get("roots");
        assertThat(find(active, parent)).isNull();
        assertThat(ids(active)).contains(child);
        assertThat(find(active, child).get("detachedFromParent").asBoolean()).isTrue();
        assertThat(find(active, child).get("parentGoalId").asText()).isEqualTo(parent);

        JsonNode archived = tree("true").get("roots");
        assertThat(ids(archived)).contains(parent);
        assertThat(find(archived, child)).isNull();

        JsonNode all = tree("all").get("roots");
        assertThat(ids(find(all, parent).get("children"))).containsExactly(child);
        assertThat(find(all, child).get("detachedFromParent").asBoolean()).isFalse();
    }

    @Test
    void eachNodeCarriesOutcomeAndExecutionSeparately() throws Exception {
        String g = create("هدف سنجیده", "quarterly", "2026-09-23", "2026-12-21", null);
        String kr = JsonPath.read(mvc.perform(post("/api/v1/goals/" + g + "/key-results").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"kr\",\"baseline\":20,\"target\":60}")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/key-results/" + kr + "/check-ins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":40,\"measuredAt\":\"2026-11-03T12:00:00Z\"}")).andExpect(status().isCreated());
        for (String s : new String[]{"done", "done", "done", "doing"}) {
            mvc.perform(post("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"t\",\"status\":\"" + s + "\",\"goalId\":\"" + g + "\"}")).andExpect(status().isCreated());
        }
        String body = mvc.perform(get("/api/v1/goals/tree")).andReturn().getResponse().getContentAsString();
        JsonNode node = find(json.readTree(body).get("roots"), g);
        assertThat(node.get("outcomeProgress").decimalValue()).isEqualByComparingTo("50");
        assertThat(node.get("executionProgress").decimalValue()).isEqualByComparingTo("75");
        assertThat(node.get("activeTaskCount").asInt()).isEqualTo(4);
        assertThat(node.get("doneTaskCount").asInt()).isEqualTo(3);
        assertThat(body).doesNotContainIgnoringCase("combined").doesNotContain("62.5");
        // same numbers as the single-goal endpoint (one calculation)
        String single = mvc.perform(get("/api/v1/goals/" + g + "/progress")).andReturn().getResponse().getContentAsString();
        assertThat((Double) JsonPath.read(single, "$.outcomeProgress")).isEqualTo(50.0);
        assertThat((Double) JsonPath.read(single, "$.executionProgress")).isEqualTo(75.0);
    }

    @Test
    void invalidFilterIs400AndTreePathIsNotAGoalId() throws Exception {
        mvc.perform(get("/api/v1/goals/tree").param("archived", "maybe")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/goals/tree")).andExpect(status().isOk());
    }
}
