package com.lifeplanner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.FocusItem;
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.Task;
import com.lifeplanner.model.srv.DeletionSrv;
import com.lifeplanner.repository.DeletionRepository;
import com.lifeplanner.repository.GoalHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Permanent deletion from the archive. Only archived entities can be deleted (409 {@code not_archived} otherwise), so
 * nothing active disappears by accident. Owner decisions (2026-11-10): deleting a goal removes everything under it —
 * key results, check-ins, milestones, its history, its focus items and every task linked to the goal or to those focus
 * items — while child goals are kept and become standalone; deleting a focus item also deletes its tasks.
 */
@Service
public class DeletionService {

    static final String MSG_NOT_ARCHIVED = "فقط موارد بایگانی‌شده را می‌توان برای همیشه حذف کرد؛ ابتدا آن را بایگانی کنید.";

    private final DeletionRepository repo;
    private final GoalService goals;
    private final FocusItemService focusItems;
    private final TaskService tasks;
    private final GoalHistoryRepository history;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DeletionService(DeletionRepository repo, GoalService goals, FocusItemService focusItems, TaskService tasks,
                           GoalHistoryRepository history, ObjectMapper mapper, Clock clock) {
        this.repo = repo;
        this.goals = goals;
        this.focusItems = focusItems;
        this.tasks = tasks;
        this.history = history;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public DeletionSrv deleteTask(String taskId) {
        Task task = tasks.get(taskId); // 404 unknown / another user's
        requireArchived(task.isArchived(), "taskId", taskId);
        repo.deleteTask(task.id());
        return new DeletionSrv(0, 0, 1, 0);
    }

    @Transactional
    public DeletionSrv deleteFocusItem(String focusItemId) {
        FocusItem item = focusItems.get(focusItemId);
        requireArchived(item.isArchived(), "focusItemId", focusItemId);
        List<String> taskIds = repo.taskIdsOfFocusItem(item.id());
        taskIds.forEach(repo::deleteTask);
        repo.deleteFocusItem(item.id());
        return new DeletionSrv(0, 1, taskIds.size(), 0);
    }

    @Transactional
    public DeletionSrv deleteGoal(String goalId) {
        Goal goal = goals.get(goalId);
        requireArchived(goal.isArchived(), "goalId", goalId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Set<String> taskIds = new LinkedHashSet<>(repo.taskIdsOfGoal(goal.id()));
        List<String> itemIds = repo.focusItemIdsOfGoal(goal.id());
        // tasks of those focus items that point to another goal directly are deleted with their focus item too
        itemIds.forEach(id -> taskIds.addAll(repo.taskIdsOfFocusItem(id)));
        taskIds.forEach(repo::deleteTask);
        itemIds.forEach(repo::deleteFocusItem);
        List<String> children = repo.childGoalIds(goal.id());
        for (String child : children) {
            repo.detachFromParent(child, now);
            history.append(child, "parent_changed", json(Map.of("parentGoalId", goal.id())),
                    json(Collections.singletonMap("parentGoalId", null)), now);
        }
        repo.deleteGoal(goal.id());
        return new DeletionSrv(1, itemIds.size(), taskIds.size(), children.size());
    }

    private static void requireArchived(boolean archived, String field, String id) {
        if (!archived) {
            throw new ApiException.ConflictException("not_archived", MSG_NOT_ARCHIVED, Map.of(field, id));
        }
    }

    private String json(Map<String, ?> value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize goal history", e);
        }
    }
}
