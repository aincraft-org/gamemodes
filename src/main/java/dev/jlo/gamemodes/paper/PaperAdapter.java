package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.arena.ArenaCatalog;
import dev.jlo.gamemodes.arena.ArenaDefinition;
import dev.jlo.gamemodes.arena.ArenaInstanceManager;
import dev.jlo.gamemodes.arena.ArenaMode;
import dev.jlo.gamemodes.arena.GenerationId;
import dev.jlo.gamemodes.arena.TeamSlot;
import dev.jlo.gamemodes.player.PendingRestoreRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle owner for Paper-facing services and arena instances. */
public class PaperAdapter {
    private static final class Lease {
        private final ArenaDefinition definition;
        private final GenerationId generation;
        private final Path worldPath;

        private Lease(ArenaDefinition definition, GenerationId generation, Path worldPath) {
            this.definition = definition;
            this.generation = generation;
            this.worldPath = worldPath;
        }
    }

    private final JavaPlugin plugin;
    private final PendingRestoreRepository restoreRepository;
    private final Path arenasDirectory;
    private final ArenaCatalog catalog;
    private final ArenaInstanceManager instanceManager;
    private final Map<String, Lease> leases = new LinkedHashMap<>();
    private final PaperMatchService service;
    private final PaperUi ui;
    private final PaperSafetyListener safetyListener;
    private final PaperGameplayListener gameplayListener;
    private Integer taskId;

    public PaperAdapter(JavaPlugin plugin, PendingRestoreRepository restoreRepository) {
        this.plugin = plugin;
        this.restoreRepository = restoreRepository;
        this.arenasDirectory = plugin.getDataFolder().toPath().resolve("arenas");
        this.catalog = new ArenaCatalog(arenasDirectory, new YamlArenaDefinitionSource());
        Path worldContainer = plugin.getServer().getWorldContainer().toPath();
        this.instanceManager = new ArenaInstanceManager(
                worldContainer,
                worldContainer,
                new NioArenaFilesystem(),
                new PaperArenaWorldGateway(plugin));
        this.service = new PaperMatchService(
                (mode, requested) -> requested != null
                        ? requested
                        : catalog.all().stream()
                                .filter(definition -> toMode(definition.getMode()) == mode)
                                .findFirst()
                                .map(ArenaDefinition::getId)
                                .orElse(null),
                session -> {
                    ArenaDefinition definition = catalog.get(session.getArenaId());
                    if (definition == null) {
                        throw new IllegalArgumentException("Arena '" + session.getArenaId() + "' is not available");
                    }
                    if (toMode(definition.getMode()) != session.getMode()) {
                        throw new IllegalArgumentException("Arena mode mismatch");
                    }
                    GenerationId generation = new GenerationId(UUID.randomUUID().toString());
                    Path worldPath = instanceManager.load(definition, generation);
                    leases.put(session.getId(), new Lease(definition, generation, worldPath));
                },
                session -> {
                    Lease lease = leases.remove(session.getId());
                    if (lease != null) {
                        instanceManager.delete(lease.definition.getId(), lease.generation);
                    }
                },
                restoreRepository);
        this.ui = new PaperUi(plugin);
        this.safetyListener = new PaperSafetyListener(service.safetyPolicy(), world -> leases.values().stream()
                .anyMatch(lease -> lease.worldPath.getFileName().toString().equals(world.getName())));
        this.gameplayListener = new PaperGameplayListener(service, ui);
    }

    public PaperMatchService getService() {
        return service;
    }

