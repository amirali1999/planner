package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.Task;
import com.lifeplanner.model.WeeklyPlanItem;
import com.lifeplanner.model.srv.TaskSrv;
import com.lifeplanner.model.srv.WeeklyPlanItemSrv;
import com.lifeplanner.repository.DailyPlanRepository;
import com.lifeplanner.repository.TaskRepository;
import com.lifeplanner.repository.WeeklyPlanRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tasks put on a Saturday–Friday week without a day. Only membership (no capacity, no commitment): the weekly page
 * shows them next to the week's daily plans and deadlines. A task is on a week at most once.
 */
@Service
public class WeeklyPlanService {

    static final String MSG_NOT_SATURDAY = "هفته باید با تاریخ شنبهٔ همان هفته مشخص شود.";
    static final String MSG_DUPLICATE = "این کار قبلاً به برنامهٔ همین هفته اضافه شده است.";
    static final String MSG_TASK_ARCHIVED = "کار بایگانی‌شده را نمی‌توان به برنامهٔ هفته اضافه کرد.";
    static final String MSG_NOT_PLANNED = "این کار در برنامهٔ این هفته نیست.";

    private final WeeklyPlanRepository items;
    private final DailyPlanRepository dailyPlans;
    private final TaskRepository taskRepository;
    private final TaskService tasks;
    private final Clock clock;

    public WeeklyPlanService(WeeklyPlanRepository items, DailyPlanRepository dailyPlans,
                             TaskRepository taskRepository, TaskService tasks, Clock clock) {
        this.items = items;
        this.dailyPlans = dailyPlans;
        this.taskRepository = taskRepository;
        this.tasks = tasks;
        this.clock = clock;
    }

    public List<WeeklyPlanItemSrv> list(LocalDate weekStart) {
        requireSaturday(weekStart);
        List<WeeklyPlanItem> list = items.findByWeek(weekStart);
        Map<String, Task> byId = taskRepository.findByIds(list.stream().map(WeeklyPlanItem::taskId).toList()).stream()
                .collect(Collectors.toMap(Task::id, Function.identity()));
        return list.stream().map(i -> new WeeklyPlanItemSrv(i.id(), i.weekStart(), i.createdAt(), TaskSrv.from(byId.get(i.taskId()))))
                .toList();
    }

    @Transactional
    public WeeklyPlanItemSrv addTask(LocalDate weekStart, String taskId) {
        requireSaturday(weekStart);
        Task task = tasks.get(taskId); // 404 for unknown / another user's task
        if (task.isArchived()) {
            throw new ApiException.ConflictException("task_archived", MSG_TASK_ARCHIVED, Map.of("taskId", taskId));
        }
        if (items.contains(weekStart, taskId)) {
            throw duplicate(weekStart, taskId);
        }
        WeeklyPlanItem item = new WeeklyPlanItem(UUID.randomUUID().toString(), weekStart, taskId,
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
        try {
            items.insert(item);
        } catch (DuplicateKeyException e) {
            throw duplicate(weekStart, taskId);
        }
        return new WeeklyPlanItemSrv(item.id(), weekStart, item.createdAt(), TaskSrv.from(task));
    }

    /**
     * Removes the task's planning membership from this entire Saturday–Friday week: both the undated weekly item and
     * every daily-plan item in the week. Task fields, especially dueDate and status, are deliberately unchanged.
     */
    @Transactional
    public void removeTask(LocalDate weekStart, String taskId) {
        requireSaturday(weekStart);
        tasks.get(taskId); // 404 for unknown / another user's task
        int removed = items.delete(weekStart, taskId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        for (int offset = 0; offset < 7; offset++) {
            var plan = dailyPlans.findByDate(weekStart.plusDays(offset));
            if (plan.isPresent()) {
                var dailyItem = dailyPlans.findItemByTask(plan.get().id(), taskId);
                if (dailyItem.isPresent()) {
                    removed += dailyPlans.deleteItem(plan.get().id(), dailyItem.get().id());
                    dailyPlans.touch(plan.get().id(), now);
                }
            }
        }
        if (removed == 0) {
            throw new ApiException.NotFoundException(MSG_NOT_PLANNED,
                    Map.of("weekStart", weekStart.toString(), "taskId", taskId));
        }
    }

    private static void requireSaturday(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.SATURDAY) {
            throw new ApiException.ValidationException(MSG_NOT_SATURDAY, Map.of("weekStart", "must_be_saturday"));
        }
    }

    private static ApiException.ConflictException duplicate(LocalDate weekStart, String taskId) {
        return new ApiException.ConflictException("duplicate_plan_task", MSG_DUPLICATE,
                Map.of("weekStart", weekStart.toString(), "taskId", taskId));
    }
}
