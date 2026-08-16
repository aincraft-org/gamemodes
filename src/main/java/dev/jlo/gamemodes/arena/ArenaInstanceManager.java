package dev.jlo.gamemodes.arena;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaInstanceManager {
    private record Reservation(GenerationId generation, Path instance) {
    }

    private final Path templateRoot;
    private final Path instanceRoot;
    private final ArenaFilesystem filesystem;
    private final ArenaWorldGateway worlds;
    private final ConcurrentHashMap<String, Reservation> reservations = new ConcurrentHashMap<>();

    public ArenaInstanceManager(Path templateRoot, Path instanceRoot,
                                ArenaFilesystem filesystem, ArenaWorldGateway worlds) {
        this.templateRoot = Objects.requireNonNull(templateRoot, "templateRoot");
        this.instanceRoot = Objects.requireNonNull(instanceRoot, "instanceRoot");
        this.filesystem = Objects.requireNonNull(filesystem, "filesystem");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    public synchronized Path reserve(ArenaDefinition definition, GenerationId generation) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(generation, "generation");
        definition.validate();
        if (!ArenaCatalogKt.isSafeRelativePath(definition.getTemplateWorld())) {
            throw new IllegalArgumentException("Unsafe template world path");
        }
        if (reservations.get(definition.getId()) != null) {
            throw new IllegalArgumentException("Arena is already loaded");
        }
        Path template = templatePath(definition);
        if (!filesystem.exists(template)) {
            throw new IllegalArgumentException("Arena template does not exist");
        }
        Path instance = instancePath(definition.getId(), generation);
        reservations.put(definition.getId(), new Reservation(generation, instance));
        return instance;
    }

    public synchronized Path load(ArenaDefinition definition, GenerationId generation) {
        Path instance = reserve(definition, generation);
        try {
            worlds.load(templatePath(definition), instance);
        } catch (RuntimeException failure) {
            reservations.remove(definition.getId(), new Reservation(generation, instance));
            throw failure;
        }
        return instance;
    }

    public synchronized boolean unload(String id, GenerationId generation) {
        Reservation reservation = reservations.get(id);
        if (reservation == null || !reservation.generation().equals(generation)) {
            return false;
        }
        worlds.unload(reservation.instance());
        reservations.remove(id, reservation);
        return true;
    }

    public synchronized boolean delete(String id, GenerationId generation) {
        Reservation reservation = reservations.get(id);
        if (reservation != null) {
            if (!reservation.generation().equals(generation)) {
                return false;
            }
            worlds.unload(reservation.instance());
            reservations.remove(id, reservation);
            filesystem.deleteRecursively(reservation.instance());
            return true;
        }
        Path instance = instancePath(id, generation);
        filesystem.deleteRecursively(instance);
        return true;
    }

    public GenerationId generationOf(String id) {
        Reservation reservation = reservations.get(id);
        return reservation == null ? null : reservation.generation();
    }

    public Path instancePath(String id, GenerationId generation) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(generation, "generation");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Arena ID must not be blank");
        }
        for (int i = 0; i < id.length(); i++) {
            char character = id.charAt(i);
            if (!(Character.isLetterOrDigit(character) || character == '-' || character == '_')) {
                throw new IllegalArgumentException("Unsafe arena ID");
            }
        }
        String name = "gamemodes-" + id + "-" + generation.getValue();
        Path normalizedRoot = instanceRoot.normalize();
        Path instance = instanceRoot.resolve(name).normalize();
        if (!instance.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Instance escapes instance root");
        }
        return instance;
    }

    private Path templatePath(ArenaDefinition definition) {
        Path normalizedRoot = templateRoot.normalize();
        Path template = templateRoot.resolve(definition.getTemplateWorld()).normalize();
        if (!template.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Template escapes template root");
        }
        return template;
    }
}
