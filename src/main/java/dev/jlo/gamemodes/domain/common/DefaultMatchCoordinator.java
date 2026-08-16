package dev.jlo.gamemodes.domain.common;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultMatchCoordinator implements MatchCoordinator {
    private final Clock clock;
    private final Map<MatchId, Match> matches = new LinkedHashMap<>();
    private final PlayerOwnership ownership = new PlayerOwnership();

    public DefaultMatchCoordinator() {
        this(Clock.systemUTC());
    }

    public DefaultMatchCoordinator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized void register(Match match) {
        if (matches.containsKey(match.getId())) {
            throw new IllegalStateException("Match is already registered: " + match.getId().getValue());
        }
        matches.put(match.getId(), match);
    }

    @Override
    public synchronized void unregister(MatchId id) {
        matches.remove(id);
        ownership.releaseOwner(new Ownership.Match(id.getValue()));
    }

    @Override
    public synchronized Ownership ownerOf(UUID player) {
        return ownership.ownerOf(player, clock.instant());
    }

    public synchronized void claim(UUID player, Ownership owner) {
        ownership.claim(player, owner);
    }

    public synchronized boolean leave(UUID player) {
        return ownership.releaseQueue(player);
    }

    public synchronized void disconnect(UUID player, Duration reservationDuration) {
        ownership.disconnect(player, clock.instant(), reservationDuration);
    }

    public synchronized List<MatchEvent> dispatch(MatchId id, MatchEvent event) {
        Match match = matches.get(id);
        if (match == null) {
            throw new IllegalStateException("Unknown match: " + id.getValue());
        }
        return match.handle(event);
    }
}
