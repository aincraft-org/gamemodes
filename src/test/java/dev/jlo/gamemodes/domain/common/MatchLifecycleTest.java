package dev.jlo.gamemodes.domain.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchLifecycleTest {
    @Test
    void lifecycleFollowsCommonArenaFlow() {
        MatchLifecycle lifecycle = new MatchLifecycle();

        assertEquals(MatchPhase.DISABLED, lifecycle.getPhase());
        lifecycle.transitionTo(MatchPhase.WAITING);
        lifecycle.transitionTo(MatchPhase.PREPARING);
        lifecycle.transitionTo(MatchPhase.ACTIVE);
        lifecycle.transitionTo(MatchPhase.RESOLVING);
        lifecycle.transitionTo(MatchPhase.CLEANUP);
        lifecycle.transitionTo(MatchPhase.WAITING);
        assertEquals(MatchPhase.WAITING, lifecycle.getPhase());
    }

    @Test
    void illegalTransitionsAreRejected() {
        MatchLifecycle lifecycle = new MatchLifecycle();

        assertThrows(IllegalStateException.class, () ->
                lifecycle.transitionTo(MatchPhase.ACTIVE));
        lifecycle.transitionTo(MatchPhase.WAITING);
        assertThrows(IllegalStateException.class, () ->
                lifecycle.transitionTo(MatchPhase.CLEANUP));
    }
}
