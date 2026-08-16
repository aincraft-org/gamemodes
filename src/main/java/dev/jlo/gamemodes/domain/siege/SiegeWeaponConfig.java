package dev.jlo.gamemodes.domain.siege;

import java.time.Duration;
import java.util.Objects;

public final class SiegeWeaponConfig {
    private final int quota;
    private final int ammo;
    private final Duration cooldown;
    private final int damage;

    public SiegeWeaponConfig(int quota, int ammo, Duration cooldown, int damage) {
        if (quota < 0 || ammo < 0 || damage < 0) throw new IllegalArgumentException("quota, ammo, and damage must be non-negative");
        if (cooldown.isNegative()) throw new IllegalArgumentException("cooldown must not be negative");
        this.quota = quota; this.ammo = ammo; this.cooldown = cooldown; this.damage = damage;
    }
    public int getQuota() { return quota; }
    public int getAmmo() { return ammo; }
    public Duration getCooldown() { return cooldown; }
    public int getDamage() { return damage; }
    public SiegeWeaponConfig copy(int quota, int ammo, Duration cooldown, int damage) { return new SiegeWeaponConfig(quota, ammo, cooldown, damage); }
    @Override public boolean equals(Object o) { return this == o || (o instanceof SiegeWeaponConfig x && quota == x.quota && ammo == x.ammo && damage == x.damage && Objects.equals(cooldown, x.cooldown)); }
    @Override public int hashCode() { return Objects.hash(quota, ammo, cooldown, damage); }
    @Override public String toString() { return "SiegeWeaponConfig(quota="+quota+", ammo="+ammo+", cooldown="+cooldown+", damage="+damage+")"; }
}
