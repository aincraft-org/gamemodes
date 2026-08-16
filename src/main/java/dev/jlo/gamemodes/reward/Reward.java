package dev.jlo.gamemodes.reward;

import java.util.Objects;
import java.util.UUID;

public final class Reward {
    private final String matchId;
    private final UUID playerId;
    private final String type;
    private final long amount;
    private final RewardState state;
    private final int attempts;
    private final Long leaseUntilEpochMs;

    public Reward(String matchId, UUID playerId, String type, long amount, RewardState state) {
        this(matchId, playerId, type, amount, state, 0, null);
    }

    public Reward(String matchId, UUID playerId, String type, long amount, RewardState state,
                  int attempts) {
        this(matchId, playerId, type, amount, state, attempts, null);
    }

    public Reward(String matchId, UUID playerId, String type, long amount, RewardState state,
                  int attempts, Long leaseUntilEpochMs) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.type = type;
        this.amount = amount;
        this.state = state;
        this.attempts = attempts;
        this.leaseUntilEpochMs = leaseUntilEpochMs;
    }

    public String getMatchId() { return matchId; }
    public UUID getPlayerId() { return playerId; }
    public String getType() { return type; }
    public long getAmount() { return amount; }
    public RewardState getState() { return state; }
    public int getAttempts() { return attempts; }
    public Long getLeaseUntilEpochMs() { return leaseUntilEpochMs; }

    public Reward copy(String matchId, UUID playerId, String type, long amount, RewardState state,
                       int attempts, Long leaseUntilEpochMs) {
        return new Reward(matchId, playerId, type, amount, state, attempts, leaseUntilEpochMs);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Reward reward)) return false;
        return amount == reward.amount && attempts == reward.attempts
                && Objects.equals(matchId, reward.matchId)
                && Objects.equals(playerId, reward.playerId)
                && Objects.equals(type, reward.type)
                && state == reward.state
                && Objects.equals(leaseUntilEpochMs, reward.leaseUntilEpochMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, playerId, type, amount, state, attempts, leaseUntilEpochMs);
    }

    @Override
    public String toString() {
        return "Reward(matchId=" + matchId + ", playerId=" + playerId + ", type=" + type
                + ", amount=" + amount + ", state=" + state + ", attempts=" + attempts
                + ", leaseUntilEpochMs=" + leaseUntilEpochMs + ")";
    }
}
