package dev.jlo.gamemodes.domain.common;

import java.util.UUID;

public interface MatchCoordinator {
    void register(Match match);
    void unregister(MatchId id);
    Ownership ownerOf(UUID player);
}
