package dev.jlo.gamemodes.arena;

import java.nio.file.Path;

public interface ArenaFilesystem {
    boolean exists(Path path);

    void deleteRecursively(Path path);
}
