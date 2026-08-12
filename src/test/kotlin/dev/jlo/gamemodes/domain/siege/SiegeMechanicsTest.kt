package dev.jlo.gamemodes.domain.siege

import dev.jlo.gamemodes.domain.common.MatchId
import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.domain.common.Team
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiegeMechanicsTest {
    private val t = Instant.parse("2026-01-01T00:00:00Z")
    private val a = UUID.fromString("00000000-0000-0000-0000-000000000011")
    private val d = UUID.fromString("00000000-0000-0000-0000-000000000012")

    private fun active(config: SiegeConfig = SiegeConfig(preparation = Duration.ZERO, battle = Duration.ofSeconds(30), rallyCapture = Duration.ofSeconds(2), quorum = 1)): SiegeMatch {
        val m = SiegeMatch(MatchId(UUID.randomUUID().toString()), config, t)
        m.join(a); m.join(d); m.ready(a); m.ready(d); m.startPreparing(); m.advanceTo(t)
        return m
    }

    @Test
    fun `contesting pauses rally and captured rally remains permanent`() {
        val m = active()
        assertFalse(m.captureRally(RallyPoint.A, a, t.plusSeconds(1)))
        m.contestRally(RallyPoint.A, d)
        assertEquals(Duration.ZERO, m.rallyProgress(RallyPoint.A))
        assertFalse(m.captureRally(RallyPoint.A, a, t.plusSeconds(2)))
        assertTrue(m.captureRally(RallyPoint.A, a, t.plusSeconds(4)))
        m.contestRally(RallyPoint.A, d)
        assertTrue(m.captureRally(RallyPoint.A, a, t.plusSeconds(5)).not())
    }

    @Test
    fun `weapon quota cooldown and team ownership are enforced`() {
        val m = active(SiegeConfig(preparation = Duration.ZERO, battle = Duration.ofSeconds(30), rallyCapture = Duration.ofSeconds(2), weaponConfigs = mapOf(SiegeWeapon.CANNON to SiegeWeaponConfig(1, 3, Duration.ofSeconds(2), 10)), quorum = 1))
        RallyPoint.entries.forEach {
            assertFalse(m.captureRally(it, a, t.plusSeconds(1)))
            assertTrue(m.captureRally(it, a, t.plusSeconds(3)))
        }
        assertTrue(m.damageStructure(Structure.GATE, SiegeWeapon.CANNON, a, now = t.plusSeconds(4)))
        assertFalse(m.damageStructure(Structure.GATE, SiegeWeapon.CANNON, a, now = t.plusSeconds(5)))
        assertFalse(m.damageStructure(Structure.GATE, SiegeWeapon.CANNON, d, now = t.plusSeconds(6)))
    }

    @Test
    fun `keg can be disarmed only by its arming team and cleanup cancels it`() {
        val m = active()
        val id = m.armKeg(a, t)
        assertFalse(m.disarmKeg(id, d))
        assertTrue(m.disarmKeg(id, a))
        assertFalse(m.destroyKeg(id, t.plusSeconds(2)))
        m.resolve(Team.B)
        m.cleanup()
        assertEquals(MatchPhase.WAITING, m.lifecycle.phase)
    }

    @Test
    fun `defender supplies accrue by configured waves and repairs are bounded`() {
        val m = active(SiegeConfig(preparation = Duration.ZERO, battle = Duration.ofSeconds(30), defenderWave = Duration.ofSeconds(5), suppliesPerWave = 3, structureHealth = mapOf(Structure.GATE to 20), quorum = 1))
        m.advanceTo(t.plusSeconds(11))
        assertEquals(6, m.attackerSupplies)
        assertFalse(m.repairStructure(Structure.GATE, d))
        m.generateSiegeSupplies(1)
        assertTrue(m.repairStructure(Structure.GATE, d))
        assertEquals(20, m.structuresState.getValue(Structure.GATE).health)
    }
}
