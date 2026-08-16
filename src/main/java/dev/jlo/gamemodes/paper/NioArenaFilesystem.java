package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.arena.ArenaFilesystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class NioArenaFilesystem implements ArenaFilesystem {
    @Override
    public boolean exists(Path path) {
        return Files.isDirectory(path);
    }

    @Override
    public void deleteRecursively(Path path) {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
