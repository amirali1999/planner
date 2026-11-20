package com.lifeplanner.service;

import com.lifeplanner.model.srv.ActivityTimerSrv;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Minute-precision local-day intervals occupied by a persisted activity timer. */
final class ActivityTimerIntervals {

    record Interval(LocalDate date, int startMinute, int endMinute) {}

    private ActivityTimerIntervals() {}

    static Instant protectedFinish(ActivityTimerSrv timer, Instant now) {
        if (timer.plannedMinutes() != null) {
            return timer.startedAt().plusSeconds(timer.plannedMinutes() * 60L);
        }
        Instant minimum = timer.startedAt().plusSeconds(60);
        return now.isAfter(minimum) ? now : minimum;
    }

    static Instant actualFinish(ActivityTimerSrv timer, Instant now) {
        if (timer.plannedMinutes() == null) return now;
        Instant cap = timer.startedAt().plusSeconds(timer.plannedMinutes() * 60L);
        return now.isBefore(cap) ? now : cap;
    }

    static List<Interval> split(ActivityTimerSrv timer, Instant finishInstant) {
        ZoneId zone = ZoneId.of(timer.timeZone());
        var start = timer.startedAt().atZone(zone);
        var finish = finishInstant.atZone(zone);
        List<Interval> result = new ArrayList<>();
        for (LocalDate day = start.toLocalDate(); !day.isAfter(finish.toLocalDate()); day = day.plusDays(1)) {
            int from = day.equals(start.toLocalDate()) ? start.getHour() * 60 + start.getMinute() : 0;
            int to = day.equals(finish.toLocalDate()) ? finish.getHour() * 60 + finish.getMinute() : 1440;
            if (day.equals(start.toLocalDate()) && day.equals(finish.toLocalDate())) to = Math.max(from + 1, to);
            if (to > from) result.add(new Interval(day, from, to));
        }
        return result;
    }

    static boolean overlaps(ActivityTimerSrv timer, Instant finish, LocalDate date, int startMinute, int endMinute) {
        return split(timer, finish).stream().anyMatch(interval -> interval.date().equals(date)
                && startMinute < interval.endMinute() && endMinute > interval.startMinute());
    }
}
