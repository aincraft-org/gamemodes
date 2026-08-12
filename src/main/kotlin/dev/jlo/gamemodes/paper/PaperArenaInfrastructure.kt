package dev.jlo.gamemodes.paper

import dev.jlo.gamemodes.arena.ArenaDefinition
import dev.jlo.gamemodes.arena.ArenaDefinitionSource
import dev.jlo.gamemodes.arena.ArenaFilesystem
import dev.jlo.gamemodes.arena.ArenaMode
import dev.jlo.gamemodes.arena.ArenaWorldGateway
import dev.jlo.gamemodes.arena.BlockPosition
import dev.jlo.gamemodes.arena.Objective
import dev.jlo.gamemodes.arena.Region
import dev.jlo.gamemodes.arena.ResourceKind
import dev.jlo.gamemodes.arena.ResourcePlacement
import dev.jlo.gamemodes.arena.SiegePlacement
import dev.jlo.gamemodes.arena.Spawn
import dev.jlo.gamemodes.arena.StructureKind
import dev.jlo.gamemodes.arena.StructurePlacement
import dev.jlo.gamemodes.arena.TeamSlot
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

class YamlArenaDefinitionSource : ArenaDefinitionSource {
    override fun load(path: Path): ArenaDefinition {
        val yaml = YamlConfiguration.loadConfiguration(path.toFile())
        require(yaml.getInt("schema-version", 0) == 1) { "Unsupported arena schema" }
        val mode = when (yaml.getString("mode")?.lowercase()) {
            "opr" -> ArenaMode.OPR
            "siege" -> ArenaMode.SIEGE
            else -> error("Unknown arena mode")
        }
        return ArenaDefinition(
            id = yaml.requireString("id"),
            mode = mode,
            templateWorld = yaml.requireString("template-world"),
            bounds = yaml.requireSection("bounds").region(),
            spawns = yaml.getMapList("spawns").map { map ->
                Spawn(
                    map.requireString("id"),
                    TeamSlot.valueOf(map.requireString("team").uppercase()),
                    map.position()
                )
            },
            objectives = yaml.getMapList("objectives").map { map ->
                Objective(map.requireString("id"), map.requireMap("region").region())
            },
            structures = yaml.getMapList("structures").map { map ->
                StructurePlacement(
                    map.requireString("id"),
                    StructureKind.valueOf(map.requireString("kind").uppercase()),
                    map.position()
                )
            },
            resources = yaml.getMapList("resources").map { map ->
                ResourcePlacement(
                    map.requireString("id"),
                    ResourceKind.valueOf(map.requireString("kind").uppercase()),
                    map.position()
                )
            },
            siegePlacements = yaml.getMapList("siege-placements").map { map ->
                SiegePlacement(map.requireString("id"), map.position())
            }
        )
    }

    private fun YamlConfiguration.requireString(path: String): String =
        requireNotNull(getString(path)) { "Missing $path" }.also { require(it.isNotBlank()) }

    private fun YamlConfiguration.requireSection(path: String): ConfigurationSection =
        requireNotNull(getConfigurationSection(path)) { "Missing $path" }

    private fun ConfigurationSection.region() = Region(
        getInt("min-x"), getInt("min-y"), getInt("min-z"),
        getInt("max-x"), getInt("max-y"), getInt("max-z")
    )

    private fun Map<*, *>.requireString(key: String): String =
        (this[key] as? String)?.also { require(it.isNotBlank()) } ?: error("Missing $key")

    private fun Map<*, *>.requireInt(key: String): Int =
        (this[key] as? Number)?.toInt() ?: error("Missing $key")

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.requireMap(key: String): Map<*, *> =
        this[key] as? Map<*, *> ?: error("Missing $key")

    private fun Map<*, *>.position() = BlockPosition(requireInt("x"), requireInt("y"), requireInt("z"))

    private fun Map<*, *>.region() = Region(
        requireInt("min-x"), requireInt("min-y"), requireInt("min-z"),
        requireInt("max-x"), requireInt("max-y"), requireInt("max-z")
    )
}

class NioArenaFilesystem : ArenaFilesystem {
    override fun exists(path: Path): Boolean = Files.isDirectory(path)

    override fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

class PaperArenaWorldGateway(private val plugin: JavaPlugin) : ArenaWorldGateway {
    override fun load(template: Path, instance: Path) {
        val templateName = template.fileName.toString()
        require(Bukkit.getWorld(templateName) == null) { "Template world '$templateName' must be unloaded" }
        require(!Files.exists(instance)) { "Arena instance already exists" }
        Files.walk(template).use { paths ->
            paths.forEach { source ->
                val destination = instance.resolve(template.relativize(source))
                if (Files.isDirectory(source)) Files.createDirectories(destination)
                else Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
            }
        }
        Files.deleteIfExists(instance.resolve("uid.dat"))
        require(Bukkit.createWorld(WorldCreator(instance.fileName.toString())) != null) {
            "Could not load arena instance ${instance.fileName}"
        }
    }

    override fun unload(instance: Path) {
        val world = Bukkit.getWorld(instance.fileName.toString()) ?: return
        val fallback = plugin.server.worlds.firstOrNull { it.uid != world.uid }?.spawnLocation
        if (fallback != null) world.players.forEach { it.teleport(fallback) }
        require(Bukkit.unloadWorld(world, false)) { "Could not unload arena ${world.name}" }
    }
}
