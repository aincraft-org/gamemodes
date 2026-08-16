package dev.jlo.gamemodes.paper;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class SafetyPolicy {
    private final Set<UUID> active = new LinkedHashSet<>();

    public synchronized void activate(UUID player) {
        active.add(player);
    }

    public synchronized void deactivate(UUID player) {
        active.remove(player);
    }

    public synchronized boolean isActive(UUID player) {
        return active.contains(player);
    }

    public synchronized boolean blocks(UUID player, SafetyAction action) {
        return blocks(player, action, false);
    }

    public synchronized boolean blocks(UUID player, SafetyAction action, boolean authorized) {
        return active.contains(player) && !authorized;
    }
}
