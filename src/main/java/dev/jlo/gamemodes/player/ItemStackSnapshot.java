package dev.jlo.gamemodes.player;

import java.util.Objects;

public final class ItemStackSnapshot {
    private final String material;
    private final int amount;
    private final String metadata;
    public ItemStackSnapshot(String material, int amount) { this(material, amount, ""); }
    public ItemStackSnapshot(String material, int amount, String metadata) { this.material = material; this.amount = amount; this.metadata = metadata; }
    public String getMaterial() { return material; }
    public int getAmount() { return amount; }
    public String getMetadata() { return metadata; }
    public ItemStackSnapshot copy(String material, int amount, String metadata) { return new ItemStackSnapshot(material, amount, metadata); }
    @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof ItemStackSnapshot that)) return false; return amount == that.amount && Objects.equals(material, that.material) && Objects.equals(metadata, that.metadata); }
    @Override public int hashCode() { return Objects.hash(material, amount, metadata); }
    @Override public String toString() { return "ItemStackSnapshot[material=" + material + ", amount=" + amount + ", metadata=" + metadata + "]"; }
}
