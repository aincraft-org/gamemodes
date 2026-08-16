package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.arena.ArenaDefinition;
import dev.jlo.gamemodes.arena.ArenaDefinitionSource;
import dev.jlo.gamemodes.arena.ArenaMode;
import dev.jlo.gamemodes.arena.BlockPosition;
import dev.jlo.gamemodes.arena.Objective;
import dev.jlo.gamemodes.arena.Region;
import dev.jlo.gamemodes.arena.ResourceKind;
import dev.jlo.gamemodes.arena.ResourcePlacement;
import dev.jlo.gamemodes.arena.SiegePlacement;
import dev.jlo.gamemodes.arena.Spawn;
import dev.jlo.gamemodes.arena.StructureKind;
import dev.jlo.gamemodes.arena.StructurePlacement;
import dev.jlo.gamemodes.arena.TeamSlot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class YamlArenaDefinitionSource implements ArenaDefinitionSource {
    @Override
    public ArenaDefinition load(Path path) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        if (yaml.getInt("schema-version", 0) != 1) {
            throw new IllegalArgumentException("Unsupported arena schema");
        }
        ArenaMode mode = switch (yaml.getString("mode", "").toLowerCase()) {
            case "opr" -> ArenaMode.OPR;
            case "siege" -> ArenaMode.SIEGE;
            default -> throw new IllegalStateException("Unknown arena mode");
        };
        List<Spawn> spawns = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("spawns")) {
            spawns.add(new Spawn(requireString(map, "id"),
                    TeamSlot.valueOf(requireString(map, "team").toUpperCase()), position(map)));
        }
        List<Objective> objectives = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("objectives")) {
            objectives.add(new Objective(requireString(map, "id"), region(requireMap(map, "region"))));
        }
        List<StructurePlacement> structures = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("structures")) {
            structures.add(new StructurePlacement(requireString(map, "id"),
                    StructureKind.valueOf(requireString(map, "kind").toUpperCase()), position(map)));
        }
        List<ResourcePlacement> resources = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("resources")) {
            resources.add(new ResourcePlacement(requireString(map, "id"),
                    ResourceKind.valueOf(requireString(map, "kind").toUpperCase()), position(map)));
        }
        List<SiegePlacement> siegePlacements = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("siege-placements")) {
            siegePlacements.add(new SiegePlacement(requireString(map, "id"), position(map)));
        }
        return new ArenaDefinition(requireString(yaml, "id"), mode,
                requireString(yaml, "template-world"), region(requireSection(yaml, "bounds")),
                spawns, objectives, structures, resources, siegePlacements);
    }

    private static String requireString(YamlConfiguration yaml, String path) {
        String value = yaml.getString(path);
        if (value == null) throw new NullPointerException("Missing " + path);
        if (value.isBlank()) throw new IllegalArgumentException("Failed requirement.");
        return value;
    }

    private static ConfigurationSection requireSection(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) throw new NullPointerException("Missing " + path);
        return section;
    }

    private static Region region(ConfigurationSection section) {
        return new Region(section.getInt("min-x"), section.getInt("min-y"), section.getInt("min-z"),
                section.getInt("max-x"), section.getInt("max-y"), section.getInt("max-z"));
    }

    private static String requireString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalStateException("Missing " + key);
        }
        return string;
    }

    private static int requireInt(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) throw new IllegalStateException("Missing " + key);
        return number.intValue();
    }

    private static Map<?, ?> requireMap(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Map<?, ?> nested)) throw new IllegalStateException("Missing " + key);
        return nested;
    }

    private static BlockPosition position(Map<?, ?> map) {
        return new BlockPosition(requireInt(map, "x"), requireInt(map, "y"), requireInt(map, "z"));
    }

    private static Region region(Map<?, ?> map) {
        return new Region(requireInt(map, "min-x"), requireInt(map, "min-y"), requireInt(map, "min-z"),
                requireInt(map, "max-x"), requireInt(map, "max-y"), requireInt(map, "max-z"));
    }
}
