package com.lifeplanner.service;

import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.GoalStatus;
import com.lifeplanner.model.enums.PeriodType;
import com.lifeplanner.model.srv.GoalWarningSrv;
import com.lifeplanner.model.vo.CreateGoalVO;
import com.lifeplanner.model.vo.GoalQueryVO;
import com.lifeplanner.model.vo.UpdateGoalVO;
import com.lifeplanner.repository.GoalHistoryRepository;
import com.lifeplanner.repository.GoalRepository;
import com.lifeplanner.util.IsoDates;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Goal lifecycle. Status is always set explicitly by the user. Archiving never cascades: KRs, check-ins, tasks
 * and child goals keep their own records and links. Lifecycle changes are written to goal_history in the same
 * transaction as the change itself.
 */
@Service
public class GoalService {

    static final String MSG_GOAL_NOT_FOUND = "هدف پیدا نشد.";
    static final String MSG_PARENT_NOT_FOUND = "هدف والد پیدا نشد.";
    static final String MSG_PARENT_ARCHIVED = "هدف والد بایگانی شده است.";
    public static final String MSG_INVALID_GOAL = "اطلاعات هدف معتبر نیست.";
    static final String MSG_GOAL_ARCHIVED = "این هدف بایگانی شده است؛ ابتدا آن را از بایگانی خارج کنید.";
    static final String MSG_CYCLE = "این انتخاب والد یک چرخه در درخت اهداف می‌سازد.";
    static final String MSG_GRANULARITY = "دوره هدف والد باید بزرگ‌تر از دوره این هدف باشد (سالانه > فصلی > ماهانه > هفتگی).";

    public static final String CODE_CYCLE = "goal_cycle";
    public static final String CODE_GRANULARITY = "incompatible_period_granularity";

    private final GoalRepository goals;
    private final GoalHistoryRepository history;
    private final ObjectMapper mapper;
    private final Clock clock;

