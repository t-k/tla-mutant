package dev.tlamutant.experiment;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

final class FullPropertyDeadline {
    private final long startedAtNanos;
    private final long timeoutNanos;
    private final LongSupplier nanoTime;

    private FullPropertyDeadline(long startedAtNanos, long timeoutNanos, LongSupplier nanoTime) {
        this.startedAtNanos = startedAtNanos;
        this.timeoutNanos = timeoutNanos;
        this.nanoTime = nanoTime;
    }

    static FullPropertyDeadline start(Duration timeout, LongSupplier nanoTime) {
        long timeoutNanos = positiveNanos(timeout, "Full-property experiment timeout");
        LongSupplier clock = Objects.requireNonNull(nanoTime, "nanoTime");
        return new FullPropertyDeadline(clock.getAsLong(), timeoutNanos, clock);
    }

    boolean expired() {
        return remainingNanos() == 0;
    }

    Duration remainingCappedBy(Duration perInvocationTimeout) {
        long invocationNanos = positiveNanos(perInvocationTimeout, "TLC timeout");
        return Duration.ofNanos(Math.min(remainingNanos(), invocationNanos));
    }

    private long remainingNanos() {
        long elapsed = nanoTime.getAsLong() - startedAtNanos;
        if (elapsed <= 0) {
            return timeoutNanos;
        }
        return elapsed >= timeoutNanos ? 0 : timeoutNanos - elapsed;
    }

    private static long positiveNanos(Duration duration, String label) {
        Objects.requireNonNull(duration, label);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(label + " is too large", exception);
        }
    }
}
