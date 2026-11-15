package com.lifeplanner.service;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.exception.ErrorCodes;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection for login: at most {@value #MAX_FAILURES} failed attempts per account e-mail within
 * {@link #WINDOW}; further attempts get 429 until the oldest failure leaves the window. In memory (single process);
 * a restart clears it.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public void check(String key) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            Instant now = clock.instant();
            prune(recent, now);
            if (recent.size() >= MAX_FAILURES) {
                long wait = Math.max(1, Duration.between(now, recent.peekFirst().plus(WINDOW)).toSeconds());
                throw new ApiException.TooManyRequestsException(ErrorCodes.TOO_MANY_ATTEMPTS,
                        "تلاش‌های ناموفق زیادی برای ورود انجام شد. کمی بعد دوباره امتحان کنید.", wait);
            }
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > MAX_TRACKED) {
            failures.clear(); // bounded memory; worst case resets the counters
        }
        Deque<Instant> recent = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent, clock.instant());
            recent.addLast(clock.instant());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private static void prune(Deque<Instant> recent, Instant now) {
        while (!recent.isEmpty() && !recent.peekFirst().plus(WINDOW).isAfter(now)) {
            recent.removeFirst();
        }
    }
}
