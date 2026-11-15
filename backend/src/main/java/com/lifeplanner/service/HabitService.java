package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.Habit;
import com.lifeplanner.model.HabitLimits;
import com.lifeplanner.model.srv.HabitSrv;
import com.lifeplanner.model.vo.CreateHabitVO;
import com.lifeplanner.model.vo.UpdateHabitVO;
import com.lifeplanner.repository.HabitRepository;
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
import java.util.stream.Collectors;

/**
 * Daily habits: a title and the local days it was done. Archiving hides a habit and keeps its checks; an archived habit
 * cannot be ticked. GET never writes.
 */
@Service
public class HabitService {

    public static final String MSG_INVALID = "اطلاعات عادت معتبر نیست.";
    static final String MSG_NOT_FOUND = "عادت پیدا نشد.";
    static final String MSG_ARCHIVED = "عادت بایگانی‌شده را نمی‌توان علامت زد.";

    private final HabitRepository habits;
    private final Clock clock;

    public HabitService(HabitRepository habits, Clock clock) {
        this.habits = habits;
        this.clock = clock;
    }

    public List<HabitSrv> list(LocalDate from, LocalDate to, boolean archived) {
        if (to.isBefore(from)) {
            throw new ApiException.ValidationException(MSG_INVALID, Map.of("to", "must_not_be_before_from"));
        }
        if (ChronoUnit.DAYS.between(from, to) >= HabitLimits.MAX_RANGE_DAYS) {
            throw new ApiException.ValidationException(MSG_INVALID, Map.of("to", "range_too_long"));
        }
        Map<String, List<LocalDate>> done = habits.findChecks(from, to).stream().collect(Collectors.groupingBy(
                HabitRepository.Check::habitId, Collectors.mapping(HabitRepository.Check::date, Collectors.toList())));
        return habits.findAll(archived).stream().map(h -> toSrv(h, done.getOrDefault(h.id(), List.of()))).toList();
    }

    @Transactional
    public HabitSrv create(CreateHabitVO req) {
        Instant now = now();
        Habit habit = new Habit(UUID.randomUUID().toString(), req.title().trim(), null, now, now);
        habits.insert(habit);
        return toSrv(habit, List.of());
    }

    @Transactional
    public HabitSrv update(String id, UpdateHabitVO req) {
        Habit current = find(id);
        Map<String, Object> errors = new LinkedHashMap<>();
        String title = current.title();
        if (req.title() != null) {
            String t = req.title().map(String::trim).orElse("");
            if (t.isEmpty()) {
                errors.put("title", "required");
            } else if (t.length() > HabitLimits.TITLE_MAX) {
                errors.put("title", "too_long");
            } else {
                title = t;
            }
        }
        Instant archivedAt = current.archivedAt();
        if (req.archived() != null) {
            if (req.archived().isEmpty()) {
                errors.put("archived", "required");
            } else if (req.archived().get()) {
                archivedAt = archivedAt == null ? now() : archivedAt;
            } else {
                archivedAt = null;
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID, errors);
        }
        Habit updated = new Habit(id, title, archivedAt, current.createdAt(), now());
        habits.update(updated);
        return toSrv(updated, List.of());
    }

    @Transactional
    public void setDone(String id, LocalDate date, boolean done) {
        Habit habit = find(id);
        if (habit.isArchived()) {
            throw new ApiException.ConflictException("habit_archived", MSG_ARCHIVED, Map.of("habitId", id));
        }
        if (done) {
            habits.check(id, date, now());
        } else {
            habits.uncheck(id, date);
        }
    }

    private Habit find(String id) {
        return habits.findById(id).orElseThrow(() -> new ApiException.NotFoundException(MSG_NOT_FOUND, Map.of("habitId", id)));
    }

    private static HabitSrv toSrv(Habit h, List<LocalDate> done) {
        return new HabitSrv(h.id(), h.title(), done.stream().sorted().toList(), h.archivedAt(), h.createdAt(), h.updatedAt());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
