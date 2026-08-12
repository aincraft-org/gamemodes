package dev.jlo.gamemodes.paper

import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.domain.opr.OutpostId
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent

class PaperGameplayListener(
    private val service: PaperMatchService,
    private val ui: PaperUi
) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) { service.restore(event.player) }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        service.disconnect(event.player.uniqueId)
        ui.clear(event.player)
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        service.respawn(event.player)?.let { event.respawnLocation = it }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (!event.hasChangedPosition()) return
        val session = service.sessions().firstOrNull { it.teamOf(event.player.uniqueId) != null } ?: return
        ui.update(event.player, session)
        session.reconcileObjective(event.player.uniqueId, event.player.location)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        if (!service.isParticipant(event.player.uniqueId)) return
        event.keepInventory = false
        service.playerDied(event.player.uniqueId, event.player.killer?.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? Player ?: return
        val killer = event.damager as? Player ?: return
        val session = service.sessions().firstOrNull {
            it.phase == MatchPhase.ACTIVE &&
                it.teamOf(victim.uniqueId) != null &&
                it.teamOf(killer.uniqueId) != null
        } ?: return
        if (session.teamOf(killer.uniqueId) == session.teamOf(victim.uniqueId)) {
            event.isCancelled = true
        } else {
            service.recordCombat(killer.uniqueId, victim.uniqueId)
        }
    }
}
