package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.domain.common.MatchPhase;
import dev.jlo.gamemodes.domain.common.Team;
import dev.jlo.gamemodes.domain.opr.OprConfig;
import dev.jlo.gamemodes.domain.opr.OutpostId;
import dev.jlo.gamemodes.domain.siege.RallyPoint;
import dev.jlo.gamemodes.domain.siege.SiegeConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchSessionTest {
    private final Instant start = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(start, ZoneOffset.UTC);

    @Test
    void sessionAppliesPreferredTeamToBothEngines() {
        UUID player = UUID.randomUUID();
        MatchSession session = new MatchSession(Mode.SIEGE, "arena", 2, 1, clock);

        assertEquals(TeamAssignment.B, session.join(player, TeamAssignment.B));
        assertEquals(TeamAssignment.B, session.teamOf(player));
    }

    @Test
    void oprCapturesAndRetainsResultUntilExplicitCleanup() {
        MatchSession session = new MatchSession(
                Mode.OPR,
                "arena",
                2,
                1,
                clock,
                new OprConfig(
                        2,
                        1,
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        1,
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(90),
                        Duration.ofMinutes(10),
                        Duration.ofMinutes(5),
                        25,
                        20,
                        30,
                        10,
                        6,
                        Duration.ofSeconds(10)),
                new SiegeConfig(1));
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        session.join(attacker, TeamAssignment.A);
        session.join(defender, TeamAssignment.B);
        assertTrue(session.startIfReady());
        session.enterOutpost(attacker, OutpostId.LUNA);
        session.advanceAt(start.plusSeconds(4));
        session.advanceAt(start.plusSeconds(5));

        assertEquals(MatchPhase.RESOLVING, session.getPhase());
        assertEquals(Team.A, session.getResult().getWinner());
        session.advanceAt(start.plusSeconds(5));
        assertEquals(MatchPhase.RESOLVING, session.getPhase());
        assertEquals(Team.A, session.getResult().getWinner());
        session.cleanup();
        assertEquals(MatchPhase.WAITING, session.getPhase());
        assertNull(session.getResult());
    }

    @Test
    void siegeRoutesRalliesGateAndClaimThenRetainsResult() {
        MatchSession session = new MatchSession(
                Mode.SIEGE,
                "arena",
                2,
                1,
                clock,
                new OprConfig(2, 1),
                new SiegeConfig(
                        Duration.ZERO,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(20),
                        Duration.ofSeconds(20),
                        1,
                        java.util.Map.of(
                                dev.jlo.gamemodes.domain.siege.Structure.GATE, 100,
                                dev.jlo.gamemodes.domain.siege.Structure.ARMORY, 100,
                                dev.jlo.gamemodes.domain.siege.Structure.GENERATOR, 100),
                        10,
                        1,
                        10,
                        1,
                        java.util.Collections.emptyMap(),
                        40,
                        80,
                        Duration.ofSeconds(2)));
        UUID attacker = UUID.randomUUID();
        UUID defender = UUID.randomUUID();
        session.join(attacker, TeamAssignment.A);
        session.join(defender, TeamAssignment.B);
        session.ready(attacker);
        session.ready(defender);
        assertTrue(session.startIfReady());
        for (RallyPoint point : RallyPoint.values()) {
            assertFalse(session.captureRally(point, attacker, start.plusSeconds(1)));
            assertTrue(session.captureRally(point, attacker, start.plusSeconds(2)));
        }
        assertTrue(session.damageGate(attacker, 100, start.plusSeconds(1)));
        assertTrue(session.beginClaim(attacker, start.plusSeconds(2)));
        assertTrue(session.completeClaim(attacker, start.plusSeconds(3)));
        assertEquals(MatchPhase.CLEANUP, session.getPhase());
        assertEquals(Team.A, session.getResult().getWinner());
        session.cleanup();
        assertEquals(MatchPhase.WAITING, session.getPhase());
        assertNull(session.getResult());
    }

    @Test
    void preparingCleanupAbortsAndReleasesAllSessionState() {
        MatchSession session = new MatchSession(Mode.SIEGE, "arena", 2, 1, clock);
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        session.join(one, TeamAssignment.A);
        session.join(two, TeamAssignment.B);
        assertFalse(session.startIfReady());
        assertEquals(MatchPhase.PREPARING, session.getPhase());

        session.cleanup();

        assertEquals(MatchPhase.WAITING, session.getPhase());
        assertNull(session.teamOf(one));
        assertNull(session.teamOf(two));
        assertNull(session.getResult());
    }
}
