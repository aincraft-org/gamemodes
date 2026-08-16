package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.domain.common.Team;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Thread-safe queue with deterministic balanced team assignment. */
public class MatchQueue {
    private final Mode mode;
    private final int capacityPerTeam;
    private final int quorumPerTeam;
    private final Map<Team, LinkedHashSet<UUID>> teams = new EnumMap<>(Team.class);
    private final LinkedHashSet<UUID> ready = new LinkedHashSet<>();

    public MatchQueue(Mode mode, int capacityPerTeam, int quorumPerTeam) {
        if (capacityPerTeam <= 0) {
            throw new IllegalArgumentException("Team capacity must be positive");
        }
        if (quorumPerTeam < 1 || quorumPerTeam > capacityPerTeam) {
            throw new IllegalArgumentException("Quorum must be within capacity");
        }
        this.mode = mode;
        this.capacityPerTeam = capacityPerTeam;
        this.quorumPerTeam = quorumPerTeam;
        teams.put(Team.A, new LinkedHashSet<>());
        teams.put(Team.B, new LinkedHashSet<>());
    }

    public Mode getMode() {
        return mode;
    }

    public int getCapacityPerTeam() {
        return capacityPerTeam;
    }

    public int getQuorumPerTeam() {
        return quorumPerTeam;
    }

    public synchronized Team join(UUID player) {
        return join(player, null);
    }

    public synchronized Team join(UUID player, Team preferred) {
        if (teamOf(player) != null) {
            throw new IllegalStateException("Player is already queued");
        }
        Team team = null;
        if (preferred != null && teams.get(preferred).size() < capacityPerTeam) {
            team = preferred;
        }
        if (team == null) {
            int smallestSize = Integer.MAX_VALUE;
            for (Team candidate : Team.values()) {
                int size = teams.get(candidate).size();
                if (size < smallestSize) {
                    smallestSize = size;
                    team = candidate;
                }
            }
        }
        if (teams.get(team).size() >= capacityPerTeam) {
            throw new IllegalStateException("Queue is full");
        }
        teams.get(team).add(player);
        return team;
    }

    public synchronized Team leave(UUID player) {
        ready.remove(player);
        for (Map.Entry<Team, LinkedHashSet<UUID>> entry : teams.entrySet()) {
            if (entry.getValue().remove(player)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized boolean markReady(UUID player) {
        if (teamOf(player) == null) {
            throw new IllegalStateException("Player is not queued");
        }
        return ready.add(player);
    }

    public synchronized boolean isReady(UUID player) {
        return ready.contains(player);
    }

    public synchronized Team teamOf(UUID player) {
        for (Map.Entry<Team, LinkedHashSet<UUID>> entry : teams.entrySet()) {
            if (entry.getValue().contains(player)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized Set<UUID> players(Team team) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(teams.get(team)));
    }

    public synchronized Set<UUID> allPlayers() {
        LinkedHashSet<UUID> players = new LinkedHashSet<>();
        for (Set<UUID> teamPlayers : teams.values()) {
            players.addAll(teamPlayers);
        }
        return Collections.unmodifiableSet(players);
    }

    public synchronized boolean hasQuorum() {
        for (Team team : Team.values()) {
            if (teams.get(team).size() < quorumPerTeam) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean allReady() {
        if (!hasQuorum()) {
            return false;
        }
        for (UUID player : allPlayers()) {
            if (!ready.contains(player)) {
                return false;
            }
        }
        return true;
    }

    public synchronized int size() {
        int size = 0;
        for (Set<UUID> teamPlayers : teams.values()) {
            size += teamPlayers.size();
        }
        return size;
    }
}
