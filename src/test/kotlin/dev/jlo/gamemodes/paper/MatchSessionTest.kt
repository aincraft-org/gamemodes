package dev.jlo.gamemodes.paper

import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.domain.common.Team
import dev.jlo.gamemodes.domain.opr.OprConfig
import dev.jlo.gamemodes.domain.opr.OutpostId
import dev.jlo.gamemodes.domain.siege.RallyPoint
import dev.jlo.gamemodes.domain.siege.SiegeConfig
import dev.jlo.gamemodes.domain.siege.Structure
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
class MatchSessionTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(start, ZoneOffset.UTC)

    @Test
    fun `session applies preferred team to both engines`() {
        val player = UUID.randomUUID()
        val session = MatchSession(Mode.SIEGE, "arena", 2, 1, clock)

        assertEquals(TeamAssignment.B, session.join(player, TeamAssignment.B))
        assertEquals(TeamAssignment.B, session.teamOf(player))
    }

    @Test
    fun `OPR captures and retains result until explicit cleanup`() {
        val session = MatchSession(
            Mode.OPR,
            "arena",
            2,
            1,
            clock,
            OprConfig(
                teamCapacity = 2,
                quorumPerTeam = 1,
                captureDuration = Duration.ofSeconds(3),
                scoreInterval = Duration.ofSeconds(1),
                targetScore = 1
            )
        )
        val attacker = UUID.randomUUID()
        val defender = UUID.randomUUID()
        session.join(attacker, TeamAssignment.A)
        session.join(defender, TeamAssignment.B)
        assertTrue(session.startIfReady())
        session.enterOutpost(attacker, OutpostId.LUNA)
        session.advanceAt(start.plusSeconds(4))
        session.advanceAt(start.plusSeconds(5))

        assertEquals(MatchPhase.RESOLVING, session.phase)
        assertEquals(Team.A, session.result?.winner)
        session.advanceAt(start.plusSeconds(5))
        assertEquals(MatchPhase.RESOLVING, session.phase)
        assertEquals(Team.A, session.result?.winner)
        session.cleanup()
        assertEquals(MatchPhase.WAITING, session.phase)
        assertNull(session.result)
    }

    @Test
    fun `Siege routes rallies gate and claim then retains result`() {
        val session = MatchSession(
            Mode.SIEGE,
            "arena",
            2,
            1,
            clock,
            siegeConfig = SiegeConfig(
                preparation = Duration.ZERO,
                battle = Duration.ofSeconds(30),
                rallyCapture = Duration.ofSeconds(1),
                claimCapture = Duration.ofSeconds(1),
                quorum = 1
            )
        )
        val attacker = UUID.randomUUID()
        val defender = UUID.randomUUID()
        session.join(attacker, TeamAssignment.A)
        session.join(defender, TeamAssignment.B)
        session.ready(attacker)
        session.ready(defender)
        assertTrue(session.startIfReady())
        RallyPoint.entries.forEach { point ->
            assertFalse(session.captureRally(point, attacker, start.plusSeconds(1)))
            assertTrue(session.captureRally(point, attacker, start.plusSeconds(2)))
        }
        assertTrue(session.damageGate(attacker, 100, start.plusSeconds(1)))
        assertTrue(session.beginClaim(attacker, start.plusSeconds(2)))
        assertTrue(session.completeClaim(attacker, start.plusSeconds(3)))
        assertEquals(MatchPhase.CLEANUP, session.phase)
        assertEquals(Team.A, session.result?.winner)
        session.cleanup()
        assertEquals(MatchPhase.WAITING, session.phase)
        assertNull(session.result)
    }

    @Test
    fun `preparing cleanup aborts and releases all session state`() {
        val session = MatchSession(Mode.SIEGE, "arena", 2, 1, clock)
        val one = UUID.randomUUID()
        val two = UUID.randomUUID()
        session.join(one, TeamAssignment.A)
        session.join(two, TeamAssignment.B)
        assertFalse(session.startIfReady())
        assertEquals(MatchPhase.PREPARING, session.phase)

        session.cleanup()

        assertEquals(MatchPhase.WAITING, session.phase)
        assertNull(session.teamOf(one))
        assertNull(session.teamOf(two))
        assertNull(session.result)
    }
}

