package dev.jlo.gamemodes.domain.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class Deadline {
    private final Instant at;

    public Deadline(Instant at) {
        this.at = Objects.requireNonNull(at, "at");
    }

    public Instant getAt() {
        return at;
    }

    public Duration remainingAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.compareTo(at) >= 0 ? Duration.ZERO : Duration.between(now, at);
    }

    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(at);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Deadline deadline)) return false;
        return at.equals(deadline.at);
    }

    @Override
    public int hashCode() {
        return Objects.hash(at);
    }

    @Override
    public String toString() {
        return "Deadline(at=" + at + ")";
    }
}
