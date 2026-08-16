package dev.jlo.gamemodes.paper;

import java.util.List;
import java.util.UUID;

public interface CommandService {
    TeamAssignment join(UUID player, Mode mode, String arena);

    boolean leave(UUID player);

    boolean ready(UUID player);

    String status(UUID player);

    TeamAssignment team(UUID player);

    String admin(UUID player, String action, List<String> args);
}
