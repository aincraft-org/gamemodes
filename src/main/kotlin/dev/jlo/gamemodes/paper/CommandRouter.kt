package dev.jlo.gamemodes.paper

import java.util.UUID

interface CommandService {
    fun join(player: UUID, mode: Mode, arena: String? = null): TeamAssignment
    fun leave(player: UUID): Boolean
    fun ready(player: UUID): Boolean
    fun status(player: UUID): String
    fun team(player: UUID): TeamAssignment?
    fun admin(player: UUID?, action: String, args: List<String>): String
}

/** Pure command parser shared by Bukkit executor and fake-based tests. */
class CommandRouter(private val service: CommandService) {
    fun execute(player: UUID, arguments: List<String>): String {
        if (arguments.isEmpty()) return "Usage: join <opr|siege> [arena], leave, ready, status, team"
        val command = arguments.first().lowercase()
        return when (command) {
            "opr", "siege" -> join(player, Mode.parse(command)!!, arguments.getOrNull(1))
            "join" -> {
                val mode = arguments.getOrNull(1)?.let(Mode::parse)
                    ?: return "Usage: join <opr|siege> [arena]"
                join(player, mode, arguments.getOrNull(2))
            }
            "leave" -> if (service.leave(player)) "left" else "not in a queue or match"
            "ready" -> if (service.ready(player)) "ready" else "not in a queue or match"
            "status" -> service.status(player)
            "team" -> service.team(player)?.let { "team ${it.name}" } ?: "not in a queue or match"
            else -> "Unknown command: $command"
        }
    }

    private fun join(player: UUID, mode: Mode, arena: String?): String =
        "joined $mode".replace("$mode", mode.name)
            .let { service.join(player, mode, arena); it }
}
