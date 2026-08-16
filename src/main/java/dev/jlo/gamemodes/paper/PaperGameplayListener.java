package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.domain.common.MatchPhase;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PaperGameplayListener implements Listener {
    private final PaperMatchService service;
    private final PaperUi ui;

    public PaperGameplayListener(PaperMatchService service, PaperUi ui) {
        this.service = service;
        this.ui = ui;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.restore(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.disconnect(event.getPlayer().getUniqueId());
        ui.clear(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        var location = service.respawn(event.getPlayer());
        if (location != null) {
            event.setRespawnLocation(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        MatchSession session = null;
        UUID playerId = event.getPlayer().getUniqueId();
        for (MatchSession candidate : service.sessions()) {
            if (candidate.teamOf(playerId) != null) {
                session = candidate;
                break;
            }
        }
        if (session == null) {
            return;
        }
        ui.update(event.getPlayer(), session);
        session.reconcileObjective(playerId, event.getPlayer().getLocation());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!service.isParticipant(playerId)) {
            return;
        }
        event.setKeepInventory(false);
        Player killer = event.getPlayer().getKiller();
        service.playerDied(playerId, killer == null ? null : killer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player killer)) {
            return;
        }
        MatchSession session = null;
        UUID victimId = victim.getUniqueId();
        UUID killerId = killer.getUniqueId();
        for (MatchSession candidate : service.sessions()) {
            if (candidate.getPhase() == MatchPhase.ACTIVE
                    && candidate.teamOf(victimId) != null
                    && candidate.teamOf(killerId) != null) {
                session = candidate;
                break;
            }
        }
        if (session == null) {
            return;
        }
        if (session.teamOf(killerId) == session.teamOf(victimId)) {
            event.setCancelled(true);
        } else {
            service.recordCombat(killerId, victimId);
        }
    }
}
