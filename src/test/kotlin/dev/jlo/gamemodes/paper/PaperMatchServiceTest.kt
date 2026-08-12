package dev.jlo.gamemodes.paper

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaperMatchServiceTest {
    @Test
    fun `join rejects missing and wrong mode arenas without synthetic fallback`() {
        val service = PaperMatchService(arenaProvider = { _, requested -> requested })
        val player = UUID.randomUUID()

        assertFailsWith<IllegalStateException> { service.join(player, Mode.OPR, null) }
        assertFailsWith<IllegalStateException> { service.join(player, Mode.OPR, "missing") }

        service.configureArenas(listOf(PaperMatchService.ArenaConfig("siege", Mode.SIEGE, 2, 1)))
        assertFailsWith<IllegalArgumentException> { service.join(player, Mode.OPR, "siege") }
        assertFalse(service.isParticipant(player))
    }

    @Test
    fun `join captures before admission and leave restores exactly once`() {
        val order = mutableListOf<String>()
        val service = PaperMatchService(arenaProvider = { _, requested -> requested })
        service.configureArenas(listOf(PaperMatchService.ArenaConfig("opr", Mode.OPR, 2, 1)))
        service.installPlayerHooks(
            capture = { order += "capture:$it" },
            restore = { order += "restore:$it" },
            admit = { id, _, team -> order += "admit:$id:$team" }
        )
        val player = UUID.randomUUID()

        assertEquals(TeamAssignment.A, service.join(player, Mode.OPR, "opr"))
        assertTrue(service.isParticipant(player))
        assertEquals(listOf("capture:$player", "admit:$player:A"), order)

        assertTrue(service.leave(player))
        assertFalse(service.isParticipant(player))
        assertEquals("restore:$player", order.last())
        assertEquals(1, order.count { it == "restore:$player" })
    }

    @Test
    fun `failed admission rolls back ownership and restores snapshot`() {
        val restored = mutableListOf<UUID>()
        val service = PaperMatchService(arenaProvider = { _, requested -> requested })
        service.configureArenas(listOf(PaperMatchService.ArenaConfig("opr", Mode.OPR, 2, 1)))
        service.installPlayerHooks(
            capture = {},
            restore = { restored += it },
            admit = { _, _, _ -> error("teleport rejected") }
        )
        val player = UUID.randomUUID()

        assertFailsWith<IllegalStateException> { service.join(player, Mode.OPR, "opr") }
        assertEquals(listOf(player), restored)
        assertFalse(service.isParticipant(player))
        assertEquals(null, service.team(player))
    }

    @Test
    fun `terminal stop restores players before deleting arena session`() {
        val order = mutableListOf<String>()
        val service = PaperMatchService(
            arenaProvider = { _, requested -> requested },
            onSessionRemoved = { order += "remove:${it.id}" }
        )
        service.configureArenas(listOf(PaperMatchService.ArenaConfig("opr", Mode.OPR, 2, 1)))
        service.installPlayerHooks(
            capture = {},
            restore = { order += "restore:$it" },
            admit = { _, _, _ -> }
        )
        val player = UUID.randomUUID()
        service.join(player, Mode.OPR, "opr")
        assertTrue(service.safetyPolicy().isActive(player))

        assertEquals("stopped 1", service.admin(null, "stop", listOf("opr")))

        assertEquals(2, order.size)
        assertEquals("restore:$player", order.first())
        assertTrue(order.last().startsWith("remove:"))
        assertFalse(service.isParticipant(player))
        assertFalse(service.safetyPolicy().isActive(player))
        assertTrue(service.sessions().isEmpty())
    }
}
