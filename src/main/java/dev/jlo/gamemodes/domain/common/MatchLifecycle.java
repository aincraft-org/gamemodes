package dev.jlo.gamemodes.domain.common;

import java.util.Map;
import java.util.Set;

public class MatchLifecycle {
    private MatchPhase phase;

    private static final Map<MatchPhase, Set<MatchPhase>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = Map.of(
                MatchPhase.DISABLED, Set.of(MatchPhase.WAITING),
                MatchPhase.WAITING, Set.of(MatchPhase.PREPARING, MatchPhase.DISABLED),
                MatchPhase.PREPARING, Set.of(MatchPhase.ACTIVE, MatchPhase.CLEANUP),
                MatchPhase.ACTIVE, Set.of(MatchPhase.RESOLVING),
                MatchPhase.RESOLVING, Set.of(MatchPhase.CLEANUP),
                MatchPhase.CLEANUP, Set.of(MatchPhase.WAITING, MatchPhase.DISABLED)
        );
    }

    public MatchLifecycle() {
        this(MatchPhase.DISABLED);
    }

    public MatchLifecycle(MatchPhase initialPhase) {
        this.phase = initialPhase;
    }

    public MatchPhase getPhase() {
        return phase;
    }

    public void transitionTo(MatchPhase next) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(phase, Set.of()).contains(next)) {
            throw new IllegalStateException("Illegal match transition: " + phase + " -> " + next);
        }
        phase = next;
    }
}
