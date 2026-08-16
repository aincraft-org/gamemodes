package dev.jlo.gamemodes.domain.common;

import java.util.UUID;

public interface PaperGateway {
    void sendMessage(UUID player, String message);
}
