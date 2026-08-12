package dev.jlo.gamemodes.paper

import dev.jlo.gamemodes.domain.common.Team
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchQueueTest {
    @Test
    fun `queue rejects duplicate ownership and balances teams`() {
        val queue = MatchQueue(Mode.OPR, capacityPerTeam = 2, quorumPerTeam = 1)
        val players = (1..4).map { UUID.nameUUIDFromBytes("player-$it".toByteArray()) }

        players.forEach { queue.join(it) }

        assertEquals(Team.A, queue.teamOf(players[0]))
        assertEquals(Team.B, queue.teamOf(players[1]))
        assertEquals(Team.A, queue.teamOf(players[2]))
        assertEquals(Team.B, queue.teamOf(players[3]))
        assertFailsWith<IllegalStateException> { queue.join(UUID.randomUUID()) }
        assertFailsWith<IllegalStateException> { queue.join(players[0]) }
        assertEquals(true, queue.hasQuorum())
    }

    @Test
    fun `queue leave releases player`() {
        val queue = MatchQueue(Mode.SIEGE, capacityPerTeam = 50, quorumPerTeam = 1)
        val player = UUID.randomUUID()
        queue.join(player)

        assertEquals(Team.A, queue.leave(player))
        assertEquals(null, queue.teamOf(player))
    }
}
