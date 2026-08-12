package dev.jlo.gamemodes.arena

@JvmInline
value class GenerationId(val value: String) {
    init { require(value.isNotBlank()) { "Generation ID must not be blank" } }
}

data class BlockPosition(val x: Int, val y: Int, val z: Int)

data class Region(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int
) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "Region bounds must not be inverted" }
    }

    fun contains(position: BlockPosition): Boolean =
        position.x in minX..maxX && position.y in minY..maxY && position.z in minZ..maxZ

    fun overlaps(other: Region): Boolean =
        minX <= other.maxX && maxX >= other.minX &&
            minY <= other.maxY && maxY >= other.minY &&
            minZ <= other.maxZ && maxZ >= other.minZ
}

enum class ArenaMode { OPR, SIEGE }
enum class TeamSlot { A, B }

data class Spawn(val id: String, val team: TeamSlot, val position: BlockPosition)
data class Objective(val id: String, val region: Region)
enum class StructureKind { GATE, COMMAND_POST, WALL }
data class StructurePlacement(val id: String, val kind: StructureKind, val position: BlockPosition)

enum class ResourceKind { ORE, WOOD, HIDES, AZOTH }
data class ResourcePlacement(val id: String, val kind: ResourceKind, val position: BlockPosition)

data class SiegePlacement(val id: String, val position: BlockPosition)

data class ArenaDefinition(
    val id: String,
    val mode: ArenaMode,
    val templateWorld: String,
    val bounds: Region,
    val spawns: List<Spawn>,
    val objectives: List<Objective>,
    val structures: List<StructurePlacement>,
    val resources: List<ResourcePlacement>,
    val siegePlacements: List<SiegePlacement>
) {
    init {
        require(id.isNotBlank()) { "Arena ID must not be blank" }
        require(templateWorld.isNotBlank()) { "Template world must not be blank" }
        require(templateWorld.isSafeRelativePath()) { "Unsafe template world path" }
        validate()
    }

    fun validate() {
        require(spawns.isNotEmpty()) { "Arena must define spawns" }
        requireUnique(spawns.map { it.id }, "spawn")
        requireUnique(objectives.map { it.id }, "objective")
        requireUnique(structures.map { it.id }, "structure")
        requireUnique(resources.map { it.id }, "resource")
        requireUnique(siegePlacements.map { it.id }, "siege placement")
        require(spawns.any { it.team == TeamSlot.A } && spawns.any { it.team == TeamSlot.B }) {
            "Arena must define a spawn for both teams"
        }
        when (mode) {
            ArenaMode.OPR -> {
                require(objectives.size == OPR_OBJECTIVE_COUNT) {
                    "OPR arena must define exactly $OPR_OBJECTIVE_COUNT objectives"
                }
                require(structures.count { it.kind == StructureKind.GATE } >= 2 &&
                    structures.count { it.kind == StructureKind.COMMAND_POST } >= 2) {
                    "OPR arena must define both team structures"
                }
                require(siegePlacements.isEmpty()) { "OPR arena must not define siege placements" }
            }
            ArenaMode.SIEGE -> {
                require(objectives.size == SIEGE_OBJECTIVE_COUNT) {
                    "Siege arena must define exactly $SIEGE_OBJECTIVE_COUNT objectives"
                }
                require(structures.any { it.kind == StructureKind.GATE } &&
                    structures.any { it.kind == StructureKind.COMMAND_POST } &&
                    structures.any { it.kind == StructureKind.WALL }) {
                    "Siege arena must define gate, command post, and wall structures"
                }
                require(siegePlacements.isNotEmpty()) { "Siege arena must define siege placements" }
            }
        }
        spawns.forEach { require(bounds.contains(it.position)) { "Spawn outside arena bounds" } }
        objectives.forEach { require(regionWithin(it.region)) { "Objective outside arena bounds" } }
        structures.forEach { require(bounds.contains(it.position)) { "Structure outside arena bounds" } }
        resources.forEach { require(bounds.contains(it.position)) { "Resource outside arena bounds" } }
        siegePlacements.forEach { require(bounds.contains(it.position)) { "Siege placement outside arena bounds" } }
        val regions = objectives.map { it.id to it.region }
        for (i in regions.indices) for (j in i + 1 until regions.size) {
            require(!regions[i].second.overlaps(regions[j].second)) { "Objectives overlap" }
        }
    }

    private fun regionWithin(region: Region) =
        bounds.contains(BlockPosition(region.minX, region.minY, region.minZ)) &&
            bounds.contains(BlockPosition(region.maxX, region.maxY, region.maxZ))

    private fun requireUnique(ids: List<String>, kind: String) {
        require(ids.all { it.isNotBlank() }) { "$kind IDs must not be blank" }
        require(ids.size == ids.toSet().size) { "Duplicate $kind IDs" }
    }

    private companion object {
        const val OPR_OBJECTIVE_COUNT = 3
        const val SIEGE_OBJECTIVE_COUNT = 3
    }
}

