package dev.jlo.gamemodes.arena;

import java.nio.file.Path;

public interface ArenaWorldGateway {
    void load(Path template, Path instance);

    void unload(Path instance);
}
