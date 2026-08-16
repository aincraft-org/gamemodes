package dev.jlo.gamemodes.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Versioned opaque checkpoint suitable for deterministic reconstruction by a mode adapter. */
public final class MatchSnapshot {
    private final String matchId;
    private final long sequence;
    private final int version;
    private final byte[] payload;
    private final Instant deadline;

    public MatchSnapshot(String matchId, long sequence, int version, byte[] payload, Instant deadline) {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.matchId = matchId;
        this.sequence = sequence;
        this.version = version;
        this.payload = Objects.requireNonNull(payload, "payload");
        this.deadline = deadline;
    }

    public String getMatchId() { return matchId; }
    public long getSequence() { return sequence; }
    public int getVersion() { return version; }
    public byte[] getPayload() { return payload; }
    public Instant getDeadline() { return deadline; }

    public MatchSnapshot copy(String matchId, long sequence, int version, byte[] payload, Instant deadline) {
        return new MatchSnapshot(matchId, sequence, version, payload, deadline);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MatchSnapshot that
                && matchId.equals(that.matchId)
                && sequence == that.sequence
                && version == that.version
                && Arrays.equals(payload, that.payload)
                && Objects.equals(deadline, that.deadline);
    }

    @Override
    public int hashCode() {
        int result = 31 * (31 * (31 * (31 * matchId.hashCode() + Long.hashCode(sequence)) + version)
                + Arrays.hashCode(payload));
        return result + (deadline == null ? 0 : deadline.hashCode());
    }

    @Override
    public String toString() {
        return "MatchSnapshot(matchId=" + matchId + ", sequence=" + sequence + ", version=" + version
                + ", payload=" + Arrays.toString(payload) + ", deadline=" + deadline + ")";
    }
}
