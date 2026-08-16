package dev.jlo.gamemodes.domain.common;

import java.util.Objects;

public final class MatchResult {
    private final Team winner;
    private final boolean draw;

    public MatchResult(Team winner) {
        this(winner, winner == null);
    }

    public MatchResult(Team winner, boolean draw) {
        if (draw != (winner == null)) {
            throw new IllegalArgumentException("A draw must not have a winner, and a winner is not a draw");
        }
        this.winner = winner;
        this.draw = draw;
    }

    public Team getWinner() {
        return winner;
    }

    public boolean getDraw() {
        return draw;
    }

    public MatchResult copy(Team winner, boolean draw) {
        return new MatchResult(winner, draw);
    }

    public MatchResult copy(Team winner) {
        return new MatchResult(winner, winner == null);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MatchResult result)) return false;
        return draw == result.draw && winner == result.winner;
    }

    @Override
    public int hashCode() {
        return Objects.hash(winner, draw);
    }

    @Override
    public String toString() {
        return "MatchResult(winner=" + winner + ", draw=" + draw + ")";
    }
}
