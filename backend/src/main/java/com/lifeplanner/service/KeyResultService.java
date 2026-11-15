package com.lifeplanner.service;

import com.lifeplanner.db.DecimalCodec;
import com.lifeplanner.db.UtcTimestamps;
import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.CheckIn;
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.KeyResult;
import com.lifeplanner.model.srv.KeyResultHistorySrv;
import com.lifeplanner.model.vo.CreateCheckInVO;
import com.lifeplanner.model.vo.CreateKeyResultVO;
import com.lifeplanner.model.vo.UpdateKeyResultVO;
import com.lifeplanner.repository.GoalHistoryRepository;
import com.lifeplanner.repository.KeyResultRepository;
import com.lifeplanner.util.IsoDates;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class KeyResultService {

    static final String MSG_KR_NOT_FOUND = "نتیجه کلیدی پیدا نشد.";
    public static final String MSG_INVALID_KR = "اطلاعات نتیجه کلیدی معتبر نیست.";
    static final String MSG_INVALID_CHECK_IN = "اطلاعات ثبت اندازه‌گیری معتبر نیست.";
    static final String MSG_GOAL_ARCHIVED = "هدف بایگانی شده است و نتیجه کلیدی جدید نمی‌پذیرد.";
    static final String MSG_KR_ARCHIVED = "نتیجه کلیدی بایگانی شده است و اندازه‌گیری جدید نمی‌پذیرد.";
    static final String MSG_KR_ARCHIVED_EDIT = "نتیجه کلیدی بایگانی شده است و قابل ویرایش نیست.";
    static final String MSG_GOAL_ARCHIVED_EDIT = "هدف بایگانی شده است؛ نتایج کلیدی آن قابل تغییر نیستند.";

    private static final int NAME_MAX = 200;
    private static final int UNIT_MAX = 32;
    private static final int NOTE_MAX = 1000;

    private final KeyResultRepository keyResults;
    private final GoalService goals;
    private final GoalHistoryRepository history;
    private final ObjectMapper mapper;
    private final Clock clock;

    public KeyResultService(KeyResultRepository keyResults, GoalService goals, GoalHistoryRepository history,
                            ObjectMapper mapper, Clock clock) {
        this.keyResults = keyResults;
        this.goals = goals;
        this.history = history;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<KeyResult> listForGoal(String goalId, boolean includeArchived) {
        goals.get(goalId);
        return keyResults.findByGoal(goalId, includeArchived);
    }

    public KeyResult get(String keyResultId) {
        return keyResults.findById(keyResultId).orElseThrow(() ->
                new ApiException.NotFoundException(MSG_KR_NOT_FOUND, Map.of("keyResultId", keyResultId)));
    }

    @Transactional
    public KeyResult create(String goalId, CreateKeyResultVO req) {
        Goal goal = goals.get(goalId);
        if (goal.isArchived()) {
            throw new ApiException.ConflictException("goal_archived", MSG_GOAL_ARCHIVED, Map.of("goalId", goalId));
        }
        BigDecimal weight = req.weight() == null ? BigDecimal.ONE : req.weight();

        Map<String, Object> errors = new LinkedHashMap<>();
        rejectUnstorable(errors, "baseline", req.baseline());
        rejectUnstorable(errors, "target", req.target());
        rejectUnstorable(errors, "weight", weight);
        if (!errors.containsKey("weight") && weight.signum() <= 0) {
            errors.put("weight", "must_be_positive");
        }
        if (!errors.containsKey("baseline") && !errors.containsKey("target")
                && req.baseline().compareTo(req.target()) == 0) {
            errors.put("target", "equals_baseline");
        }
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID_KR, errors);
        }

        Instant now = now();
        KeyResult kr = new KeyResult(UUID.randomUUID().toString(), goal.id(), req.name().trim(), blankToNull(req.unit()),
                req.baseline(), req.target(), weight, null, now, now, null, null);
        keyResults.insert(kr);
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("name", kr.name());
        created.put("unit", kr.unit());
        created.put("baseline", DecimalCodec.toDb(kr.baseline()));
        created.put("target", DecimalCodec.toDb(kr.target()));
        created.put("weight", DecimalCodec.toDb(kr.weight()));
        audit(goal.id(), kr.id(), "key_result_created", Map.of(), created, null, now);
        return get(kr.id());
    }

    /**
     * Edits name/unit and, as an explicit recalibration with a mandatory note, baseline/target/weight.
     * Existing check-ins are never rewritten; current and score are re-derived from them. No-op requests write nothing.
     */
    @Transactional
    public KeyResult update(String keyResultId, UpdateKeyResultVO req) {
        KeyResult current = get(keyResultId);
        requireEditable(current);

        Map<String, Object> errors = new LinkedHashMap<>();
        String name = current.name();
        if (req.name() != null) {
            name = req.name().map(String::trim).orElse("");
            if (name.isEmpty()) {
                errors.put("name", "required");
            } else if (name.length() > NAME_MAX) {
                errors.put("name", "too_long");
            }
        }
        String unit = current.unit();
        if (req.unit() != null) {
            unit = blankToNull(req.unit().orElse(null));
            if (unit != null && unit.length() > UNIT_MAX) {
                errors.put("unit", "too_long");
            }
        }
        BigDecimal baseline = requiredDecimal(errors, "baseline", req.baseline(), current.baseline());
        BigDecimal target = requiredDecimal(errors, "target", req.target(), current.target());
        BigDecimal weight = requiredDecimal(errors, "weight", req.weight(), current.weight());
        if (weight != null && !errors.containsKey("weight") && weight.signum() <= 0) {
            errors.put("weight", "must_be_positive");
        }
        if (baseline != null && target != null && !errors.containsKey("baseline") && !errors.containsKey("target")
                && baseline.compareTo(target) == 0) {
            errors.put(req.target() != null ? "target" : "baseline", "equals_baseline");
        }
        String note = req.note() == null ? null : blankToNull(req.note().orElse(null));
        if (note != null && note.length() > NOTE_MAX) {
            errors.put("note", "too_long");
        }
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID_KR, errors);
        }

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        diff(before, after, "name", current.name(), name);
        diff(before, after, "unit", current.unit(), unit);
        boolean recalibrated = decimalDiff(before, after, "baseline", current.baseline(), baseline)
                | decimalDiff(before, after, "target", current.target(), target)
                | decimalDiff(before, after, "weight", current.weight(), weight);
        if (before.isEmpty() && after.isEmpty()) {
            return current;
        }
        if (recalibrated && note == null) {
            throw new ApiException.ValidationException(MSG_INVALID_KR, Map.of("note", "required_for_recalibration"));
        }

        Instant now = now();
        KeyResult updated = new KeyResult(current.id(), current.goalId(), name, unit, baseline, target, weight,
                null, current.createdAt(), now, null, null);
        keyResults.update(updated);
        audit(current.goalId(), current.id(), "key_result_changed", before, after, note, now);
        return get(current.id());
    }

    /** Idempotent. Check-ins remain readable; archived KRs no longer count toward Outcome. */
    @Transactional
    public KeyResult archive(String keyResultId, String note) {
        KeyResult current = get(keyResultId);
        if (current.isArchived()) {
            return current;
        }
        requireGoalActive(current);
        Instant now = now();
        keyResults.update(new KeyResult(current.id(), current.goalId(), current.name(), current.unit(), current.baseline(),
                current.target(), current.weight(), now, current.createdAt(), now, null, null));
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("archivedAt", null);
        audit(current.goalId(), current.id(), "key_result_archived", before, Map.of("archivedAt", UtcTimestamps.toDb(now)),
                blankToNull(note), now);
        return get(current.id());
    }

    /** Audited structural changes of one KR, chronological. Works for archived KRs too (history never disappears). */
    public List<KeyResultHistorySrv> history(String keyResultId) {
        get(keyResultId);
        return history.findByKeyResult(keyResultId).stream()
                .map(e -> new KeyResultHistorySrv(e.id(), e.eventType(), readJson(e.beforeJson()), readJson(e.afterJson()),
                        e.note(), e.createdAt()))
                .toList();
    }

    private void requireEditable(KeyResult kr) {
        if (kr.isArchived()) {
            throw new ApiException.ConflictException("key_result_archived", MSG_KR_ARCHIVED_EDIT, Map.of("keyResultId", kr.id()));
        }
        requireGoalActive(kr);
    }

    private void requireGoalActive(KeyResult kr) {
        if (goals.get(kr.goalId()).isArchived()) {
            throw new ApiException.ConflictException("goal_archived", MSG_GOAL_ARCHIVED_EDIT, Map.of("goalId", kr.goalId()));
        }
    }

    private static BigDecimal requiredDecimal(Map<String, Object> errors, String field, Optional<BigDecimal> patch, BigDecimal current) {
        if (patch == null) {
            return current;
        }
        if (patch.isEmpty()) {
            errors.put(field, "required");
            return null;
        }
        if (!DecimalCodec.isStorable(patch.get())) {
            errors.put(field, "precision_exceeded");
            return null;
        }
        return DecimalCodec.fromDb(DecimalCodec.toDb(patch.get()));
    }

    private static void diff(Map<String, Object> before, Map<String, Object> after, String field, Object a, Object b) {
        if (!Objects.equals(a, b)) {
            before.put(field, a);
            after.put(field, b);
        }
    }

    /** Numeric comparison (20 == 20.00); values recorded in canonical text form. */
    private static boolean decimalDiff(Map<String, Object> before, Map<String, Object> after, String field, BigDecimal a, BigDecimal b) {
        if (a.compareTo(b) == 0) {
            return false;
        }
        before.put(field, DecimalCodec.toDb(a));
        after.put(field, DecimalCodec.toDb(b));
        return true;
    }

    private void audit(String goalId, String krId, String event, Map<String, ?> before, Map<String, ?> after, String note, Instant at) {
        try {
            history.appendKeyResult(goalId, krId, event, mapper.writeValueAsString(before), mapper.writeValueAsString(after), note, at);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize key result history", e);
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt history JSON", e);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    public List<CheckIn> listCheckIns(String keyResultId) {
        get(keyResultId);
        return keyResults.findCheckIns(keyResultId);
    }

    /** Appends a measurement. Corrections are new check-ins (with a note); history is never edited. */
    @Transactional
    public CheckIn addCheckIn(String keyResultId, CreateCheckInVO req) {
        KeyResult kr = get(keyResultId);
        if (kr.isArchived()) {
            throw new ApiException.ConflictException("key_result_archived", MSG_KR_ARCHIVED,
                    Map.of("keyResultId", keyResultId));
        }
        Map<String, Object> errors = new LinkedHashMap<>();
        rejectUnstorable(errors, "value", req.value());
        if (!IsoDates.isStorable(req.measuredAt())) {
            errors.put("measuredAt", "out_of_range");
        }
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG_INVALID_CHECK_IN, errors);
        }

        // Response mirrors what is stored: canonical decimal, millisecond UTC timestamps.
        CheckIn checkIn = new CheckIn(UUID.randomUUID().toString(), kr.id(),
                DecimalCodec.fromDb(DecimalCodec.toDb(req.value())),
                req.measuredAt().truncatedTo(ChronoUnit.MILLIS), blankToNull(req.note()),
                clock.instant().truncatedTo(ChronoUnit.MILLIS));
        keyResults.insertCheckIn(checkIn);
        return checkIn;
    }

    private static void rejectUnstorable(Map<String, Object> errors, String field, BigDecimal value) {
        if (!DecimalCodec.isStorable(value)) {
            errors.put(field, "precision_exceeded");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
