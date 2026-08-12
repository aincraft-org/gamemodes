package dev.jlo.gamemodes.arena

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArenaModelTest {
    @Test
    fun `region contains boundaries and rejects inverted bounds`() {
        val region = Region(0, 0, 0, 10, 20, 30)
        assertEquals(true, region.contains(BlockPosition(0, 0, 0)))
        assertEquals(true, region.contains(BlockPosition(10, 20, 30)))
        assertEquals(false, region.contains(BlockPosition(11, 20, 30)))
        assertFailsWith<IllegalArgumentException> { Region(1, 0, 0, 0, 1, 1) }
    }

    @Test
    fun `arena definition validates mode spawns and placements`() {
        val valid = arenaDefinition()
        valid.validate()

        assertFailsWith<IllegalArgumentException> {
            valid.copy(spawns = emptyList()).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(objectives = emptyList()).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(structures = emptyList()).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(objectives = listOf(valid.objectives.first(), valid.objectives.first())).validate()
        }
    }

    @Test
    fun `siege definition requires rally objectives, structures, and placements`() {
        val valid = arenaDefinition(mode = ArenaMode.SIEGE)
        valid.validate()

        assertFailsWith<IllegalArgumentException> {
            valid.copy(objectives = valid.objectives.dropLast(1)).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(structures = valid.structures.filter { it.kind != StructureKind.WALL }).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(siegePlacements = emptyList()).validate()
        }
    }

    @Test
    fun `validation errors identify missing required arena content`() {
        val valid = arenaDefinition()
        assertEquals("OPR arena must define exactly 3 objectives", assertFailsWith<IllegalArgumentException> {
            valid.copy(objectives = emptyList()).validate()
        }.message)
        assertEquals("OPR arena must define both team structures", assertFailsWith<IllegalArgumentException> {
            valid.copy(structures = emptyList()).validate()
        }.message)
    }

    @Test
    fun `generation ids are nonblank and immutable`() {
        assertFailsWith<IllegalArgumentException> { GenerationId("") }
        assertEquals("g-1", GenerationId("g-1").value)
    }

    private fun arenaDefinition(
        mode: ArenaMode = ArenaMode.OPR
    ) = ArenaDefinition(
        id = "arena-${mode.name.lowercase()}",
        mode = mode,
        templateWorld = "templates/arena",
        bounds = Region(0, 0, 0, 100, 100, 100),
        spawns = listOf(
            Spawn("a", TeamSlot.A, BlockPosition(1, 1, 1)),
            Spawn("b", TeamSlot.B, BlockPosition(99, 1, 99))
        ),
        objectives = listOf(
            Objective("a", Region(10, 0, 10, 20, 20, 20)),
            Objective("b", Region(40, 0, 40, 50, 20, 50)),
            Objective("c", Region(70, 0, 70, 80, 20, 80))
        ),
        structures = listOf(
            StructurePlacement("gate-a", StructureKind.GATE, BlockPosition(5, 1, 5)),
            StructurePlacement("gate-b", StructureKind.GATE, BlockPosition(95, 1, 95)),
            StructurePlacement("command-a", StructureKind.COMMAND_POST, BlockPosition(8, 1, 8)),
            StructurePlacement("command-b", StructureKind.COMMAND_POST, BlockPosition(92, 1, 92)),
            StructurePlacement("wall", StructureKind.WALL, BlockPosition(50, 1, 50))
        ),
        resources = listOf(ResourcePlacement("ore", ResourceKind.ORE, BlockPosition(20, 1, 20))),
        siegePlacements = if (mode == ArenaMode.SIEGE) {
            listOf(
                SiegePlacement("weapon-a", BlockPosition(25, 1, 25)),
                SiegePlacement("weapon-b", BlockPosition(75, 1, 75))
            )
        } else emptyList()
    )
}

