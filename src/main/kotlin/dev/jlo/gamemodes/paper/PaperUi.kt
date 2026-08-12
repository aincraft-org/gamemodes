package dev.jlo.gamemodes.paper

import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID

class PaperUi(private val plugin: Plugin) {
    private val bars = mutableMapOf<UUID, org.bukkit.boss.BossBar>()

    fun update(player: Player, session: MatchSession) {
        player.sendActionBar("${session.mode.name} • ${session.phase.name} • team ${session.teamOf(player.uniqueId)?.name ?: "?"}")
        val bar = bars.getOrPut(player.uniqueId) {
            Bukkit.createBossBar("${session.mode.name} — ${session.arenaId}", BarColor.BLUE, BarStyle.SOLID).also { it.addPlayer(player) }
        }
        bar.setTitle("${session.mode.name} — ${session.phase.name}")
        bar.progress = if (session.phase.name == "ACTIVE") 1.0 else 0.5
    }

    fun clear(player: Player) {
        bars.remove(player.uniqueId)?.removeAll()
        player.scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: player.scoreboard
    }

    fun clearAll() {
        bars.values.forEach { it.removeAll() }
        bars.clear()
    }
}
