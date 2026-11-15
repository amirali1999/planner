package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.Task;
import com.lifeplanner.model.TimeBlock;
import com.lifeplanner.model.TimeBlockLimits;
import com.lifeplanner.model.enums.TimeBlockColor;
import com.lifeplanner.model.srv.TaskSrv;
import com.lifeplanner.model.srv.TimeBlockSrv;
import com.lifeplanner.model.vo.CreateTimeBlockVO;
import com.lifeplanner.model.vo.UpdateTimeBlockVO;
import com.lifeplanner.repository.TaskRepository;
import com.lifeplanner.repository.TimeBlockRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Weekly time-box: planned time on a local day, optionally linked to a task. A block is a planning aid only: it never
 * changes the task (status, plan, progress). Blocks of one day never overlap; blocks of archived tasks are ignored for
 * that check (they are hidden in the UI).
 */
@Service
public class TimeBlockService {

    public static final String MSG_INVALID = "اطلاعات بلاک زمانی معتبر نیست.";
    static final String MSG_NOT_FOUND = "بلاک زمانی پیدا نشد.";
    static final String MSG_OVERLAP = "این بازه با بلاک زمانی دیگری در همان روز هم‌پوشانی دارد.";
    static final String MSG_TASK_ARCHIVED = "کار بایگانی‌شده را نمی‌توان به بلاک زمانی وصل کرد.";

    private final TimeBlockRepository blocks;
    private final TaskRepository taskRepository;
    private final TaskService tasks;
    private final Clock clock;

    public TimeBlockService(TimeBlockRepository blocks, TaskRepository taskRepository, TaskService tasks, Clock clock) {
        this.blocks = blocks;
        this.taskRepository = taskRepository;
        this.tasks = tasks;
        this.clock = clock;
    }

    public List<TimeBlockSrv> list(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException.ValidationException(MSG_INVALID, Map.of("to", "must_not_be_before_from"));
        }
        if (ChronoUnit.DAYS.between(from, to) >= TimeBlockLimits.MAX_RANGE_DAYS) {
            throw new ApiException.ValidationException(MSG_INVALID, Map.of("to", "range_too_long"));
        }
        return toResponses(blocks.findBetween(from, to));
    }

    public TimeBlockSrv get(String id) {
        return toResponses(List.of(find(id))).get(0);
    }

    @Transactional
    public TimeBlockSrv create(CreateTimeBlockVO req) {
        String title = trimToNull(req.title());
        String taskId = trimToNull(req.taskId());
        validate(req.startMinute(), req.endMinute(), title, taskId);
        if (taskId != null) {
            requireLinkableTask(taskId);
        }
        requireFree(req.date(), req.startMinute(), req.endMinute(), null);
        Instant now = now();
        TimeBlock block = new TimeBlock(UUID.randomUUID().toString(), req.date(), req.startMinute(), req.endMinute(), title,
                taskId, req.color() == null ? TimeBlockColor.TEAL : req.color(), now, now);
        blocks.insert(block);
        return get(block.id());
    }

    @Transactional
    public TimeBlockSrv update(String id, UpdateTimeBlockVO req) {
        TimeBlock current = find(id);
        Map<String, Object> errors = new LinkedHashMap<>();
        LocalDate date = required(errors, "date", req.date(), current.date());
        Integer start = required(errors, "startMinute", req.startMinute(), current.startMinute());
        Integer end = required(errors, "endMinute", req.endMinute(), current.endMinute());
        TimeBlockColor color = required(errors, "color", req.color(), current.color());
        throwIfAny(errors);
        String title = req.title() == null ? current.title() : trimToNull(req.title().orElse(null));
        String taskId = req.taskId() == null ? current.taskId() : trimToNull(req.taskId().orElse(null));
        validate(start, end, title, taskId);
        if (taskId != null && !taskId.equals(current.taskId())) {
            requireLinkableTask(taskId); // an existing link to a since-archived task may stay
        }
        requireFree(date, start, end, id);
        blocks.update(new TimeBlock(id, date, start, end, title, taskId, color, current.createdAt(), now()));
        return get(id);
    }

    @Transactional
    public void delete(String id) {
        if (blocks.delete(id) == 0) {
            throw new ApiException.NotFoundException(MSG_NOT_FOUND, Map.of("timeBlockId", id));
        }
    }

    private TimeBlock find(String id) {
        return blocks.findById(id).orElseThrow(() ->
                new ApiException.NotFoundException(MSG_NOT_FOUND, Map.of("timeBlockId", id)));
    }

    private static void validate(int start, int end, String title, String taskId) {
        Map<String, Object> errors = new LinkedHashMap<>();
        if (start < 0 || start >= TimeBlockLimits.DAY_MINUTES || start % TimeBlockLimits.STEP_MINUTES != 0) {
            errors.put("startMinute", "invalid_value");
        }
        if (end <= 0 || end > TimeBlockLimits.DAY_MINUTES || end % TimeBlockLimits.STEP_MINUTES != 0) {
            errors.put("endMinute", "invalid_value");
        } else if (!errors.containsKey("startMinute") && end <= start) {
            errors.put("endMinute", "must_be_after_startMinute");
        }
        if (title != null && title.length() > TimeBlockLimits.TITLE_MAX) {
            errors.put("title", "too_long");
        }
        if (title == null && taskId == null) {
            errors.put("title", "required"); // a free block needs a title
        }
        throwIfAny(errors);
    }

    private void requireLinkableTask(String taskId) {
        Task task = tasks.get(taskId); // 404 for unknown / another user's task
        if (task.isArchived()) {
            throw new ApiException.ConflictException("task_archived", MSG_TASK_ARCHIVED, Map.of("taskId", taskId));
        }
    }

    private void requireFree(LocalDate date, int start, int end, String exceptId) {
        blocks.findOverlapping(date, start, end, exceptId).ifPresent(other -> {
            throw new ApiException.ConflictException("time_block_overlap", MSG_OVERLAP, Map.of("timeBlockId", other));
        });
    }

    private List<TimeBlockSrv> toResponses(List<TimeBlock> list) {
        Map<String, Task> byId = taskRepository.findByIds(list.stream().map(TimeBlock::taskId)
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Task::id, Function.identity()));
        return list.stream().map(b -> {
            Task t = b.taskId() == null ? null : byId.get(b.taskId());
            return new TimeBlockSrv(b.id(), b.date(), b.startMinute(), b.endMinute(), b.title(), b.taskId(),
                    t == null ? null : TaskSrv.from(t), b.color().code(), b.createdAt(), b.updatedAt());
        }).toList();
    }

    private static <T> T required(Map<String, Object> errors, String field, java.util.Optional<T> patch, T current) {
        if (patch == null) {
            return current;
        }
        if (patch.isEmpty()) {
            errors.put(field, "required");
            return current;
        }
        return patch.get();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void throwIfAny(Map<String, Object> errors) {
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID, errors);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
