package dev.jlo.gamemodes.domain.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class Task1ReviewFixTest {
    @Test
    void unregisterReleasesEveryMatchOwnedPlayer() {
        DefaultMatchCoordinator coordinator = new DefaultMatchCoordinator();
        StubMatch match = new StubMatch(new MatchId("m1"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        coordinator.register(match);
        coordinator.claim(first, new Ownership.Match("m1"));
        coordinator.claim(second, new Ownership.Match("m1"));

        coordinator.unregister(match.getId());

        assertEquals(null, coordinator.ownerOf(first));
        assertEquals(null, coordinator.ownerOf(second));
    }

    @Test
    void leaveOnlyReleasesQueueOwnership() {
        DefaultMatchCoordinator coordinator = new DefaultMatchCoordinator();
        UUID queuePlayer = UUID.randomUUID();
        UUID matchPlayer = UUID.randomUUID();
        coordinator.claim(queuePlayer, new Ownership.Queue("opr"));
        coordinator.claim(matchPlayer, new Ownership.Match("m1"));

        assertTrue(coordinator.leave(queuePlayer));
        assertEquals(null, coordinator.ownerOf(queuePlayer));
        assertTrue(!coordinator.leave(matchPlayer));
        assertEquals(new Ownership.Match("m1"), coordinator.ownerOf(matchPlayer));
    }

    @Test
    void coordinatorLookupExpiresReservationsUsingInjectedClock() {
        Instant start = Instant.parse("2026-08-11T12:00:00Z");
        MutableClock clock = new MutableClock(start);
        DefaultMatchCoordinator coordinator = new DefaultMatchCoordinator(clock);
        UUID player = UUID.randomUUID();
        coordinator.claim(player, new Ownership.Queue("opr"));
        coordinator.disconnect(player, Duration.ofMinutes(5));

        assertEquals(new Ownership.Queue("opr"), coordinator.ownerOf(player));
        clock.instant = start.plusSeconds(300);
        assertEquals(null, coordinator.ownerOf(player));
    }

    @Test
    void sameTickComparatorOrdersEventKindsBeforeArrivalOrder() {
        List<MatchEvent> events = List.of(
                new MatchEvent.DeadlineCheck(7),
                new MatchEvent.Objective(7, 2),
                new MatchEvent.VictoryCheck(7),
                new MatchEvent.ScoreUpdate(7),
                new MatchEvent.Objective(7, 1));

        assertEquals(
                List.of(
                        new MatchEvent.Objective(7, 1), new MatchEvent.Objective(7, 2),
                        new MatchEvent.ScoreUpdate(7), new MatchEvent.VictoryCheck(7), new MatchEvent.DeadlineCheck(7)),
                events.stream().sorted(MatchEvent.ORDERING).toList());
    }

    @Test
    void matchResultRejectsInconsistentWinnerAndDrawStates() {
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(Team.A, true));
        assertThrows(IllegalArgumentException.class, () -> new MatchResult(null, false));
        assertEquals(new MatchResult(null, true), new MatchResult(null));
        assertEquals(new MatchResult(Team.B, false), new MatchResult(Team.B));
    }

    @Test
    void runtimeClockIsMonotonicWhileEpochTimeRemainsPersistenceClock() {
        RuntimeClock clock = new RuntimeClock(
                new MutableClock(Instant.parse("2026-08-11T12:00:00Z")),
                () -> 1_000_000_000L);
        assertEquals(Instant.parse("2026-08-11T12:00:00Z"), clock.epochNow());
        assertEquals(1_000_000_000L, clock.monotonicNanos());
        assertTrue(clock.monotonicNanos() >= 0);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class StubMatch implements Match {
        private final MatchId id;
        private final MatchLifecycle lifecycle = new MatchLifecycle();

        private StubMatch(MatchId id) {
            this.id = id;
        }

        @Override
        public MatchId getId() {
            return id;
        }

        @Override
        public MatchLifecycle getLifecycle() {
            return lifecycle;
        }

        @Override
        public List<MatchEvent> handle(MatchEvent event) {
            return List.of();
        }
    }
}
