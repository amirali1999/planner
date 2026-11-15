package com.lifeplanner.controller;

import com.lifeplanner.model.enums.TaskPriority;
import com.lifeplanner.model.enums.TaskStatus;
import com.lifeplanner.model.vo.UpdateTaskVO;
import com.lifeplanner.service.TaskService;
import com.lifeplanner.util.JsonPatchBody;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.Set;

/** Builds an {@link UpdateTaskVO} from the raw PATCH body (absent key ≠ explicit null). */
@Component
class TaskPatchReader {

    private static final Set<String> EDITABLE = Set.of(
            "title", "description", "goalId", "focusItemId", "status", "priority", "estimatedMinutes", "dueDate", "blockedReason", "archived");

    private final ObjectMapper mapper;

    TaskPatchReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    UpdateTaskVO read(JsonNode body) {
        JsonPatchBody p = JsonPatchBody.of(body, mapper, EDITABLE, Set.of(), TaskService.MSG_INVALID_TASK);
        return new UpdateTaskVO(
                p.field("title", String.class),
                p.field("description", String.class),
                p.field("goalId", String.class),
                p.field("focusItemId", String.class),
                p.field("status", TaskStatus.class),
                p.field("priority", TaskPriority.class),
                p.field("estimatedMinutes", Integer.class),
                p.field("dueDate", LocalDate.class),
                p.field("blockedReason", String.class),
                p.field("archived", Boolean.class));
    }
}
