package dev.jlo.gamemodes.domain.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class RespawnWave {
    private final Duration period;
    private final Instant anchor;

    public RespawnWave(Duration period, Instant anchor) {
        this.period = Objects.requireNonNull(period, "period");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("Respawn period must be positive");
        }
    }

    public Duration getPeriod() {
        return period;
    }

    public Instant getAnchor() {
        return anchor;
    }

    public Instant nextWaveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(anchor)) {
            return anchor;
        }
        Duration elapsed = Duration.between(anchor, now);
        long periods = elapsed.toNanos() / period.toNanos() + 1;
        return anchor.plus(period.multipliedBy(periods));
    }

    public RespawnWave copy(Duration period, Instant anchor) {
        return new RespawnWave(period, anchor);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RespawnWave wave)) return false;
        return period.equals(wave.period) && anchor.equals(wave.anchor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(period, anchor);
    }

    @Override
    public String toString() {
        return "RespawnWave(period=" + period + ", anchor=" + anchor + ")";
    }
}
