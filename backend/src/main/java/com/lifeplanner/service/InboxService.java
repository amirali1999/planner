package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.InboxItem;
import com.lifeplanner.model.InboxLimits;
import com.lifeplanner.model.enums.InboxDecision;
import com.lifeplanner.model.enums.InboxStatus;
import com.lifeplanner.model.vo.ProcessInboxItemVO;
import com.lifeplanner.repository.InboxRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quick capture stores free text as typed (no AI, nothing inferred). Triage is an explicit user step that, in ONE
 * transaction, creates the chosen entity through its normal service (same validation as its own endpoint) and records
 * the item as processed with provenance. Any failure leaves the item unprocessed and creates nothing.
 */
@Service
public class InboxService {

    static final String MSG_EMPTY = "متن یادداشت نمی‌تواند خالی باشد.";
    static final String MSG_TOO_LONG = "متن یادداشت حداکثر ۴۰۰۰ نویسه می‌تواند باشد.";
    static final String MSG_NOT_FOUND = "مورد صندوق ورودی پیدا نشد.";
    static final String MSG_ALREADY_PROCESSED = "این مورد قبلاً پردازش شده است.";
    static final String MSG_INVALID_TRIAGE = "اطلاعات پردازش معتبر نیست.";

    private final InboxRepository inbox;
    private final TaskService tasks;
    private final GoalService goals;
    private final FocusItemService focusItems;
    private final Validator validator;
    private final Clock clock;

    public InboxService(InboxRepository inbox, TaskService tasks, GoalService goals, FocusItemService focusItems,
                        Validator validator, Clock clock) {
        this.inbox = inbox;
        this.tasks = tasks;
        this.goals = goals;
        this.focusItems = focusItems;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public InboxItem process(String id, ProcessInboxItemVO req) {
        InboxItem item = get(id);
        if (item.status() == InboxStatus.PROCESSED) {
            throw alreadyProcessed(item);
        }
        InboxDecision decision = req.decision();
        requireOnlyMatchingPayload(req, decision);
        switch (decision) {
            case TASK -> validatePayload("task", req.task());
            case GOAL -> validatePayload("goal", req.goal());
            case FOCUS_ITEM -> validatePayload("focusItem", req.focusItem());
            case REFERENCE, SOMEDAY, DISCARD -> { }
        }

        String entityType = null;
        String entityId = null;
        switch (decision) {
            case TASK -> {
                entityType = "task";
                entityId = tasks.create(req.task()).id();
            }
            case GOAL -> {
                entityType = "goal";
                entityId = goals.create(req.goal()).id();
            }
            case FOCUS_ITEM -> {
                entityType = "focus_item";
                entityId = focusItems.create(req.focusItem()).id();
            }
            case REFERENCE, SOMEDAY, DISCARD -> { }
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!inbox.markProcessed(id, decision.code(), entityType, entityId, now)) {
            // processed concurrently between the read and this update: roll back the created entity
            throw alreadyProcessed(get(id));
        }
        return get(id);
    }

    private static void requireOnlyMatchingPayload(ProcessInboxItemVO req, InboxDecision decision) {
        Map<String, Object> errors = new java.util.LinkedHashMap<>();
        check(errors, "task", req.task() != null, decision == InboxDecision.TASK);
        check(errors, "goal", req.goal() != null, decision == InboxDecision.GOAL);
        check(errors, "focusItem", req.focusItem() != null, decision == InboxDecision.FOCUS_ITEM);
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID_TRIAGE, errors);
        }
    }

    /** Same Bean Validation rules as the payload's own create endpoint; errors keyed as {@code task.title}. */
    private void validatePayload(String prefix, Object payload) {
        Map<String, Object> errors = new java.util.LinkedHashMap<>();
        for (ConstraintViolation<Object> v : validator.validate(payload)) {
            errors.putIfAbsent(prefix + "." + v.getPropertyPath(), v.getMessage());
        }
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID_TRIAGE, errors);
        }
    }

    private static void check(Map<String, Object> errors, String field, boolean present, boolean expected) {
        if (expected && !present) {
            errors.put(field, "required");
        } else if (!expected && present) {
            errors.put(field, "not_allowed_for_decision");
        }
    }

    private static ApiException.ConflictException alreadyProcessed(InboxItem item) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("inboxItemId", item.id());
        details.put("decision", item.decision());
        if (item.createdEntityId() != null) {
            details.put("createdEntityType", item.createdEntityType());
            details.put("createdEntityId", item.createdEntityId());
        }
        return new ApiException.ConflictException("inbox_item_already_processed", MSG_ALREADY_PROCESSED, details);
    }

    @Transactional
    public InboxItem capture(String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty()) {
            throw new ApiException.ValidationException(MSG_EMPTY, Map.of("rawText", "required"));
        }
        int length = text.codePointCount(0, text.length());
        if (length > InboxLimits.RAW_TEXT_MAX) {
            throw new ApiException.ValidationException(MSG_TOO_LONG,
                    Map.of("rawText", "too_long", "maxLength", InboxLimits.RAW_TEXT_MAX, "length", length));
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        InboxItem item = new InboxItem(UUID.randomUUID().toString(), text, InboxStatus.UNPROCESSED, null, null, null, null,
                now, now, now);
        inbox.insert(item);
        return item;
    }

    public List<InboxItem> list(InboxStatus status, List<String> decisions) {
        return inbox.find(status, decisions);
    }

    public InboxItem get(String id) {
        return inbox.findById(id).orElseThrow(() -> new ApiException.NotFoundException(MSG_NOT_FOUND, Map.of("inboxItemId", id)));
    }
}
