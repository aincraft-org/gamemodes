package dev.jlo.gamemodes.arena;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaBehaviorTest {
    @Test
    void definitionRejectsUnsafeTemplateAndPlacements() {
        assertThrows(IllegalArgumentException.class, () -> definition("../escape", null));
        assertThrows(IllegalArgumentException.class, () -> definition(null,
                List.of(new Spawn("a", TeamSlot.A, new BlockPosition(1, 1, 1)))));
    }

    @Test
    void regionOverlapIsInclusive() {
        assertTrue(new Region(0, 0, 0, 1, 1, 1)
                .overlaps(new Region(1, 1, 1, 2, 2, 2)));
    }

    @Test
    void generationCleanupCannotRemoveNewerReservation() {
        ArenaFilesystem filesystem = new ArenaFilesystem() {
            @Override
            public boolean exists(Path path) {
                return true;
            }

            @Override
            public void deleteRecursively(Path path) {
            }
        };
        ArenaWorldGateway worlds = new ArenaWorldGateway() {
            @Override
            public void load(Path template, Path instance) {
            }

            @Override
            public void unload(Path instance) {
            }
        };
        ArenaInstanceManager manager = new ArenaInstanceManager(
                Path.of("templates"), Path.of("instances"), filesystem, worlds);
        ArenaDefinition definition = definition(null, null);
        manager.load(definition, new GenerationId("g-2"));
        assertFalse(manager.unload(definition.getId(), new GenerationId("g-1")));
        assertTrue(manager.unload(definition.getId(), new GenerationId("g-2")));
    }

    private static ArenaDefinition definition(String templateWorld, List<Spawn> spawns) {
        if (templateWorld == null) {
            templateWorld = "opr-one";
        }
        if (spawns == null) {
            spawns = List.of(
                    new Spawn("a", TeamSlot.A, new BlockPosition(1, 1, 1)),
                    new Spawn("b", TeamSlot.B, new BlockPosition(9, 1, 9)));
        }
        return new ArenaDefinition(
                "arena",
                ArenaMode.OPR,
                templateWorld,
                new Region(0, 0, 0, 10, 10, 10),
                spawns,
                List.of(
                        new Objective("a", new Region(1, 1, 1, 2, 2, 2)),
                        new Objective("b", new Region(4, 1, 4, 5, 2, 5)),
                        new Objective("c", new Region(7, 1, 7, 8, 2, 8))),
                List.of(
                        new StructurePlacement("gate-a", StructureKind.GATE, new BlockPosition(1, 1, 1)),
                        new StructurePlacement("gate-b", StructureKind.GATE, new BlockPosition(9, 1, 9)),
                        new StructurePlacement("command-a", StructureKind.COMMAND_POST, new BlockPosition(2, 1, 2)),
                        new StructurePlacement("command-b", StructureKind.COMMAND_POST, new BlockPosition(8, 1, 8))),
                List.of(new ResourcePlacement("ore", ResourceKind.ORE, new BlockPosition(3, 1, 3))),
                List.of());
    }
}