    public void enable() {
        installDefaultDefinitions();
        reloadCatalog();
        service.installPlayerHooks(
                id -> service.capture(online(id)),
                id -> {
                    Player player = plugin.getServer().getPlayer(id);
                    if (player != null) {
                        service.restore(player);
                    }
                },
                (id, session, team) -> admit(online(id), session, team));
        service.installReloadHook(() -> service.sessions().isEmpty()
                ? reloadCatalog()
                : "Cannot reload while matches are queued or active");
        plugin.getServer().getPluginManager().registerEvents(safetyListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(gameplayListener, plugin);
        var command = plugin.getCommand("gamemodes");
        if (command == null) {
            throw new IllegalArgumentException("gamemodes command is not declared");
        }
        PaperCommandExecutor executor = new PaperCommandExecutor(service);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            service.tick();
            for (MatchSession session : service.sessions()) {
                for (Player player : sessionPlayers(session)) {
                    ui.update(player, session);
                }
            }
        }, 1L, 1L);
    }

    private void installDefaultDefinitions() {
        try {
            Files.createDirectories(arenasDirectory);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        for (String name : List.of("opr-default.yml", "siege-default.yml")) {
            Path path = arenasDirectory.resolve(name);
            if (!Files.exists(path)) {
                plugin.saveResource("arenas/" + name, false);
            }
        }
    }

    private String reloadCatalog() {
        List<String> ids = catalog.reload();
        provisionMissingTemplates(catalog.all());
        List<PaperMatchService.ArenaConfig> configs = new ArrayList<>();
        for (ArenaDefinition definition : catalog.all()) {
            Mode mode = toMode(definition.getMode());
            String modePath = mode.name().toLowerCase(java.util.Locale.ROOT);
            int capacity = plugin.getConfig().getInt(
                    "modes." + modePath + ".nominal-team-size",
                    mode == Mode.OPR ? 20 : 50);
            int quorum = plugin.getConfig().getInt(
                    "modes.small-server-quorum." + modePath,
                    Math.max(1, capacity / 2));
            configs.add(new PaperMatchService.ArenaConfig(definition.getId(), mode, capacity, quorum));
        }
        service.configureArenas(configs);
        return "Loaded " + ids.size() + " arenas: " + String.join(", ", ids);
    }

    private void provisionMissingTemplates(List<ArenaDefinition> definitions) {
        Path worldContainer = plugin.getServer().getWorldContainer().toPath();
        for (ArenaDefinition definition : definitions) {
            Path worldDirectory = worldContainer.resolve(definition.getTemplateWorld());
            if (Files.isDirectory(worldDirectory)) {
                continue;
            }
            World world = Bukkit.createWorld(new WorldCreator(definition.getTemplateWorld())
                    .type(WorldType.FLAT)
                    .generateStructures(false));
            if (world == null) {
                throw new IllegalArgumentException("Could not create template " + definition.getTemplateWorld());
            }
            int floorY = definition.getSpawns().stream()
                    .mapToInt(spawn -> spawn.getPosition().getY())
                    .min()
                    .orElseThrow() - 1;
            for (int x = definition.getBounds().getMinX(); x <= definition.getBounds().getMaxX(); x++) {
                for (int z = definition.getBounds().getMinZ(); z <= definition.getBounds().getMaxZ(); z++) {
                    world.getBlockAt(x, floorY, z).setType(Material.GRASS_BLOCK, false);
                }
            }
            int index = 0;
            for (var objective : definition.getObjectives()) {
                int x = (objective.getRegion().getMinX() + objective.getRegion().getMaxX()) / 2;
                int z = (objective.getRegion().getMinZ() + objective.getRegion().getMaxZ()) / 2;
                Material material = index == 0 ? Material.RED_WOOL : index == 1 ? Material.WHITE_WOOL : Material.BLUE_WOOL;
                world.getBlockAt(x, floorY + 1, z).setType(material, false);
                index++;
            }
            for (var structure : definition.getStructures()) {
                world.getBlockAt(structure.getPosition().getX(), structure.getPosition().getY(), structure.getPosition().getZ())
                        .setType(Material.IRON_BLOCK, false);
            }
            var firstSpawn = definition.getSpawns().getFirst().getPosition();
            world.setSpawnLocation(firstSpawn.getX(), firstSpawn.getY(), firstSpawn.getZ());
            world.getWorldBorder().setCenter(
                    (definition.getBounds().getMinX() + definition.getBounds().getMaxX()) / 2.0,
                    (definition.getBounds().getMinZ() + definition.getBounds().getMaxZ()) / 2.0);
            world.getWorldBorder().setSize((double) Math.max(
                    definition.getBounds().getMaxX() - definition.getBounds().getMinX(),
                    definition.getBounds().getMaxZ() - definition.getBounds().getMinZ()));
            world.save();
            if (!Bukkit.unloadWorld(world, true)) {
                throw new IllegalStateException("Could not unload template " + definition.getTemplateWorld());
            }
        }
    }

    private void admit(Player player, MatchSession session, TeamAssignment team) {
        Lease lease = leases.get(session.getId());
        if (lease == null) {
            throw new IllegalArgumentException("Arena instance was not allocated");
        }
        World world = Bukkit.getWorld(lease.worldPath.getFileName().toString());
        if (world == null) {
            throw new IllegalArgumentException("Arena instance world is not loaded");
        }
        TeamSlot slot = team == TeamAssignment.A ? TeamSlot.A : TeamSlot.B;
        var spawn = lease.definition.getSpawns().stream()
                .filter(candidate -> candidate.getTeam() == slot)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Arena has no spawn for team " + slot));
        if (!player.teleport(new Location(world, spawn.getPosition().getX() + 0.5,
                (double) spawn.getPosition().getY(), spawn.getPosition().getZ() + 0.5))) {
            throw new IllegalArgumentException("Player teleport into arena was rejected");
        }
        player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        player.getInventory().addItem(new ItemStack(Material.BOW));
        player.getInventory().addItem(new ItemStack(Material.ARROW, 32));
    }

    public void disable() {
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        service.shutdown(plugin.getServer()::getPlayer);
        ui.clearAll();
    }

    private Player online(UUID id) {
        Player player = plugin.getServer().getPlayer(id);
        if (player == null) {
            throw new IllegalArgumentException("Player is offline");
        }
        return player;
    }

    private List<Player> sessionPlayers(MatchSession session) {
        List<Player> result = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (session.teamOf(player.getUniqueId()) != null) {
                result.add(player);
            }
        }
        return result;
    }

    private static Mode toMode(ArenaMode mode) {
        return mode == ArenaMode.OPR ? Mode.OPR : Mode.SIEGE;
    }
}
