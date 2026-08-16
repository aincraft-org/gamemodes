package dev.jlo.gamemodes.arena;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaModelTest {
    @Test
    void regionContainsBoundariesAndRejectsInvertedBounds() {
        Region region = new Region(0, 0, 0, 10, 20, 30);
        assertTrue(region.contains(new BlockPosition(0, 0, 0)));
        assertTrue(region.contains(new BlockPosition(10, 20, 30)));
        assertFalse(region.contains(new BlockPosition(11, 20, 30)));
        assertThrows(IllegalArgumentException.class, () -> new Region(1, 0, 0, 0, 1, 1));
    }

    @Test
    void arenaDefinitionValidatesModeSpawnsAndPlacements() {
        ArenaDefinition valid = arenaDefinition();
        valid.validate();

        assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(),
                List.of(), valid.getObjectives(), valid.getStructures(), valid.getResources(), valid.getSiegePlacements()
        ).validate());
        assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(),
                valid.getSpawns(), List.of(), valid.getStructures(), valid.getResources(), valid.getSiegePlacements()
        ).validate());
        assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(),
                valid.getSpawns(), valid.getObjectives(), List.of(), valid.getResources(), valid.getSiegePlacements()
        ).validate());
        assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(),
                valid.getSpawns(), List.of(valid.getObjectives().getFirst(), valid.getObjectives().getFirst()),
                valid.getStructures(), valid.getResources(), valid.getSiegePlacements()
        ).validate());
    }

    @Test
    void siegeDefinitionRequiresRallyObjectivesStructuresAndPlacements() {
        ArenaDefinition valid = arenaDefinition(ArenaMode.SIEGE);
        valid.validate();

        assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(), valid.getSpawns(),
                valid.getObjectives().subList(0, valid.getObjectives().size() - 1), valid.getStructures(),
                valid.getResources(), valid.getSiegePlacements()
        ).validate());
        assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(), valid.getSpawns(),
                valid.getObjectives(), valid.getStructures().stream().filter(s -> s.getKind() != StructureKind.WALL).toList(),
                valid.getResources(), valid.getSiegePlacements()
        ).validate());
        assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(), valid.getSpawns(),
                valid.getObjectives(), valid.getStructures(), valid.getResources(), List.of()
        ).validate());
    }

    @Test
    void validationErrorsIdentifyMissingRequiredArenaContent() {
        ArenaDefinition valid = arenaDefinition();
        IllegalArgumentException missingObjectives = assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(), valid.getSpawns(),
                List.of(), valid.getStructures(), valid.getResources(), valid.getSiegePlacements()
        ).validate());
        assertEquals("OPR arena must define exactly 3 objectives", missingObjectives.getMessage());

        IllegalArgumentException missingStructures = assertThrows(IllegalArgumentException.class, () -> valid.copy(
                valid.getId(), valid.getMode(), valid.getTemplateWorld(), valid.getBounds(), valid.getSpawns(),
                valid.getObjectives(), List.of(), valid.getResources(), valid.getSiegePlacements()
        ).validate());
        assertEquals("OPR arena must define both team structures", missingStructures.getMessage());
    }

    @Test
    void generationIdsAreNonblankAndImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new GenerationId(""));
        assertEquals("g-1", new GenerationId("g-1").getValue());
    }

    private ArenaDefinition arenaDefinition() {
        return arenaDefinition(ArenaMode.OPR);
    }

    private ArenaDefinition arenaDefinition(ArenaMode mode) {
        return new ArenaDefinition(
                "arena-" + mode.name().toLowerCase(),
                mode,
                "templates/arena",
                new Region(0, 0, 0, 100, 100, 100),
                List.of(
                        new Spawn("a", TeamSlot.A, new BlockPosition(1, 1, 1)),
                        new Spawn("b", TeamSlot.B, new BlockPosition(99, 1, 99))
                ),
                List.of(
                        new Objective("a", new Region(10, 0, 10, 20, 20, 20)),
                        new Objective("b", new Region(40, 0, 40, 50, 20, 50)),
                        new Objective("c", new Region(70, 0, 70, 80, 20, 80))
                ),
                List.of(
                        new StructurePlacement("gate-a", StructureKind.GATE, new BlockPosition(5, 1, 5)),
                        new StructurePlacement("gate-b", StructureKind.GATE, new BlockPosition(95, 1, 95)),
                        new StructurePlacement("command-a", StructureKind.COMMAND_POST, new BlockPosition(8, 1, 8)),
                        new StructurePlacement("command-b", StructureKind.COMMAND_POST, new BlockPosition(92, 1, 92)),
                        new StructurePlacement("wall", StructureKind.WALL, new BlockPosition(50, 1, 50))
                ),
                List.of(new ResourcePlacement("ore", ResourceKind.ORE, new BlockPosition(20, 1, 20))),
                mode == ArenaMode.SIEGE
                        ? List.of(
                                new SiegePlacement("weapon-a", new BlockPosition(25, 1, 25)),
                                new SiegePlacement("weapon-b", new BlockPosition(75, 1, 75))
                        )
                        : List.of()
        );
    }
}
