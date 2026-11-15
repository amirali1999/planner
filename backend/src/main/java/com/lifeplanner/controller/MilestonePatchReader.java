package com.lifeplanner.controller;

import com.lifeplanner.model.vo.UpdateMilestoneVO;
import com.lifeplanner.service.MilestoneService;
import com.lifeplanner.util.JsonPatchBody;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Set;

@Component
class MilestonePatchReader {

    private static final Set<String> EDITABLE = Set.of("title", "description", "dueDate", "completed", "archived");
    private static final Set<String> READ_ONLY = Set.of("id", "goalId", "completedAt", "archivedAt", "createdAt", "updatedAt");

    private final ObjectMapper mapper;

    MilestonePatchReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    UpdateMilestoneVO read(JsonNode body) {
        JsonPatchBody p = JsonPatchBody.of(body, mapper, EDITABLE, READ_ONLY, MilestoneService.MSG_INVALID);
        return new UpdateMilestoneVO(
                p.field("title", String.class),
                p.field("description", String.class),
                p.field("dueDate", LocalDate.class),
                p.field("completed", Boolean.class),
                p.field("archived", Boolean.class));
    }
}
