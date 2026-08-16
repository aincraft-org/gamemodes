package dev.jlo.gamemodes.domain.siege;

import dev.jlo.gamemodes.domain.common.MatchId;
import dev.jlo.gamemodes.domain.common.MatchPhase;
import dev.jlo.gamemodes.domain.common.Team;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegeMatchTest {
    private final Instant start = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID attacker = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID defender = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private SiegeMatch match() {
        return new SiegeMatch(
                new MatchId("siege-test"),
                new SiegeConfig(
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(20),
                        Duration.ofSeconds(20),
                        1,
                        java.util.Map.of(),
                        10,
                        1,
                        10,
                        1,
                        java.util.Map.of(),
                        40,
                        80,
                        Duration.ofSeconds(2)
                ),
                start
        );
    }

    @Test
    void preparationStartsActiveBattleOnlyWithBothRosters() {
        SiegeMatch match = match();
        assertEquals(MatchPhase.WAITING, match.getLifecycle().getPhase());
        assertEquals(Team.A, match.join(attacker));
        match.ready(attacker);
        match.startPreparing();
        match.advanceTo(start.plusSeconds(5));
        assertEquals(MatchPhase.PREPARING, match.getLifecycle().getPhase());
        assertEquals(Team.B, match.join(defender));
        match.ready(defender);
        match.advanceTo(start.plusSeconds(6));
        assertEquals(MatchPhase.ACTIVE, match.getLifecycle().getPhase());
    }

    @Test
    void allThreePermanentRalliesUnlockGate() {
        SiegeMatch match = activeMatch();
        for (RallyPoint point : RallyPoint.values()) {
            assertFalse(match.captureRally(point, attacker, start.plusSeconds(7)));
            assertFalse(match.captureRally(point, attacker, start.plusSeconds(9)));
            assertTrue(match.captureRally(point, attacker, start.plusSeconds(10)));
        }
        assertTrue(match.isAllRalliesCaptured());
        assertTrue(match.damageStructure(Structure.GATE, SiegeWeapon.CANNON, attacker, 10, start.plusSeconds(14)));
        assertFalse(match.damageStructure(Structure.GATE, SiegeWeapon.CANNON, attacker, 10, start.plusSeconds(15)));
    }

    @Test
    void claimCannotFinishAtOrAfterStrictDeadline() {
        SiegeMatch match = activeMatch();
        for (RallyPoint point : RallyPoint.values()) {
            match.captureRally(point, attacker, start.plusSeconds(3));
        }
        Instant deadline = match.getBattleDeadline();
        assertFalse(match.beginClaim(attacker, start.plusSeconds(10)));
        assertFalse(match.completeClaim(attacker, deadline));
        assertEquals(MatchPhase.RESOLVING, match.getLifecycle().getPhase());
        assertEquals(Team.B, match.getResult().getWinner());
    }

    private SiegeMatch activeMatch() {
        SiegeMatch match = match();
        match.join(attacker);
        match.join(defender);
        match.ready(attacker);
        match.ready(defender);
        match.startPreparing();
        match.advanceTo(start.plusSeconds(6));
        return match;
    }
}