    public GoalService(GoalRepository goals, GoalHistoryRepository history, ObjectMapper mapper, Clock clock) {
        this.goals = goals;
        this.history = history;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<Goal> list(ArchiveFilter archive) {
        return goals.find(archive);
    }

    /** Filtered page plus the total number of matches (read-only). */
    public GoalPage list(GoalQueryVO query) {
        return new GoalPage(goals.find(query), goals.count(query));
    }

    public record GoalPage(List<Goal> items, int totalCount) {
    }

    public Goal get(String goalId) {
        return goals.findById(goalId)
                .orElseThrow(() -> new ApiException.NotFoundException(MSG_GOAL_NOT_FOUND, Map.of("goalId", goalId)));
    }

    /** Non-blocking date-consistency warnings for a single goal (read-only). */
    public List<GoalWarningSrv> warnings(Goal goal) {
        Goal parent = goal.parentGoalId() == null ? null : goals.findById(goal.parentGoalId()).orElse(null);
        return GoalWarnings.compute(goal, parent, goals.findChildren(goal.id(), ArchiveFilter.ACTIVE));
    }

    @Transactional
    public Goal create(CreateGoalVO req) {
        Map<String, Object> errors = new LinkedHashMap<>();
        checkDates(errors, req.periodStart(), req.periodEnd(), req.deadline());
        throwIfAny(errors);

        String parentId = blankToNull(req.parentGoalId());
        // A brand-new goal has no descendants, so only self/granularity/archived checks can apply.
        String newId = UUID.randomUUID().toString();
        if (parentId != null) {
            requireValidParent(newId, req.periodType(), parentId);
        }
        Instant now = now();
        Goal goal = new Goal(newId, req.title().trim(), blankToNull(req.description()),
                req.periodType(), req.periodStart(), req.periodEnd(), req.deadline(),
                req.status() == null ? GoalStatus.NOT_STARTED : req.status(),
                parentId, null, now, now);
        goals.insert(goal);
        return goals.findById(newId).orElseThrow(); // carries the priority assigned on insert
    }

    /** Applies only the fields present. Archived goals are read-only until restored. No-op requests write nothing. */
    @Transactional
    public Goal update(String goalId, UpdateGoalVO req) {
        Goal current = get(goalId);
        if (current.isArchived()) {
            throw new ApiException.ConflictException("goal_archived", MSG_GOAL_ARCHIVED, Map.of("goalId", goalId));
        }

        Map<String, Object> errors = new LinkedHashMap<>();
        String title = current.title();
        if (req.title() != null) {
            title = req.title().map(String::trim).orElse("");
            if (title.isEmpty()) {
                errors.put("title", "required");
            } else if (title.length() > 200) {
                errors.put("title", "too_long");
            }
        }
        String description = current.description();
        if (req.description() != null) {
            description = blankToNull(req.description().orElse(null));
            if (description != null && description.length() > 2000) {
                errors.put("description", "too_long");
            }
        }
        GoalStatus status = current.status();
        if (req.status() != null) {
            status = req.status().orElse(null);
            if (status == null) {
                errors.put("status", "required");
            }
        }
        LocalDate start = required(errors, "periodStart", req.periodStart(), current.periodStart());
        LocalDate end = required(errors, "periodEnd", req.periodEnd(), current.periodEnd());
        LocalDate deadline = req.deadline() == null ? current.deadline() : req.deadline().orElse(null);
        if (start != null && end != null) {
            checkDates(errors, start, end, deadline);
        }
        throwIfAny(errors);

        String parentId = current.parentGoalId();
        if (req.parentGoalId() != null) {
            parentId = blankToNull(req.parentGoalId().orElse(null));
            if (parentId != null && !parentId.equals(current.parentGoalId())) {
                requireValidParent(current.id(), current.periodType(), parentId);
            }
        }

        Goal updated = new Goal(current.id(), title, description, current.periodType(), start, end, deadline, status,
                parentId, null, current.createdAt(), current.updatedAt(), current.priority());
        Map<String, Object[]> changes = diff(current, updated);
        if (changes.isEmpty()) {
            return current;
        }
        Instant now = now();
        updated = new Goal(updated.id(), title, description, updated.periodType(), start, end, deadline, status,
                parentId, null, current.createdAt(), now, current.priority());
        goals.update(updated);
        recordChanges(current.id(), changes, now);
        return updated;
    }

    /**
     * Sets the manual priority of ALL the owner's active goals at once: {@code goalIds} must list each of them exactly
     * once, highest priority first. A partial or stale list is rejected (400) instead of guessing where the others go.
     */
    @Transactional
    public List<Goal> reorder(List<String> goalIds) {
        List<String> requested = goalIds == null ? List.of() : goalIds;
        List<String> active = goals.activeIdsByPriority();
        boolean distinct = new java.util.HashSet<>(requested).size() == requested.size();
        if (requested.isEmpty() || !distinct || !new java.util.HashSet<>(requested).equals(new java.util.HashSet<>(active))) {
            throw new ApiException.ValidationException("فهرست باید همهٔ اهداف فعال را دقیقاً یک بار داشته باشد.",
                    Map.of("goalIds", requested.isEmpty() ? "required" : !distinct ? "duplicate" : "must_list_all_active_goals"));
        }
        for (int i = 0; i < requested.size(); i++) {
            goals.setPriority(requested.get(i), i + 1);
        }
        return requested.stream().map(id -> goals.findById(id).orElseThrow()).toList();
    }

    /** Idempotent: archiving an archived goal returns it unchanged and writes no history. Never cascades. */
    @Transactional
    public Goal archive(String goalId) {
        Goal current = get(goalId);
        if (current.isArchived()) {
            return current;
        }
        Instant now = now();
        Goal archived = withArchivedAt(current, now, now);
        goals.update(archived);
        goals.setPriority(goalId, null); // archived goals leave the priority order …
        goals.renumberPriorities();      // … and the others close the gap
        appendHistory(goalId, "archived", entry("archivedAt", null), entry("archivedAt", UtcTimestamps.toDb(now)), now);
        return archived;
    }

    /** Idempotent restore. The goal keeps its parent link; if that parent is archived the UI shows it as such. */
    @Transactional
    public Goal unarchive(String goalId) {
        Goal current = get(goalId);
        if (!current.isArchived()) {
            return current;
        }
        Instant now = now();
        Goal restored = withArchivedAt(current, null, now);
        goals.update(restored);
        goals.appendToPriority(goalId); // a restored goal re-enters at the end
        appendHistory(goalId, "unarchived", entry("archivedAt", UtcTimestamps.toDb(current.archivedAt())), entry("archivedAt", null), now);
        return goals.findById(goalId).orElseThrow();
    }

    /**
     * Single place for parent rules (create and PATCH): parent must exist (404) and be active (409), must not be the
     * goal itself or one of its descendants (409 goal_cycle — checked first), and must have a strictly coarser
     * period type (409 incompatible_period_granularity). Skipped levels are allowed.
     */
    private void requireValidParent(String goalId, PeriodType childType, String parentId) {
        if (parentId.equals(goalId)) {
            throw new ApiException.ConflictException(CODE_CYCLE, MSG_CYCLE, Map.of("parentGoalId", parentId));
        }
        Goal parent = goals.findById(parentId).orElseThrow(() ->
                new ApiException.NotFoundException(MSG_PARENT_NOT_FOUND, Map.of("parentGoalId", parentId)));
        if (goals.selfAndAncestorIds(parent.id()).contains(goalId)) {
            throw new ApiException.ConflictException(CODE_CYCLE, MSG_CYCLE, Map.of("parentGoalId", parentId));
        }
        if (parent.isArchived()) {
            throw new ApiException.ConflictException("parent_goal_archived", MSG_PARENT_ARCHIVED, Map.of("parentGoalId", parentId));
        }
        if (!parent.periodType().isCoarserThan(childType)) {
            throw new ApiException.ConflictException(CODE_GRANULARITY, MSG_GRANULARITY, Map.of(
                    "parentGoalId", parentId,
                    "parentPeriodType", parent.periodType().code(),
                    "periodType", childType.code()));
        }
    }

    private static Map<String, Object[]> diff(Goal a, Goal b) {
        Map<String, Object[]> changes = new LinkedHashMap<>();
        put(changes, "title", a.title(), b.title());
        put(changes, "description", a.description(), b.description());
        put(changes, "periodStart", a.periodStart(), b.periodStart());
        put(changes, "periodEnd", a.periodEnd(), b.periodEnd());
        put(changes, "deadline", a.deadline(), b.deadline());
        put(changes, "status", a.status().code(), b.status().code());
        put(changes, "parentGoalId", a.parentGoalId(), b.parentGoalId());
        return changes;
    }

    private static void put(Map<String, Object[]> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.put(field, new Object[]{before == null ? null : before.toString(), after == null ? null : after.toString()});
        }
    }

