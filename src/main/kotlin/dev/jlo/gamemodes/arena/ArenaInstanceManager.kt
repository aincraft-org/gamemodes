package dev.jlo.gamemodes.arena

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

interface ArenaFilesystem {
    fun exists(path: Path): Boolean
    fun deleteRecursively(path: Path)
}

interface ArenaWorldGateway {
    fun load(template: Path, instance: Path)
    fun unload(instance: Path)
}

class ArenaInstanceManager(
    private val templateRoot: Path,
    private val instanceRoot: Path,
    private val filesystem: ArenaFilesystem,
    private val worlds: ArenaWorldGateway
) {
    private data class Reservation(val generation: GenerationId, val instance: Path)
    private val reservations = ConcurrentHashMap<String, Reservation>()

    @Synchronized
    fun reserve(definition: ArenaDefinition, generation: GenerationId): Path {
        definition.validate()
        require(definition.templateWorld.isSafeRelativePath()) { "Unsafe template world path" }
        require(reservations[definition.id] == null) { "Arena is already loaded" }
        val template = templatePath(definition)
        require(filesystem.exists(template)) { "Arena template does not exist" }
        val instance = instancePath(definition.id, generation)
        reservations[definition.id] = Reservation(generation, instance)
        return instance
    }

    @Synchronized
    fun load(definition: ArenaDefinition, generation: GenerationId): Path {
        val instance = reserve(definition, generation)
        try {
            worlds.load(templatePath(definition), instance)
        } catch (failure: RuntimeException) {
            reservations.remove(definition.id, Reservation(generation, instance))
            throw failure
        }
        return instance
    }

    @Synchronized
    fun unload(id: String, generation: GenerationId): Boolean {
        val reservation = reservations[id] ?: return false
        if (reservation.generation != generation) return false
        worlds.unload(reservation.instance)
        reservations.remove(id, reservation)
        return true
    }

    @Synchronized
    fun delete(id: String, generation: GenerationId): Boolean {
        val reservation = reservations[id]
        if (reservation != null) {
            if (reservation.generation != generation) return false
            worlds.unload(reservation.instance)
            reservations.remove(id, reservation)
            filesystem.deleteRecursively(reservation.instance)
            return true
        }
        val instance = instancePath(id, generation)
        filesystem.deleteRecursively(instance)
        return true
    }

    fun generationOf(id: String): GenerationId? = reservations[id]?.generation

    fun instancePath(id: String, generation: GenerationId): Path {
        require(id.isNotBlank()) { "Arena ID must not be blank" }
        require(id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Unsafe arena ID" }
        val name = "gamemodes-$id-${generation.value}"
        val instance = instanceRoot.resolve(name).normalize()
        require(instance.startsWith(instanceRoot.normalize())) { "Instance escapes instance root" }
        return instance
    }

    private fun templatePath(definition: ArenaDefinition): Path {
        val template = templateRoot.resolve(definition.templateWorld).normalize()
        require(template.startsWith(templateRoot.normalize())) { "Template escapes template root" }
        return template
    }
}
