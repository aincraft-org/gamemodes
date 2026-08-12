package dev.jlo.gamemodes.paper

import dev.jlo.gamemodes.arena.ArenaCatalog
import dev.jlo.gamemodes.arena.ArenaDefinition
import dev.jlo.gamemodes.arena.ArenaInstanceManager
import dev.jlo.gamemodes.arena.ArenaMode
import dev.jlo.gamemodes.arena.GenerationId
import dev.jlo.gamemodes.arena.TeamSlot
import dev.jlo.gamemodes.player.PendingRestoreRepository
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

/** Lifecycle owner for Paper-facing services and arena instances. */
class PaperAdapter(
    private val plugin: JavaPlugin,
    private val restoreRepository: PendingRestoreRepository
) {
    private data class Lease(
        val definition: ArenaDefinition,
        val generation: GenerationId,
        val worldPath: Path
    )

    private val arenasDirectory = plugin.dataFolder.toPath().resolve("arenas")
    private val catalog = ArenaCatalog(arenasDirectory, YamlArenaDefinitionSource())
    private val instanceManager = ArenaInstanceManager(
        plugin.server.worldContainer.toPath(),
        plugin.server.worldContainer.toPath(),
        NioArenaFilesystem(),
        PaperArenaWorldGateway(plugin)
    )
    private val leases = linkedMapOf<String, Lease>()

    val service = PaperMatchService(
        arenaProvider = { mode, requested ->
            requested ?: catalog.all().firstOrNull { it.mode.toMode() == mode }?.id
        },
        onSessionCreated = { session ->
            val definition = requireNotNull(catalog.get(session.arenaId)) {
                "Arena '${session.arenaId}' is not available"
            }
            require(definition.mode.toMode() == session.mode) { "Arena mode mismatch" }
            val generation = GenerationId(UUID.randomUUID().toString())
            val worldPath = instanceManager.load(definition, generation)
            leases[session.id] = Lease(definition, generation, worldPath)
        },
        onSessionRemoved = { session ->
            leases.remove(session.id)?.let { lease ->
                instanceManager.delete(lease.definition.id, lease.generation)
            }
        },
        restoreRepository = restoreRepository
    )
    private val ui = PaperUi(plugin)
    private val safetyListener = PaperSafetyListener(service.safetyPolicy()) { world ->
        leases.values.any { it.worldPath.fileName.toString() == world.name }
    }
    private val gameplayListener = PaperGameplayListener(service, ui)
    private var taskId: Int? = null

    fun enable() {
        installDefaultDefinitions()
        reloadCatalog()
        service.installPlayerHooks(
            capture = { id -> online(id).let(service::capture) },
            restore = { id -> plugin.server.getPlayer(id)?.let(service::restore) },
            admit = { id, session, team -> admit(online(id), session, team) }
        )
        service.installReloadHook {
            if (service.sessions().isNotEmpty()) "Cannot reload while matches are queued or active"
            else reloadCatalog()
        }
        plugin.server.pluginManager.registerEvents(safetyListener, plugin)
        plugin.server.pluginManager.registerEvents(gameplayListener, plugin)
        val command = requireNotNull(plugin.getCommand("gamemodes")) { "gamemodes command is not declared" }
        val executor = PaperCommandExecutor(service)
        command.setExecutor(executor)
        command.tabCompleter = executor
        taskId = plugin.server.scheduler.scheduleSyncRepeatingTask(plugin, Runnable {
            service.tick()
            service.sessions().forEach { session ->
                sessionPlayers(session).forEach { ui.update(it, session) }
            }
        }, 1L, 1L)
    }

    private fun installDefaultDefinitions() {
        Files.createDirectories(arenasDirectory)
        listOf("opr-default.yml", "siege-default.yml").forEach { name ->
            if (!Files.exists(arenasDirectory.resolve(name))) {
                plugin.saveResource("arenas/$name", false)
            }
        }
    }

    private fun reloadCatalog(): String {
        val ids = catalog.reload()
        provisionMissingTemplates(catalog.all())
        val configs = catalog.all().map { definition ->
            val mode = definition.mode.toMode()
            val modePath = mode.name.lowercase()
            val capacity = plugin.config.getInt(
                "modes.$modePath.nominal-team-size",
                if (mode == Mode.OPR) 20 else 50
            )
            val quorum = plugin.config.getInt(
                "modes.small-server-quorum.$modePath",
                maxOf(1, capacity / 2)
            )
            PaperMatchService.ArenaConfig(definition.id, mode, capacity, quorum)
        }
        service.configureArenas(configs)
        return "Loaded ${ids.size} arenas: ${ids.joinToString()}"
    }
    private fun provisionMissingTemplates(definitions: List<ArenaDefinition>) {
        val worldContainer = plugin.server.worldContainer.toPath()
        definitions.forEach { definition ->
            val worldDirectory = worldContainer.resolve(definition.templateWorld)
            if (Files.isDirectory(worldDirectory)) return@forEach
            val world = requireNotNull(
                Bukkit.createWorld(
                    WorldCreator(definition.templateWorld)
                        .type(WorldType.FLAT)
                        .generateStructures(false)
                )
            ) { "Could not create template ${definition.templateWorld}" }
            val floorY = definition.spawns.minOf { it.position.y } - 1
            for (x in definition.bounds.minX..definition.bounds.maxX) {
                for (z in definition.bounds.minZ..definition.bounds.maxZ) {
                    world.getBlockAt(x, floorY, z).setType(Material.GRASS_BLOCK, false)
                }
            }
            definition.objectives.forEachIndexed { index, objective ->
                val x = (objective.region.minX + objective.region.maxX) / 2
                val z = (objective.region.minZ + objective.region.maxZ) / 2
                val material = if (index == 0) Material.RED_WOOL else if (index == 1) Material.WHITE_WOOL else Material.BLUE_WOOL
                world.getBlockAt(x, floorY + 1, z).setType(material, false)
            }
            definition.structures.forEach { structure ->
                world.getBlockAt(structure.position.x, structure.position.y, structure.position.z)
                    .setType(Material.IRON_BLOCK, false)
            }
            val firstSpawn = definition.spawns.first().position
            world.setSpawnLocation(firstSpawn.x, firstSpawn.y, firstSpawn.z)
            world.worldBorder.setCenter(
                (definition.bounds.minX + definition.bounds.maxX) / 2.0,
                (definition.bounds.minZ + definition.bounds.maxZ) / 2.0
            )
            world.worldBorder.size = maxOf(
                definition.bounds.maxX - definition.bounds.minX,
                definition.bounds.maxZ - definition.bounds.minZ
            ).toDouble()
            world.save()
            require(Bukkit.unloadWorld(world, true)) { "Could not unload template ${definition.templateWorld}" }
        }
    }


    private fun admit(player: Player, session: MatchSession, team: TeamAssignment) {
        val lease = requireNotNull(leases[session.id]) { "Arena instance was not allocated" }
        val world = requireNotNull(Bukkit.getWorld(lease.worldPath.fileName.toString())) {
            "Arena instance world is not loaded"
        }
        val slot = if (team == TeamAssignment.A) TeamSlot.A else TeamSlot.B
        val spawn = requireNotNull(lease.definition.spawns.firstOrNull { it.team == slot }) {
            "Arena has no spawn for team $slot"
        }
        require(player.teleport(Location(world, spawn.position.x + 0.5, spawn.position.y.toDouble(), spawn.position.z + 0.5))) {
            "Player teleport into arena was rejected"
        }
        player.inventory.addItem(ItemStack(Material.IRON_SWORD))
        player.inventory.addItem(ItemStack(Material.BOW))
        player.inventory.addItem(ItemStack(Material.ARROW, 32))
    }

    fun disable() {
        taskId?.let(plugin.server.scheduler::cancelTask)
        service.shutdown { plugin.server.getPlayer(it) }
        ui.clearAll()
    }

    private fun online(id: UUID): Player = requireNotNull(plugin.server.getPlayer(id)) { "Player is offline" }

    private fun sessionPlayers(session: MatchSession) =
        plugin.server.onlinePlayers.filter { session.teamOf(it.uniqueId) != null }

    private fun ArenaMode.toMode() = if (this == ArenaMode.OPR) Mode.OPR else Mode.SIEGE
}
