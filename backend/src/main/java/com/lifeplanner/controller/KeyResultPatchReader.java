package com.lifeplanner.controller;

import com.lifeplanner.model.vo.UpdateKeyResultVO;
import com.lifeplanner.service.KeyResultService;
import com.lifeplanner.util.JsonPatchBody;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Set;

/** Builds an {@link UpdateKeyResultVO} from the raw PATCH body (absent key ≠ explicit null). */
@Component
class KeyResultPatchReader {

    private static final Set<String> EDITABLE = Set.of("name", "unit", "baseline", "target", "weight", "note");
    /** Exist on a KR but are not editable here (current/score are derived; archive has its own endpoint). */
    private static final Set<String> READ_ONLY = Set.of(
            "id", "goalId", "current", "currentSource", "currentMeasuredAt", "score", "archivedAt", "createdAt", "updatedAt");

    private final ObjectMapper mapper;

    KeyResultPatchReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    UpdateKeyResultVO read(JsonNode body) {
        JsonPatchBody p = JsonPatchBody.of(body, mapper, EDITABLE, READ_ONLY, KeyResultService.MSG_INVALID_KR);
        return new UpdateKeyResultVO(
                p.field("name", String.class),
                p.field("unit", String.class),
                p.field("baseline", BigDecimal.class),
                p.field("target", BigDecimal.class),
                p.field("weight", BigDecimal.class),
                p.field("note", String.class));
    }
}
