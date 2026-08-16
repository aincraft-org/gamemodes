package dev.jlo.gamemodes.paper;

import dev.jlo.gamemodes.domain.common.MatchPhase;
import dev.jlo.gamemodes.player.EffectSnapshot;
import dev.jlo.gamemodes.player.ItemStackSnapshot;
import dev.jlo.gamemodes.player.LocationSnapshot;
import dev.jlo.gamemodes.player.PendingRestore;
import dev.jlo.gamemodes.player.PendingRestoreRepository;
import dev.jlo.gamemodes.player.PlayerSnapshot;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PaperMatchService implements CommandService {
    public static final class ArenaConfig {
        private final String id;
        private final Mode mode;
        private final int capacityPerTeam;
        private final int quorumPerTeam;

        public ArenaConfig(String id, Mode mode, int capacityPerTeam, int quorumPerTeam) {
            this.id = id;
            this.mode = mode;
            this.capacityPerTeam = capacityPerTeam;
            this.quorumPerTeam = quorumPerTeam;
        }

        public String getId() { return id; }
        public Mode getMode() { return mode; }
        public int getCapacityPerTeam() { return capacityPerTeam; }
        public int getQuorumPerTeam() { return quorumPerTeam; }
    }

    private final BiFunction<Mode, String, String> arenaProvider;
    private final Consumer<MatchSession> onSessionCreated;
    private final Consumer<MatchSession> onSessionRemoved;
    private final PendingRestoreRepository restoreRepository;
    private final Map<String, ArenaConfig> configuredArenas = new LinkedHashMap<>();
    private final Map<String, MatchSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, MatchSession> owners = new LinkedHashMap<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new LinkedHashMap<>();
    private final SafetyPolicy safety = new SafetyPolicy();
    private Consumer<UUID> captureHook = ignored -> {};
    private Consumer<UUID> restoreHook = ignored -> {};
    private TriConsumer<UUID, MatchSession, TeamAssignment> admitHook = (a, b, c) -> {};
    private Function<Void, String> reloadHook = ignored -> "No arena catalog is installed";

    public PaperMatchService() {
        this((mode, arena) -> arena, ignored -> {}, ignored -> {}, null);
    }

    public PaperMatchService(BiFunction<Mode, String, String> arenaProvider,
                             Consumer<MatchSession> onSessionCreated,
                             Consumer<MatchSession> onSessionRemoved,
                             PendingRestoreRepository restoreRepository) {
        this.arenaProvider = arenaProvider;
        this.onSessionCreated = onSessionCreated;
        this.onSessionRemoved = onSessionRemoved;
        this.restoreRepository = restoreRepository;
    }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> { void accept(A a, B b, C c); }

    public static final class ArenaConfigBuilder {}

    public synchronized void configureArenas(Collection<ArenaConfig> arenas) {
        configuredArenas.clear();
        for (ArenaConfig arena : arenas) {
            if (!(arena.getCapacityPerTeam() > 0 && arena.getQuorumPerTeam() >= 1
                    && arena.getQuorumPerTeam() <= arena.getCapacityPerTeam())) {
                throw new IllegalArgumentException("Failed requirement.");
            }
            if (configuredArenas.putIfAbsent(arena.getId(), arena) != null) {
                throw new IllegalArgumentException("Duplicate arena ID " + arena.getId());
            }
        }
    }

    public void installPlayerHooks(Consumer<UUID> capture, Consumer<UUID> restore,
                                   TriConsumer<UUID, MatchSession, TeamAssignment> admit) {
        captureHook = capture;
        restoreHook = restore;
        admitHook = admit;
    }

    public void installReloadHook(SupplierString reload) { reloadHook = ignored -> reload.get(); }

    @FunctionalInterface
    public interface SupplierString { String get(); }

    public SafetyPolicy safetyPolicy() { return safety; }
    public List<MatchSession> sessions() { return new ArrayList<>(sessions.values()); }
    public ArenaConfig arenaConfig(String id) { return configuredArenas.get(id); }

    @Override
    public TeamAssignment join(UUID player, Mode mode, String arena) {
        if (owners.containsKey(player)) throw new IllegalStateException("Player is already queued or in a match");
        String arenaId = arenaProvider.apply(mode, arena);
        if (arenaId == null) throw new IllegalStateException("No configured " + mode.name() + " arena is available");
        ArenaConfig configured = configuredArenas.get(arenaId);
        if (configured == null) throw new IllegalStateException("Arena '" + arenaId + "' is not configured");
        if (configured.getMode() != mode) throw new IllegalArgumentException("Arena '" + arenaId + "' is unavailable for " + mode.name());
        MatchSession session = sessions.values().stream()
                .filter(it -> it.getMode() == mode && it.getArenaId().equals(arenaId) && it.getPhase() == MatchPhase.WAITING)
                .findFirst().orElseGet(() -> {
                    MatchSession created = new MatchSession(mode, arenaId, configured.getCapacityPerTeam(), configured.getQuorumPerTeam());
                    onSessionCreated.accept(created);
                    sessions.put(created.getId(), created);
                    return created;
                });
        captureHook.accept(player);
        try {
            TeamAssignment team = session.join(player);
            owners.put(player, session);
            admitHook.accept(player, session, team);
            safety.activate(player);
            return team;
        } catch (RuntimeException failure) {
            owners.remove(player);
            safety.deactivate(player);
            if (session.teamOf(player) != null) session.leave(player);
            restoreHook.accept(player);
            throw failure;
        }
    }

    @Override
    public boolean leave(UUID player) {
        MatchSession session = owners.remove(player);
        if (session == null) return false;
        session.leave(player);
        safety.deactivate(player);
        restoreHook.accept(player);
        return true;
    }

    @Override public boolean ready(UUID player) { MatchSession s = owners.get(player); return s != null && s.ready(player); }
    @Override public String status(UUID player) { MatchSession s = owners.get(player); return s == null ? "NONE" : s.getMode().name() + " " + s.getPhase().name(); }
    @Override public TeamAssignment team(UUID player) { MatchSession s = owners.get(player); return s == null ? null : s.teamOf(player); }

    @Override
    public String admin(UUID player, String action, List<String> args) {
        return switch (action.toLowerCase()) {
            case "start" -> {
                String arena = args.isEmpty() ? null : args.get(0);
                boolean started = sessions.values().stream().filter(s -> arena == null || s.getArenaId().equals(arena)).anyMatch(MatchSession::startIfReady);
                yield started ? "started" : "not ready";
            }
            case "stop" -> {
                String arena = args.isEmpty() ? null : args.get(0);
                List<MatchSession> selected = sessions.values().stream().filter(s -> arena == null || s.getArenaId().equals(arena)).toList();
                selected.forEach(this::terminate);
                yield "stopped " + selected.size();
            }
            case "arena" -> {
                String sub = args.isEmpty() ? null : args.get(0).toLowerCase();
                if (sub == null || sub.equals("list")) {
                    StringBuilder result = new StringBuilder("arenas: ");
                    boolean first = true;
                    for (ArenaConfig a : configuredArenas.values()) { if (!first) result.append(", "); first = false; result.append(a.getId()).append('(').append(a.getMode()).append(')'); }
                    yield result.toString();
                }
                yield sub.equals("validate") ? "validated " + configuredArenas.size() + " arenas" : "Usage: arena <list|validate>";
            }
            case "reload" -> reloadHook.apply(null);
            case "debug" -> {
                StringBuilder result = new StringBuilder("sessions: ");
                boolean first = true;
                for (MatchSession s : sessions.values()) { if (!first) result.append(", "); first = false; result.append(s.getMode()).append('/').append(s.getArenaId()).append('/').append(s.getPhase()); }
                yield result.toString();
            }
            default -> "unknown admin action";
        };
    }

    private void terminate(MatchSession session) {
        List<UUID> players = owners.entrySet().stream().filter(e -> e.getValue() == session).map(Map.Entry::getKey).toList();
        session.cleanup();
        for (UUID player : players) { safety.deactivate(player); restoreHook.accept(player); owners.remove(player); }
        sessions.remove(session.getId());
        onSessionRemoved.accept(session);
    }

    public void tick() {
        for (MatchSession session : new ArrayList<>(sessions.values())) {
            session.startIfReady(); session.advance();
            if (session.getPhase() == MatchPhase.RESOLVING || session.getPhase() == MatchPhase.CLEANUP) terminate(session);
        }
    }

    public void shutdown(Function<UUID, Player> players) {
        for (MatchSession session : new ArrayList<>(sessions.values())) terminate(session);
        for (UUID player : new ArrayList<>(owners.keySet())) {
            Player p = players.apply(player);
            if (p != null) restore(p); else restoreHook.accept(player);
            safety.deactivate(player);
        }
        owners.clear(); sessions.clear();
    }

    public boolean isParticipant(UUID player) { return owners.containsKey(player); }
    public void disconnect(UUID player) { MatchSession s = owners.get(player); if (s != null) s.disconnect(player); }
    public boolean recordCombat(UUID attacker, UUID victim) { MatchSession s = owners.get(attacker); return s != null && owners.get(victim) == s && s.recordCombat(attacker, victim); }
    public boolean playerDied(UUID player, UUID killer) { MatchSession s = owners.get(player); if (s == null) return false; if (killer != null && owners.get(killer) != s) return s.playerDied(player); return s.playerDied(player, killer); }
    public Location respawn(Player player) { MatchSession s = owners.get(player.getUniqueId()); return s == null ? null : s.respawnLocation(player); }

    public void capture(Player player) {
        List<ItemStackSnapshot> inventory = new ArrayList<>(); for (ItemStack item : player.getInventory().getStorageContents()) inventory.add(itemSnapshot(item));
        ItemStack cursor = player.getItemOnCursor(); ItemStack armor = null;
        List<ItemStackSnapshot> armorSnapshots = new ArrayList<>(); for (ItemStack item : player.getInventory().getArmorContents()) armorSnapshots.add(itemSnapshot(item));
        ItemStack offhand = player.getInventory().getItemInOffHand();
        List<EffectSnapshot> effects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) effects.add(new EffectSnapshot(effect.getType().getKey().toString(), effect.getAmplifier(), effect.getDuration(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? player.getHealth() : player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        Location location = player.getLocation();
        PlayerSnapshot snapshot = new PlayerSnapshot(player.getUniqueId(), inventory, itemSnapshotOrNull(cursor), armorSnapshots, itemSnapshotOrNull(offhand), effects, Map.of("minecraft:max_health", maxHealth), player.getHealth(), player.getFoodLevel(), player.getSaturation(), player.getExp(), player.getLevel(), player.getGameMode().name(), player.getAllowFlight(), player.isFlying(), new LocationSnapshot(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()));
        if (restoreRepository != null) restoreRepository.put(snapshot); else snapshots.put(player.getUniqueId(), snapshot);
        player.getInventory().clear(); player.getInventory().setArmorContents(new ItemStack[0]); player.setItemOnCursor(new ItemStack(Material.AIR));
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
        player.setGameMode(GameMode.ADVENTURE); player.setAllowFlight(false); player.setFlying(false);
    }

    private ItemStackSnapshot itemSnapshotOrNull(ItemStack item) { return item == null || item.getType().isAir() ? null : itemSnapshot(item); }
    private ItemStackSnapshot itemSnapshot(ItemStack item) { if (item == null || item.getType().isAir()) return new ItemStackSnapshot(Material.AIR.name(), 0); return new ItemStackSnapshot(item.getType().name(), item.getAmount(), Base64.getEncoder().encodeToString(item.serializeAsBytes())); }

    public void restore(Player player) {
        PendingRestore pending = restoreRepository == null ? null : restoreRepository.claim(player.getUniqueId());
        PlayerSnapshot snapshot = pending == null ? snapshots.remove(player.getUniqueId()) : pending.getSnapshot(); if (snapshot == null) return;
        try {
            player.getInventory().setStorageContents(snapshot.getInventory().stream().map(this::restoreItem).toArray(ItemStack[]::new));
            player.getInventory().setArmorContents(snapshot.getArmor().stream().map(this::restoreItem).toArray(ItemStack[]::new));
            player.getInventory().setItemInOffHand(restoreItem(snapshot.getOffhand()) == null ? new ItemStack(Material.AIR) : restoreItem(snapshot.getOffhand())); player.setItemOnCursor(restoreItem(snapshot.getCursor()) == null ? new ItemStack(Material.AIR) : restoreItem(snapshot.getCursor()));
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
            for (EffectSnapshot effect : snapshot.getEffects()) { NamespacedKey key = NamespacedKey.fromString(effect.getType()); if (key == null) continue; PotionEffectType type = PotionEffectType.getByKey(key); if (type != null) player.addPotionEffect(new PotionEffect(type, effect.getDurationTicks(), effect.getAmplifier(), effect.getAmbient(), effect.getParticles(), effect.getIcon())); }
            Double max = snapshot.getAttributes().get("minecraft:max_health"); if (max != null && player.getAttribute(Attribute.MAX_HEALTH) != null) player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(max);
            double currentMax = player.getAttribute(Attribute.MAX_HEALTH) == null ? snapshot.getHealth() : player.getAttribute(Attribute.MAX_HEALTH).getValue(); player.setHealth(Math.min(snapshot.getHealth(), currentMax)); player.setFoodLevel(snapshot.getFood()); player.setSaturation((float) snapshot.getSaturation()); player.setExp(snapshot.getExperience()); player.setLevel(snapshot.getLevel()); player.setGameMode(GameMode.valueOf(snapshot.getGameMode())); player.setAllowFlight(snapshot.getAllowFlight()); player.setFlying(snapshot.getFlying());
            LocationSnapshot location = snapshot.getReturnLocation(); if (location != null) { var server = player.getServer(); var world = server.getWorld(location.getWorld()); if (world == null) world = server.getWorlds().get(0); player.teleport(new Location(world, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch())); }
            if (pending != null && !restoreRepository.markRestored(player.getUniqueId())) throw new IllegalStateException("Failed to mark player restore complete");
        } catch (RuntimeException failure) { if (pending != null) restoreRepository.markFailed(player.getUniqueId(), failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()); throw failure; }
    }

    private ItemStack restoreItem(ItemStackSnapshot snapshot) { if (snapshot == null || snapshot.getAmount() <= 0 || snapshot.getMaterial().equals(Material.AIR.name())) return null; return snapshot.getMetadata() != null && !snapshot.getMetadata().isEmpty() ? ItemStack.deserializeBytes(Base64.getDecoder().decode(snapshot.getMetadata())) : new ItemStack(Material.valueOf(snapshot.getMaterial()), snapshot.getAmount()); }
}
