package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.FocusItem;
import com.lifeplanner.model.FocusItemLimits;
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.FocusKind;
import com.lifeplanner.model.enums.FocusStatus;
import com.lifeplanner.model.vo.CreateFocusItemVO;
import com.lifeplanner.model.vo.UpdateFocusItemVO;
import com.lifeplanner.repository.FocusItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Focus Items with an optional, single Goal link. Goals are never created implicitly; a given goalId must exist (404)
 * and be active when newly linked (409). An existing link to a later-archived goal is kept. Nothing is hard-deleted.
 */
@Service
public class FocusItemService {

    public static final String MSG_INVALID = "اطلاعات آیتم تمرکز معتبر نیست.";
    static final String MSG_NOT_FOUND = "آیتم تمرکز پیدا نشد.";
    static final String MSG_GOAL_NOT_FOUND = "هدف مرتبط پیدا نشد.";
    static final String MSG_GOAL_ARCHIVED = "هدف انتخاب‌شده بایگانی شده است.";
    static final String MSG_ARCHIVED = "این آیتم تمرکز بایگانی شده است؛ ابتدا آن را از بایگانی خارج کنید.";

    private final FocusItemRepository items;
    private final GoalService goals;
    private final Clock clock;

    public FocusItemService(FocusItemRepository items, GoalService goals, Clock clock) {
        this.items = items;
        this.goals = goals;
        this.clock = clock;
    }

    public List<FocusItem> list(ArchiveFilter archive, String goalId, FocusKind kind, FocusStatus status) {
        return items.find(archive, blankToNull(goalId), kind, status);
    }

    public FocusItem get(String id) {
        return items.findById(id).orElseThrow(() -> new ApiException.NotFoundException(MSG_NOT_FOUND, Map.of("focusItemId", id)));
    }

    @Transactional
    public FocusItem create(CreateFocusItemVO req) {
        String goalId = requireLinkableGoal(blankToNull(req.goalId()));
        Instant now = now();
        FocusItem item = new FocusItem(UUID.randomUUID().toString(), req.title().trim(), req.kind(), blankToNull(req.description()),
                goalId, req.status() == null ? FocusStatus.NOT_STARTED : req.status(), null, now, now);
        items.insert(item);
        return get(item.id()); // carries the position assigned on insert
    }

    /** Only present fields change. {@code archived} archives/restores; edits need the item active (or restored in the same request). */
    @Transactional
    public FocusItem update(String id, UpdateFocusItemVO req) {
        FocusItem current = get(id);
        Instant now = now();

        Instant archivedAt = current.archivedAt();
        if (req.archived() != null) {
            boolean archive = req.archived().orElseThrow(() -> invalid("archived", "required"));
            archivedAt = archive ? (current.isArchived() ? current.archivedAt() : now) : null;
        }
        boolean editsFields = req.title() != null || req.kind() != null || req.description() != null
                || req.goalId() != null || req.status() != null;
        if (editsFields && archivedAt != null) {
            throw new ApiException.ConflictException("focus_item_archived", MSG_ARCHIVED, Map.of("focusItemId", id));
        }

        Map<String, Object> errors = new LinkedHashMap<>();
        String title = current.title();
        if (req.title() != null) {
            title = req.title().map(String::trim).orElse("");
            if (title.isEmpty()) {
                errors.put("title", "required");
            } else if (title.length() > FocusItemLimits.TITLE_MAX) {
                errors.put("title", "too_long");
            }
        }
        FocusKind kind = current.kind();
        if (req.kind() != null) {
            kind = req.kind().orElse(null);
            if (kind == null) {
                errors.put("kind", "required");
            }
        }
        FocusStatus status = current.status();
        if (req.status() != null) {
            status = req.status().orElse(null);
            if (status == null) {
                errors.put("status", "required");
            }
        }
        String description = current.description();
        if (req.description() != null) {
            description = blankToNull(req.description().orElse(null));
            if (description != null && description.length() > FocusItemLimits.DESCRIPTION_MAX) {
                errors.put("description", "too_long");
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID, errors);
        }
        String goalId = current.goalId();
        if (req.goalId() != null) {
            String requested = blankToNull(req.goalId().orElse(null));
            // Re-validate only when the link changes, so an existing link to a since-archived goal can stay.
            goalId = requested == null || requested.equals(current.goalId()) ? requested : requireLinkableGoal(requested);
        }

        FocusItem updated = new FocusItem(current.id(), title, kind, description, goalId, status, archivedAt,
                current.createdAt(), current.updatedAt(), current.position());
        if (updated.equals(current)) {
            return current;
        }
        updated = new FocusItem(updated.id(), title, kind, description, goalId, status, archivedAt, current.createdAt(), now,
                current.position());
        items.update(updated);
        if (!java.util.Objects.equals(goalId, current.goalId())) {
            items.appendToGoal(id, goalId); // a different goal: append to its list (or no position without goal)
            return get(id);
        }
        return updated;
    }

    /**
     * New order of a goal's focus items: {@code focusItemIds} must list every active focus item of that goal exactly
     * once (top first). Items of other goals are rejected, so items can never move between goals this way.
     */
    @Transactional
    public List<FocusItem> reorder(String goalId, List<String> focusItemIds) {
        goals.get(goalId); // 404 for unknown / another user's goal
        List<String> requested = focusItemIds == null ? List.of() : focusItemIds;
        List<String> current = items.find(ArchiveFilter.ACTIVE, goalId, null, null).stream().map(FocusItem::id).toList();
        boolean distinct = new java.util.HashSet<>(requested).size() == requested.size();
        if (requested.isEmpty() || !distinct || !new java.util.HashSet<>(requested).equals(new java.util.HashSet<>(current))) {
            throw new ApiException.ValidationException("فهرست باید همهٔ آیتم‌های تمرکز فعال همین هدف را دقیقاً یک بار داشته باشد.",
                    Map.of("focusItemIds", requested.isEmpty() ? "required" : !distinct ? "duplicate" : "must_list_all_active_items_of_goal"));
        }
        for (int i = 0; i < requested.size(); i++) {
            items.setPosition(requested.get(i), i + 1);
        }
        return requested.stream().map(this::get).toList();
    }

    private String requireLinkableGoal(String goalId) {
        if (goalId == null) {
            return null;
        }
        Goal goal;
        try {
            goal = goals.get(goalId);
        } catch (ApiException.NotFoundException e) {
            throw new ApiException.NotFoundException(MSG_GOAL_NOT_FOUND, Map.of("goalId", goalId));
        }
        if (goal.isArchived()) {
            throw new ApiException.ConflictException("goal_archived", MSG_GOAL_ARCHIVED, Map.of("goalId", goalId));
        }
        return goal.id();
    }

    private static ApiException.ValidationException invalid(String field, String code) {
        return new ApiException.ValidationException(MSG_INVALID, Map.of(field, code));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
