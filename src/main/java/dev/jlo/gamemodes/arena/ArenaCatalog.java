package dev.jlo.gamemodes.arena;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArenaCatalog {
    private final Path root;
    private final ArenaDefinitionSource source;

    private volatile Snapshot snapshot = new Snapshot(Collections.emptyMap(), Collections.emptySet());

    public ArenaCatalog(Path root, ArenaDefinitionSource source) {
        this.root = root;
        this.source = source;
    }

    /**
     * Loads a complete replacement snapshot. If reading the directory itself fails,
     * the currently active snapshot is retained.
     */
    public synchronized List<String> reload() {
        Map<String, ArenaDefinition> loaded = new LinkedHashMap<>();
        Set<String> failed = new LinkedHashSet<>();
        if (!Files.exists(root)) {
            snapshot = new Snapshot(Collections.emptyMap(), Collections.emptySet());
            return Collections.emptyList();
        }
        try (var stream = Files.list(root)) {
            stream.filter(Files::isRegularFile).sorted().forEach(path -> {
                try {
                    ArenaDefinition definition = source.load(path);
                    if (!ArenaCatalogKt.isSafeRelativePath(definition.getTemplateWorld())) {
                        throw new IllegalArgumentException("Unsafe template world path");
                    }
                    if (loaded.putIfAbsent(definition.getId(), definition) != null) {
                        throw new IllegalArgumentException("Duplicate arena ID");
                    }
                } catch (RuntimeException ignored) {
                    String fileName = path.getFileName().toString();
                    int extension = fileName.lastIndexOf('.');
                    failed.add(extension >= 0 ? fileName.substring(0, extension) : fileName);
                }
            });
        } catch (IOException | RuntimeException ignored) {
            return new ArrayList<>(snapshot.definitions.keySet());
        }
        snapshot = new Snapshot(Map.copyOf(loaded), Set.copyOf(failed));
        return new ArrayList<>(loaded.keySet());
    }

    public ArenaDefinition get(String id) {
        ArenaDefinition definition = snapshot.definitions.get(id);
        return definition != null && !snapshot.disabled.contains(definition.getId()) ? definition : null;
    }

    public List<ArenaDefinition> all() {
        List<ArenaDefinition> result = new ArrayList<>();
        for (ArenaDefinition definition : snapshot.definitions.values()) {
            if (!snapshot.disabled.contains(definition.getId())) {
                result.add(definition);
            }
        }
        return result;
    }

    public boolean isDisabled(String id) {
        return snapshot.disabled.contains(id);
    }

    public List<String> ids() {
        return new ArrayList<>(snapshot.definitions.keySet());
    }

    private record Snapshot(Map<String, ArenaDefinition> definitions, Set<String> disabled) {
    }
}
