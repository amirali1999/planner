package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.Milestone;
import com.lifeplanner.model.MilestoneLimits;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.MilestoneStatusFilter;
import com.lifeplanner.model.vo.CreateMilestoneVO;
import com.lifeplanner.model.vo.UpdateMilestoneVO;
import com.lifeplanner.repository.MilestoneRepository;
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

/**
 * Milestones are delivery checkpoints of a Goal. They are deliberately independent of progress: nothing here touches
 * KRs, check-ins, tasks or {@code ProgressCalculator}. Nothing is hard-deleted; archive keeps the row.
 */
@Service
public class MilestoneService {

    public static final String MSG_INVALID = "اطلاعات نقطه تحویل معتبر نیست.";
    static final String MSG_NOT_FOUND = "نقطه تحویل پیدا نشد.";
    static final String MSG_GOAL_ARCHIVED = "هدف بایگانی شده است؛ نقاط تحویل آن قابل تغییر نیستند.";
    static final String MSG_ARCHIVED = "این نقطه تحویل بایگانی شده است؛ ابتدا آن را از بایگانی خارج کنید.";

    private final MilestoneRepository milestones;
    private final GoalService goals;
    private final Clock clock;

    public MilestoneService(MilestoneRepository milestones, GoalService goals, Clock clock) {
        this.milestones = milestones;
        this.goals = goals;
        this.clock = clock;
    }

    public List<Milestone> list(String goalId, MilestoneStatusFilter status, LocalDate today, ArchiveFilter archive) {
        goals.get(goalId); // 404 for unknown goals; archived goals stay readable
        return milestones.findByGoal(goalId, status, today, archive);
    }

    public Milestone get(String id) {
        return milestones.findById(id).orElseThrow(() ->
                new ApiException.NotFoundException(MSG_NOT_FOUND, Map.of("milestoneId", id)));
    }

    @Transactional
    public Milestone create(String goalId, CreateMilestoneVO req) {
        Goal goal = goals.get(goalId);
        requireGoalActive(goal);
        if (!IsoDates.isStorable(req.dueDate())) {
            throw new ApiException.ValidationException(MSG_INVALID, Map.of("dueDate", "out_of_range"));
        }
        Instant now = now();
        Milestone m = new Milestone(UUID.randomUUID().toString(), goal.id(), req.title().trim(), blankToNull(req.description()),
                req.dueDate(), null, null, now, now);
        milestones.insert(m);
        return m;
    }

    /**
     * {@code completed: true/false} sets/clears completedAt; {@code archived: true/false} archives/restores.
     * Editing an archived milestone is refused unless the same request restores it. No-op requests write nothing.
     */
    @Transactional
    public Milestone update(String id, UpdateMilestoneVO req) {
        Milestone current = get(id);
        requireGoalActive(goals.get(current.goalId()));
        Instant now = now();

        Instant archivedAt = current.archivedAt();
        if (req.archived() != null) {
            boolean archive = req.archived().orElseThrow(() -> invalid("archived", "required"));
            archivedAt = archive ? (current.isArchived() ? current.archivedAt() : now) : null;
        }
        boolean editsFields = req.title() != null || req.description() != null || req.dueDate() != null || req.completed() != null;
        if (editsFields && archivedAt != null) {
            throw new ApiException.ConflictException("milestone_archived", MSG_ARCHIVED, Map.of("milestoneId", id));
        }

        Map<String, Object> errors = new LinkedHashMap<>();
        String title = current.title();
        if (req.title() != null) {
            title = req.title().map(String::trim).orElse("");
            if (title.isEmpty()) {
                errors.put("title", "required");
            } else if (title.length() > MilestoneLimits.TITLE_MAX) {
                errors.put("title", "too_long");
            }
        }
        String description = current.description();
        if (req.description() != null) {
            description = blankToNull(req.description().orElse(null));
            if (description != null && description.length() > MilestoneLimits.DESCRIPTION_MAX) {
                errors.put("description", "too_long");
            }
        }
        LocalDate dueDate = req.dueDate() == null ? current.dueDate() : req.dueDate().orElse(null);
        if (!IsoDates.isStorable(dueDate)) {
            errors.put("dueDate", "out_of_range");
        }
        Instant completedAt = current.completedAt();
        if (req.completed() != null) {
            Boolean completed = req.completed().orElse(null);
            if (completed == null) {
                errors.put("completed", "required");
            } else {
                completedAt = completed ? (current.isCompleted() ? current.completedAt() : now) : null;
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID, errors);
        }

        Milestone updated = new Milestone(current.id(), current.goalId(), title, description, dueDate, completedAt, archivedAt,
                current.createdAt(), current.updatedAt());
        if (updated.equals(current)) {
            return current;
        }
        updated = new Milestone(updated.id(), updated.goalId(), title, description, dueDate, completedAt, archivedAt,
                current.createdAt(), now);
        milestones.update(updated);
        return updated;
    }

    private static void requireGoalActive(Goal goal) {
        if (goal.isArchived()) {
            throw new ApiException.ConflictException("goal_archived", MSG_GOAL_ARCHIVED, Map.of("goalId", goal.id()));
        }
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
