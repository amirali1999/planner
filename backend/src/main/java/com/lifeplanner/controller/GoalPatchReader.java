package com.lifeplanner.controller;

import com.lifeplanner.model.enums.GoalStatus;
import com.lifeplanner.model.vo.UpdateGoalVO;
import com.lifeplanner.service.GoalService;
import com.lifeplanner.util.JsonPatchBody;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Set;

/** Builds an {@link UpdateGoalVO} from the raw PATCH body (absent key ≠ explicit null). */
@Component
class GoalPatchReader {

    private static final Set<String> EDITABLE = Set.of(
            "title", "description", "status", "periodStart", "periodEnd", "deadline", "parentGoalId");
    /** Exist on a Goal but cannot be changed by PATCH (archive has its own endpoint). */
    private static final Set<String> READ_ONLY = Set.of("id", "periodType", "archivedAt", "createdAt", "updatedAt");

    private final ObjectMapper mapper;

    GoalPatchReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    UpdateGoalVO read(JsonNode body) {
        JsonPatchBody p = JsonPatchBody.of(body, mapper, EDITABLE, READ_ONLY, GoalService.MSG_INVALID_GOAL);
        return new UpdateGoalVO(
                p.field("title", String.class),
                p.field("description", String.class),
                p.field("status", GoalStatus.class),
                p.field("periodStart", LocalDate.class),
                p.field("periodEnd", LocalDate.class),
                p.field("deadline", LocalDate.class),
                p.field("parentGoalId", String.class));
    }
}