    /** One history row per category so status and parent changes stay easy to find. */
    private void recordChanges(String goalId, Map<String, Object[]> changes, Instant at) {
        for (String category : List.of("status", "parentGoalId", "details")) {
            Map<String, Object> before = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            changes.forEach((field, values) -> {
                boolean matches = category.equals("details") ? !field.equals("status") && !field.equals("parentGoalId") : field.equals(category);
                if (matches) {
                    before.put(field, values[0]);
                    after.put(field, values[1]);
                }
            });
            if (!before.isEmpty()) {
                String event = switch (category) {
                    case "status" -> "status_changed";
                    case "parentGoalId" -> "parent_changed";
                    default -> "details_changed";
                };
                appendHistory(goalId, event, before, after, at);
            }
        }
    }

    private void appendHistory(String goalId, String event, Map<String, ?> before, Map<String, ?> after, Instant at) {
        try {
            history.append(goalId, event, mapper.writeValueAsString(before), mapper.writeValueAsString(after), at);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize goal history", e);
        }
    }

    /** Single-entry map that, unlike Map.of, accepts a null value. */
    private static Map<String, Object> entry(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Goal withArchivedAt(Goal g, Instant archivedAt, Instant updatedAt) {
        return new Goal(g.id(), g.title(), g.description(), g.periodType(), g.periodStart(), g.periodEnd(), g.deadline(),
                g.status(), g.parentGoalId(), archivedAt, g.createdAt(), updatedAt, archivedAt == null ? g.priority() : null);
    }

    private static LocalDate required(Map<String, Object> errors, String field, Optional<LocalDate> patch, LocalDate current) {
        if (patch == null) {
            return current;
        }
        if (patch.isEmpty()) {
            errors.put(field, "required");
            return null;
        }
        return patch.get();
    }

    private static void checkDates(Map<String, Object> errors, LocalDate start, LocalDate end, LocalDate deadline) {
        if (!IsoDates.isStorable(start)) {
            errors.put("periodStart", "out_of_range");
        }
        if (!IsoDates.isStorable(end)) {
            errors.put("periodEnd", "out_of_range");
        }
        if (!IsoDates.isStorable(deadline)) {
            errors.put("deadline", "out_of_range");
        }
        if (!errors.containsKey("periodStart") && !errors.containsKey("periodEnd") && end.isBefore(start)) {
            errors.put("periodEnd", "before_period_start");
        }
    }

    private static void throwIfAny(Map<String, Object> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID_GOAL, errors);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
