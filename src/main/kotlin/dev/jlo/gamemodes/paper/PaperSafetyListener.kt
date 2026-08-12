package dev.jlo.gamemodes.paper

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.block.TNTPrimeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.vehicle.VehicleEnterEvent

class PaperSafetyListener(
    private val policy: SafetyPolicy,
    private val isArenaWorld: (World) -> Boolean
) : Listener {
    private fun blocked(player: Player, action: SafetyAction): Boolean = policy.blocks(player.uniqueId, action)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) { if (blocked(event.player, SafetyAction.BLOCK_BREAK)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) { if (blocked(event.player, SafetyAction.BLOCK_PLACE)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (blocked(event.player, SafetyAction.BUCKET)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        if (blocked(event.player, SafetyAction.BUCKET)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onIgnite(event: BlockIgniteEvent) {
        val player = event.player ?: return
        if (blocked(player, SafetyAction.FIRE)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onTnt(event: TNTPrimeEvent) {
        val player = event.primingEntity as? Player ?: return
        if (blocked(player, SafetyAction.TNT)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onExplode(event: EntityExplodeEvent) {
        if (isArenaWorld(event.entity.world)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (isArenaWorld(event.block.world)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (isArenaWorld(event.block.world)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (isArenaWorld(event.block.world)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onRedstone(event: BlockRedstoneEvent) {
        if (isArenaWorld(event.block.world)) event.newCurrent = 0
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPortal(event: PlayerPortalEvent) { if (blocked(event.player, SafetyAction.PORTAL)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (event !is PlayerPortalEvent && blocked(event.player, SafetyAction.TELEPORT)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBed(event: PlayerBedEnterEvent) { if (blocked(event.player, SafetyAction.BED)) event.isCancelled = true }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVehicle(event: VehicleEnterEvent) {
        val player = event.entered as? Player ?: return
        if (blocked(player, SafetyAction.VEHICLE)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event.hasChangedPosition() && blocked(event.player, SafetyAction.TELEPORT)) {
            // Movement is allowed; only destination-changing server teleports are blocked.
        }
    }
}
