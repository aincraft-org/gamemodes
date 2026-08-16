package dev.jlo.gamemodes.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class EpochDeadlineRecovery {
    public static final EpochDeadlineRecovery INSTANCE = new EpochDeadlineRecovery();

    private EpochDeadlineRecovery() {
    }

    public static RecoveredDeadline recover(Instant deadline, Instant now) {
        Objects.requireNonNull(now, "now");
        if (deadline == null) {
            return new RecoveredDeadline(null, Duration.ZERO, false);
        }
        Duration remaining = now.isBefore(deadline) ? Duration.between(now, deadline) : Duration.ZERO;
        return new RecoveredDeadline(deadline, remaining, !now.isBefore(deadline));
    }
}
