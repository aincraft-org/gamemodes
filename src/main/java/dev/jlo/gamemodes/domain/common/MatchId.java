package dev.jlo.gamemodes.domain.common;

import java.util.Objects;

public final class MatchId {
    private final String value;

    public MatchId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Match ID must not be blank");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MatchId matchId)) return false;
        return value.equals(matchId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "MatchId(value=" + value + ")";
    }
}
