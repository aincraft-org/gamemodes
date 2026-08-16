package dev.jlo.gamemodes.domain.common;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MatchRoster {
    private final int teamCapacity;
    private final Quorum quorum;
    private final Map<Team, LinkedHashSet<UUID>> members = new EnumMap<>(Team.class);

    public MatchRoster(int teamCapacity, Quorum quorum) {
        if (teamCapacity <= 0) {
            throw new IllegalArgumentException("Team capacity must be positive");
        }
        this.teamCapacity = teamCapacity;
        this.quorum = quorum;
        members.put(Team.A, new LinkedHashSet<>());
        members.put(Team.B, new LinkedHashSet<>());
    }

    public int getTeamCapacity() {
        return teamCapacity;
    }

    public Quorum getQuorum() {
        return quorum;
    }

    public Team join(UUID player) {
        if (members.values().stream().anyMatch(team -> team.contains(player))) {
            throw new IllegalStateException("Player is already in this roster");
        }
        Team selected = Team.A;
        for (Team team : Team.values()) {
            if (members.get(team).size() < members.get(selected).size()) {
                selected = team;
            }
        }
        if (members.get(selected).size() >= teamCapacity) {
            throw new IllegalStateException("Both teams are full");
        }
        members.get(selected).add(player);
        return selected;
    }

    public Team leave(UUID player) {
        for (Map.Entry<Team, LinkedHashSet<UUID>> entry : members.entrySet()) {
            if (entry.getValue().remove(player)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Set<UUID> players(Team team) {
        return Set.copyOf(members.get(team));
    }

    public Team teamOf(UUID player) {
        for (Map.Entry<Team, LinkedHashSet<UUID>> entry : members.entrySet()) {
            if (entry.getValue().contains(player)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean hasQuorum() {
        for (Team team : Team.values()) {
            if (members.get(team).size() < quorum.getRequiredPlayers()) {
                return false;
            }
        }
        return true;
    }
}
