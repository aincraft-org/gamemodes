package dev.jlo.gamemodes.paper

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandRouterTest {
    @Test
    fun `join status leave and team route through service`() {
        val service = FakeCommandService()
        val router = CommandRouter(service)
        val player = UUID.randomUUID()

        assertEquals("joined OPR", router.execute(player, listOf("join", "opr")))
        assertEquals("team A", router.execute(player, listOf("team")))
        assertEquals("OPR", router.execute(player, listOf("status")))
        assertEquals("left", router.execute(player, listOf("leave")))
    }

    @Test
    fun `aliases map to join mode`() {
        val service = FakeCommandService()
        val router = CommandRouter(service)
        val player = UUID.randomUUID()

        assertEquals("joined OPR", router.execute(player, listOf("opr")))
        assertEquals("joined SIEGE", router.execute(player, listOf("siege")))
    }
}

private class FakeCommandService : CommandService {
    private var mode: Mode? = null
    private var team: TeamAssignment? = null
    override fun join(player: UUID, mode: Mode, arena: String?): TeamAssignment {
        this.mode = mode
        this.team = if (mode == Mode.OPR) TeamAssignment.A else TeamAssignment.B
        return team!!
    }
    override fun leave(player: UUID): Boolean { mode = null; team = null; return true }
    override fun ready(player: UUID): Boolean = true
    override fun status(player: UUID): String = mode?.name ?: "NONE"
    override fun team(player: UUID): TeamAssignment? = team
    override fun admin(player: UUID?, action: String, args: List<String>): String = action
}
