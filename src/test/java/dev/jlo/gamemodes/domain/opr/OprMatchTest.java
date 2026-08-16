package dev.jlo.gamemodes.domain.opr;

import dev.jlo.gamemodes.domain.common.MatchPhase;
import dev.jlo.gamemodes.domain.common.Team;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OprMatchTest {
    private final Instant start = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void lifecycleStartsWaitingAndRequiresBalancedQuorum() {
        OprMatch match = new OprMatch(new OprConfig(20, 1), start);
        assertEquals(MatchPhase.WAITING, match.getLifecycle().getPhase());
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(Team.A, match.join(a));
        assertEquals(Team.B, match.join(b));
        match.beginPreparation(start);
        match.start(start.plusSeconds(1));
        assertEquals(MatchPhase.ACTIVE, match.getLifecycle().getPhase());
    }

    @Test
    void outpostCapturePausesWhenContestedAndAwardsPeriodicPoints() {
        OprMatch match = activeMatch();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        match.assign(a, Team.A);
        match.assign(b, Team.B);
        match.enterOutpost(a, OutpostId.LUNA);
        match.advanceTo(start.plusSeconds(31));
        assertEquals(Team.A, match.getOutposts().get(OutpostId.LUNA).getOwner());
        match.enterOutpost(b, OutpostId.LUNA);
        match.advanceTo(start.plusSeconds(32));
        assertEquals(0, match.score(Team.A));
        match.leaveOutpost(b, OutpostId.LUNA);
        match.advanceTo(start.plusSeconds(36));
        assertEquals(1, match.score(Team.A));
    }

    @Test
    void coarseAdvanceDoesNotAwardScoreBeforeCaptureCompletes() {
        OprMatch match = activeMatch();
        UUID a = UUID.randomUUID();
        match.assign(a, Team.A);
        match.enterOutpost(a, OutpostId.LUNA);
        match.advanceTo(start.plusSeconds(34));
        assertEquals(Team.A, match.getOutposts().get(OutpostId.LUNA).getOwner());
        assertEquals(0, match.score(Team.A));
        match.advanceTo(start.plusSeconds(37));
        assertEquals(1, match.score(Team.A));
    }

    @Test
    void eligibleKillsAwardOncePerVictimCooldown() {
        OprMatch match = activeMatch();
        UUID killer = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        match.assign(killer, Team.A);
        match.assign(victim, Team.B);
        match.recordKill(killer, victim, start.plusSeconds(1));
        match.recordKill(killer, victim, start.plusSeconds(30));
        assertEquals(1, match.score(Team.A));
        match.recordKill(killer, victim, start.plusSeconds(61));
        assertEquals(2, match.score(Team.A));
    }

    @Test
    void deathScoringRequiresPostProtectionCombatFromAnEnemyParticipant() {
        OprMatch match = activeMatch();
        UUID killer = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        match.assign(killer, Team.A);
        match.assign(victim, Team.B);
        match.carry(victim, Resource.ORE, 10);
        assertFalse(match.recordCombat(killer, victim, start.plusSeconds(5)));
        assertFalse(match.recordDeath(victim, killer, start.plusSeconds(5)));
        assertEquals(0, match.score(Team.A));
        assertEquals(5, match.carried(victim, Resource.ORE));
        match.carry(victim, Resource.ORE, 5);
        assertTrue(match.recordCombat(killer, victim, start.plusSeconds(12)));
        assertTrue(match.recordDeath(victim, killer, start.plusSeconds(13)));
        assertEquals(1, match.score(Team.A));
        assertEquals(5, match.carried(victim, Resource.ORE));
    }

    @Test
    void resourcesLoseHalfCarriedAmountOnDeathWhileStorageIsSafe() {
        OprMatch match = activeMatch();
        UUID player = UUID.randomUUID();
        match.assign(player, Team.A);
        match.carry(player, Resource.INFUSED_WOOD, 10);
        match.deposit(player, Resource.INFUSED_WOOD, 10);
        match.carry(player, Resource.INFUSED_WOOD, 10);
        match.playerDied(player);
        assertEquals(10, match.storage(Team.A, Resource.INFUSED_WOOD));
        assertEquals(5, match.carried(player, Resource.INFUSED_WOOD));
    }

    @Test
    void targetAndDeadlineResolveWithTieChainAndSuddenDeath() {
        OprConfig config = new OprConfig(20, 1, Duration.ofSeconds(30), Duration.ofSeconds(15), Duration.ofSeconds(3),
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofSeconds(30), 2, Duration.ofSeconds(10),
                Duration.ofMinutes(5), Duration.ofSeconds(3), Duration.ofMinutes(10), Duration.ofMinutes(5), 25, 20, 30, 10, 6,
                Duration.ofSeconds(10));
        OprMatch match = activeMatch(config);
        match.addScore(Team.A, 2, start.plusSeconds(9));
        assertEquals(MatchPhase.RESOLVING, match.getLifecycle().getPhase());
        assertEquals(Team.A, match.getResult().getWinner());
    }

    @Test
    void armoryUpgradesWardsRepairsAndSummonsEnforceTeamOwnership() {
        OprMatch match = activeMatch();
        UUID player = UUID.randomUUID();
        match.assign(player, Team.A);
        match.addBattleTokens(Team.A, 100);
        match.purchaseArmory(player, ArmoryItem.GATE_TIER_TWO);
        assertEquals(2, match.getGates().get(Team.A).getTier());
        match.activateWard(Team.A, start.plusSeconds(60));
        assertFalse(match.getOutposts().values().stream().anyMatch(o -> o.captureBlockedByWard(Team.A, start.plusSeconds(1))));
        assertTrue(match.getOutposts().values().stream().allMatch(o -> o.captureBlockedByWard(Team.B, start.plusSeconds(1))));
        match.getOutposts().get(OutpostId.LUNA).setOwner(Team.A);
        match.summon(Team.A, SummonKind.BEAR, OutpostId.LUNA);
        assertEquals(1, match.summons(Team.A).size());
    }

    @Test
    void emptyCaptureProgressWaitsFifteenSecondsThenDecaysOncePerElapsedInterval() {
        OprConfig config = new OprConfig(20, 1, Duration.ofSeconds(30), Duration.ofSeconds(15), Duration.ofSeconds(3),
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofSeconds(30), 1000, Duration.ofMinutes(30),
                Duration.ofMinutes(5), Duration.ofSeconds(90), Duration.ofMinutes(10), Duration.ofMinutes(5), 25, 20, 30, 10, 6,
                Duration.ofSeconds(10));
        OprMatch match = activeMatch(config);
        UUID attacker = UUID.randomUUID();
        match.assign(attacker, Team.A);
        match.enterOutpost(attacker, OutpostId.LUNA);
        match.advanceTo(start.plusSeconds(11));
        assertEquals(Duration.ofSeconds(10), match.getOutposts().get(OutpostId.LUNA).getProgress());
        match.leaveOutpost(attacker, OutpostId.LUNA);
        match.advanceTo(start.plusSeconds(21));
        assertEquals(Duration.ofSeconds(10), match.getOutposts().get(OutpostId.LUNA).getProgress());
        match.advanceTo(start.plusSeconds(31));
        assertEquals(Duration.ofSeconds(5), match.getOutposts().get(OutpostId.LUNA).getProgress());
        match.advanceTo(start.plusSeconds(36));
        assertEquals(Duration.ZERO, match.getOutposts().get(OutpostId.LUNA).getProgress());
    }

    @Test
    void disconnectExpiresAfterConfiguredReservationAndCleanupClearsMatch() {
        OprConfig config = new OprConfig(20, 1, Duration.ofSeconds(30), Duration.ofSeconds(15), Duration.ofSeconds(3),
                Duration.ofSeconds(60), Duration.ofSeconds(10), Duration.ofSeconds(30), 1000, Duration.ofMinutes(30),
                Duration.ofSeconds(5), Duration.ofSeconds(90), Duration.ofMinutes(10), Duration.ofMinutes(5), 25, 20, 30, 10, 6,
                Duration.ofSeconds(10));
        OprMatch match = activeMatch(config);
        UUID player = UUID.randomUUID();
        match.assign(player, Team.A);
        match.disconnect(player, start);
        assertTrue(match.reconnect(player, start.plusSeconds(4)));
        match.disconnect(player, start.plusSeconds(4));
        match.advanceTo(start.plusSeconds(10));
        assertTrue(match.getExpiredPlayers().contains(player));
        match.resolveAndCleanup(start.plusSeconds(11));
        assertEquals(MatchPhase.WAITING, match.getLifecycle().getPhase());
    }

    private OprMatch activeMatch() {
        return activeMatch(new OprConfig(20, 1));
    }

    private OprMatch activeMatch(OprConfig config) {
        OprConfig effective = config.getQuorumPerTeam() > 1 ? config.copy(config.getTeamCapacity(), 1,
                config.getCaptureDuration(), config.getCaptureDecayDelay(), config.getScoreInterval(), config.getVictimCooldown(),
                config.getSpawnProtection(), config.getCombatWindow(), config.getTargetScore(), config.getMatchDuration(),
                config.getDisconnectReservation(), config.getSuddenDeathDuration(), config.getBaronessInterval(), config.getPortalInterval(),
                config.getGateTierTwoCost(), config.getCommandPostTierTwoCost(), config.getProtectionWardCost(), config.getRepairCost(),
                config.getSummonCapPerTeam(), config.getWaveInterval()) : config;
        OprMatch match = new OprMatch(effective, start);
        match.join(UUID.randomUUID());
        match.join(UUID.randomUUID());
        match.beginPreparation(start);
        match.start(start.plusSeconds(1));
        return match;
    }
}
