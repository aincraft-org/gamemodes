package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.arena.ArenaWorldGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperArenaWorldGateway implements ArenaWorldGateway {
    private final JavaPlugin plugin;

    public PaperArenaWorldGateway(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void load(Path template, Path instance) {
        String templateName = template.getFileName().toString();
        if (Bukkit.getWorld(templateName) != null) {
            throw new IllegalArgumentException("Template world '" + templateName + "' must be unloaded");
        }
        if (Files.exists(instance)) {
            throw new IllegalArgumentException("Arena instance already exists");
        }
        try (var paths = Files.walk(template)) {
            paths.forEach(source -> {
                Path destination = instance.resolve(template.relativize(source));
                try {
                    if (Files.isDirectory(source)) Files.createDirectories(destination);
                    else Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        try {
            Files.deleteIfExists(instance.resolve("uid.dat"));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        if (Bukkit.createWorld(new WorldCreator(instance.getFileName().toString())) == null) {
            throw new IllegalStateException("Could not load arena instance " + instance.getFileName());
        }
    }

    @Override
    public void unload(Path instance) {
        World world = Bukkit.getWorld(instance.getFileName().toString());
        if (world == null) return;
        var fallback = plugin.getServer().getWorlds().stream()
                .filter(candidate -> !candidate.getUID().equals(world.getUID()))
                .findFirst()
                .map(World::getSpawnLocation)
                .orElse(null);
        if (fallback != null) {
            world.getPlayers().forEach(player -> player.teleport(fallback));
        }
        if (!Bukkit.unloadWorld(world, false)) {
            throw new IllegalArgumentException("Could not unload arena " + world.getName());
        }
    }
}
