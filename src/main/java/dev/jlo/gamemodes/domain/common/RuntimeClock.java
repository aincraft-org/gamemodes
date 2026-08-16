package dev.jlo.gamemodes.domain.common;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Runtime clock with monotonic measurements for live scheduling and epoch time for persistence. */
public final class RuntimeClock {
    private final Clock epochClock;
    private final Supplier<Long> monotonicNanos;

    public RuntimeClock() {
        this(Clock.systemUTC(), System::nanoTime);
    }

    public RuntimeClock(Clock epochClock) {
        this(epochClock, System::nanoTime);
    }

    public RuntimeClock(Clock epochClock, Supplier<Long> monotonicNanos) {
        this.epochClock = Objects.requireNonNull(epochClock, "epochClock");
        this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
    }

    public Instant epochNow() {
        return epochClock.instant();
    }

    public long monotonicNanos() {
        return monotonicNanos.get();
    }
}
