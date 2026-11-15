package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.DailyPlan;
import com.lifeplanner.model.DailyPlanItem;
import com.lifeplanner.model.Task;
import com.lifeplanner.model.WeeklyPlanItem;
import com.lifeplanner.model.enums.TaskStatus;
import com.lifeplanner.model.srv.CarriedTaskSrv;
import com.lifeplanner.model.srv.DailyPlanItemSrv;
import com.lifeplanner.model.srv.DailyPlanSrv;
import com.lifeplanner.model.srv.TaskSrv;
import com.lifeplanner.repository.DailyPlanRepository;
import com.lifeplanner.repository.TaskRepository;
import com.lifeplanner.repository.WeeklyPlanRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DailyPlanService {

    static final String MSG_DUPLICATE = "این کار قبلاً به برنامه همین روز اضافه شده است.";
    static final String MSG_PLAN_NOT_FOUND = "برای این روز برنامه‌ای وجود ندارد.";
    static final String MSG_ORDER_INVALID = "فهرست باید همهٔ آیتم‌های برنامهٔ همین روز را دقیقاً یک بار داشته باشد.";
    static final String MSG_WINDOW_INVALID = "completedFrom و completedTo باید با هم و به ترتیب درست فرستاده شوند.";
    static final String MSG_TASK_ARCHIVED = "کار بایگانی‌شده را نمی‌توان به برنامه روز اضافه کرد.";

    /** Result of an idempotent create-or-get. */
    public record Ensured(DailyPlan plan, boolean created) {
    }

    private final DailyPlanRepository plans;
    private final TaskRepository taskRepository;
    private final WeeklyPlanRepository weeklyPlans;
    private final TaskService tasks;
    private final Clock clock;

    public DailyPlanService(DailyPlanRepository plans, TaskRepository taskRepository, WeeklyPlanRepository weeklyPlans,
                            TaskService tasks, Clock clock) {
        this.plans = plans;
        this.taskRepository = taskRepository;
        this.weeklyPlans = weeklyPlans;
        this.tasks = tasks;
        this.clock = clock;
    }

    /** Read-only: a missing plan is reported as empty and is NOT created. */
    public DailyPlanSrv view(LocalDate date) {
        return plans.findByDate(date)
                .map(this::toResponse)
                .orElseGet(() -> new DailyPlanSrv(date, null, false, List.of(), 0, 0, null, null, null));
    }

    @Transactional
    public Ensured ensure(LocalDate date) {
        return plans.findByDate(date)
                .map(existing -> new Ensured(existing, false))
                .orElseGet(() -> {
                    Instant now = now();
                    DailyPlan plan = new DailyPlan(UUID.randomUUID().toString(), date, now, now);
                    plans.insert(plan);
                    return new Ensured(plan, true);
                });
    }

    /** Assigns an existing task to the date's plan (created if needed). Same task twice on one date → 409. */
    @Transactional
    public DailyPlanItemSrv addTask(LocalDate date, String taskId) {
        return addTask(date, taskId, false);
    }

    /** Adds to the day and, when requested, to the containing Saturday–Friday week in the same transaction. */
    @Transactional
    public DailyPlanItemSrv addTask(LocalDate date, String taskId, boolean addToWeek) {
        Task task = tasks.get(taskId);
        if (task.isArchived()) {
            throw new ApiException.ConflictException("task_archived", MSG_TASK_ARCHIVED, Map.of("taskId", taskId));
        }
        DailyPlan plan = ensure(date).plan();
        if (plans.containsTask(plan.id(), task.id())) {
            throw duplicate(date, taskId);
        }
        Instant now = now();
        DailyPlanItem item = new DailyPlanItem(UUID.randomUUID().toString(), plan.id(), task.id(), plans.nextPosition(plan.id()), now);
        try {
            plans.insertItem(item);
        } catch (DuplicateKeyException e) {
            throw duplicate(date, taskId); // concurrent add raced past the check; UNIQUE(plan_id, task_id) holds
        }
        plans.touch(plan.id(), now);
        if (addToWeek) {
            LocalDate saturday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
            if (!weeklyPlans.contains(saturday, task.id())) {
                WeeklyPlanItem weeklyItem = new WeeklyPlanItem(UUID.randomUUID().toString(), saturday, task.id(), now);
                try {
                    weeklyPlans.insert(weeklyItem);
                } catch (DuplicateKeyException ignored) {
                    // A concurrent request already established the requested membership; the final state is correct.
                }
            }
        }
        return new DailyPlanItemSrv(item.id(), item.position(), item.createdAt(), TaskSrv.from(task));
    }

    /**
     * Moves a planned task from one date to another in one transaction (e.g. drag-and-drop in the calendar): the item
     * leaves {@code from} and is appended to {@code to} (plan created if needed). If the task is already on {@code to}
     * it simply leaves {@code from}. The task itself (status, dates) is not changed.
     */
    @Transactional
    public DailyPlanItemSrv moveTask(LocalDate from, String taskId, LocalDate to) {
        Task task = tasks.get(taskId);
        if (task.isArchived()) {
            throw new ApiException.ConflictException("task_archived", MSG_TASK_ARCHIVED, Map.of("taskId", taskId));
        }
        DailyPlan source = plans.findByDate(from).orElseThrow(() -> notPlanned(from, taskId));
        DailyPlanItem item = plans.findItemByTask(source.id(), taskId).orElseThrow(() -> notPlanned(from, taskId));
        if (from.equals(to)) {
            return new DailyPlanItemSrv(item.id(), item.position(), item.createdAt(), TaskSrv.from(task));
        }
        Instant now = now();
        plans.deleteItem(source.id(), item.id());
        plans.touch(source.id(), now);
        DailyPlan target = ensure(to).plan();
        DailyPlanItem moved = plans.findItemByTask(target.id(), taskId).orElseGet(() -> {
            DailyPlanItem fresh = new DailyPlanItem(UUID.randomUUID().toString(), target.id(), taskId, plans.nextPosition(target.id()), now);
            plans.insertItem(fresh);
            return fresh;
        });
        plans.touch(target.id(), now);
        return new DailyPlanItemSrv(moved.id(), moved.position(), moved.createdAt(), TaskSrv.from(task));
    }

    private static ApiException.NotFoundException notPlanned(LocalDate date, String taskId) {
        return new ApiException.NotFoundException("این کار در برنامهٔ آن روز نیست.", Map.of("date", date.toString(), "taskId", taskId));
    }

    /**
     * New manual order of a date's plan (e.g. the Kanban board). {@code itemIds} must list every item of the plan
     * exactly once; positions become 0..n-1. A date without a plan is 404 (nothing is created).
     */
    @Transactional
    public DailyPlanSrv reorder(LocalDate date, List<String> itemIds) {
        DailyPlan plan = plans.findByDate(date)
                .orElseThrow(() -> new ApiException.NotFoundException(MSG_PLAN_NOT_FOUND, Map.of("date", date.toString())));
        List<String> requested = itemIds == null ? List.of() : itemIds;
        java.util.Set<String> unique = new java.util.HashSet<>(requested);
        java.util.Set<String> current = plans.findItems(plan.id()).stream().map(DailyPlanItem::id)
                .collect(Collectors.toSet());
        if (requested.isEmpty() || unique.size() != requested.size() || !unique.equals(current)) {
            throw new ApiException.ValidationException(MSG_ORDER_INVALID, Map.of("itemIds",
                    requested.isEmpty() ? "required" : unique.size() != requested.size() ? "duplicate" : "must_list_all_items_of_plan"));
        }
        for (int i = 0; i < requested.size(); i++) {
            plans.setItemPosition(plan.id(), requested.get(i), i);
        }
        plans.touch(plan.id(), now());
        return toResponse(plans.findByDate(date).orElseThrow());
    }

    /**
     * Read-only extra cards of a day's board: tasks planned on an earlier day that are not on this day's plan and are
     * still unfinished, plus — when the client sends its local day as a UTC window — the ones finished inside it.
     * Nothing is written; the client plans a carried task on the day only when the user moves it.
     */
    public List<CarriedTaskSrv> carriedOver(LocalDate date, Instant completedFrom, Instant completedTo) {
        if ((completedFrom == null) != (completedTo == null)) {
            throw new ApiException.ValidationException(MSG_WINDOW_INVALID,
                    Map.of(completedFrom == null ? "completedFrom" : "completedTo", "required"));
        }
        if (completedFrom != null && !completedFrom.isBefore(completedTo)) {
            throw new ApiException.ValidationException(MSG_WINDOW_INVALID, Map.of("completedTo", "must_be_after_completedFrom"));
        }
        return taskRepository.findCarriedOver(date, completedFrom, completedTo).stream()
                .map(c -> new CarriedTaskSrv(c.plannedOn(), TaskSrv.from(c.task())))
                .toList();
    }

    public DailyPlanSrv toResponse(DailyPlan plan) {
        List<DailyPlanItem> items = plans.findItems(plan.id());
        Map<String, Task> byId = taskRepository.findByIds(items.stream().map(DailyPlanItem::taskId).toList())
                .stream().collect(Collectors.toMap(Task::id, Function.identity()));
        List<DailyPlanItemSrv> views = items.stream()
                .map(i -> new DailyPlanItemSrv(i.id(), i.position(), i.createdAt(), TaskSrv.from(byId.get(i.taskId()))))
                .toList();
        List<Task> active = items.stream().map(i -> byId.get(i.taskId())).filter(t -> !t.isArchived()).toList();
        int done = (int) active.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        BigDecimal percent = active.isEmpty() ? null
                : BigDecimal.valueOf(done * 100L).divide(BigDecimal.valueOf(active.size()), 2, RoundingMode.HALF_UP);
        return new DailyPlanSrv(plan.localDate(), plan.id(), true, views, active.size(), done, percent,
                plan.createdAt(), plan.updatedAt());
    }

    private static ApiException.ConflictException duplicate(LocalDate date, String taskId) {
        return new ApiException.ConflictException("duplicate_plan_task", MSG_DUPLICATE,
                Map.of("date", date.toString(), "taskId", taskId));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
