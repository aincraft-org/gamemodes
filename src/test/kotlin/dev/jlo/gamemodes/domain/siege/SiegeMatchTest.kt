package dev.jlo.gamemodes.domain.siege

import dev.jlo.gamemodes.domain.common.MatchId
import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.domain.common.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SiegeMatchTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val attacker = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val defender = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun match() = SiegeMatch(
        MatchId("siege-test"),
        SiegeConfig(
            preparation = Duration.ofSeconds(5),
            battle = Duration.ofSeconds(30),
            rallyCapture = Duration.ofSeconds(3),
            claimCapture = Duration.ofSeconds(3),
            quorum = 1
        ),
        start
    )

    @Test
    fun `preparation starts active battle only with both rosters`() {
        val match = match()
        assertEquals(MatchPhase.WAITING, match.lifecycle.phase)
        assertEquals(Team.A, match.join(attacker))
        match.ready(attacker)
        match.startPreparing()
        match.advanceTo(start.plusSeconds(5))
        assertEquals(MatchPhase.PREPARING, match.lifecycle.phase)
        assertEquals(Team.B, match.join(defender))
        match.ready(defender)
        match.advanceTo(start.plusSeconds(6))
        assertEquals(MatchPhase.ACTIVE, match.lifecycle.phase)
    }

    @Test
    fun `all three permanent rallies unlock gate`() {
        val match = activeMatch()
        RallyPoint.entries.forEach { point ->
            assertFalse(match.captureRally(point, attacker, start.plusSeconds(7)))
            assertFalse(match.captureRally(point, attacker, start.plusSeconds(9)))
            assertTrue(match.captureRally(point, attacker, start.plusSeconds(10)))
        }
        assertTrue(match.allRalliesCaptured)
        assertTrue(match.damageStructure(Structure.GATE, SiegeWeapon.CANNON, attacker, 10, start.plusSeconds(14)))
        assertFalse(match.damageStructure(Structure.GATE, SiegeWeapon.CANNON, attacker, 10, start.plusSeconds(15)))
    }

    @Test
    fun `claim cannot finish at or after strict deadline`() {
        val match = activeMatch()
        RallyPoint.entries.forEach { match.captureRally(it, attacker, start.plusSeconds(3)) }
        val deadline = match.battleDeadline
        assertFalse(match.beginClaim(attacker, start.plusSeconds(10)))
        assertFalse(match.completeClaim(attacker, deadline))
        assertEquals(MatchPhase.RESOLVING, match.lifecycle.phase)
        assertEquals(Team.B, match.result?.winner)
    }

    private fun activeMatch(): SiegeMatch {
        val match = match()
        match.join(attacker)
        match.join(defender)
        match.ready(attacker)
        match.ready(defender)
        match.startPreparing()
        match.advanceTo(start.plusSeconds(6))
        return match
    }
}
