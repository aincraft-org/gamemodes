package dev.jlo.gamemodes.player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Core-only player state; all values are plain serializable primitives, never Bukkit objects. */
public final class PlayerSnapshot {
    private final UUID playerId;
    private final List<ItemStackSnapshot> inventory;
    private final ItemStackSnapshot cursor;
    private final List<ItemStackSnapshot> armor;
    private final ItemStackSnapshot offhand;
    private final List<EffectSnapshot> effects;
    private final Map<String, Double> attributes;
    private final double health;
    private final int food;
    private final double saturation;
    private final float experience;
    private final int level;
    private final String gameMode;
    private final boolean allowFlight;
    private final boolean flying;
    private final LocationSnapshot returnLocation;

    public PlayerSnapshot(UUID playerId, List<ItemStackSnapshot> inventory, ItemStackSnapshot cursor,
                          List<ItemStackSnapshot> armor, ItemStackSnapshot offhand, List<EffectSnapshot> effects,
                          Map<String, Double> attributes, double health, int food, double saturation,
                          float experience, int level, String gameMode, boolean allowFlight, boolean flying,
                          LocationSnapshot returnLocation) {
        this.playerId = playerId;
        this.inventory = inventory;
        this.cursor = cursor;
        this.armor = armor;
        this.offhand = offhand;
        this.effects = effects;
        this.attributes = attributes;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.experience = experience;
        this.level = level;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.returnLocation = returnLocation;
    }

    public UUID getPlayerId() { return playerId; }
    public List<ItemStackSnapshot> getInventory() { return inventory; }
    public ItemStackSnapshot getCursor() { return cursor; }
    public List<ItemStackSnapshot> getArmor() { return armor; }
    public ItemStackSnapshot getOffhand() { return offhand; }
    public List<EffectSnapshot> getEffects() { return effects; }
    public Map<String, Double> getAttributes() { return attributes; }
    public double getHealth() { return health; }
    public int getFood() { return food; }
    public double getSaturation() { return saturation; }
    public float getExperience() { return experience; }
    public int getLevel() { return level; }
    public String getGameMode() { return gameMode; }
    public boolean getAllowFlight() { return allowFlight; }
    public boolean getFlying() { return flying; }
    public LocationSnapshot getReturnLocation() { return returnLocation; }
    public byte[] encode() { return PlayerSnapshotCodec.encode(this); }
    public static PlayerSnapshot decode(byte[] bytes) { return PlayerSnapshotCodec.decode(bytes); }

    public PlayerSnapshot copy(UUID playerId, List<ItemStackSnapshot> inventory, ItemStackSnapshot cursor,
                               List<ItemStackSnapshot> armor, ItemStackSnapshot offhand, List<EffectSnapshot> effects,
                               Map<String, Double> attributes, double health, int food, double saturation,
                               float experience, int level, String gameMode, boolean allowFlight, boolean flying,
                               LocationSnapshot returnLocation) {
        return new PlayerSnapshot(playerId, inventory, cursor, armor, offhand, effects, attributes, health, food,
                saturation, experience, level, gameMode, allowFlight, flying, returnLocation);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerSnapshot that)) return false;
        return Double.compare(health, that.health) == 0 && food == that.food && Double.compare(saturation, that.saturation) == 0
                && Float.compare(experience, that.experience) == 0 && level == that.level && allowFlight == that.allowFlight
                && flying == that.flying && Objects.equals(playerId, that.playerId) && Objects.equals(inventory, that.inventory)
                && Objects.equals(cursor, that.cursor) && Objects.equals(armor, that.armor) && Objects.equals(offhand, that.offhand)
                && Objects.equals(effects, that.effects) && Objects.equals(attributes, that.attributes)
                && Objects.equals(gameMode, that.gameMode) && Objects.equals(returnLocation, that.returnLocation);
    }
    @Override public int hashCode() { return Objects.hash(playerId, inventory, cursor, armor, offhand, effects, attributes, health, food, saturation, experience, level, gameMode, allowFlight, flying, returnLocation); }
    @Override public String toString() { return "PlayerSnapshot[playerId=" + playerId + ", inventory=" + inventory + ", cursor=" + cursor + ", armor=" + armor + ", offhand=" + offhand + ", effects=" + effects + ", attributes=" + attributes + ", health=" + health + ", food=" + food + ", saturation=" + saturation + ", experience=" + experience + ", level=" + level + ", gameMode=" + gameMode + ", allowFlight=" + allowFlight + ", flying=" + flying + ", returnLocation=" + returnLocation + "]"; }
}
