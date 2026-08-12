package dev.jlo.gamemodes.arena

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaBehaviorTest {
    @Test
    fun `definition rejects unsafe template and placements`() {
        assertFailsWith<IllegalArgumentException> { definition(templateWorld = "../escape") }
        assertFailsWith<IllegalArgumentException> {
            definition(spawns = listOf(Spawn("a", TeamSlot.A, BlockPosition(1, 1, 1))))
        }
    }

    @Test
    fun `region overlap is inclusive`() {
        assertTrue(Region(0, 0, 0, 1, 1, 1).overlaps(Region(1, 1, 1, 2, 2, 2)))
    }

    @Test
    fun `generation cleanup cannot remove newer reservation`() {
        val fs = object : ArenaFilesystem {
            override fun exists(path: java.nio.file.Path) = true
            override fun deleteRecursively(path: java.nio.file.Path) {}
        }
        val worlds = object : ArenaWorldGateway {
            override fun load(template: java.nio.file.Path, instance: java.nio.file.Path) {}
            override fun unload(instance: java.nio.file.Path) {}
        }
        val manager = ArenaInstanceManager(java.nio.file.Path.of("templates"), java.nio.file.Path.of("instances"), fs, worlds)
        val definition = definition()
        manager.load(definition, GenerationId("g-2"))
        assertFalse(manager.unload(definition.id, GenerationId("g-1")))
        assertTrue(manager.unload(definition.id, GenerationId("g-2")))
    }

    private fun definition(
        templateWorld: String = "opr-one",
        spawns: List<Spawn> = listOf(
            Spawn("a", TeamSlot.A, BlockPosition(1, 1, 1)),
            Spawn("b", TeamSlot.B, BlockPosition(9, 1, 9))
        )
    ) = ArenaDefinition(
        "arena",
        ArenaMode.OPR,
        templateWorld,
        Region(0, 0, 0, 10, 10, 10),
        spawns,
        listOf(
            Objective("a", Region(1, 1, 1, 2, 2, 2)),
            Objective("b", Region(4, 1, 4, 5, 2, 5)),
            Objective("c", Region(7, 1, 7, 8, 2, 8))
        ),
        listOf(
            StructurePlacement("gate-a", StructureKind.GATE, BlockPosition(1, 1, 1)),
            StructurePlacement("gate-b", StructureKind.GATE, BlockPosition(9, 1, 9)),
            StructurePlacement("command-a", StructureKind.COMMAND_POST, BlockPosition(2, 1, 2)),
            StructurePlacement("command-b", StructureKind.COMMAND_POST, BlockPosition(8, 1, 8))
        ),
        listOf(ResourcePlacement("ore", ResourceKind.ORE, BlockPosition(3, 1, 3))),
        emptyList()
    )
}
