package dev.tlamutant.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class FullPropertyDeadlineTest {
    @Test
    void capsAnInvocationAtTheRemainingBudget() {
        AtomicLong clock = new AtomicLong(100);
        FullPropertyDeadline deadline = FullPropertyDeadline.start(Duration.ofSeconds(10), clock::get);

        assertEquals(Duration.ofSeconds(10), deadline.remainingCappedBy(Duration.ofSeconds(30)));
        assertFalse(deadline.expired());

        clock.addAndGet(Duration.ofSeconds(3).toNanos());
        assertEquals(Duration.ofSeconds(2), deadline.remainingCappedBy(Duration.ofSeconds(2)));
        assertEquals(Duration.ofSeconds(7), deadline.remainingCappedBy(Duration.ofSeconds(30)));

        clock.addAndGet(Duration.ofSeconds(7).toNanos());
        assertEquals(Duration.ZERO, deadline.remainingCappedBy(Duration.ofSeconds(30)));
        assertTrue(deadline.expired());
    }

    @Test
    void rejectsNonpositiveExperimentAndInvocationTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> FullPropertyDeadline.start(Duration.ZERO, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> FullPropertyDeadline.start(Duration.ofSeconds(-1), () -> 0));

        FullPropertyDeadline deadline = FullPropertyDeadline.start(Duration.ofHours(1), () -> 0);
        assertThrows(IllegalArgumentException.class, () -> deadline.remainingCappedBy(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> deadline.remainingCappedBy(Duration.ofNanos(-1)));
    }

    @Test
    void ignoresAClockValueEarlierThanTheMonotonicStart() {
        AtomicLong clock = new AtomicLong(100);
        FullPropertyDeadline deadline = FullPropertyDeadline.start(Duration.ofNanos(10), clock::get);

        clock.set(99);

        assertEquals(Duration.ofNanos(10), deadline.remainingCappedBy(Duration.ofNanos(20)));
        assertFalse(deadline.expired());
    }
}
