package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.ActivityLog;
import com.lifeplanner.model.ActivityLogLimits;
import com.lifeplanner.model.enums.ActivityCategory;
import com.lifeplanner.model.srv.ActivityLogSrv;
import com.lifeplanner.model.vo.CreateActivityLogVO;
import com.lifeplanner.model.vo.UpdateActivityLogVO;
import com.lifeplanner.repository.ActivityLogRepository;
import com.lifeplanner.repository.ActivityTimerRepository;
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

/** CRUD for intervals that actually happened; independent from plans/status, but mutually exclusive with a running timer. */
@Service
public class ActivityLogService {

    static final String MSG_INVALID = "اطلاعات فعالیت روزانه معتبر نیست.";
    static final String MSG_NOT_FOUND = "فعالیت روزانه پیدا نشد.";
    static final String MSG_OVERLAP = "این بازه با فعالیت ثبت‌شدهٔ دیگری در همان روز هم‌پوشانی دارد.";
    static final String MSG_TIMER_OVERLAP = "این بازه با تایمر در حال اجرا هم‌پوشانی دارد؛ ابتدا تایمر را ثبت یا لغو کنید.";

    private final ActivityLogRepository repository;
    private final Clock clock;
    private final TaskService tasks;
    private final ActivityTimerRepository timers;

    public ActivityLogService(ActivityLogRepository repository, Clock clock, TaskService tasks, ActivityTimerRepository timers) {
        this.repository = repository;
        this.clock = clock;
        this.tasks = tasks;
        this.timers = timers;
    }

    public List<ActivityLogSrv> list(LocalDate date) {
        return repository.findByDate(date).stream().map(ActivityLogSrv::from).toList();
    }

    @Transactional
    public ActivityLogSrv create(CreateActivityLogVO request) {
        return create(request, true);
    }

    ActivityLogSrv createFromTimer(CreateActivityLogVO request) {
        return create(request, false);
    }

    private ActivityLogSrv create(CreateActivityLogVO request, boolean checkRunningTimer) {
        String title = normalized(request.title());
        validate(request.date(), title, request.category(), request.startMinute(), request.endMinute());
        requireFree(request.date(), request.startMinute(), request.endMinute(), null);
        if (checkRunningTimer) requireFreeOfTimer(request.date(), request.startMinute(), request.endMinute());
        Instant now = now();
        ActivityLog log = new ActivityLog(UUID.randomUUID().toString(), request.date(), title, request.category(),
                request.startMinute(), request.endMinute(), link(request.taskId()), now, now);
        repository.insert(log);
        return ActivityLogSrv.from(log);
    }

    @Transactional
    public ActivityLogSrv update(String id, UpdateActivityLogVO request) {
        ActivityLog current = find(id);
        String title = normalized(request.title());
        validate(request.date(), title, request.category(), request.startMinute(), request.endMinute());
        requireFree(request.date(), request.startMinute(), request.endMinute(), id);
        requireFreeOfTimer(request.date(), request.startMinute(), request.endMinute());
        ActivityLog updated = new ActivityLog(id, request.date(), title, request.category(), request.startMinute(),
                request.endMinute(), link(request.taskId()), current.createdAt(), now());
        repository.update(updated);
        return ActivityLogSrv.from(updated);
    }

    @Transactional
    public void delete(String id) {
        if (repository.delete(id) == 0) {
            throw notFound(id);
        }
    }

    private ActivityLog find(String id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private void requireFree(LocalDate date, int start, int end, String exceptId) {
        repository.findOverlapping(date, start, end, exceptId).ifPresent(other -> {
            throw new ApiException.ConflictException("activity_log_overlap", MSG_OVERLAP, Map.of("activityLogId", other));
        });
    }

    void requireFreeForTimer(LocalDate date, int start, int end) {
        requireFree(date, start, end, null);
    }

    Optional<ActivityLogSrv> firstOverlapForTimer(LocalDate date, int start, int end) {
        return repository.findFirstOverlapping(date, start, end, null).map(ActivityLogSrv::from);
    }

    private void requireFreeOfTimer(LocalDate date, int start, int end) {
        timers.get().ifPresent(timer -> {
            Instant finish = ActivityTimerIntervals.protectedFinish(timer, clock.instant());
            if (ActivityTimerIntervals.overlaps(timer, finish, date, start, end)) {
                throw new ApiException.ConflictException("activity_timer_overlap", MSG_TIMER_OVERLAP,
                        Map.of("timerStartedAt", timer.startedAt().toString()));
            }
        });
    }

    private static void validate(LocalDate date, String title, ActivityCategory category, Integer start, Integer end) {
        Map<String, Object> errors = new LinkedHashMap<>();
        if (date == null || !IsoDates.isStorable(date)) errors.put("date", "invalid_value");
        if (title == null) errors.put("title", "required");
        else if (title.length() > ActivityLogLimits.TITLE_MAX) errors.put("title", "too_long");
        if (category == null) errors.put("category", "required");
        if (start == null || start < 0 || start >= ActivityLogLimits.DAY_MINUTES) {
            errors.put("startMinute", "invalid_value");
        }
        if (end == null || end <= 0 || end > ActivityLogLimits.DAY_MINUTES) {
            errors.put("endMinute", "invalid_value");
        } else if (!errors.containsKey("startMinute") && end <= start) {
            errors.put("endMinute", "must_be_after_startMinute");
        }
        if (!errors.isEmpty()) throw new ApiException.ValidationException(MSG_INVALID, errors);
    }

    private static String normalized(String title) {
        return title == null || title.isBlank() ? null : title.trim();
    }

    private ApiException.NotFoundException notFound(String id) {
        return new ApiException.NotFoundException(MSG_NOT_FOUND, Map.of("activityLogId", id));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private String link(String taskId) {
        if (taskId == null || taskId.isBlank()) return null;
        if (tasks.get(taskId).isArchived()) throw new ApiException.ConflictException("task_archived", "این تسک بایگانی شده است.", Map.of("taskId", taskId));
        return taskId;
    }
}
