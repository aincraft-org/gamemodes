package dev.jlo.gamemodes.paper

import java.util.UUID

/** Safety actions that are never allowed for active participants without a plugin authorization token. */
enum class SafetyAction {
    BLOCK_BREAK, BLOCK_PLACE, BUCKET, FIRE, TNT, EXPLOSION, PISTON, HOPPER,
    PORTAL, BED, PEARL, CHORUS, VEHICLE, TELEPORT
}

class SafetyPolicy {
    private val active = linkedSetOf<UUID>()

    @Synchronized
    fun activate(player: UUID) { active += player }

    @Synchronized
    fun deactivate(player: UUID) { active -= player }

    @Synchronized
    fun isActive(player: UUID): Boolean = player in active

    @Synchronized
    fun blocks(player: UUID, action: SafetyAction, authorized: Boolean = false): Boolean =
        player in active && !authorized
}
