package dev.jlo.gamemodes.domain.common;

public enum MatchPhase {
    DISABLED,
    WAITING,
    PREPARING,
    ACTIVE,
    RESOLVING,
    CLEANUP;

    public String getName() {
        return name();
    }
}
