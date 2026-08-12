package dev.jlo.gamemodes.paper

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PaperCommandExecutor(
    private val service: PaperMatchService
) : CommandExecutor, TabCompleter {
    private val router = CommandRouter(service)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            if (args.firstOrNull()?.lowercase() == "debug") sender.sendMessage(service.admin(null, "debug", args.drop(1)))
            else sender.sendMessage("This command is player-only")
            return true
        }
        val input = if (label.lowercase() in setOf("opr", "siege")) {
            listOf(label.lowercase()) + args
        } else {
            args.toList()
        }
        try {
            if (input.firstOrNull()?.lowercase() in setOf("start", "stop", "reload", "debug", "arena")) {
                if (!sender.hasPermission("gamemodes.admin")) {
                    sender.sendMessage("You do not have permission")
                    return true
                }
                sender.sendMessage(service.admin(sender.uniqueId, input.first(), input.drop(1)))
            } else {
                sender.sendMessage(router.execute(sender.uniqueId, input))
            }
        } catch (failure: RuntimeException) {
            sender.sendMessage(failure.message ?: "Gamemode operation failed")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> = when (args.size) {
        1 -> listOf("join", "leave", "ready", "status", "team", "opr", "siege", "start", "stop", "reload", "debug", "arena").filter { it.startsWith(args[0], true) }
        2 -> if (args[0].equals("join", true)) listOf("opr", "siege") else emptyList()
        else -> emptyList()
    }
}
