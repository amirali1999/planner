package com.lifeplanner.controller;

import com.lifeplanner.model.enums.TimeBlockColor;
import com.lifeplanner.model.vo.UpdateTimeBlockVO;
import com.lifeplanner.service.TimeBlockService;
import com.lifeplanner.util.JsonPatchBody;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Set;

@Component
class TimeBlockPatchReader {

    private static final Set<String> EDITABLE = Set.of("date", "startMinute", "endMinute", "title", "taskId", "color");
    private static final Set<String> READ_ONLY = Set.of("id", "task", "createdAt", "updatedAt");

    private final ObjectMapper mapper;

    TimeBlockPatchReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    UpdateTimeBlockVO read(JsonNode body) {
        JsonPatchBody p = JsonPatchBody.of(body, mapper, EDITABLE, READ_ONLY, TimeBlockService.MSG_INVALID);
        return new UpdateTimeBlockVO(
                p.field("date", LocalDate.class),
                p.field("startMinute", Integer.class),
                p.field("endMinute", Integer.class),
                p.field("title", String.class),
                p.field("taskId", String.class),
                p.field("color", TimeBlockColor.class));
    }
}
