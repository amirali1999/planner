package com.lifeplanner.controller;

import com.lifeplanner.model.enums.FocusKind;
import com.lifeplanner.model.enums.FocusStatus;
import com.lifeplanner.model.vo.UpdateFocusItemVO;
import com.lifeplanner.service.FocusItemService;
import com.lifeplanner.util.JsonPatchBody;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
class FocusItemPatchReader {

    private static final Set<String> EDITABLE = Set.of("title", "kind", "description", "goalId", "status", "archived");
    private static final Set<String> READ_ONLY = Set.of("id", "archivedAt", "createdAt", "updatedAt");

    private final ObjectMapper mapper;

    FocusItemPatchReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    UpdateFocusItemVO read(JsonNode body) {
        JsonPatchBody p = JsonPatchBody.of(body, mapper, EDITABLE, READ_ONLY, FocusItemService.MSG_INVALID);
        return new UpdateFocusItemVO(
                p.field("title", String.class),
                p.field("kind", FocusKind.class),
                p.field("description", String.class),
                p.field("goalId", String.class),
                p.field("status", FocusStatus.class),
                p.field("archived", Boolean.class));
    }
}
