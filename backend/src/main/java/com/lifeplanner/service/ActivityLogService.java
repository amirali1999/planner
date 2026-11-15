package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.ActivityLog;
import com.lifeplanner.model.ActivityLogLimits;
import com.lifeplanner.model.enums.ActivityCategory;
import com.lifeplanner.model.srv.ActivityLogSrv;
import com.lifeplanner.model.vo.CreateActivityLogVO;
import com.lifeplanner.model.vo.UpdateActivityLogVO;
import com.lifeplanner.repository.ActivityLogRepository;
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
import java.util.UUID;

/** CRUD for intervals that actually happened; deliberately independent from plans, tasks and task status. */
@Service
public class ActivityLogService {

    static final String MSG_INVALID = "اطلاعات فعالیت روزانه معتبر نیست.";
    static final String MSG_NOT_FOUND = "فعالیت روزانه پیدا نشد.";
    static final String MSG_OVERLAP = "این بازه با فعالیت ثبت‌شدهٔ دیگری در همان روز هم‌پوشانی دارد.";

    private final ActivityLogRepository repository;
    private final Clock clock;
    private final TaskService tasks;

    public ActivityLogService(ActivityLogRepository repository, Clock clock, TaskService tasks) {
        this.repository = repository;
        this.clock = clock;
        this.tasks = tasks;
    }

    public List<ActivityLogSrv> list(LocalDate date) {
        return repository.findByDate(date).stream().map(ActivityLogSrv::from).toList();
    }

    @Transactional
    public ActivityLogSrv create(CreateActivityLogVO request) {
        String title = normalized(request.title());
        validate(request.date(), title, request.category(), request.startMinute(), request.endMinute());
        requireFree(request.date(), request.startMinute(), request.endMinute(), null);
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
