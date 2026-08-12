package dev.jlo.gamemodes.domain.common

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerOwnershipTest {
    @Test
    fun `player can own only one queue or match`() {
        val ownership = PlayerOwnership()
        val player = UUID.randomUUID()

        ownership.claim(player, Ownership.Queue("opr"))
        assertFailsWith<IllegalStateException> {
            ownership.claim(player, Ownership.Match("siege-1"))
        }
        ownership.release(player)
        ownership.claim(player, Ownership.Match("siege-1"))
        assertEquals(Ownership.Match("siege-1"), ownership.ownerOf(player))
    }

    @Test
    fun `disconnect reservation expires after configured duration`() {
        val ownership = PlayerOwnership()
        val player = UUID.randomUUID()
        val disconnectedAt = Instant.parse("2026-08-11T12:00:00Z")

        ownership.claim(player, Ownership.Match("opr-1"))
        ownership.disconnect(player, disconnectedAt, Duration.ofMinutes(5))

        assertEquals(true, ownership.isReserved(player, disconnectedAt.plusSeconds(299)))
        assertEquals(false, ownership.isReserved(player, disconnectedAt.plusSeconds(300)))
        assertEquals(null, ownership.expireReservation(player, disconnectedAt.plusSeconds(300)))
    }
}
