package dev.jlo.gamemodes.arena

import java.nio.file.Files
import java.nio.file.Path

interface ArenaDefinitionSource {
    fun load(path: Path): ArenaDefinition
}

class ArenaCatalog(
    private val root: Path,
    private val source: ArenaDefinitionSource
) {
    @Volatile
    private var snapshot: Snapshot = Snapshot(emptyMap(), emptySet())

    private data class Snapshot(
        val definitions: Map<String, ArenaDefinition>,
        val disabled: Set<String>
    )

    /**
     * Loads a complete replacement snapshot. If reading the directory itself fails,
     * the currently active snapshot is retained.
     */
    @Synchronized
    fun reload(): List<String> {
        val loaded = linkedMapOf<String, ArenaDefinition>()
        val failed = linkedSetOf<String>()
        if (!Files.exists(root)) {
            snapshot = Snapshot(emptyMap(), emptySet())
            return emptyList()
        }
        try {
            Files.list(root).use { stream ->
                stream.filter { Files.isRegularFile(it) }.sorted().forEach { path ->
                    try {
                        val definition = source.load(path)
                        require(definition.templateWorld.isSafeRelativePath()) { "Unsafe template world path" }
                        require(loaded.putIfAbsent(definition.id, definition) == null) {
                            "Duplicate arena ID"
                        }
                    } catch (_: RuntimeException) {
                        failed += path.fileName.toString().substringBeforeLast('.')
                    }
                }
            }
        } catch (_: RuntimeException) {
            return snapshot.definitions.keys.toList()
        }
        snapshot = Snapshot(loaded.toMap(), failed.toSet())
        return loaded.keys.toList()
    }

    fun get(id: String): ArenaDefinition? =
        snapshot.definitions[id]?.takeUnless { it.id in snapshot.disabled }

    fun all(): List<ArenaDefinition> =
        snapshot.definitions.values.filterNot { it.id in snapshot.disabled }

    fun isDisabled(id: String): Boolean = id in snapshot.disabled

    fun ids(): List<String> = snapshot.definitions.keys.toList()
}

internal fun String.isSafeRelativePath(): Boolean {
    if (isBlank() || startsWith('/') || startsWith('\\')) return false
    val path = Path.of(this)
    return !path.isAbsolute && path.normalize() == path && path.none { it.toString() == ".." }
}
