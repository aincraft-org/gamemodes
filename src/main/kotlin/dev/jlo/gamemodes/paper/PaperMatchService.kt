package dev.jlo.gamemodes.paper

import dev.jlo.gamemodes.domain.common.MatchPhase
import dev.jlo.gamemodes.player.EffectSnapshot
import dev.jlo.gamemodes.player.ItemStackSnapshot
import dev.jlo.gamemodes.player.LocationSnapshot
import dev.jlo.gamemodes.player.PendingRestoreRepository
import dev.jlo.gamemodes.player.PlayerSnapshot
import java.util.Base64
import java.util.UUID
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class PaperMatchService(
    private val arenaProvider: (Mode, String?) -> String? = { _, arena -> arena },
    private val onSessionCreated: (MatchSession) -> Unit = {},
    private val onSessionRemoved: (MatchSession) -> Unit = {},
    private val restoreRepository: PendingRestoreRepository? = null
) : CommandService {
    data class ArenaConfig(val id: String, val mode: Mode, val capacityPerTeam: Int, val quorumPerTeam: Int)
    private val configuredArenas = linkedMapOf<String, ArenaConfig>()
    private val sessions = linkedMapOf<String, MatchSession>()
    private val owners = linkedMapOf<UUID, MatchSession>()
    private val snapshots = linkedMapOf<UUID, PlayerSnapshot>()
    private val safety = SafetyPolicy()
    private var captureHook: (UUID) -> Unit = {}
    private var restoreHook: (UUID) -> Unit = {}
    private var admitHook: (UUID, MatchSession, TeamAssignment) -> Unit = { _, _, _ -> }
    private var reloadHook: () -> String = { "No arena catalog is installed" }

    @Synchronized
    fun configureArenas(arenas: Collection<ArenaConfig>) {
        configuredArenas.clear()
        arenas.forEach {
            require(it.capacityPerTeam > 0 && it.quorumPerTeam in 1..it.capacityPerTeam)
            require(configuredArenas.putIfAbsent(it.id, it) == null) { "Duplicate arena ID ${it.id}" }
        }
    }

    fun installPlayerHooks(
        capture: (UUID) -> Unit,
        restore: (UUID) -> Unit,
        admit: (UUID, MatchSession, TeamAssignment) -> Unit
    ) {
        captureHook = capture
        restoreHook = restore
        admitHook = admit
    }
    fun installReloadHook(reload: () -> String) {
        reloadHook = reload
    }
    fun safetyPolicy(): SafetyPolicy = safety
    fun sessions(): List<MatchSession> = sessions.values.toList()
    fun arenaConfig(id: String): ArenaConfig? = configuredArenas[id]

    override fun join(player: UUID, mode: Mode, arena: String?): TeamAssignment {
        check(player !in owners) { "Player is already queued or in a match" }
        val arenaId = arenaProvider(mode, arena) ?: error("No configured ${mode.name} arena is available")
        val configured = configuredArenas[arenaId] ?: error("Arena '$arenaId' is not configured")
        require(configured.mode == mode) { "Arena '$arenaId' is unavailable for ${mode.name}" }
        val session = sessions.values.firstOrNull {
            it.mode == mode && it.arenaId == arenaId && it.phase == MatchPhase.WAITING
        } ?: MatchSession(mode, arenaId, configured.capacityPerTeam, configured.quorumPerTeam).also {
            onSessionCreated(it)
            sessions[it.id] = it
        }
        captureHook(player)
        try {
            val team = session.join(player)
            owners[player] = session
            admitHook(player, session, team)
            safety.activate(player)
            return team
        } catch (failure: RuntimeException) {
            owners.remove(player)
            safety.deactivate(player)
            if (session.teamOf(player) != null) session.leave(player)
            restoreHook(player)
            throw failure
        }
    }

    override fun leave(player: UUID): Boolean {
        val session = owners.remove(player) ?: return false
        session.leave(player)
        safety.deactivate(player)
        restoreHook(player)
        return true
    }

    override fun ready(player: UUID): Boolean = owners[player]?.ready(player) ?: false

    override fun status(player: UUID): String = owners[player]?.let { "${it.mode.name} ${it.phase.name}" } ?: "NONE"

    override fun team(player: UUID): TeamAssignment? = owners[player]?.teamOf(player)

    override fun admin(player: UUID?, action: String, args: List<String>): String = when (action.lowercase()) {
        "start" -> {
            val arena = args.firstOrNull()
            val started = sessions.filterValues { arena == null || it.arenaId == arena }.values.any { it.startIfReady() }
            if (started) "started" else "not ready"
        }
        "stop" -> {
            val selected = sessions.filterValues { args.firstOrNull() == null || it.arenaId == args.first() }.values.toList()
            selected.forEach(::terminate)
            "stopped ${selected.size}"
        }
        "arena" -> when (args.firstOrNull()?.lowercase()) {
            null, "list" -> configuredArenas.values.joinToString(prefix = "arenas: ") { "${it.id}(${it.mode})" }
            "validate" -> "validated ${configuredArenas.size} arenas"
            else -> "Usage: arena <list|validate>"
        }
        "reload" -> reloadHook()
        "debug" -> sessions.values.joinToString(prefix = "sessions: ") { "${it.mode}/${it.arenaId}/${it.phase}" }
        else -> "unknown admin action"
    }

    private fun terminate(session: MatchSession) {
        val players = owners.filterValues { it === session }.keys.toList()
        session.cleanup()
        players.forEach { player ->
            safety.deactivate(player)
            restoreHook(player)
            owners.remove(player)
        }
        sessions.remove(session.id)
        onSessionRemoved(session)
    }

    fun tick() {
        sessions.values.toList().forEach { session ->
            session.startIfReady()
            session.advance()
            if (session.phase == MatchPhase.RESOLVING || session.phase == MatchPhase.CLEANUP) terminate(session)
        }
    }

    fun shutdown(players: (UUID) -> Player?) {
        sessions.values.toList().forEach(::terminate)
        owners.keys.toList().forEach { player ->
            players(player)?.let { restore(it) } ?: restoreHook(player)
            safety.deactivate(player)
        }
        owners.clear()
        sessions.clear()
    }
    fun isParticipant(player: UUID): Boolean = owners.containsKey(player)

    fun disconnect(player: UUID) {
        owners[player]?.disconnect(player)
    }

    fun recordCombat(attacker: UUID, victim: UUID): Boolean {
        val session = owners[attacker] ?: return false
        if (owners[victim] !== session) return false
        return session.recordCombat(attacker, victim)
    }

    fun playerDied(player: UUID, killer: UUID? = null): Boolean {
        val session = owners[player] ?: return false
        if (killer != null && owners[killer] !== session) return session.playerDied(player)
        return session.playerDied(player, killer)
    }

    fun respawn(player: Player): Location? = owners[player.uniqueId]?.respawnLocation(player)

    fun capture(player: Player) {
        val snapshot = PlayerSnapshot(
            playerId = player.uniqueId,
            inventory = player.inventory.storageContents.map(::itemSnapshot),
            cursor = player.itemOnCursor.takeUnless { it.type.isAir }?.let(::itemSnapshot),
            armor = player.inventory.armorContents.map(::itemSnapshot),
            offhand = player.inventory.itemInOffHand.takeUnless { it.type.isAir }?.let(::itemSnapshot),
            effects = player.activePotionEffects.map {
                EffectSnapshot(
                    it.type.key.toString(), it.amplifier, it.duration,
                    it.isAmbient, it.hasParticles(), it.hasIcon()
                )
            },
            attributes = mapOf(
                "minecraft:max_health" to
                    (player.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: player.health)
            ),
            health = player.health,
            food = player.foodLevel,
            saturation = player.saturation.toDouble(),
            experience = player.exp,
            level = player.level,
            gameMode = player.gameMode.name,
            allowFlight = player.allowFlight,
            flying = player.isFlying,
            returnLocation = player.location.let {
                LocationSnapshot(it.world.name, it.x, it.y, it.z, it.yaw, it.pitch)
            }
        )
        if (restoreRepository != null) restoreRepository.put(snapshot) else snapshots[player.uniqueId] = snapshot
        player.inventory.clear()
        player.inventory.armorContents = emptyArray()
        player.setItemOnCursor(ItemStack(Material.AIR))
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.gameMode = GameMode.ADVENTURE
        player.allowFlight = false
        player.isFlying = false
    }

    fun restore(player: Player) {
        val pending = restoreRepository?.claim(player.uniqueId)
        val snapshot = pending?.snapshot ?: snapshots.remove(player.uniqueId) ?: return
        try {
            player.inventory.storageContents = snapshot.inventory.map(::restoreItem).toTypedArray()
            player.inventory.armorContents = snapshot.armor.map(::restoreItem).toTypedArray()
            player.inventory.setItemInOffHand(restoreItem(snapshot.offhand) ?: ItemStack(Material.AIR))
            player.setItemOnCursor(restoreItem(snapshot.cursor) ?: ItemStack(Material.AIR))
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            snapshot.effects.forEach { effect ->
                val key = NamespacedKey.fromString(effect.type) ?: return@forEach
                val type = PotionEffectType.getByKey(key) ?: return@forEach
                player.addPotionEffect(
                    PotionEffect(
                        type, effect.durationTicks, effect.amplifier,
                        effect.ambient, effect.particles, effect.icon
                    )
                )
            }
            snapshot.attributes["minecraft:max_health"]?.let { value ->
                player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = value
            }
            player.health = snapshot.health.coerceAtMost(player.getAttribute(Attribute.MAX_HEALTH)?.value ?: snapshot.health)
            player.foodLevel = snapshot.food
            player.saturation = snapshot.saturation.toFloat()
            player.exp = snapshot.experience
            player.level = snapshot.level
            player.gameMode = GameMode.valueOf(snapshot.gameMode)
            player.allowFlight = snapshot.allowFlight
            player.isFlying = snapshot.flying
            snapshot.returnLocation?.let { location ->
                val world = player.server.getWorld(location.world) ?: player.server.worlds.first()
                player.teleport(Location(world, location.x, location.y, location.z, location.yaw, location.pitch))
            }
            if (pending != null) check(restoreRepository!!.markRestored(player.uniqueId))
        } catch (failure: RuntimeException) {
            if (pending != null) restoreRepository!!.markFailed(player.uniqueId, failure.message ?: failure.javaClass.simpleName)
            throw failure
        }
    }

    private fun itemSnapshot(item: ItemStack?): ItemStackSnapshot {
        if (item == null || item.type.isAir) return ItemStackSnapshot(Material.AIR.name, 0)
        return ItemStackSnapshot(
            item.type.name,
            item.amount,
            Base64.getEncoder().encodeToString(item.serializeAsBytes())
        )
    }

    private fun restoreItem(snapshot: ItemStackSnapshot?): ItemStack? {
        if (snapshot == null || snapshot.amount <= 0 || snapshot.material == Material.AIR.name) return null
        return if (snapshot.metadata.isNotEmpty()) {
            ItemStack.deserializeBytes(Base64.getDecoder().decode(snapshot.metadata))
        } else {
            ItemStack(Material.valueOf(snapshot.material), snapshot.amount)
        }
    }
}
