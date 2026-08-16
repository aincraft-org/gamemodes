package dev.jlo.gamemodes.arena;

import java.nio.file.Path;

public interface ArenaDefinitionSource {
    ArenaDefinition load(Path path);
}
