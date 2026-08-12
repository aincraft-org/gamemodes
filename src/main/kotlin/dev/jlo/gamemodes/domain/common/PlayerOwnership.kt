package dev.jlo.gamemodes.domain.common

import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface Ownership {
    data class Queue(val id: String) : Ownership
    data class Match(val id: String) : Ownership
}

class PlayerOwnership {
    private data class Reservation(val owner: Ownership, val expiresAt: Instant?)

    private val owners = mutableMapOf<UUID, Reservation>()

    fun claim(player: UUID, owner: Ownership) {
        check(player !in owners) { "Player is already owned" }
        owners[player] = Reservation(owner, null)
    }

    fun ownerOf(player: UUID, now: Instant? = null): Ownership? {
        expireIfNeeded(player, now)
        return owners[player]?.owner
    }

    fun release(player: UUID) {
        owners.remove(player)
    }
    fun releaseOwner(owner: Ownership) {
        owners.entries.removeIf { it.value.owner == owner }
    }

    fun releaseQueue(id: String): Boolean {
        val owner = Ownership.Queue(id)
        val player = owners.entries.firstOrNull { it.value.owner == owner }?.key ?: return false
        owners.remove(player)
        return true
    }

    fun releaseQueue(player: UUID): Boolean =
        owners[player]?.owner is Ownership.Queue && owners.remove(player) != null
    fun disconnect(player: UUID, at: Instant, reservationDuration: Duration) {
        require(!reservationDuration.isNegative && !reservationDuration.isZero) { "Reservation duration must be positive" }
        val reservation = owners[player] ?: return
        owners[player] = reservation.copy(expiresAt = at.plus(reservationDuration))
    }



    fun isReserved(player: UUID, at: Instant): Boolean {
        val reservation = owners[player] ?: return false
        if (reservation.expiresAt != null && !reservation.expiresAt.isAfter(at)) {
            owners.remove(player)
            return false
        }
        return reservation.expiresAt != null
    }

    fun expireReservation(player: UUID, at: Instant): Ownership? {
        val reservation = owners[player] ?: return null
        if (reservation.expiresAt != null && !reservation.expiresAt.isAfter(at)) {
            owners.remove(player)
            return reservation.owner
        }
        return null
    }

    private fun expireIfNeeded(player: UUID, now: Instant?) {
        if (now != null) expireReservation(player, now)
    }
}
