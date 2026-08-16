package dev.jlo.gamemodes.domain.common;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerOwnership {
    private static final class Reservation {
        private final Ownership owner;
        private final Instant expiresAt;

        private Reservation(Ownership owner, Instant expiresAt) {
            this.owner = owner;
            this.expiresAt = expiresAt;
        }

        private Ownership getOwner() {
            return owner;
        }

        private Instant getExpiresAt() {
            return expiresAt;
        }

        private Reservation copy(Ownership owner, Instant expiresAt) {
            return new Reservation(owner, expiresAt);
        }
    }

    private final Map<UUID, Reservation> owners = new HashMap<>();

    public void claim(UUID player, Ownership owner) {
        if (owners.containsKey(player)) {
            throw new IllegalStateException("Player is already owned");
        }
        owners.put(player, new Reservation(owner, null));
    }

    public Ownership ownerOf(UUID player) {
        return ownerOf(player, null);
    }

    public Ownership ownerOf(UUID player, Instant now) {
        expireIfNeeded(player, now);
        Reservation reservation = owners.get(player);
        return reservation == null ? null : reservation.getOwner();
    }

    public void release(UUID player) {
        owners.remove(player);
    }

    public void releaseOwner(Ownership owner) {
        owners.entrySet().removeIf(entry -> entry.getValue().getOwner().equals(owner));
    }

    public boolean releaseQueue(String id) {
        Ownership.Queue owner = new Ownership.Queue(id);
        UUID player = owners.entrySet().stream()
                .filter(entry -> entry.getValue().getOwner().equals(owner))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (player == null) {
            return false;
        }
        owners.remove(player);
        return true;
    }

    public boolean releaseQueue(UUID player) {
        Reservation reservation = owners.get(player);
        return reservation != null
                && reservation.getOwner() instanceof Ownership.Queue
                && owners.remove(player) != null;
    }

    public void disconnect(UUID player, Instant at, Duration reservationDuration) {
        if (reservationDuration.isNegative() || reservationDuration.isZero()) {
            throw new IllegalArgumentException("Reservation duration must be positive");
        }
        Reservation reservation = owners.get(player);
        if (reservation == null) {
            return;
        }
        owners.put(player, reservation.copy(reservation.getOwner(), at.plus(reservationDuration)));
    }

    public boolean isReserved(UUID player, Instant at) {
        Reservation reservation = owners.get(player);
        if (reservation == null) {
            return false;
        }
        if (reservation.getExpiresAt() != null && !reservation.getExpiresAt().isAfter(at)) {
            owners.remove(player);
            return false;
        }
        return reservation.getExpiresAt() != null;
    }

    public Ownership expireReservation(UUID player, Instant at) {
        Reservation reservation = owners.get(player);
        if (reservation == null) {
            return null;
        }
        if (reservation.getExpiresAt() != null && !reservation.getExpiresAt().isAfter(at)) {
            owners.remove(player);
            return reservation.getOwner();
        }
        return null;
    }

    private void expireIfNeeded(UUID player, Instant now) {
        if (now != null) {
            expireReservation(player, now);
        }
    }
}
