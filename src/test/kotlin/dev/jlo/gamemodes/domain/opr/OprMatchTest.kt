package dev.jlo.gamemodes.domain.opr

import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.domain.common.Team
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OprMatchTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `lifecycle starts waiting and requires balanced quorum`() {
        val match = OprMatch(config = OprConfig(quorumPerTeam = 1), start = start)
        assertEquals(MatchPhase.WAITING, match.lifecycle.phase)
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        assertEquals(Team.A, match.join(a))
        assertEquals(Team.B, match.join(b))
        match.beginPreparation(start)
        match.start(start.plusSeconds(1))
        assertEquals(MatchPhase.ACTIVE, match.lifecycle.phase)
    }

    @Test
    fun `outpost capture pauses when contested and awards periodic points`() {
        val match = activeMatch()
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        match.assign(a, Team.A)
        match.assign(b, Team.B)
        match.enterOutpost(a, OutpostId.LUNA)
        match.advanceTo(start.plusSeconds(31))
        assertEquals(Team.A, match.outposts.getValue(OutpostId.LUNA).owner)
        match.enterOutpost(b, OutpostId.LUNA)
        match.advanceTo(start.plusSeconds(32))
        assertEquals(0, match.score(Team.A))
        match.leaveOutpost(b, OutpostId.LUNA)
        match.advanceTo(start.plusSeconds(36))
        assertEquals(1, match.score(Team.A))
    }

    @Test
    fun `coarse advance does not award score before capture completes`() {
        val match = activeMatch()
        val a = UUID.randomUUID()
        match.assign(a, Team.A)
        match.enterOutpost(a, OutpostId.LUNA)

        match.advanceTo(start.plusSeconds(34))

        assertEquals(Team.A, match.outposts.getValue(OutpostId.LUNA).owner)
        assertEquals(0, match.score(Team.A))
        match.advanceTo(start.plusSeconds(37))
        assertEquals(1, match.score(Team.A))
    }

    @Test
    fun `eligible kills award once per victim cooldown`() {
        val match = activeMatch()
        val killer = UUID.randomUUID()
        val victim = UUID.randomUUID()
        match.assign(killer, Team.A)
        match.assign(victim, Team.B)
        match.recordKill(killer, victim, start.plusSeconds(1))
        match.recordKill(killer, victim, start.plusSeconds(30))
        assertEquals(1, match.score(Team.A))
        match.recordKill(killer, victim, start.plusSeconds(61))
        assertEquals(2, match.score(Team.A))
    }
    @Test
    fun `death scoring requires post-protection combat from an enemy participant`() {
        val match = activeMatch()
        val killer = UUID.randomUUID()
        val victim = UUID.randomUUID()
        match.assign(killer, Team.A)
        match.assign(victim, Team.B)
        match.carry(victim, Resource.ORE, 10)

        assertFalse(match.recordCombat(killer, victim, start.plusSeconds(5)))
        assertFalse(match.recordDeath(victim, killer, start.plusSeconds(5)))
        assertEquals(0, match.score(Team.A))
        assertEquals(5, match.carried(victim, Resource.ORE))

        match.carry(victim, Resource.ORE, 5)
        assertTrue(match.recordCombat(killer, victim, start.plusSeconds(12)))
        assertTrue(match.recordDeath(victim, killer, start.plusSeconds(13)))
        assertEquals(1, match.score(Team.A))
        assertEquals(5, match.carried(victim, Resource.ORE))
    }


    @Test
    fun `resources lose half carried amount on death while storage is safe`() {
        val match = activeMatch()
        val player = UUID.randomUUID()
        match.assign(player, Team.A)
        match.carry(player, Resource.INFUSED_WOOD, 10)
        match.deposit(player, Resource.INFUSED_WOOD, 10)
        match.carry(player, Resource.INFUSED_WOOD, 10)
        match.playerDied(player)
        assertEquals(10, match.storage(Team.A, Resource.INFUSED_WOOD))
        assertEquals(5, match.carried(player, Resource.INFUSED_WOOD))
    }

    @Test
    fun `target and deadline resolve with tie chain and sudden death`() {
        val match = activeMatch(OprConfig(targetScore = 2, matchDuration = Duration.ofSeconds(10), suddenDeathDuration = Duration.ofSeconds(3)))
        match.addScore(Team.A, 2, start.plusSeconds(9))
        assertEquals(MatchPhase.RESOLVING, match.lifecycle.phase)
        assertEquals(Team.A, match.result?.winner)
    }

    @Test
    fun `armory upgrades wards repairs and summons enforce team ownership`() {
        val match = activeMatch()
        val player = UUID.randomUUID()
        match.assign(player, Team.A)
        match.addBattleTokens(Team.A, 100)
        match.purchaseArmory(player, ArmoryItem.GATE_TIER_TWO)
        assertEquals(2, match.gates.getValue(Team.A).tier)
        match.activateWard(Team.A, start.plusSeconds(60))
        assertFalse(match.outposts.values.any { it.captureBlockedByWard(Team.A, start.plusSeconds(1)) })
        assertTrue(match.outposts.values.all { it.captureBlockedByWard(Team.B, start.plusSeconds(1)) })
        match.outposts.getValue(OutpostId.LUNA).owner = Team.A
        match.summon(Team.A, SummonKind.BEAR, OutpostId.LUNA)
        assertEquals(1, match.summons(Team.A).size)
    }

    @Test
    fun `empty capture progress waits fifteen seconds then decays once per elapsed interval`() {
        val match = activeMatch(
            OprConfig(
                quorumPerTeam = 1,
                captureDuration = Duration.ofSeconds(30),
                captureDecayDelay = Duration.ofSeconds(15)
            )
        )
        val attacker = UUID.randomUUID()
        match.assign(attacker, Team.A)
        match.enterOutpost(attacker, OutpostId.LUNA)
        match.advanceTo(start.plusSeconds(11))
        assertEquals(Duration.ofSeconds(10), match.outposts.getValue(OutpostId.LUNA).progress)

        match.leaveOutpost(attacker, OutpostId.LUNA)
        match.advanceTo(start.plusSeconds(21))
        assertEquals(Duration.ofSeconds(10), match.outposts.getValue(OutpostId.LUNA).progress)
        match.advanceTo(start.plusSeconds(31))
        assertEquals(Duration.ofSeconds(5), match.outposts.getValue(OutpostId.LUNA).progress)
        match.advanceTo(start.plusSeconds(36))
        assertEquals(Duration.ZERO, match.outposts.getValue(OutpostId.LUNA).progress)
    }

    @Test
    fun `disconnect expires after configured reservation and cleanup clears match`() {
        val match = activeMatch(OprConfig(disconnectReservation = Duration.ofSeconds(5)))
        val player = UUID.randomUUID()
        match.assign(player, Team.A)
        match.disconnect(player, start)
        assertTrue(match.reconnect(player, start.plusSeconds(4)))
        match.disconnect(player, start.plusSeconds(4))
        match.advanceTo(start.plusSeconds(10))
        assertTrue(match.expiredPlayers.contains(player))
        match.resolveAndCleanup(start.plusSeconds(11))
        assertEquals(MatchPhase.WAITING, match.lifecycle.phase)
    }

    private fun activeMatch(config: OprConfig = OprConfig(quorumPerTeam = 1)) =
        OprMatch(config = if (config.quorumPerTeam > 1) config.copy(quorumPerTeam = 1) else config, start = start).also {
            it.join(UUID.randomUUID())
            it.join(UUID.randomUUID())
            it.beginPreparation(start)
            it.start(start.plusSeconds(1))
        }
}
