package dev.jlo.gamemodes.domain.siege;

import dev.jlo.gamemodes.domain.common.Team;
import java.time.Duration;

public enum SiegeWeapon {
    CANNON(Team.A, 2, 20, Duration.ofSeconds(2), 20),
    FIRE_LAUNCHER(Team.A, 2, 12, Duration.ofSeconds(3), 15),
    REPEATER(Team.A, 4, 60, Duration.ofMillis(500), 5),
    BALLISTA(Team.B, 2, 20, Duration.ofSeconds(2), 20),
    EXPLOSIVE_CANNON(Team.B, 2, 12, Duration.ofSeconds(3), 25),
    REPEATER_TURRET(Team.B, 4, 60, Duration.ofMillis(500), 5),
    FIRE_DROPPER(Team.B, 3, 20, Duration.ofSeconds(2), 15),
    HORN_OF_RESILIENCE(Team.B, 1, 3, Duration.ofSeconds(10), 0);

    private final Team team;
    private final int quota;
    private final int ammo;
    private final Duration cooldown;
    private final int damage;

    SiegeWeapon(Team team, int quota, int ammo, Duration cooldown, int damage) {
        this.team = team;
        this.quota = quota;
        this.ammo = ammo;
        this.cooldown = cooldown;
        this.damage = damage;
    }

    public Team getTeam() { return team; }
    public int getQuota() { return quota; }
    public int getAmmo() { return ammo; }
    public Duration getCooldown() { return cooldown; }
    public int getDamage() { return damage; }
}
