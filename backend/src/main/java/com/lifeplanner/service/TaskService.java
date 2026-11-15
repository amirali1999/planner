package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.FocusItem;
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.Task;
import com.lifeplanner.model.TaskLimits;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.GoalMatch;
import com.lifeplanner.model.enums.TaskPriority;
import com.lifeplanner.model.enums.TaskStatus;
import com.lifeplanner.model.vo.CreateTaskVO;
import com.lifeplanner.model.vo.UpdateTaskVO;
import com.lifeplanner.repository.FocusItemRepository;
import com.lifeplanner.repository.TaskRepository;
import com.lifeplanner.util.IsoDates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TaskService {

    static final String MSG_TASK_NOT_FOUND = "کار پیدا نشد.";
    static final String MSG_GOAL_NOT_FOUND = "هدف مرتبط پیدا نشد.";
    static final String MSG_GOAL_ARCHIVED = "هدف مرتبط بایگانی شده است.";
    static final String MSG_TASK_ARCHIVED = "این کار بایگانی شده است؛ ابتدا آن را از بایگانی خارج کنید.";
    public static final String MSG_INVALID_TASK = "اطلاعات کار معتبر نیست.";
    static final String MSG_FOCUS_NOT_FOUND = "آیتم تمرکز مرتبط پیدا نشد.";
    static final String MSG_FOCUS_ARCHIVED = "آیتم تمرکز انتخاب‌شده بایگانی شده است.";

    private final TaskRepository tasks;
    private final GoalService goals;
    private final FocusItemRepository focusItems;
    private final Clock clock;

    public TaskService(TaskRepository tasks, GoalService goals, FocusItemRepository focusItems, Clock clock) {
        this.tasks = tasks;
        this.goals = goals;
        this.focusItems = focusItems;
        this.clock = clock;
    }

    /** /tasks list: optional direct-goal and focus-item filters. */
    public List<Task> list(TaskStatus status, String goalId, String focusItemId, ArchiveFilter archive) {
        return tasks.find(status, blankToNull(goalId), GoalMatch.DIRECT, blankToNull(focusItemId), archive);
    }

    /** /goals/{id}/tasks: distinct tasks whose EFFECTIVE goal is this goal (direct link wins over focus item). */
    public List<Task> listForGoal(String goalId, TaskStatus status, ArchiveFilter archive) {
        goals.get(goalId);
        return tasks.find(status, goalId, GoalMatch.EFFECTIVE, null, archive);
    }

    public Task get(String taskId) {
        return tasks.findById(taskId).orElseThrow(() ->
                new ApiException.NotFoundException(MSG_TASK_NOT_FOUND, Map.of("taskId", taskId)));
    }

    @Transactional
    public Task create(CreateTaskVO req) {
        Map<String, Object> errors = new LinkedHashMap<>();
        checkDueDate(errors, req.dueDate());
        throwIfAny(errors);
        String goalId = requireLinkableGoal(blankToNull(req.goalId()));
        String focusItemId = requireLinkableFocusItem(blankToNull(req.focusItemId()));

        Instant now = now();
        Task task = new Task(UUID.randomUUID().toString(), req.title().trim(), blankToNull(req.description()),
                goalId, focusItemId, req.status() == null ? TaskStatus.NOT_STARTED : req.status(),
                req.priority() == null ? TaskPriority.LOW : req.priority(), // default priority: low
                req.estimatedMinutes(), req.dueDate(), blankToNull(req.blockedReason()), null, now, now);
        tasks.insert(task);
        return get(task.id()); // read back with the derived focus-item goal
    }

    /**
     * Applies only the fields present in the request. All status changes among the three statuses are allowed
     * (including reopening a done task); editing an archived task is not, unless the same request restores it.
     */
    @Transactional
    public Task update(String taskId, UpdateTaskVO req) {
        Task current = get(taskId);
        Instant now = now();

        Instant archivedAt = current.archivedAt();
        if (req.archived() != null) {
            boolean archive = req.archived().orElseThrow(() -> validation("archived", "required"));
            archivedAt = archive ? (current.isArchived() ? current.archivedAt() : now) : null;
        }
        boolean editsFields = req.title() != null || req.description() != null || req.goalId() != null
                || req.focusItemId() != null
                || req.status() != null || req.priority() != null || req.estimatedMinutes() != null
                || req.dueDate() != null || req.blockedReason() != null;
        if (editsFields && current.isArchived() && archivedAt != null) {
            throw new ApiException.ConflictException("task_archived", MSG_TASK_ARCHIVED, Map.of("taskId", taskId));
        }

        Map<String, Object> errors = new LinkedHashMap<>();
        String title = current.title();
        if (req.title() != null) {
            String t = req.title().map(String::trim).orElse("");
            if (t.isEmpty()) {
                errors.put("title", "required");
            } else if (t.length() > TaskLimits.TITLE_MAX) {
                errors.put("title", "too_long");
            }
            title = t;
        }
        String description = patchText(errors, "description", req.description(), current.description(), TaskLimits.DESCRIPTION_MAX);
        String blockedReason = patchText(errors, "blockedReason", req.blockedReason(), current.blockedReason(), TaskLimits.BLOCKED_REASON_MAX);
        TaskStatus status = current.status();
        if (req.status() != null) {
            status = req.status().orElse(null);
            if (status == null) {
                errors.put("status", "required");
            }
        }
        TaskPriority priority = current.priority() == null ? TaskPriority.LOW : current.priority();
        if (req.priority() != null) {
            priority = req.priority().orElse(null);
            if (priority == null) {
                errors.put("priority", "required"); // every task has a priority (low | medium | high)
            }
        }
        Integer estimate = current.estimatedMinutes();
        if (req.estimatedMinutes() != null) {
            estimate = req.estimatedMinutes().orElse(null);
            if (estimate != null && estimate < 0) {
                errors.put("estimatedMinutes", "must_not_be_negative");
            } else if (estimate != null && estimate > TaskLimits.ESTIMATE_MAX) {
                errors.put("estimatedMinutes", "too_large");
            }
        }
        LocalDate dueDate = req.dueDate() == null ? current.dueDate() : req.dueDate().orElse(null);
        checkDueDate(errors, dueDate);
        throwIfAny(errors);

        String goalId = current.goalId();
        if (req.goalId() != null) {
            String requested = blankToNull(req.goalId().orElse(null));
            // Re-validate only when the link changes, so an existing link to a since-archived goal can stay.
            goalId = requested == null || requested.equals(current.goalId()) ? requested : requireLinkableGoal(requested);
        }

        String focusItemId = current.focusItemId();
        if (req.focusItemId() != null) {
            String requested = blankToNull(req.focusItemId().orElse(null));
            // Only a changed link is validated, so an existing link to a since-archived focus item can stay.
            focusItemId = requested == null || requested.equals(current.focusItemId()) ? requested : requireLinkableFocusItem(requested);
        }

        Task updated = new Task(current.id(), title, description, goalId, focusItemId, status, priority,
                estimate, dueDate, blockedReason, archivedAt, current.createdAt(), now);
        tasks.update(updated);
        return get(updated.id());
    }

    /** Links are optional; a given goalId must exist and be active. Goals are never created implicitly. */
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

    /** A given focusItemId must exist (404) and be active (409). Focus items are never created implicitly. */
    private String requireLinkableFocusItem(String focusItemId) {
        if (focusItemId == null) {
            return null;
        }
        FocusItem item = focusItems.findById(focusItemId).orElseThrow(() ->
                new ApiException.NotFoundException(MSG_FOCUS_NOT_FOUND, Map.of("focusItemId", focusItemId)));
        if (item.isArchived()) {
            throw new ApiException.ConflictException("focus_item_archived", MSG_FOCUS_ARCHIVED, Map.of("focusItemId", focusItemId));
        }
        return item.id();
    }

    private static String patchText(Map<String, Object> errors, String field, Optional<String> patch, String current, int max) {
        if (patch == null) {
            return current;
        }
        String value = blankToNull(patch.orElse(null));
        if (value != null && value.length() > max) {
            errors.put(field, "too_long");
        }
        return value;
    }

    private static void checkDueDate(Map<String, Object> errors, LocalDate dueDate) {
        if (!IsoDates.isStorable(dueDate)) {
            errors.put("dueDate", "out_of_range");
        }
    }

    private static void throwIfAny(Map<String, Object> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID_TASK, errors);
        }
    }

    private static ApiException.ValidationException validation(String field, String code) {
        return new ApiException.ValidationException(MSG_INVALID_TASK, Map.of(field, code));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
