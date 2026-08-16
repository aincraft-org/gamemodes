package dev.jlo.gamemodes.domain.common;


public final class Quorum {
    private final int requiredPlayers;

    private Quorum(int requiredPlayers) {
        if (requiredPlayers <= 0) {
            throw new IllegalArgumentException("Quorum must require at least one player");
        }
        this.requiredPlayers = requiredPlayers;
    }

    public int getRequiredPlayers() {
        return requiredPlayers;
    }

    public static Quorum fixed(int requiredPlayers) {
        return new Quorum(requiredPlayers);
    }

    public static Quorum minimumPlayers(int capacityPerTeam, double fraction) {
        if (capacityPerTeam <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (!(fraction > 0.0 && fraction <= 1.0)) {
            throw new IllegalArgumentException("Quorum fraction must be in (0, 1]");
        }
        return new Quorum((int) Math.ceil(capacityPerTeam * fraction));
    }

    public Quorum copy(int requiredPlayers) {
        return new Quorum(requiredPlayers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quorum quorum)) return false;
        return requiredPlayers == quorum.requiredPlayers;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(requiredPlayers);
    }

    @Override
    public String toString() {
        return "Quorum(requiredPlayers=" + requiredPlayers + ")";
    }
}
