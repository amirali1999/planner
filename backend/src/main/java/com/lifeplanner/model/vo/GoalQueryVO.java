package com.lifeplanner.model.vo;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.GoalSort;
import com.lifeplanner.model.enums.GoalStatus;
import com.lifeplanner.model.enums.PeriodType;
import com.lifeplanner.util.IsoDates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Validated filters, ordering and page window of {@code GET /goals}. Every ordering ends in {@code id}, so the order is
 * total and a page window ({@code offset}/{@code limit}) is stable as long as the data does not change.
 *
 * @param statuses     empty = any status
 * @param periodTypes  empty = any period type
 * @param deadlineFrom inclusive; with a window, goals without deadline never match
 * @param deadlineTo   inclusive
 * @param overdueOn    the client's local "today": deadline &lt; overdueOn and status still open
 *                     ({@link #OPEN_STATUSES}); done/dropped goals are never overdue
 * @param limit        null = no limit (all matching goals)
 */
public record GoalQueryVO(ArchiveFilter archive, Set<GoalStatus> statuses, Set<PeriodType> periodTypes,
                        LocalDate deadlineFrom, LocalDate deadlineTo, LocalDate overdueOn,
                        GoalSort sort, Integer limit, int offset) {

    public static final int MAX_LIMIT = 200;
    /** Statuses for which a passed deadline is "overdue". */
    public static final Set<GoalStatus> OPEN_STATUSES = EnumSet.of(GoalStatus.NOT_STARTED, GoalStatus.DOING, GoalStatus.ON_HOLD);
    private static final String MSG = "پارامتر جست‌وجو معتبر نیست.";

    /** Everything, active only — the behaviour of a parameterless request. */
    public static GoalQueryVO all(ArchiveFilter archive) {
        return new GoalQueryVO(archive, Set.of(), Set.of(), null, null, null, GoalSort.PERIOD, null, 0);
    }

    public static GoalQueryVO parse(String archived, List<String> status, List<String> periodType, String deadlineFrom,
                                  String deadlineTo, String overdueOn, String sort, String limit, String offset) {
        Map<String, Object> errors = new LinkedHashMap<>();
        ArchiveFilter archive = ArchiveFilter.fromParam(archived);
        Set<GoalStatus> statuses = codes(status, "status", GoalStatus::fromCode, errors, EnumSet.noneOf(GoalStatus.class));
        Set<PeriodType> periods = codes(periodType, "periodType", PeriodType::fromCode, errors, EnumSet.noneOf(PeriodType.class));
        LocalDate from = date("deadlineFrom", deadlineFrom, errors);
        LocalDate to = date("deadlineTo", deadlineTo, errors);
        LocalDate overdue = date("overdueOn", overdueOn, errors);
        if (from != null && to != null && to.isBefore(from)) {
            errors.put("deadlineTo", "before_deadline_from");
        }
        GoalSort order = GoalSort.PERIOD;
        if (sort != null) {
            order = switch (sort) {
                case "period" -> GoalSort.PERIOD;
                case "deadline" -> GoalSort.DEADLINE;
                case "priority" -> GoalSort.PRIORITY;
                default -> {
                    errors.put("sort", "invalid_value");
                    yield GoalSort.PERIOD;
                }
            };
        }
        Integer max = integer("limit", limit, 1, MAX_LIMIT, errors);
        Integer skip = integer("offset", offset, 0, Integer.MAX_VALUE, errors);
        if (!errors.isEmpty()) {
            throw new ApiException.ValidationException(MSG, errors);
        }
        return new GoalQueryVO(archive, statuses, periods, from, to, overdue, order, max, skip == null ? 0 : skip);
    }

    private static <E extends Enum<E>> Set<E> codes(List<String> raw, String field, Function<String, E> parser,
                                                     Map<String, Object> errors, Set<E> into) {
        if (raw == null) {
            return into;
        }
        for (String value : raw) {
            try {
                into.add(parser.apply(value == null ? "" : value.trim()));
            } catch (IllegalArgumentException e) {
                errors.put(field, "invalid_value");
            }
        }
        return into;
    }

    private static LocalDate date(String field, String value, Map<String, Object> errors) {
        if (value == null) {
            return null;
        }
        try {
            return IsoDates.parseParam(field, value);
        } catch (ApiException.ValidationException e) {
            errors.put(field, "invalid_value");
            return null;
        }
    }

    private static Integer integer(String field, String value, int min, int max, Map<String, Object> errors) {
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d{1,10}")) {
            errors.put(field, "invalid_value");
            return null;
        }
        long n = Long.parseLong(value);
        if (n < min || n > max) {
            errors.put(field, "out_of_range");
            return null;
        }
        return (int) n;
    }

    /** WHERE clause (without the keyword) and its positional parameters. Only fixed SQL fragments are concatenated. */
    public List<Object> where(StringBuilder sql) {
        List<Object> params = new ArrayList<>();
        sql.append(archive.sqlCondition("archived_at"));
        if (!statuses.isEmpty()) {
            sql.append(" AND status IN (").append(placeholders(statuses.size())).append(')');
            statuses.forEach(s -> params.add(s.code()));
        }
        if (!periodTypes.isEmpty()) {
            sql.append(" AND period_type IN (").append(placeholders(periodTypes.size())).append(')');
            periodTypes.forEach(p -> params.add(p.code()));
        }
        if (deadlineFrom != null) {
            sql.append(" AND deadline IS NOT NULL AND deadline >= ?");
            params.add(deadlineFrom.toString());
        }
        if (deadlineTo != null) {
            sql.append(" AND deadline IS NOT NULL AND deadline <= ?");
            params.add(deadlineTo.toString());
        }
        if (overdueOn != null) {
            sql.append(" AND deadline IS NOT NULL AND deadline < ? AND status IN (")
                    .append(placeholders(OPEN_STATUSES.size())).append(')');
            params.add(overdueOn.toString());
            OPEN_STATUSES.forEach(s -> params.add(s.code()));
        }
        return params;
    }

    public String orderBy() {
        String period = "period_start, CASE period_type WHEN 'annual' THEN 0 WHEN 'quarterly' THEN 1 WHEN 'monthly' THEN 2 ELSE 3 END, created_at, id";
        return switch (sort) {
            case DEADLINE -> "deadline IS NULL, deadline, " + period;
            case PRIORITY -> "priority IS NULL, priority, " + period;
            case PERIOD -> period;
        };
    }

    private static String placeholders(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }
}
