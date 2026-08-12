package dev.jlo.gamemodes.domain.common

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchRosterTest {
    @Test
    fun `assignment keeps teams balanced and respects quorum`() {
        val roster = MatchRoster(teamCapacity = 4, quorum = Quorum.minimumPlayers(4, 0.5))
        val players = (1..6).map { UUID.nameUUIDFromBytes("player-$it".toByteArray()) }

        players.forEach(roster::join)

        assertEquals(3, roster.players(Team.A).size)
        assertEquals(3, roster.players(Team.B).size)
        assertEquals(true, roster.hasQuorum())
    }

    @Test
    fun `team capacity and duplicate joins are rejected`() {
        val roster = MatchRoster(teamCapacity = 1, quorum = Quorum.fixed(1))
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        roster.join(first)
        assertFailsWith<IllegalStateException> { roster.join(first) }
        roster.join(second)
        assertFailsWith<IllegalStateException> { roster.join(UUID.randomUUID()) }
    }

    @Test
    fun `quorum rounds fractional capacity up`() {
        assertEquals(2, Quorum.minimumPlayers(3, 0.5).requiredPlayers)
        assertEquals(1, Quorum.minimumPlayers(3, 0.1).requiredPlayers)
    }
}
