package com.lifeplanner.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeplanner.model.vo.UpdateHabitVO;
import com.lifeplanner.service.HabitService;
import com.lifeplanner.util.JsonPatchBody;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
class HabitPatchReader {

    private static final Set<String> EDITABLE = Set.of("title", "archived");
    private static final Set<String> READ_ONLY = Set.of("id", "doneDates", "archivedAt", "createdAt", "updatedAt");

    private final ObjectMapper mapper;

    HabitPatchReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    UpdateHabitVO read(JsonNode body) {
        JsonPatchBody p = JsonPatchBody.of(body, mapper, EDITABLE, READ_ONLY, HabitService.MSG_INVALID);
        return new UpdateHabitVO(p.field("title", String.class), p.field("archived", Boolean.class));
    }
}
