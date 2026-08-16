package dev.jlo.gamemodes.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class RecoveredDeadline {
    private final Instant deadline;
    private final Duration remaining;
    private final boolean expired;

    public RecoveredDeadline(Instant deadline, Duration remaining, boolean expired) {
        this.deadline = deadline;
        this.remaining = Objects.requireNonNull(remaining, "remaining");
        this.expired = expired;
    }

    public Instant getDeadline() { return deadline; }
    public Duration getRemaining() { return remaining; }
    public boolean isExpired() { return expired; }

    public RecoveredDeadline copy(Instant deadline, Duration remaining, boolean expired) {
        return new RecoveredDeadline(deadline, remaining, expired);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecoveredDeadline that
                && Objects.equals(deadline, that.deadline)
                && remaining.equals(that.remaining)
                && expired == that.expired;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (deadline == null ? 0 : deadline.hashCode()) + remaining.hashCode())
                + Boolean.hashCode(expired);
    }

    @Override
    public String toString() {
        return "RecoveredDeadline(deadline=" + deadline + ", remaining=" + remaining + ", expired=" + expired + ")";
    }
}
