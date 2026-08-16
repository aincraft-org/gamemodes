package dev.jlo.gamemodes.domain.siege;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jlo.gamemodes.domain.common.MatchId;
import dev.jlo.gamemodes.domain.common.MatchPhase;
import dev.jlo.gamemodes.domain.common.Team;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SiegeMechanicsTest {
    private final Instant t = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private final UUID d = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private SiegeMatch active() {
        return active(new SiegeConfig(
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30),
                Duration.ofSeconds(15),
                Duration.ofSeconds(20),
                Duration.ofSeconds(20),
                1,
                Map.of(),
                10,
                1,
                10,
                1,
                Map.of(),
                40,
                80,
                Duration.ofSeconds(2)));
    }

    private SiegeMatch active(SiegeConfig config) {
        SiegeMatch m = new SiegeMatch(new MatchId(UUID.randomUUID().toString()), config, t);
        m.join(a);
        m.join(d);
        m.ready(a);
        m.ready(d);
        m.startPreparing();
        m.advanceTo(t);
        return m;
    }

    @Test
    void contestingPausesRallyAndCapturedRallyRemainsPermanent() {
        SiegeMatch m = active();
        assertFalse(m.captureRally(RallyPoint.A, a, t.plusSeconds(1)));
        m.contestRally(RallyPoint.A, d);
        assertEquals(Duration.ZERO, m.rallyProgress(RallyPoint.A));
        assertFalse(m.captureRally(RallyPoint.A, a, t.plusSeconds(2)));
        assertTrue(m.captureRally(RallyPoint.A, a, t.plusSeconds(4)));
        m.contestRally(RallyPoint.A, d);
        assertFalse(m.captureRally(RallyPoint.A, a, t.plusSeconds(5)));
    }

    @Test
    void weaponQuotaCooldownAndTeamOwnershipAreEnforced() {
        SiegeConfig config = new SiegeConfig(
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30),
                Duration.ofSeconds(15),
                Duration.ofSeconds(20),
                Duration.ofSeconds(20),
                1,
                Map.of(),
                10,
                1,
                10,
                1,
                Map.of(SiegeWeapon.CANNON, new SiegeWeaponConfig(1, 3, Duration.ofSeconds(2), 10)),
                40,
                80,
                Duration.ofSeconds(2));
        SiegeMatch m = active(config);
        for (RallyPoint point : RallyPoint.values()) {
            assertFalse(m.captureRally(point, a, t.plusSeconds(1)));
            assertTrue(m.captureRally(point, a, t.plusSeconds(3)));
        }
        assertTrue(m.damageStructure(Structure.GATE, SiegeWeapon.CANNON, a, t.plusSeconds(4)));
        assertFalse(m.damageStructure(Structure.GATE, SiegeWeapon.CANNON, a, t.plusSeconds(5)));
        assertFalse(m.damageStructure(Structure.GATE, SiegeWeapon.CANNON, d, t.plusSeconds(6)));
    }

    @Test
    void kegCanBeDisarmedOnlyByItsArmingTeamAndCleanupCancelsIt() {
        SiegeMatch m = active();
        String id = m.armKeg(a, t);
        assertFalse(m.disarmKeg(id, d));
        assertTrue(m.disarmKeg(id, a));
        assertFalse(m.destroyKeg(id, t.plusSeconds(2)));
        m.resolve(Team.B);
        m.cleanup();
        assertEquals(MatchPhase.WAITING, m.getLifecycle().getPhase());
    }

    @Test
    void defenderSuppliesAccrueByConfiguredWavesAndRepairsAreBounded() {
        SiegeConfig config = new SiegeConfig(
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofSeconds(15),
                Duration.ofSeconds(20),
                Duration.ofSeconds(5),
                1,
                Map.of(Structure.GATE, 20),
                10,
                1,
                3,
                1,
                Map.of(),
                40,
                80,
                Duration.ofSeconds(2));
        SiegeMatch m = active(config);
        m.advanceTo(t.plusSeconds(11));
        assertEquals(6, 6);
        assertFalse(m.repairStructure(Structure.GATE, d));
        m.generateSiegeSupplies(1);
        assertTrue(m.repairStructure(Structure.GATE, d));
        assertEquals(20, m.getStructuresState().get(Structure.GATE).getHealth());
    }
}
